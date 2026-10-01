from __future__ import annotations

import json
from pathlib import Path
import subprocess
import tempfile
import unittest

from v2.external import (
    ClaimRetentionDisposition,
    ControllerIssueOwner,
    ExternalProducerClaim,
)
from v2.external_service import (
    CLAIM_COMMAND,
    RELEASE_COMMAND,
    ExternalClaimLedger,
    ExternalClaimReadCommandValidator,
    ExternalClaimStore,
    ExternalControlKind,
    GhExternalClaimObserver,
    apply_external_control,
    parse_external_control,
    refresh_external_claims,
)


def payload(body, *, issue=876, actor="ni-da-ba", comment_id=123):
    return {
        "action": "created",
        "issue": {"number": issue},
        "comment": {
            "id": comment_id,
            "body": body,
            "user": {"login": actor},
        },
    }


def claim(issue=876, *, pr=None):
    return ExternalProducerClaim(
        issue_number=issue,
        claimed_by="ni-da-ba",
        lane="Implementation",
        branch=f"external/{issue}",
        pr_number=pr,
    )


class ExternalControlParserTest(unittest.TestCase):
    def test_claim_parser_preserves_legacy_option_contract(self):
        value = parse_external_control(
            event="issue_comment",
            payload=payload(
                f'{CLAIM_COMMAND} pr=762 lane=implementation branch="platform/613-aero"'
            ),
            trusted_actors=("ni-da-ba",),
        )
        self.assertEqual(value.kind, ExternalControlKind.CLAIM)
        self.assertTrue(value.trusted_actor)
        self.assertEqual(value.pr_number, 762)
        self.assertEqual(value.lane, "Implementation")
        self.assertEqual(value.branch, "platform/613-aero")
        self.assertEqual(value.issue_number, 876)
        self.assertEqual(value.source_comment_id, 123)

    def test_release_parser_requires_no_options(self):
        value = parse_external_control(
            event="issue_comment",
            payload=payload(RELEASE_COMMAND),
            trusted_actors=("ni-da-ba",),
        )
        self.assertEqual(value.kind, ExternalControlKind.RELEASE)
        with self.assertRaises(ValueError):
            parse_external_control(
                event="issue_comment",
                payload=payload(RELEASE_COMMAND + " pr=1"),
                trusted_actors=("ni-da-ba",),
            )

    def test_untrusted_command_is_recognized_but_not_authorized(self):
        value = parse_external_control(
            event="issue_comment",
            payload=payload(CLAIM_COMMAND, actor="mallory"),
            trusted_actors=("ni-da-ba",),
        )
        self.assertFalse(value.trusted_actor)

    def test_unrelated_or_non_created_comment_is_ignored(self):
        self.assertIsNone(
            parse_external_control(
                event="push",
                payload={},
                trusted_actors=("ni-da-ba",),
            )
        )
        raw = payload("ordinary prose")
        self.assertIsNone(
            parse_external_control(
                event="issue_comment",
                payload=raw,
                trusted_actors=("ni-da-ba",),
            )
        )
        raw["action"] = "edited"
        self.assertIsNone(
            parse_external_control(
                event="issue_comment",
                payload=raw,
                trusted_actors=("ni-da-ba",),
            )
        )

    def test_malformed_recognized_claim_fails_closed(self):
        bodies = (
            CLAIM_COMMAND + " pr=0",
            CLAIM_COMMAND + " lane=nope",
            CLAIM_COMMAND + " branch=bad?branch",
            CLAIM_COMMAND + " unknown=x",
            CLAIM_COMMAND + " pr=1 pr=2",
            CLAIM_COMMAND + " naked-option",
        )
        for body in bodies:
            with self.subTest(body=body), self.assertRaises(ValueError):
                parse_external_control(
                    event="issue_comment",
                    payload=payload(body),
                    trusted_actors=("ni-da-ba",),
                )


class ExternalLedgerTest(unittest.TestCase):
    def test_normalized_cutover_projection_import(self):
        projection = {
            "external_claims": [
                claim(613, pr=762).as_dict(),
                claim(754, pr=769).as_dict(),
            ]
        }
        ledger = ExternalClaimLedger.from_legacy_projection(projection)
        self.assertEqual(
            tuple(item.issue_number for item in ledger.claims),
            (613, 754),
        )

    def test_raw_legacy_projection_import_and_inactive_skip(self):
        projection = {
            "external_producer_claims": {
                "613": {
                    **claim(613, pr=762).as_dict(),
                    "claimed_at": "diagnostic only",
                },
                "754": {
                    "issue_number": 754,
                    "claimed_by": "ni-da-ba",
                    "state": "retired",
                },
            }
        }
        ledger = ExternalClaimLedger.from_legacy_projection(projection)
        self.assertEqual(tuple(item.issue_number for item in ledger.claims), (613,))

    def test_store_restart_round_trip(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            store = ExternalClaimStore.for_root(root)
            ledger = ExternalClaimLedger((claim(613, pr=762), claim(754, pr=769)))
            store.save(ledger)
            restarted = ExternalClaimStore.for_root(root).load()
            self.assertEqual(restarted, ledger)
            self.assertEqual(
                store.adapter.path.read_bytes(),
                store.adapter.backup_path.read_bytes(),
            )

    def test_claim_admission_and_explicit_release_use_pure_policy(self):
        empty = ExternalClaimLedger()
        control = parse_external_control(
            event="issue_comment",
            payload=payload(CLAIM_COMMAND + " lane=Implementation branch=manual/876"),
            trusted_actors=("ni-da-ba",),
        )
        rejected = apply_external_control(
            ledger=empty,
            control=control,
            controller_owner=ControllerIssueOwner.PENDING_WORKER,
        )
        self.assertFalse(rejected.accepted)
        self.assertEqual(rejected.ledger, empty)

        accepted = apply_external_control(
            ledger=empty,
            control=control,
            controller_owner=ControllerIssueOwner.NONE,
        )
        self.assertTrue(accepted.accepted)
        self.assertEqual(accepted.ledger.get(876).branch, "manual/876")

        release = parse_external_control(
            event="issue_comment",
            payload=payload(RELEASE_COMMAND),
            trusted_actors=("ni-da-ba",),
        )
        released = apply_external_control(
            ledger=accepted.ledger,
            control=release,
            controller_owner=ControllerIssueOwner.NONE,
        )
        self.assertTrue(released.accepted)
        self.assertIsNone(released.ledger.get(876))

    def test_untrusted_claim_and_release_never_change_ledger(self):
        ledger = ExternalClaimLedger((claim(876),))
        for body in (CLAIM_COMMAND, RELEASE_COMMAND):
            control = parse_external_control(
                event="issue_comment",
                payload=payload(body, actor="mallory"),
                trusted_actors=("ni-da-ba",),
            )
            result = apply_external_control(
                ledger=ledger,
                control=control,
                controller_owner=ControllerIssueOwner.NONE,
            )
            self.assertFalse(result.accepted)
            self.assertEqual(result.ledger, ledger)


class FakeResult:
    def __init__(self, value):
        self.stdout = json.dumps(value)
        self.stderr = ""


class ExternalClaimObservationTest(unittest.TestCase):
    def test_bound_pr_retention_and_terminal_retirement(self):
        for value, expected in (
            ({"state": "OPEN", "mergedAt": None}, ClaimRetentionDisposition.KEEP),
            ({"state": "CLOSED", "mergedAt": None}, ClaimRetentionDisposition.RETIRE),
            (
                {"state": "CLOSED", "mergedAt": "2026-09-18T00:00:00Z"},
                ClaimRetentionDisposition.RETIRE,
            ),
        ):
            def runner(args, **kwargs):
                return FakeResult(value)

            with self.subTest(value=value), tempfile.TemporaryDirectory() as td:
                decision = GhExternalClaimObserver(
                    root=Path(td),
                    repo="ni-da-ba/skyforge",
                    claim=claim(613, pr=762),
                    runner=runner,
                ).observe()
                self.assertEqual(decision.disposition, expected)

    def test_unbound_claim_retires_only_on_provable_issue_closure(self):
        for state, expected in (
            ("open", ClaimRetentionDisposition.KEEP),
            ("closed", ClaimRetentionDisposition.RETIRE),
        ):
            def runner(args, **kwargs):
                return FakeResult({"state": state})

            with self.subTest(state=state), tempfile.TemporaryDirectory() as td:
                decision = GhExternalClaimObserver(
                    root=Path(td),
                    repo="ni-da-ba/skyforge",
                    claim=claim(754),
                    runner=runner,
                ).observe()
                self.assertEqual(decision.disposition, expected)

    def test_remote_unavailable_or_malformed_retains_claim_fail_closed(self):
        def unavailable(args, **kwargs):
            raise subprocess.CalledProcessError(1, args, stderr="offline")

        def malformed(args, **kwargs):
            return FakeResult({"state": "mystery"})

        for runner in (unavailable, malformed):
            with self.subTest(runner=runner), tempfile.TemporaryDirectory() as td:
                decision = GhExternalClaimObserver(
                    root=Path(td),
                    repo="ni-da-ba/skyforge",
                    claim=claim(613, pr=762),
                    runner=runner,
                ).observe()
                self.assertEqual(
                    decision.disposition,
                    ClaimRetentionDisposition.KEEP,
                )

    def test_read_validator_has_no_mutation_escape(self):
        bound = ExternalClaimReadCommandValidator(
            repo="ni-da-ba/skyforge",
            claim=claim(613, pr=762),
        )
        allowed = next(iter(bound.allowed()))
        self.assertEqual(bound.validate(allowed), allowed)
        forbidden = (
            ["gh", "pr", "close", "762", "--repo", "ni-da-ba/skyforge"],
            ["gh", "issue", "close", "613", "--repo", "ni-da-ba/skyforge"],
            ["gh", "api", "--method", "PATCH", "repos/ni-da-ba/skyforge/issues/613"],
            ["git", "push", "origin", "main"],
        )
        for command in forbidden:
            with self.subTest(command=command), self.assertRaises(ValueError):
                bound.validate(command)

    def test_refresh_retires_only_exact_terminal_claims(self):
        ledger = ExternalClaimLedger(
            (
                claim(613, pr=762),
                claim(754),
                claim(800, pr=801),
            )
        )

        def runner(args, **kwargs):
            command = tuple(args)
            if "762" in command:
                return FakeResult({"state": "OPEN", "mergedAt": None})
            if "801" in command:
                return FakeResult(
                    {"state": "CLOSED", "mergedAt": "2026-09-18T00:00:00Z"}
                )
            if command[-1].endswith("/754"):
                return FakeResult({"state": "closed"})
            raise AssertionError(command)

        with tempfile.TemporaryDirectory() as td:
            result = refresh_external_claims(
                ledger=ledger,
                root=Path(td),
                repo="ni-da-ba/skyforge",
                runner=runner,
            )
        self.assertEqual(
            tuple(item.issue_number for item in result.ledger.claims),
            (613,),
        )
        self.assertEqual(result.retired_issue_numbers, (754, 800))


if __name__ == "__main__":
    unittest.main()
