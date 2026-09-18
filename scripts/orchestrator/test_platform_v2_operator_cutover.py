from __future__ import annotations

import json
from pathlib import Path
import subprocess
import tempfile
import unittest

from v2.activation_gate import ProductionActivationInput
from v2.cutover import (
    CutoverReadinessDecision,
    CutoverReadinessDisposition,
    WriterAuthority,
)
from v2.operator_cutover import (
    LEGACY_SERVICE,
    V2_SERVICE,
    OperatorCutoverController,
    OperatorDisposition,
    ServiceObservation,
)


def git(root: Path, *args: str) -> str:
    result = subprocess.run(
        ["git", "-C", str(root), *args],
        check=True,
        text=True,
        capture_output=True,
    )
    return result.stdout.strip()


def make_repo(root: Path) -> str:
    git(root, "init", "-b", "main")
    git(root, "config", "user.email", "test@example.invalid")
    git(root, "config", "user.name", "Skyforge R5C26 Test")
    (root / ".gitignore").write_text(
        ".skyforge-platform-v2/\n.skyforge-orchestrator/\n",
        encoding="utf-8",
    )
    (root / "tracked.txt").write_text("accepted\n", encoding="utf-8")
    git(root, "add", ".gitignore", "tracked.txt")
    git(root, "commit", "-m", "accepted main")
    return git(root, "rev-parse", "HEAD")


def production_input(main: str, *, dr70: bool = True, dr70_waived: bool = False) -> ProductionActivationInput:
    cutover = CutoverReadinessDecision(
        disposition=CutoverReadinessDisposition.READY_FOR_AUTHORITY_SWITCH,
        blockers=(),
        projection_digest="b" * 64,
        accepted_main_sha=main,
        legacy_runtime_sha=main,
    )
    return ProductionActivationInput(
        cutover=cutover,
        primary_workstation_preservation_pass=True,
        dr70_migration_hold_cleared=dr70,
        hosted_shadow_parity_accepted=True,
        hosted_execution_path_accepted=True,
        remote_effect_path_accepted=True,
        cutover_rollback_rehearsal_accepted=True,
        dr70_migration_hold_waived=dr70_waived,
    )


def production_mapping(value: ProductionActivationInput) -> dict[str, object]:
    return {
        "cutover": {
            "disposition": value.cutover.disposition.value,
            "blockers": list(value.cutover.blockers),
            "projection_digest": value.cutover.projection_digest,
            "accepted_main_sha": value.cutover.accepted_main_sha,
            "legacy_runtime_sha": value.cutover.legacy_runtime_sha,
        },
        "primary_workstation_preservation_pass": value.primary_workstation_preservation_pass,
        "dr70_migration_hold_cleared": value.dr70_migration_hold_cleared,
        "dr70_migration_hold_waived": value.dr70_migration_hold_waived,
        "hosted_shadow_parity_accepted": value.hosted_shadow_parity_accepted,
        "hosted_execution_path_accepted": value.hosted_execution_path_accepted,
        "remote_effect_path_accepted": value.remote_effect_path_accepted,
        "cutover_rollback_rehearsal_accepted": value.cutover_rollback_rehearsal_accepted,
    }


def write_template(root: Path, main: str, *, dr70: bool = True, dr70_waived: bool = False) -> tuple[Path, Path]:
    activation = production_input(main, dr70=dr70, dr70_waived=dr70_waived)
    template = root / ".skyforge-platform-v2" / "operator-template.json"
    evidence = root / ".skyforge-platform-v2" / "production-activation.json"
    template.parent.mkdir(parents=True, exist_ok=True)
    template.write_text(
        json.dumps(
            {
                "schema_version": 1,
                "operator_activation_requested": True,
                "accepted_main_sha": main,
                "production_activation_input_digest": activation.digest,
                "production_activation_input": production_mapping(activation),
            },
            sort_keys=True,
        ),
        encoding="utf-8",
    )
    return template, evidence


class FakeServices:
    def __init__(self, evidence: Path):
        self.evidence = str(evidence.resolve())
        self.log: list[tuple[str, str]] = []
        self.fail_stop_legacy = False
        self.fail_start_v2 = False
        self.fail_start_legacy = False
        self.state = {
            LEGACY_SERVICE: {
                "loaded": True,
                "active": True,
                "enabled": True,
                "pid": 111,
                "exec": (
                    "python skyforge_control_plane_runtime.py --startup-reconcile "
                    "--require-startup-reconcile-success"
                ),
            },
            V2_SERVICE: {
                "loaded": True,
                "active": False,
                "enabled": False,
                "pid": 0,
                "exec": (
                    "python platform_v2_hosted_runtime.py --enable-production-execution "
                    f"--activation-evidence {self.evidence}"
                ),
            },
        }

    def observe(self, name: str) -> ServiceObservation:
        value = self.state[name]
        return ServiceObservation(
            name,
            value["loaded"],
            value["active"],
            value["enabled"],
            value["pid"],
            value["exec"],
            "",
        )

    def stop(self, name: str) -> None:
        self.log.append(("stop", name))
        if name == LEGACY_SERVICE and self.fail_stop_legacy:
            raise RuntimeError("injected legacy stop failure")
        self.state[name]["active"] = False
        self.state[name]["pid"] = 0

    def start(self, name: str) -> None:
        self.log.append(("start", name))
        if name == V2_SERVICE and self.fail_start_v2:
            raise RuntimeError("injected v2 start failure")
        if name == LEGACY_SERVICE and self.fail_start_legacy:
            raise RuntimeError("injected legacy start failure")
        other = V2_SERVICE if name == LEGACY_SERVICE else LEGACY_SERVICE
        if self.state[other]["active"]:
            raise RuntimeError("refusing dual writers")
        self.state[name]["active"] = True
        self.state[name]["pid"] = 222 if name == V2_SERVICE else 333

    def enable(self, name: str) -> None:
        self.log.append(("enable", name))
        self.state[name]["enabled"] = True

    def disable(self, name: str) -> None:
        self.log.append(("disable", name))
        self.state[name]["enabled"] = False


class OperatorCutoverTest(unittest.TestCase):
    def build(self, *, dr70: bool = True, dr70_waived: bool = False):
        td = tempfile.TemporaryDirectory()
        root = Path(td.name)
        main = make_repo(root)
        template, evidence = write_template(root, main, dr70=dr70, dr70_waived=dr70_waived)
        services = FakeServices(evidence)

        def health():
            if services.state[V2_SERVICE]["active"]:
                # The actual gate digest is not known to the fake health closure. The
                # controller's v2 verifier is replaced in tests that execute cutover.
                return {
                    "controller": "platform-v2",
                    "production_execution_enabled": True,
                    "production_execution_driver": {"running": True},
                }
            return {
                "status": "ok",
                "last_startup_reconcile_error": None,
                "last_startup_reconcile_success_at": "2026-09-18T00:00:00Z",
            }

        controller = OperatorCutoverController(
            root=root,
            activation_template=template,
            activation_evidence=evidence,
            services=services,
            health_probe=health,
            geteuid=lambda: 0,
            sleep=lambda _seconds: None,
        )
        # Keep exact activation-file verification real, but avoid coupling the fake
        # health endpoint to a digest available only after finalization.
        controller._verify_v2_health = lambda _digest: None
        return td, root, main, template, evidence, services, controller

    def test_preflight_is_read_only_and_ready_with_exact_unit_contracts(self):
        td, _root, main, _template, evidence, services, controller = self.build()
        self.addCleanup(td.cleanup)
        report = controller.cutover(execute=False)
        self.assertEqual(report.disposition, OperatorDisposition.PREFLIGHT_READY)
        self.assertEqual(report.authority, WriterAuthority.LEGACY)
        self.assertEqual(report.accepted_main_sha, main)
        self.assertEqual(report.activation_evidence_path, str(evidence.resolve()))
        self.assertEqual(services.log, [])

    def test_unwaived_dr70_remains_a_real_live_cutover_blocker(self):
        td, _root, _main, _template, _evidence, services, controller = self.build(dr70=False)
        self.addCleanup(td.cleanup)
        report = controller.cutover(execute=False)
        self.assertEqual(report.disposition, OperatorDisposition.BLOCKED)
        self.assertTrue(any("DR-70" in item for item in report.blockers))
        self.assertEqual(services.log, [])

    def test_unfinished_dr70_with_explicit_operator_waiver_allows_preflight(self):
        td, _root, _main, _template, _evidence, services, controller = self.build(
            dr70=False,
            dr70_waived=True,
        )
        self.addCleanup(td.cleanup)
        report = controller.cutover(execute=False)
        self.assertEqual(report.disposition, OperatorDisposition.PREFLIGHT_READY)
        self.assertEqual(report.authority, WriterAuthority.LEGACY)
        self.assertEqual(services.log, [])

    def test_boot_enabled_v2_blocks_preflight(self):
        td, _root, _main, _template, _evidence, services, controller = self.build()
        self.addCleanup(td.cleanup)
        services.state[V2_SERVICE]["enabled"] = True
        report = controller.cutover(execute=False)
        self.assertEqual(report.disposition, OperatorDisposition.BLOCKED)
        self.assertTrue(any("boot-disabled" in item for item in report.blockers))
        self.assertEqual(services.log, [])

    def test_dirty_tracked_worktree_blocks_before_service_mutation(self):
        td, root, _main, _template, _evidence, services, controller = self.build()
        self.addCleanup(td.cleanup)
        (root / "tracked.txt").write_text("dirty\n", encoding="utf-8")
        report = controller.cutover(execute=True)
        self.assertEqual(report.disposition, OperatorDisposition.BLOCKED)
        self.assertIn("tracked worktree is not clean", report.blockers)
        self.assertEqual(services.log, [])

    def test_non_root_execute_is_refused_after_read_only_preflight(self):
        td, root, _main, template, evidence, services, _controller = self.build()
        self.addCleanup(td.cleanup)
        controller = OperatorCutoverController(
            root=root,
            activation_template=template,
            activation_evidence=evidence,
            services=services,
            geteuid=lambda: 1000,
        )
        report = controller.cutover(execute=True)
        self.assertEqual(report.disposition, OperatorDisposition.BLOCKED)
        self.assertIn("root operator", report.blockers[0])
        self.assertEqual(services.log, [])

    def test_successful_cutover_observes_none_before_v2_and_finalizes_evidence(self):
        td, _root, main, _template, evidence, services, controller = self.build()
        self.addCleanup(td.cleanup)
        report = controller.cutover(execute=True)
        self.assertEqual(report.disposition, OperatorDisposition.CUTOVER_COMPLETE)
        self.assertEqual(report.authority, WriterAuthority.V2)
        self.assertFalse(services.state[LEGACY_SERVICE]["active"])
        self.assertFalse(services.state[LEGACY_SERVICE]["enabled"])
        self.assertTrue(services.state[V2_SERVICE]["active"])
        self.assertTrue(services.state[V2_SERVICE]["enabled"])
        kinds = [event.kind for event in report.events]
        self.assertLess(kinds.index("WRITER_NONE"), kinds.index("V2_READY"))
        raw = json.loads(evidence.read_text(encoding="utf-8"))
        self.assertTrue(raw["legacy_writer_revoked_observed"])
        self.assertEqual(raw["writer_authority"], "NONE")
        self.assertEqual(raw["accepted_main_sha"], main)
        self.assertTrue(Path(report.checkpoint_path).is_file())

    def test_failed_legacy_revocation_never_activates_v2(self):
        td, _root, _main, _template, evidence, services, controller = self.build()
        self.addCleanup(td.cleanup)
        services.fail_stop_legacy = True
        report = controller.cutover(execute=True)
        self.assertEqual(report.disposition, OperatorDisposition.FAILED_SAFE_LEGACY)
        self.assertEqual(report.authority, WriterAuthority.LEGACY)
        self.assertTrue(services.state[LEGACY_SERVICE]["active"])
        self.assertFalse(services.state[V2_SERVICE]["active"])
        self.assertFalse(evidence.exists())

    def test_failed_v2_activation_stays_none_and_retires_activation_evidence(self):
        td, _root, _main, _template, evidence, services, controller = self.build()
        self.addCleanup(td.cleanup)
        services.fail_start_v2 = True
        report = controller.cutover(execute=True)
        self.assertEqual(report.disposition, OperatorDisposition.FAILED_SAFE_NONE)
        self.assertEqual(report.authority, WriterAuthority.NONE)
        self.assertFalse(services.state[LEGACY_SERVICE]["active"])
        self.assertFalse(services.state[V2_SERVICE]["active"])
        self.assertFalse(services.state[V2_SERVICE]["enabled"])
        self.assertFalse(evidence.exists())
        self.assertTrue(Path(report.retired_activation_evidence_path).is_file())

    def test_successful_rollback_revokes_v2_retires_evidence_then_restores_legacy(self):
        td, _root, _main, _template, evidence, services, controller = self.build()
        self.addCleanup(td.cleanup)
        cutover = controller.cutover(execute=True)
        self.assertEqual(cutover.disposition, OperatorDisposition.CUTOVER_COMPLETE)
        before = len(services.log)
        report = controller.rollback(execute=True)
        self.assertEqual(report.disposition, OperatorDisposition.ROLLBACK_COMPLETE)
        self.assertEqual(report.authority, WriterAuthority.LEGACY)
        self.assertTrue(services.state[LEGACY_SERVICE]["active"])
        self.assertTrue(services.state[LEGACY_SERVICE]["enabled"])
        self.assertFalse(services.state[V2_SERVICE]["active"])
        self.assertFalse(services.state[V2_SERVICE]["enabled"])
        self.assertFalse(evidence.exists())
        self.assertTrue(Path(report.retired_activation_evidence_path).is_file())
        actions = services.log[before:]
        self.assertLess(actions.index(("stop", V2_SERVICE)), actions.index(("start", LEGACY_SERVICE)))
        kinds = [event.kind for event in report.events]
        self.assertLess(kinds.index("ROLLBACK_WRITER_NONE"), kinds.index("ROLLBACK_LEGACY_READY"))

    def test_emergency_rollback_does_not_depend_on_template_remaining_valid(self):
        td, _root, _main, template, _evidence, _services, controller = self.build()
        self.addCleanup(td.cleanup)
        self.assertEqual(
            controller.cutover(execute=True).disposition,
            OperatorDisposition.CUTOVER_COMPLETE,
        )
        template.write_text("{corrupt", encoding="utf-8")
        report = controller.rollback(execute=True)
        self.assertEqual(report.disposition, OperatorDisposition.ROLLBACK_COMPLETE)
        self.assertEqual(report.authority, WriterAuthority.LEGACY)

    def test_rollback_reconcile_failure_returns_to_none(self):
        td, _root, _main, _template, _evidence, services, controller = self.build()
        self.addCleanup(td.cleanup)
        self.assertEqual(
            controller.cutover(execute=True).disposition,
            OperatorDisposition.CUTOVER_COMPLETE,
        )

        def unhealthy_legacy():
            if services.state[V2_SERVICE]["active"]:
                return {"controller": "platform-v2"}
            return {
                "status": "ok",
                "last_startup_reconcile_error": {"kind": "RuntimeError"},
                "last_startup_reconcile_success_at": None,
            }

        controller.health_probe = unhealthy_legacy
        report = controller.rollback(execute=True)
        self.assertEqual(report.disposition, OperatorDisposition.FAILED_SAFE_NONE)
        self.assertEqual(report.authority, WriterAuthority.NONE)
        self.assertFalse(services.state[LEGACY_SERVICE]["active"])
        self.assertFalse(services.state[V2_SERVICE]["active"])

    def test_dual_writer_observation_blocks_rollback(self):
        td, _root, _main, _template, _evidence, services, controller = self.build()
        self.addCleanup(td.cleanup)
        services.state[V2_SERVICE]["active"] = True
        report = controller.rollback(execute=False)
        self.assertEqual(report.disposition, OperatorDisposition.BLOCKED)
        self.assertTrue(any("dual active writers" in item for item in report.blockers))

    def test_unit_contract_requires_legacy_reconcile_guard(self):
        td, _root, _main, _template, _evidence, services, controller = self.build()
        self.addCleanup(td.cleanup)
        services.state[LEGACY_SERVICE]["exec"] = "python skyforge_control_plane_runtime.py --startup-reconcile"
        report = controller.cutover(execute=False)
        self.assertEqual(report.disposition, OperatorDisposition.BLOCKED)
        self.assertTrue(any("startup-reconcile guard" in item for item in report.blockers))


class ServiceTemplateContractTest(unittest.TestCase):
    def test_v2_template_is_distinct_and_activation_evidence_bound(self):
        root = Path(__file__).resolve().parents[2]
        source = (root / "deploy/orchestrator/skyforge-orchestrator-v2.service.in").read_text()
        self.assertIn("platform_v2_hosted_runtime.py", source)
        self.assertIn("--enable-production-execution", source)
        self.assertIn("@@ACTIVATION_EVIDENCE@@", source)
        self.assertIn("Conflicts=skyforge-orchestrator.service", source)

    def test_staging_helper_never_starts_or_restarts_a_writer(self):
        root = Path(__file__).resolve().parents[2]
        source = (root / "scripts/orchestrator/stage_platform_v2_cutover.sh").read_text()
        self.assertIn("systemctl daemon-reload", source)
        self.assertIn("disable skyforge-orchestrator-v2.service", source)
        self.assertNotIn("systemctl restart", source)
        self.assertNotIn("systemctl start", source)
        self.assertNotIn("enable --now", source)

    def test_legacy_template_requires_reconcile_before_dispatch(self):
        root = Path(__file__).resolve().parents[2]
        source = (root / "deploy/orchestrator/skyforge-orchestrator.service.in").read_text()
        self.assertIn("--startup-reconcile --require-startup-reconcile-success", source)


if __name__ == "__main__":
    unittest.main()
