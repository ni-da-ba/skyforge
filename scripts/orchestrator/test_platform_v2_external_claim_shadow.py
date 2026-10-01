from __future__ import annotations

import json
from pathlib import Path
import subprocess
import tempfile
import unittest

import platform_v2_external_claim_shadow as live


def write_state(root: Path, state: dict) -> None:
    state_dir = root / ".skyforge-orchestrator"
    state_dir.mkdir()
    (state_dir / "state.json").write_text(json.dumps(state), encoding="utf-8")


def fake_gh(args, **kwargs):
    if args[:3] == ["gh", "pr", "view"]:
        pr = int(args[3])
        payload = {
            100: {"state": "OPEN", "mergedAt": None},
            101: {"state": "MERGED", "mergedAt": "2026-09-18T00:00:00Z"},
            102: {"state": "CLOSED", "mergedAt": None},
        }[pr]
        return subprocess.CompletedProcess(args, 0, stdout=json.dumps(payload), stderr="")
    if args[:3] == ["gh", "issue", "view"]:
        issue = int(args[3])
        payload = {"state": "CLOSED" if issue == 4 else "OPEN"}
        return subprocess.CompletedProcess(args, 0, stdout=json.dumps(payload), stderr="")
    raise AssertionError(args)


class LiveExternalClaimShadowTest(unittest.TestCase):
    def test_active_open_and_recent_terminal_samples_agree(self) -> None:
        state = {
            "external_producer_claims": {
                "1": {
                    "state": "active",
                    "issue_number": 1,
                    "claimed_by": "ni-da-ba",
                    "pr_number": 100,
                },
                "5": {
                    "state": "active",
                    "issue_number": 5,
                    "claimed_by": "ni-da-ba",
                },
            },
            "last_external_producer_auto_retire": {
                "retired": [
                    {
                        "state": "active",
                        "issue_number": 2,
                        "claimed_by": "ni-da-ba",
                        "pr_number": 101,
                        "reason": "bound_pr_merged",
                    },
                    {
                        "state": "active",
                        "issue_number": 3,
                        "claimed_by": "ni-da-ba",
                        "pr_number": 102,
                        "reason": "bound_pr_closed",
                    },
                    {
                        "state": "active",
                        "issue_number": 4,
                        "claimed_by": "ni-da-ba",
                        "reason": "issue_closed",
                    },
                ]
            },
        }
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            write_state(root, state)
            value = live.collect_external_claim_shadow(
                root=root,
                repo="ni-da-ba/skyforge",
                gh_runner=fake_gh,
            )

        self.assertEqual(value["sample_count"], 5)
        self.assertEqual(value["summary"], {"AGREE": 5})
        by_issue = {sample["issue_number"]: sample for sample in value["samples"]}
        self.assertEqual(by_issue[1]["v2_disposition"], "KEEP")
        self.assertEqual(by_issue[2]["v2_disposition"], "RETIRE")
        self.assertEqual(by_issue[3]["v2_disposition"], "RETIRE")
        self.assertEqual(by_issue[4]["v2_disposition"], "RETIRE")
        self.assertEqual(by_issue[5]["v2_disposition"], "KEEP")

    def test_active_terminal_claim_surfaces_divergence_until_legacy_prunes(self) -> None:
        state = {
            "external_producer_claims": {
                "2": {
                    "state": "active",
                    "issue_number": 2,
                    "claimed_by": "ni-da-ba",
                    "pr_number": 101,
                }
            }
        }
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            write_state(root, state)
            value = live.collect_external_claim_shadow(
                root=root,
                repo="ni-da-ba/skyforge",
                gh_runner=fake_gh,
            )
        self.assertEqual(value["summary"], {"DIVERGENCE": 1})
        self.assertEqual(value["samples"][0]["v2_disposition"], "RETIRE")

    def test_exact_command_allowlists_reject_broader_or_mutating_shapes(self) -> None:
        pr = [
            "gh", "pr", "view", "100",
            "--repo", "ni-da-ba/skyforge",
            "--json", live.PR_JSON_FIELDS,
            "--jq=.",
        ]
        issue = [
            "gh", "issue", "view", "4",
            "--repo", "ni-da-ba/skyforge",
            "--json", live.ISSUE_JSON_FIELDS,
            "--jq=.",
        ]
        self.assertEqual(
            live.validate_readonly_pr_command(
                pr, repo="ni-da-ba/skyforge", expected_pr=100
            ),
            tuple(pr),
        )
        self.assertEqual(
            live.validate_readonly_issue_command(
                issue, repo="ni-da-ba/skyforge", expected_issue=4
            ),
            tuple(issue),
        )
        forbidden = [
            ["gh", "pr", "merge", "100", "--repo", "ni-da-ba/skyforge"],
            ["gh", "pr", "view", "101", "--repo", "ni-da-ba/skyforge", "--json", live.PR_JSON_FIELDS, "--jq=."],
            ["gh", "issue", "close", "4", "--repo", "ni-da-ba/skyforge"],
            ["gh", "issue", "view", "5", "--repo", "ni-da-ba/skyforge", "--json", live.ISSUE_JSON_FIELDS, "--jq=."],
        ]
        for command in forbidden:
            with self.subTest(command=command):
                if command[1] == "pr":
                    with self.assertRaises(ValueError):
                        live.validate_readonly_pr_command(
                            command,
                            repo="ni-da-ba/skyforge",
                            expected_pr=100,
                        )
                else:
                    with self.assertRaises(ValueError):
                        live.validate_readonly_issue_command(
                            command,
                            repo="ni-da-ba/skyforge",
                            expected_issue=4,
                        )

    def test_source_has_no_mutation_surface(self) -> None:
        source = Path(live.__file__).read_text(encoding="utf-8")
        for token in (
            "write_text",
            "write_bytes",
            "gh pr merge",
            "gh issue close",
            "gh issue comment",
            "openai_codex",
            "WriterFence",
        ):
            self.assertNotIn(token, source)


if __name__ == "__main__":
    unittest.main()
