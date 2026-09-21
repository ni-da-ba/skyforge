from __future__ import annotations

import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

from test_platform_v2_scope_promotion import REPO_ROOT, git, prepare, write_state
from v2.effects import EffectKind
from v2.events import DurableEvent
from v2.external import ExternalProducerClaim
from v2.external_service import ExternalClaimLedger, ExternalClaimStore
from v2.hosted_admission import HostedAdmissionStore
from v2.hosted_completion import (
    HostedCompletionLedger,
    HostedCompletionRecord,
    HostedCompletionStatus,
    HostedCompletionStore,
)
from v2.hosted_task_plan import (
    HostedTaskDispatchPlan,
    HostedTaskPlanLedger,
    HostedTaskPlanStatus,
    HostedTaskPlanStore,
)
from v2.human_review import (
    DevelopmentApiHumanReviewSource,
    HumanReviewStore,
    HumanReviewSubmission,
    HumanReviewVerdict,
)
from v2.objective_lifecycle import ObjectiveLifecycleOperation, ObjectiveLifecycleStore
from v2.objective_ingress import (
    DevelopmentApiObjectiveSource,
    ObjectiveProposalRecord,
    ObjectiveProposalStore,
)
from v2.objective_intake import (
    ObjectiveCompileDisposition,
    ObjectiveCompileResult,
    ObjectiveIntent,
    ObjectiveRequest,
)
from v2.ordinary_effects import OrdinaryEffectStore
from v2.ordinary_remote import comment_payload
from v2.program_progression import (
    ProgramAdvanceDisposition,
    ProgramContinuationStore,
    advance_program_continuation,
)
from v2.scope_promotion import PromotionStore
from v2.task_authority import TaskAuthorityWakeReference
from v2.task_event_composition import (
    TaskAuthorityEventLedger,
    TaskAuthorityEventRecord,
    TaskAuthorityEventStore,
)


def prepare_program_root(root: Path) -> None:
    prepare(root)
    extras = (
        "docs/agent-state/PROGRAM_ROADMAP.md",
        "docs/architecture/PRE_BOOTSTRAP_DEVELOPMENT_PLATFORM_GATE.md",
        "docs/agent-state/PROGRAM_PROGRESSION.json",
    )
    for rel in extras:
        dst = root / rel
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(REPO_ROOT / rel, dst)
    git(root, "add", ".")
    git(root, "commit", "-qm", "program progression fixture")
    write_state(root, gate=False)


def parent_proposal(root: Path, request_id: str) -> ObjectiveProposalRecord:
    source = DevelopmentApiObjectiveSource(
        repo="ni-da-ba/skyforge",
        request_id=request_id,
        actor="ni-da-ba",
        client="program-test",
        submitted_at="2026-09-21T03:40:00Z",
        objective_text="Continue Skyforge",
    )
    request = ObjectiveRequest(
        "Continue Skyforge",
        ObjectiveIntent.CONTINUE,
        "Skyforge",
    )
    record = ObjectiveProposalRecord(
        source=source,
        delivery_id="",
        compiled=ObjectiveCompileResult(
            ObjectiveCompileDisposition.PROGRAM_CONTINUE,
            request,
            "program test parent",
        ),
    )
    return ObjectiveProposalStore.for_root(root).capture_record(record).record


def review(
    root: Path,
    *,
    gate_id: str,
    verdict: HumanReviewVerdict,
    request_id: str,
) -> HumanReviewSubmission:
    source = DevelopmentApiHumanReviewSource(
        repo="ni-da-ba/skyforge",
        request_id=request_id,
        actor="ni-da-ba",
        client="program-test",
        submitted_at="2026-09-21T03:41:00Z",
    )
    record = HumanReviewSubmission(
        source=source,
        gate_id=gate_id,
        artifact_id=f"artifact-{request_id}",
        source_sha=git(root, "rev-parse", "HEAD"),
        verdict=verdict,
        findings=("program gate review fixture",),
        positive_findings=("bounded progression preserved",),
        material_delta="program gate evidence reconciled",
        next_boundary="program successor",
        deferred_product_work=False,
    )
    HumanReviewStore.for_root(root).capture(record)
    return record


def issue_payload() -> dict:
    return {
        "number": 754,
        "title": "Repair DR-70 hydrology canopy and fluid composition",
        "body": (
            "Repair the canonical hydrology channel and preserve retained water. "
            "Use skyforge-world/src/main/java/HydrologyChannel.java and "
            "skyforge-world/src/test/java/HydrologyChannelTest.java."
        ),
        "comments": [
            {
                "body": (
                    "Preserve retained water provenance, outlet discharge, canopy "
                    "headroom, and deterministic hydrology behavior."
                )
            }
        ],
        "updatedAt": "2026-09-21T03:42:00Z",
        "state": "OPEN",
    }


class ProgramRemote:
    def __init__(self, root: Path):
        self.root = root
        self.accepted_main = git(root, "rev-parse", "HEAD")
        self.payload = issue_payload()
        self.comments: list[dict] = []
        self.comment_exec_count = 0

    def __call__(self, args, **kwargs):
        command = tuple(args)
        if command == (
            "gh",
            "issue",
            "view",
            "754",
            "--repo",
            "ni-da-ba/skyforge",
            "--json",
            "number,title,body,comments,updatedAt,state",
            "--jq=.",
        ):
            return subprocess.CompletedProcess(
                args, 0, stdout=json.dumps(self.payload), stderr=""
            )
        if command == (
            "gh",
            "api",
            "repos/ni-da-ba/skyforge/commits/main",
            "--jq",
            ".sha",
        ):
            return subprocess.CompletedProcess(
                args, 0, stdout=self.accepted_main + "\n", stderr=""
            )
        if command == (
            "gh",
            "api",
            "repos/ni-da-ba/skyforge/issues/754/comments?per_page=100",
            "--paginate",
            "--jq",
            ".[]",
        ):
            out = "".join(json.dumps(value) + "\n" for value in self.comments)
            return subprocess.CompletedProcess(args, 0, stdout=out, stderr="")
        if len(command) >= 7 and command[:4] == (
            "gh",
            "issue",
            "comment",
            "754",
        ):
            self.comment_exec_count += 1
            body = command[command.index("--body") + 1]
            self.comments.append(
                {"id": 9100 + self.comment_exec_count, "body": body}
            )
            return subprocess.CompletedProcess(
                args,
                0,
                stdout=(
                    "https://github.com/ni-da-ba/skyforge/issues/754"
                    f"#issuecomment-{9100 + self.comment_exec_count}\n"
                ),
                stderr="",
            )
        raise AssertionError(command)


def install_child_completion(root: Path) -> HostedCompletionRecord:
    session = ProgramContinuationStore.for_root(root).load().active
    assert session is not None and session.child_proposal_id

    from v2.context_package import ContextPackageStore

    promotions = PromotionStore.for_root(root).load().records
    child_promotions = []
    packages = ContextPackageStore.for_root(root).load().records
    for record in promotions:
        if any(
            value.package_id == record.result.package_id
            and value.proposal_id == session.child_proposal_id
            for value in packages
        ):
            child_promotions.append(record)
    if len(child_promotions) != 1:
        raise AssertionError("expected one exact child promotion")
    promotion = child_promotions[0].result
    assert promotion.draft is not None

    effects = [
        value
        for value in OrdinaryEffectStore.for_root(root).load().records
        if value.identity.kind is EffectKind.POST_COMMENT
        and value.remote_identity.startswith("comment:")
    ]
    if len(effects) != 1:
        raise AssertionError("expected one promotion comment effect")
    effect = effects[0]
    comment_id = int(effect.remote_identity.split(":", 1)[1])
    full_body = comment_payload(effect.identity, promotion.draft.body)
    created = "2026-09-21T03:43:00Z"
    event = DurableEvent(
        actionable=True,
        reason="program child task authority",
        event="issue_comment",
        action="audit_signal",
        pr_number=754,
        observed_at=created,
        source_id=str(comment_id),
        signal_kind="task",
        signal_text=full_body,
    )
    reference = TaskAuthorityWakeReference(
        repo="ni-da-ba/skyforge",
        issue_number=754,
        comment_id=comment_id,
        actor="ni-da-ba",
        body=full_body,
        created_at=created,
        updated_at=created,
    )
    TaskAuthorityEventStore.for_root(root).save(
        TaskAuthorityEventLedger(
            (TaskAuthorityEventRecord(event.event_id, reference, "program-child"),)
        )
    )
    completion = HostedCompletionRecord(
        plan_id="plan-program-754",
        event_id=event.event_id,
        issue_number=754,
        admission_record_id="admission-program-754",
        attempt_id="a" * 64,
        worker_run_id="b" * 64,
        handoff_digest="c" * 64,
        lifecycle_digest="d" * 64,
        status=HostedCompletionStatus.CLEANED,
    )
    HostedCompletionStore.for_root(root).save(
        HostedCompletionLedger((completion,))
    )
    return completion


class ProgramProgressionTest(unittest.TestCase):
    def test_current_program_stops_at_prebootstrap_gate_without_touching_dr70(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            prepare_program_root(root)
            parent = parent_proposal(root, "continue-program-0001")

            def forbidden(*args, **kwargs):
                raise AssertionError("current pre-Bootstrap gate must not call GitHub")

            result = advance_program_continuation(
                root=root,
                repo="ni-da-ba/skyforge",
                runner=forbidden,
            )
            self.assertEqual(result.disposition, ProgramAdvanceDisposition.WAIT_HUMAN)
            self.assertEqual(
                result.session.current_node_id,
                "pre-bootstrap-development-platform-gate",
            )
            self.assertEqual(
                result.session.gate_id,
                "pre-bootstrap-development-platform-gate",
            )
            self.assertEqual(result.session.parent_proposal_id, parent.proposal_id)
            self.assertEqual(result.session.child_proposal_id, "")
            self.assertEqual(
                len(ObjectiveProposalStore.for_root(root).load().records),
                1,
            )
            self.assertEqual(
                len(OrdinaryEffectStore.for_root(root).load().records),
                0,
            )

    def test_paused_program_parent_waits_without_touching_child(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            prepare_program_root(root)
            parent = parent_proposal(root, "continue-program-paused-0001")
            ObjectiveLifecycleStore.for_root(root).apply(
                root=root,
                request_id="pause-program-0001",
                proposal_id=parent.proposal_id,
                operation=ObjectiveLifecycleOperation.PAUSE,
                reason="operator hold",
                actor="ni-da-ba",
                client="test",
            )

            def forbidden(*args, **kwargs):
                raise AssertionError("paused program must not touch GitHub")

            result = advance_program_continuation(
                root=root,
                repo="ni-da-ba/skyforge",
                runner=forbidden,
            )
            self.assertEqual(result.disposition, ProgramAdvanceDisposition.WAIT_CONTROL)
            self.assertEqual(result.session.child_proposal_id, "")
            self.assertIn("paused", result.reason)

    def test_changes_required_does_not_unlock_dr70(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            prepare_program_root(root)
            parent_proposal(root, "continue-program-0002")
            review(
                root,
                gate_id="pre-bootstrap-development-platform-gate",
                verdict=HumanReviewVerdict.CHANGES_REQUIRED,
                request_id="program-review-0002",
            )

            def forbidden(*args, **kwargs):
                raise AssertionError("CHANGES_REQUIRED must not touch issue 754")

            result = advance_program_continuation(
                root=root,
                repo="ni-da-ba/skyforge",
                runner=forbidden,
            )
            self.assertEqual(result.disposition, ProgramAdvanceDisposition.WAIT_HUMAN)
            self.assertEqual(result.session.child_proposal_id, "")
            self.assertIn("CHANGES_REQUIRED", result.reason)

    def test_accepted_gate_composes_child_through_exactly_once_authority_post(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            prepare_program_root(root)
            parent_proposal(root, "continue-program-0003")
            accepted = review(
                root,
                gate_id="pre-bootstrap-development-platform-gate",
                verdict=HumanReviewVerdict.ACCEPTED,
                request_id="program-review-0003",
            )
            remote = ProgramRemote(root)
            result = advance_program_continuation(
                root=root,
                repo="ni-da-ba/skyforge",
                runner=remote,
            )
            self.assertEqual(result.disposition, ProgramAdvanceDisposition.WAIT_CHILD)
            self.assertEqual(result.session.current_node_id, "post-platform-dr70-repair")
            self.assertTrue(result.session.child_proposal_id)
            self.assertTrue(
                any(
                    value.node_id == "pre-bootstrap-development-platform-gate"
                    and value.evidence_id == accepted.review_id
                    for value in result.session.completed_nodes
                )
            )
            self.assertEqual(remote.comment_exec_count, 1)
            self.assertEqual(
                len(ObjectiveProposalStore.for_root(root).load().records),
                2,
            )

            replay = advance_program_continuation(
                root=root,
                repo="ni-da-ba/skyforge",
                runner=remote,
            )
            self.assertEqual(replay.disposition, ProgramAdvanceDisposition.WAIT_CHILD)
            self.assertEqual(
                replay.session.child_proposal_id,
                result.session.child_proposal_id,
            )
            self.assertEqual(remote.comment_exec_count, 1)

    def test_duplicate_continue_invocation_reuses_one_active_session(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            prepare_program_root(root)
            first = parent_proposal(root, "continue-program-0004")
            advance_program_continuation(
                root=root,
                repo="ni-da-ba/skyforge",
                runner=lambda *a, **k: None,
            )
            second = parent_proposal(root, "continue-program-0005")
            result = advance_program_continuation(
                root=root,
                repo="ni-da-ba/skyforge",
                runner=lambda *a, **k: None,
            )
            ledger = ProgramContinuationStore.for_root(root).load()
            self.assertEqual(len(ledger.records), 1)
            self.assertEqual(result.session.parent_proposal_id, first.proposal_id)
            self.assertEqual(
                set(result.session.invocation_proposal_ids),
                {first.proposal_id, second.proposal_id},
            )

    def test_existing_exact_external_claim_blocks_child_creation(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            prepare_program_root(root)
            parent_proposal(root, "continue-program-0006")
            review(
                root,
                gate_id="pre-bootstrap-development-platform-gate",
                verdict=HumanReviewVerdict.ACCEPTED,
                request_id="program-review-0006",
            )
            ExternalClaimStore.for_root(root).save(
                ExternalClaimLedger(
                    (
                        ExternalProducerClaim(
                            issue_number=754,
                            claimed_by="ni-da-ba",
                            lane="Implementation",
                            branch="implementation/754-existing",
                            pr_number=949,
                        ),
                    )
                )
            )

            def forbidden(*args, **kwargs):
                raise AssertionError("existing authority must block before GitHub reads")

            result = advance_program_continuation(
                root=root,
                repo="ni-da-ba/skyforge",
                runner=forbidden,
            )
            self.assertEqual(
                result.disposition,
                ProgramAdvanceDisposition.WAIT_AUTHORITY,
            )
            self.assertEqual(result.session.child_proposal_id, "")
            self.assertIn("#754", result.reason)
            self.assertIn("PR #949", result.reason)

    def test_existing_exact_managed_plan_blocks_child_creation(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            prepare_program_root(root)
            parent_proposal(root, "continue-program-0009")
            review(
                root,
                gate_id="pre-bootstrap-development-platform-gate",
                verdict=HumanReviewVerdict.ACCEPTED,
                request_id="program-review-0009",
            )
            plan = HostedTaskDispatchPlan(
                event_id="sha256:" + "e" * 64,
                issue_number=754,
                authority_record_digest="f" * 64,
                status=HostedTaskPlanStatus.CLAIMED,
                reason="existing managed authority fixture",
            )
            HostedTaskPlanStore.for_root(root).save(
                HostedTaskPlanLedger((plan,))
            )

            def forbidden(*args, **kwargs):
                raise AssertionError("managed authority must block before GitHub reads")

            result = advance_program_continuation(
                root=root,
                repo="ni-da-ba/skyforge",
                runner=forbidden,
            )
            self.assertEqual(
                result.disposition,
                ProgramAdvanceDisposition.WAIT_AUTHORITY,
            )
            self.assertEqual(result.session.child_proposal_id, "")
            self.assertIn("managed Platform-v2 authority", result.reason)
            self.assertEqual(
                len(ObjectiveProposalStore.for_root(root).load().records),
                1,
            )
            self.assertIsNone(
                HostedAdmissionStore.for_root(root).load().for_issue(754)
            )

    def test_source_drift_fails_closed_without_child_creation(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            prepare_program_root(root)
            parent_proposal(root, "continue-program-0007")
            first = advance_program_continuation(
                root=root,
                repo="ni-da-ba/skyforge",
                runner=lambda *a, **k: None,
            )
            self.assertEqual(first.disposition, ProgramAdvanceDisposition.WAIT_HUMAN)
            path = root / "docs/agent-state/PROGRAM_ROADMAP.md"
            path.write_text(path.read_text() + "\nsource drift\n")
            blocked = advance_program_continuation(
                root=root,
                repo="ni-da-ba/skyforge",
            )
            self.assertEqual(blocked.disposition, ProgramAdvanceDisposition.BLOCKED)
            self.assertIn("source validation failed closed", blocked.reason)
            self.assertEqual(blocked.session.child_proposal_id, "")

    def test_x2_child_completion_advances_only_to_dr_rereview_gate(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            prepare_program_root(root)
            parent_proposal(root, "continue-program-0008")
            review(
                root,
                gate_id="pre-bootstrap-development-platform-gate",
                verdict=HumanReviewVerdict.ACCEPTED,
                request_id="program-review-0008",
            )
            remote = ProgramRemote(root)
            first = advance_program_continuation(
                root=root,
                repo="ni-da-ba/skyforge",
                runner=remote,
            )
            self.assertEqual(first.disposition, ProgramAdvanceDisposition.WAIT_CHILD)
            completion = install_child_completion(root)
            second = advance_program_continuation(
                root=root,
                repo="ni-da-ba/skyforge",
                runner=remote,
            )
            self.assertEqual(second.disposition, ProgramAdvanceDisposition.WAIT_HUMAN)
            self.assertEqual(
                second.session.current_node_id,
                "dr-human-exploration-rereview",
            )
            self.assertEqual(
                second.session.gate_id,
                "dr-human-exploration-rereview",
            )
            self.assertTrue(
                any(
                    value.node_id == "post-platform-dr70-repair"
                    and value.evidence_id == completion.completion_id
                    for value in second.session.completed_nodes
                )
            )


if __name__ == "__main__":
    unittest.main()
