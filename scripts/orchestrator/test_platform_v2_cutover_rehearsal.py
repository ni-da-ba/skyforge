from __future__ import annotations

import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from v2.cutover import WriterAuthority
from v2.cutover_rehearsal import (
    CutoverRehearsalEvidence,
    DisposableProcessSupervisor,
    RehearsalDisposition,
    RehearsalRole,
    rehearse_cutover_and_rollback,
)


def kinds(evidence: CutoverRehearsalEvidence):
    return tuple(event.kind for event in evidence.events)


class ProcessCutoverRehearsalTest(unittest.TestCase):
    def test_forward_and_rollback_require_observable_none_intervals(self):
        with tempfile.TemporaryDirectory() as td:
            evidence = rehearse_cutover_and_rollback(Path(td))

        self.assertEqual(evidence.disposition, RehearsalDisposition.ACCEPTED)
        self.assertEqual(evidence.final_authority, WriterAuthority.LEGACY)
        self.assertTrue(evidence.forward_v2_observed)
        self.assertTrue(evidence.rollback_legacy_observed)
        self.assertTrue(evidence.no_overlap_proven)
        self.assertTrue(evidence.recovery_from_none_proven)
        self.assertEqual(
            kinds(evidence),
            (
                "PROCESS_READY",
                "WRITER_NONE",
                "V2_READY",
                "ROLLBACK_WRITER_NONE",
                "ROLLBACK_LEGACY_READY",
            ),
        )
        self.assertEqual(
            tuple(event.authority for event in evidence.events),
            (
                WriterAuthority.LEGACY,
                WriterAuthority.NONE,
                WriterAuthority.V2,
                WriterAuthority.NONE,
                WriterAuthority.LEGACY,
            ),
        )
        for index in (1, 2, 3, 4):
            self.assertTrue(evidence.events[index].transition_digest)
        self.assertEqual(
            len({event.pid for event in evidence.events if event.pid is not None}),
            3,
        )

    def test_failed_legacy_revocation_never_starts_v2(self):
        with tempfile.TemporaryDirectory() as td:
            evidence = rehearse_cutover_and_rollback(
                Path(td),
                fail_legacy_revocation=True,
            )

        self.assertEqual(evidence.disposition, RehearsalDisposition.BLOCKED)
        self.assertEqual(evidence.final_authority, WriterAuthority.LEGACY)
        self.assertFalse(evidence.forward_v2_observed)
        self.assertFalse(evidence.rollback_legacy_observed)
        self.assertEqual(
            kinds(evidence),
            ("PROCESS_READY", "REVOCATION_BLOCKED"),
        )
        self.assertNotIn(
            RehearsalRole.V2,
            tuple(event.role for event in evidence.events if event.role is not None),
        )

    def test_abort_after_legacy_revocation_rolls_back_from_none_without_v2(self):
        with tempfile.TemporaryDirectory() as td:
            evidence = rehearse_cutover_and_rollback(
                Path(td),
                abort_after_legacy_revocation=True,
            )

        self.assertEqual(evidence.disposition, RehearsalDisposition.ACCEPTED)
        self.assertEqual(evidence.final_authority, WriterAuthority.LEGACY)
        self.assertFalse(evidence.forward_v2_observed)
        self.assertTrue(evidence.rollback_legacy_observed)
        self.assertTrue(evidence.recovery_from_none_proven)
        self.assertEqual(
            kinds(evidence),
            (
                "PROCESS_READY",
                "WRITER_NONE",
                "ABORT_AT_WRITER_NONE",
                "ROLLBACK_LEGACY_READY",
            ),
        )
        self.assertEqual(evidence.events[1].authority, WriterAuthority.NONE)
        self.assertEqual(evidence.events[2].authority, WriterAuthority.NONE)
        self.assertEqual(evidence.events[3].authority, WriterAuthority.LEGACY)
        self.assertNotIn(
            "V2_READY",
            kinds(evidence),
        )

    def test_failed_v2_process_activation_preserves_none_then_restores_legacy(self):
        with tempfile.TemporaryDirectory() as td:
            evidence = rehearse_cutover_and_rollback(
                Path(td),
                fail_v2_activation=True,
            )

        self.assertEqual(evidence.disposition, RehearsalDisposition.ACCEPTED)
        self.assertEqual(evidence.final_authority, WriterAuthority.LEGACY)
        self.assertFalse(evidence.forward_v2_observed)
        self.assertTrue(evidence.rollback_legacy_observed)
        self.assertTrue(evidence.no_overlap_proven)
        self.assertEqual(
            kinds(evidence),
            (
                "PROCESS_READY",
                "WRITER_NONE",
                "V2_ACTIVATION_BLOCKED",
                "ROLLBACK_LEGACY_READY",
            ),
        )
        self.assertEqual(evidence.events[1].authority, WriterAuthority.NONE)
        self.assertEqual(evidence.events[2].authority, WriterAuthority.NONE)

    def test_evidence_digest_is_stable_for_same_semantic_mapping(self):
        with tempfile.TemporaryDirectory() as td:
            evidence = rehearse_cutover_and_rollback(
                Path(td),
                abort_after_legacy_revocation=True,
            )
        raw = evidence.as_dict()
        clone = CutoverRehearsalEvidence(
            disposition=RehearsalDisposition(raw["disposition"]),
            reason=raw["reason"],
            final_authority=WriterAuthority(raw["final_authority"]),
            events=evidence.events,
            forward_v2_observed=raw["forward_v2_observed"],
            rollback_legacy_observed=raw["rollback_legacy_observed"],
            no_overlap_proven=raw["no_overlap_proven"],
            recovery_from_none_proven=raw["recovery_from_none_proven"],
        )
        self.assertEqual(clone.digest, evidence.digest)


class DisposableSupervisorTest(unittest.TestCase):
    def test_supervisor_refuses_two_live_fixture_writers(self):
        with tempfile.TemporaryDirectory() as td:
            supervisor = DisposableProcessSupervisor(Path(td))
            try:
                legacy = supervisor.start(RehearsalRole.LEGACY)
                self.assertTrue(supervisor.is_alive(legacy))
                with self.assertRaisesRegex(RuntimeError, "dual fixture writers"):
                    supervisor.start(RehearsalRole.V2)
                stopped = supervisor.stop(RehearsalRole.LEGACY)
                self.assertFalse(supervisor.is_alive(stopped))
            finally:
                supervisor.cleanup()

    def test_fixture_readiness_file_binds_role_and_pid(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            supervisor = DisposableProcessSupervisor(root)
            try:
                fixture = supervisor.start(RehearsalRole.V2)
                value = json.loads(
                    (root / "v2-ready.json").read_text(encoding="utf-8")
                )
                self.assertEqual(value["role"], "V2")
                self.assertEqual(value["pid"], fixture.process.pid)
            finally:
                supervisor.cleanup()


class RehearsalCliTest(unittest.TestCase):
    def test_cli_runs_disposable_rehearsal_only(self):
        with tempfile.TemporaryDirectory() as td:
            env = dict(os.environ)
            root = Path(__file__).resolve().parent
            prior = env.get("PYTHONPATH", "")
            env["PYTHONPATH"] = str(root) + (os.pathsep + prior if prior else "")
            result = subprocess.run(
                [
                    sys.executable,
                    "-m",
                    "v2.cutover_rehearsal",
                    "--root",
                    str(Path(td) / "fixture"),
                ],
                cwd=root,
                check=True,
                text=True,
                capture_output=True,
                env=env,
                timeout=20,
            )
            value = json.loads(result.stdout)
            self.assertEqual(value["disposition"], "ACCEPTED")
            self.assertEqual(value["final_authority"], "LEGACY")
            self.assertTrue(value["no_overlap_proven"])


class CapabilityIsolationTest(unittest.TestCase):
    def test_rehearsal_has_no_live_service_network_or_repository_mutation_surface(self):
        root = Path(__file__).resolve().parent
        source = (root / "v2/cutover_rehearsal.py").read_text(encoding="utf-8")
        for forbidden in (
            "systemctl",
            "skyforge-orchestrator.service",
            "127.0.0.1:3000",
            "platform_v2_hosted_runtime",
            "WriterFence",
            "git push",
            "gh pr",
            "gh issue",
            "Caddy",
            "requests.",
            "urllib.",
        ):
            with self.subTest(forbidden=forbidden):
                self.assertNotIn(forbidden, source)

    def test_production_hosted_runtime_does_not_import_rehearsal(self):
        root = Path(__file__).resolve().parent
        source = (root / "platform_v2_hosted_runtime.py").read_text(encoding="utf-8")
        self.assertNotIn("cutover_rehearsal", source)


if __name__ == "__main__":
    unittest.main()
