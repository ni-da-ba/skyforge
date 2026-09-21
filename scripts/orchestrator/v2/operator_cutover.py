"""Privileged, fail-closed production writer cutover for Platform v2 R5C26.

The default preflight path is read-only. Mutating cutover/rollback operations require a
root operator and preserve the accepted writer sequence:

    LEGACY -> NONE -> V2
    V2 -> NONE -> LEGACY

The controller service account is never granted service-manager authority.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import time
from typing import Any, Callable, Mapping
from urllib.request import urlopen

from .activation_gate import evaluate_production_activation
from .authority_retirement import (
    inspect_v2_authority_retirement,
    retire_v2_authority as retire_terminal_v2_authority,
)
from .cutover import WriterAuthority
from .hosted_execution_runtime import (
    HostedExecutionGateDisposition,
    load_hosted_execution_gate,
    production_activation_input_from_mapping,
)
from .events import DurableEvent, PROTECTED_AUTHORITY_SIGNAL_KINDS, normalize_legacy_event_key
from .identity import canonical_digest


LEGACY_SERVICE = "skyforge-orchestrator.service"
V2_SERVICE = "skyforge-orchestrator-v2.service"
DEFAULT_HEALTH_URL = "http://127.0.0.1:3000/healthz"
LEGACY_MAX_RETIRED_EVENT_KEYS = 1024
_SHA40_RE = re.compile(r"^[0-9a-f]{40}$")


class OperatorDisposition(str, Enum):
    BLOCKED = "BLOCKED"
    PREFLIGHT_READY = "PREFLIGHT_READY"
    CUTOVER_COMPLETE = "CUTOVER_COMPLETE"
    ROLLBACK_COMPLETE = "ROLLBACK_COMPLETE"
    AUTHORITY_TRANSFER_READY = "AUTHORITY_TRANSFER_READY"
    AUTHORITY_TRANSFER_COMPLETE = "AUTHORITY_TRANSFER_COMPLETE"
    V2_AUTHORITY_RETIREMENT_READY = "V2_AUTHORITY_RETIREMENT_READY"
    V2_AUTHORITY_RETIREMENT_COMPLETE = "V2_AUTHORITY_RETIREMENT_COMPLETE"
    FAILED_SAFE_LEGACY = "FAILED_SAFE_LEGACY"
    FAILED_SAFE_NONE = "FAILED_SAFE_NONE"


@dataclass(frozen=True)
class ServiceObservation:
    name: str
    loaded: bool
    active: bool
    enabled: bool
    main_pid: int = 0
    exec_start: str = ""
    fragment_path: str = ""

    def as_dict(self) -> dict[str, object]:
        return {
            "name": self.name,
            "loaded": self.loaded,
            "active": self.active,
            "enabled": self.enabled,
            "main_pid": self.main_pid,
            "exec_start": self.exec_start,
            "fragment_path": self.fragment_path,
        }


@dataclass(frozen=True)
class OperatorEvent:
    sequence: int
    kind: str
    authority: WriterAuthority
    detail: str = ""

    def as_dict(self) -> dict[str, object]:
        return {
            "sequence": self.sequence,
            "kind": self.kind,
            "authority": self.authority.value,
            "detail": self.detail,
        }


@dataclass(frozen=True)
class OperatorReport:
    disposition: OperatorDisposition
    authority: WriterAuthority
    blockers: tuple[str, ...]
    accepted_main_sha: str
    events: tuple[OperatorEvent, ...] = ()
    checkpoint_path: str = ""
    activation_evidence_path: str = ""
    retired_activation_evidence_path: str = ""

    def as_dict(self) -> dict[str, object]:
        return {
            "disposition": self.disposition.value,
            "authority": self.authority.value,
            "blockers": list(self.blockers),
            "accepted_main_sha": self.accepted_main_sha,
            "checkpoint_path": self.checkpoint_path,
            "activation_evidence_path": self.activation_evidence_path,
            "retired_activation_evidence_path": self.retired_activation_evidence_path,
            "events": [event.as_dict() for event in self.events],
        }

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())


@dataclass(frozen=True)
class ActivationTemplate:
    raw_production_input: Mapping[str, Any]
    production_input_digest: str
    accepted_main_sha: str
    operator_activation_requested: bool

    def final_evidence(self) -> dict[str, object]:
        return {
            "schema_version": 1,
            "production_activation_input": dict(self.raw_production_input),
            "production_activation_input_digest": self.production_input_digest,
            "accepted_main_sha": self.accepted_main_sha,
            "operator_activation_requested": self.operator_activation_requested,
            "legacy_writer_revoked_observed": True,
            "writer_authority": WriterAuthority.NONE.value,
        }


class SystemdServiceAdapter:
    """Minimal root-operated systemd adapter used only by explicit execute modes."""

    def __init__(self, *, runner=subprocess.run) -> None:
        self.runner = runner

    def _run(self, *args: str, check: bool = True) -> subprocess.CompletedProcess[str]:
        return self.runner(
            ["systemctl", *args],
            text=True,
            capture_output=True,
            check=check,
        )

    def observe(self, name: str) -> ServiceObservation:
        result = self._run(
            "show",
            name,
            "--property=LoadState,ActiveState,UnitFileState,MainPID,ExecStart,FragmentPath",
            check=False,
        )
        if result.returncode not in (0, 1, 3, 4):
            raise RuntimeError(f"systemctl show failed for {name}: {result.stderr.strip()}")
        values: dict[str, str] = {}
        for line in result.stdout.splitlines():
            if "=" in line:
                key, value = line.split("=", 1)
                values[key] = value
        load_state = values.get("LoadState", "not-found")
        active_state = values.get("ActiveState", "inactive")
        enabled = values.get("UnitFileState", "") in {
            "enabled",
            "enabled-runtime",
            "linked",
            "linked-runtime",
        }
        try:
            main_pid = int(values.get("MainPID") or 0)
        except ValueError:
            main_pid = 0
        return ServiceObservation(
            name=name,
            loaded=load_state == "loaded",
            active=active_state in {"active", "activating"},
            enabled=enabled,
            main_pid=main_pid,
            exec_start=values.get("ExecStart", ""),
            fragment_path=values.get("FragmentPath", ""),
        )

    def stop(self, name: str) -> None:
        self._run("stop", name)

    def start(self, name: str) -> None:
        self._run("start", name)

    def enable(self, name: str) -> None:
        self._run("enable", name)

    def disable(self, name: str) -> None:
        self._run("disable", name)


def _default_health_probe(url: str = DEFAULT_HEALTH_URL) -> Mapping[str, Any]:
    with urlopen(url, timeout=2) as response:
        if response.status != 200:
            raise RuntimeError(f"health endpoint returned HTTP {response.status}")
        raw = json.loads(response.read().decode("utf-8"))
    if not isinstance(raw, Mapping):
        raise RuntimeError("health endpoint did not return a JSON object")
    return raw


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        while True:
            block = handle.read(1024 * 1024)
            if not block:
                break
            digest.update(block)
    return digest.hexdigest()


def _atomic_write_json(
    path: Path,
    value: Mapping[str, Any],
    *,
    mode: int | None = None,
) -> None:
    path = Path(path).resolve()
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(path.name + ".tmp")
    payload = json.dumps(value, sort_keys=True, indent=2) + "\n"
    with tmp.open("w", encoding="utf-8") as handle:
        handle.write(payload)
        handle.flush()
        if mode is not None:
            os.fchmod(handle.fileno(), mode)
        os.fsync(handle.fileno())
    os.replace(tmp, path)


def _legacy_state_paths(root: Path) -> tuple[Path, Path]:
    base = Path(root).resolve() / ".skyforge-orchestrator"
    return base / "state.json", base / "state.json.bak"


def _load_json_mapping(path: Path, label: str) -> dict[str, Any]:
    raw = json.loads(Path(path).read_text(encoding="utf-8"))
    if not isinstance(raw, Mapping):
        raise ValueError(f"{label} must be a JSON object")
    return dict(raw)


def _atomic_write_json_preserving_metadata(
    path: Path,
    value: Mapping[str, Any],
    *,
    fallback_stat: os.stat_result | None = None,
) -> None:
    path = Path(path).resolve()
    metadata = path.stat() if path.exists() else fallback_stat
    if metadata is None:
        raise FileNotFoundError(f"cannot preserve metadata for missing state path: {path}")
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(path.name + ".operator-transfer-tmp")
    payload = json.dumps(value, sort_keys=True, indent=2) + "\n"
    try:
        with tmp.open("w", encoding="utf-8") as handle:
            handle.write(payload)
            handle.flush()
            os.fchmod(handle.fileno(), metadata.st_mode & 0o7777)
            try:
                os.fchown(handle.fileno(), metadata.st_uid, metadata.st_gid)
            except PermissionError:
                current = path.stat() if path.exists() else None
                if current is None or (current.st_uid, current.st_gid) != (
                    metadata.st_uid,
                    metadata.st_gid,
                ):
                    raise
            os.fsync(handle.fileno())
        os.replace(tmp, path)
    finally:
        if tmp.exists():
            tmp.unlink()


def _legacy_pending_events(raw: Mapping[str, Any]) -> tuple[DurableEvent, ...]:
    values = raw.get("pending_events") or []
    if not isinstance(values, list):
        raise ValueError("legacy pending_events must be a list")
    return tuple(DurableEvent.from_legacy_mapping(value) for value in values)


def _legacy_key_set(raw: Mapping[str, Any], field: str) -> set[str]:
    values = raw.get(field) or []
    if not isinstance(values, list):
        raise ValueError(f"legacy {field} must be a list")
    return {normalize_legacy_event_key(value) for value in values if str(value or "")}


def _find_transfer_target(
    raw: Mapping[str, Any],
    *,
    event_key: str,
    issue_number: int,
    source_id: str,
    signal_kind: str = "task",
) -> tuple[DurableEvent | None, tuple[DurableEvent, ...], bool]:
    if not re.fullmatch(r"sha256:[0-9a-f]{64}", event_key):
        raise ValueError("event_key must be canonical sha256 durable-event identity")
    if isinstance(issue_number, bool) or not isinstance(issue_number, int) or issue_number <= 0:
        raise ValueError("issue_number must be positive integer")
    source = str(source_id or "").strip()
    if not source:
        raise ValueError("source_id is required")
    signal = str(signal_kind or "").strip()
    if signal not in PROTECTED_AUTHORITY_SIGNAL_KINDS:
        raise ValueError("signal_kind must be a protected authority signal kind")

    completed = _legacy_key_set(raw, "completed_authority_event_keys")
    if event_key in completed:
        raise ValueError("target authority is already marked completed")

    events = _legacy_pending_events(raw)
    protected = tuple(event for event in events if event.signal_kind in PROTECTED_AUTHORITY_SIGNAL_KINDS)
    matches = tuple(
        event
        for event in protected
        if event.event_id == event_key
        and event.signal_kind == signal
        and (
            event.task_issue_number == issue_number
            if signal == "task"
            else event.pr_number == issue_number
        )
        and str(event.source_id or "") == source
    )
    if len(matches) > 1:
        raise ValueError("multiple pending events match exact authority identity")

    transfers = raw.get("platform_v2_authority_transfers") or []
    if not isinstance(transfers, list):
        raise ValueError("platform_v2_authority_transfers must be a list")
    already_transferred = any(
        isinstance(item, Mapping)
        and str(item.get("event_key") or "") == event_key
        and int(item.get("issue_number") or 0) == issue_number
        and str(item.get("source_id") or "") == source
        and str(item.get("signal_kind") or "task") == signal
        and item.get("completed") is False
        and str(item.get("disposition") or "") == "RETIRED_FOR_PLATFORM_V2_TRANSFER"
        for item in transfers
    )
    retired = _legacy_key_set(raw, "retired_event_keys")
    if not matches and already_transferred and event_key in retired:
        return None, protected, True
    if len(matches) != 1:
        raise ValueError("exact pending protected authority was not found")
    return matches[0], protected, False


def load_activation_template(path: Path) -> tuple[ActivationTemplate, tuple[str, ...]]:
    raw = json.loads(Path(path).read_text(encoding="utf-8"))
    if not isinstance(raw, Mapping) or raw.get("schema_version") != 1:
        raise ValueError("operator activation template schema_version must be 1")
    production_raw = raw.get("production_activation_input")
    if not isinstance(production_raw, Mapping):
        raise ValueError("production_activation_input must be an object")
    production = production_activation_input_from_mapping(production_raw)
    supplied_digest = str(raw.get("production_activation_input_digest") or "")
    if supplied_digest != production.digest:
        raise ValueError("production activation input digest mismatch")
    accepted_main = str(raw.get("accepted_main_sha") or "")
    if not _SHA40_RE.fullmatch(accepted_main):
        raise ValueError("accepted_main_sha must be a lowercase 40-character Git SHA")
    decision = evaluate_production_activation(production)
    blockers = list(decision.blockers)
    if accepted_main != decision.accepted_main_sha:
        blockers.append("template accepted main differs from production activation evidence")
    requested = raw.get("operator_activation_requested")
    if not isinstance(requested, bool):
        raise ValueError("operator_activation_requested must be boolean")
    if not requested:
        blockers.append("operator activation template does not request production execution")
    return (
        ActivationTemplate(
            raw_production_input=dict(production_raw),
            production_input_digest=supplied_digest,
            accepted_main_sha=accepted_main,
            operator_activation_requested=requested,
        ),
        tuple(blockers),
    )


class OperatorCutoverController:
    def __init__(
        self,
        *,
        root: Path,
        activation_template: Path,
        activation_evidence: Path,
        services: Any | None = None,
        health_probe: Callable[[], Mapping[str, Any]] | None = None,
        runner=subprocess.run,
        geteuid: Callable[[], int] = os.geteuid,
        gate_loader=load_hosted_execution_gate,
        sleep: Callable[[float], None] = time.sleep,
    ) -> None:
        self.root = Path(root).resolve()
        self.activation_template = Path(activation_template).resolve()
        self.activation_evidence = Path(activation_evidence).resolve()
        self.services = services or SystemdServiceAdapter(runner=runner)
        self.health_probe = health_probe or _default_health_probe
        self.runner = runner
        self.geteuid = geteuid
        self.gate_loader = gate_loader
        self.sleep = sleep

    def _git(self, *args: str) -> str:
        result = self.runner(
            ["git", "-C", str(self.root), *args],
            text=True,
            capture_output=True,
            check=True,
        )
        return result.stdout.strip()

    def _tracked_clean(self) -> bool:
        return not bool(self._git("status", "--porcelain", "--untracked-files=no"))

    def _head(self) -> str:
        return self._git("rev-parse", "HEAD")

    def _observations(self) -> tuple[ServiceObservation, ServiceObservation]:
        return (
            self.services.observe(LEGACY_SERVICE),
            self.services.observe(V2_SERVICE),
        )

    def _unit_contract_blockers(
        self,
        legacy: ServiceObservation,
        v2: ServiceObservation,
    ) -> list[str]:
        blockers: list[str] = []
        if not legacy.loaded:
            blockers.append("legacy systemd service is not loaded")
        if not v2.loaded:
            blockers.append("Platform-v2 systemd service is not loaded")
        if "--require-startup-reconcile-success" not in legacy.exec_start:
            blockers.append("legacy rollback service lacks required startup-reconcile guard")
        if "platform_v2_hosted_runtime.py" not in v2.exec_start:
            blockers.append("Platform-v2 service does not execute the accepted hosted runtime")
        if "--enable-production-execution" not in v2.exec_start:
            blockers.append("Platform-v2 service lacks the explicit production execution flag")
        if str(self.activation_evidence) not in v2.exec_start:
            blockers.append("Platform-v2 service is not bound to the requested activation evidence path")
        return blockers

    def preflight(self) -> OperatorReport:
        blockers: list[str] = []
        accepted_main = ""
        try:
            template, template_blockers = load_activation_template(self.activation_template)
            accepted_main = template.accepted_main_sha
            blockers.extend(template_blockers)
        except Exception as exc:
            return OperatorReport(
                OperatorDisposition.BLOCKED,
                WriterAuthority.LEGACY,
                (f"activation template invalid: {type(exc).__name__}: {exc}",),
                "",
            )

        try:
            head = self._head()
            if head != accepted_main:
                blockers.append("checkout HEAD differs from accepted main")
            if not self._tracked_clean():
                blockers.append("tracked worktree is not clean")
        except Exception as exc:
            blockers.append(f"repository preflight failed: {type(exc).__name__}: {exc}")

        try:
            legacy, v2 = self._observations()
            blockers.extend(self._unit_contract_blockers(legacy, v2))
            if not legacy.active:
                blockers.append("legacy writer service is not active")
            if not legacy.enabled:
                blockers.append("legacy writer service must remain boot-enabled before cutover")
            if v2.active:
                blockers.append("Platform-v2 writer service is already active")
            if v2.enabled:
                blockers.append("Platform-v2 writer service must be boot-disabled before cutover")
        except Exception as exc:
            blockers.append(f"service preflight failed: {type(exc).__name__}: {exc}")

        return OperatorReport(
            OperatorDisposition.PREFLIGHT_READY if not blockers else OperatorDisposition.BLOCKED,
            WriterAuthority.LEGACY,
            tuple(blockers),
            accepted_main,
            activation_evidence_path=str(self.activation_evidence),
        )

    def _checkpoint(
        self,
        template: ActivationTemplate,
        legacy: ServiceObservation,
        v2: ServiceObservation,
    ) -> Path:
        evidence_dir = self.root / ".skyforge-platform-v2" / "operator-evidence"
        stamp = time.strftime("%Y%m%dT%H%M%SZ", time.gmtime())
        path = evidence_dir / f"pre-cutover-{stamp}-{time.time_ns()}.json"
        files: dict[str, object] = {}
        candidates = [
            self.activation_template,
            self.root / ".skyforge-orchestrator" / "state.json",
            self.root / ".skyforge-platform-v2" / "budget.json",
            Path("/etc/skyforge-orchestrator/env"),
        ]
        for obs in (legacy, v2):
            if obs.fragment_path:
                candidates.append(Path(obs.fragment_path))
        for candidate in candidates:
            key = str(candidate)
            try:
                files[key] = {
                    "exists": candidate.is_file(),
                    "sha256": _sha256_file(candidate) if candidate.is_file() else "",
                }
            except OSError as exc:
                files[key] = {"exists": None, "error": type(exc).__name__}
        value = {
            "schema_version": 1,
            "captured_at_utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
            "accepted_main_sha": template.accepted_main_sha,
            "checkout_head_sha": self._head(),
            "tracked_worktree_clean": self._tracked_clean(),
            "legacy_service": legacy.as_dict(),
            "v2_service": v2.as_dict(),
            "files": files,
        }
        _atomic_write_json(path, value)
        return path

    def _retire_activation_evidence(self) -> str:
        if not self.activation_evidence.exists():
            return ""
        stamp = time.strftime("%Y%m%dT%H%M%SZ", time.gmtime())
        retired = self.activation_evidence.with_name(
            self.activation_evidence.name + f".retired-{stamp}-{time.time_ns()}"
        )
        os.replace(self.activation_evidence, retired)
        return str(retired)

    def _wait_service(
        self,
        name: str,
        *,
        active: bool,
        attempts: int = 20,
        delay: float = 0.25,
    ) -> ServiceObservation:
        last = self.services.observe(name)
        for _ in range(max(1, attempts)):
            last = self.services.observe(name)
            if last.active is active:
                return last
            self.sleep(delay)
        state = "active" if active else "inactive"
        raise RuntimeError(f"{name} did not become {state}")

    def _verify_v2_health(self, gate_digest: str, *, attempts: int = 30) -> None:
        last_error = "health endpoint unavailable"
        for _ in range(max(1, attempts)):
            try:
                health = self.health_probe()
                if health.get("controller") != "platform-v2":
                    raise RuntimeError("health endpoint is not the Platform-v2 runtime")
                if health.get("production_execution_enabled") is not True:
                    raise RuntimeError("Platform-v2 health reports production execution disabled")
                driver = health.get("production_execution_driver")
                if not isinstance(driver, Mapping) or driver.get("running") is not True:
                    raise RuntimeError("Platform-v2 execution driver is not running")
                if health.get("production_execution_gate_digest") != gate_digest:
                    raise RuntimeError("Platform-v2 health gate digest differs from finalized evidence")
                return
            except Exception as exc:
                last_error = f"{type(exc).__name__}: {exc}"
                self.sleep(0.5)
        raise RuntimeError(f"Platform-v2 health verification timed out: {last_error}")

    def _verify_legacy_health(self, *, attempts: int = 30) -> None:
        last_error = "health endpoint unavailable"
        for _ in range(max(1, attempts)):
            try:
                health = self.health_probe()
                if health.get("status") != "ok":
                    raise RuntimeError("legacy health endpoint is not healthy")
                if health.get("last_startup_reconcile_error"):
                    raise RuntimeError("legacy startup reconciliation reports an error")
                if not health.get("last_startup_reconcile_success_at"):
                    raise RuntimeError("legacy startup reconciliation has not completed successfully")
                return
            except Exception as exc:
                last_error = f"{type(exc).__name__}: {exc}"
                self.sleep(0.5)
        raise RuntimeError(f"legacy health/reconciliation verification timed out: {last_error}")

    def cutover(self, *, execute: bool = False) -> OperatorReport:
        preflight = self.preflight()
        if preflight.disposition is not OperatorDisposition.PREFLIGHT_READY:
            return preflight
        if not execute:
            return preflight
        if self.geteuid() != 0:
            return OperatorReport(
                OperatorDisposition.BLOCKED,
                WriterAuthority.LEGACY,
                ("live cutover requires a root operator",),
                preflight.accepted_main_sha,
                activation_evidence_path=str(self.activation_evidence),
            )

        template, _ = load_activation_template(self.activation_template)
        legacy, v2 = self._observations()
        checkpoint = self._checkpoint(template, legacy, v2)
        events: list[OperatorEvent] = []
        authority = WriterAuthority.LEGACY
        retired = ""

        def record(kind: str, detail: str = "") -> None:
            events.append(OperatorEvent(len(events) + 1, kind, authority, detail))

        try:
            record("PRE_CUTOVER_CHECKPOINT", str(checkpoint))
            self.services.stop(LEGACY_SERVICE)
            self._wait_service(LEGACY_SERVICE, active=False)
            authority = WriterAuthority.NONE
            v2_now = self.services.observe(V2_SERVICE)
            if v2_now.active:
                raise RuntimeError("Platform-v2 became active before legacy revocation completed")
            self.services.disable(LEGACY_SERVICE)
            record("WRITER_NONE", "legacy writer observably inactive")

            final_evidence = template.final_evidence()
            _atomic_write_json(self.activation_evidence, final_evidence, mode=0o640)
            gate = self.gate_loader(
                self.activation_evidence,
                root=self.root,
                runner=self.runner,
            )
            if gate.disposition is not HostedExecutionGateDisposition.READY:
                raise RuntimeError(
                    "final activation evidence is blocked: " + "; ".join(gate.blockers)
                )
            record("ACTIVATION_EVIDENCE_FINALIZED", gate.digest)

            self.services.enable(V2_SERVICE)
            self.services.start(V2_SERVICE)
            self._wait_service(V2_SERVICE, active=True)
            if self.services.observe(LEGACY_SERVICE).active:
                raise RuntimeError("legacy writer became active during v2 activation")
            self._verify_v2_health(gate.digest)
            authority = WriterAuthority.V2
            record("V2_READY", "v2 active and gated health verified")
            return OperatorReport(
                OperatorDisposition.CUTOVER_COMPLETE,
                authority,
                (),
                template.accepted_main_sha,
                tuple(events),
                str(checkpoint),
                str(self.activation_evidence),
            )
        except Exception as exc:
            try:
                legacy_now, v2_now = self._observations()
            except Exception as observe_exc:
                return OperatorReport(
                    OperatorDisposition.BLOCKED,
                    WriterAuthority.NONE,
                    (
                        f"cutover failed: {type(exc).__name__}: {exc}",
                        f"post-failure service observation failed: {type(observe_exc).__name__}: {observe_exc}",
                    ),
                    template.accepted_main_sha,
                    tuple(events),
                    str(checkpoint),
                    str(self.activation_evidence),
                )

            if v2_now.active:
                try:
                    self.services.stop(V2_SERVICE)
                    self._wait_service(V2_SERVICE, active=False)
                    self.services.disable(V2_SERVICE)
                    v2_now = self.services.observe(V2_SERVICE)
                except Exception as cleanup_exc:
                    return OperatorReport(
                        OperatorDisposition.BLOCKED,
                        WriterAuthority.NONE,
                        (
                            f"cutover failed: {type(exc).__name__}: {exc}",
                            f"v2 cleanup failed: {type(cleanup_exc).__name__}: {cleanup_exc}",
                        ),
                        template.accepted_main_sha,
                        tuple(events),
                        str(checkpoint),
                        str(self.activation_evidence),
                    )
            elif v2_now.enabled:
                try:
                    self.services.disable(V2_SERVICE)
                except Exception as cleanup_exc:
                    return OperatorReport(
                        OperatorDisposition.BLOCKED,
                        WriterAuthority.NONE,
                        (
                            f"cutover failed: {type(exc).__name__}: {exc}",
                            f"v2 disable failed: {type(cleanup_exc).__name__}: {cleanup_exc}",
                        ),
                        template.accepted_main_sha,
                        tuple(events),
                        str(checkpoint),
                        str(self.activation_evidence),
                    )

            retired = self._retire_activation_evidence()
            legacy_now = self.services.observe(LEGACY_SERVICE)
            v2_now = self.services.observe(V2_SERVICE)
            if legacy_now.active and not v2_now.active:
                authority = WriterAuthority.LEGACY
                record("FAILED_SAFE_LEGACY", f"{type(exc).__name__}: {exc}")
                return OperatorReport(
                    OperatorDisposition.FAILED_SAFE_LEGACY,
                    authority,
                    (f"{type(exc).__name__}: {exc}",),
                    template.accepted_main_sha,
                    tuple(events),
                    str(checkpoint),
                    str(self.activation_evidence),
                    retired,
                )
            if not legacy_now.active and not v2_now.active:
                authority = WriterAuthority.NONE
                record("FAILED_SAFE_NONE", f"{type(exc).__name__}: {exc}")
                return OperatorReport(
                    OperatorDisposition.FAILED_SAFE_NONE,
                    authority,
                    (f"{type(exc).__name__}: {exc}",),
                    template.accepted_main_sha,
                    tuple(events),
                    str(checkpoint),
                    str(self.activation_evidence),
                    retired,
                )
            return OperatorReport(
                OperatorDisposition.BLOCKED,
                WriterAuthority.NONE,
                (
                    f"cutover failed: {type(exc).__name__}: {exc}",
                    "unable to prove a single-writer or NONE post-failure state",
                ),
                template.accepted_main_sha,
                tuple(events),
                str(checkpoint),
                str(self.activation_evidence),
                retired,
            )

    def _transfer_preflight(
        self,
        *,
        event_key: str,
        issue_number: int,
        source_id: str,
        signal_kind: str = "task",
    ) -> tuple[list[str], bool]:
        blockers: list[str] = []
        already_transferred = False
        try:
            legacy, v2 = self._observations()
            if not legacy.loaded:
                blockers.append("legacy systemd service is not loaded")
            if not v2.loaded:
                blockers.append("Platform-v2 systemd service is not loaded")
            if not legacy.active:
                blockers.append("legacy writer must be active before authority transfer")
            if v2.active:
                blockers.append("Platform-v2 writer must be inactive before authority transfer")
        except Exception as exc:
            blockers.append(f"service observation failed: {type(exc).__name__}: {exc}")

        try:
            health = self.health_probe()
            if health.get("status") != "ok":
                blockers.append("legacy health endpoint is not healthy")
            if health.get("paused") is not True:
                blockers.append("legacy controller must be paused before authority transfer")
            if health.get("pending_worker"):
                blockers.append("legacy worker is in flight")
            if health.get("pending_decision"):
                blockers.append("legacy pending decision is in flight")
        except Exception as exc:
            blockers.append(f"legacy health preflight failed: {type(exc).__name__}: {exc}")

        try:
            state_path, _backup_path = _legacy_state_paths(self.root)
            raw = _load_json_mapping(state_path, "legacy state")
            if raw.get("paused") is not True:
                blockers.append("legacy durable state is not paused")
            if raw.get("pending_worker"):
                blockers.append("legacy durable state has a pending worker")
            if raw.get("pending_decision"):
                blockers.append("legacy durable state has a pending decision")
            target, protected, already_transferred = _find_transfer_target(
                raw,
                event_key=event_key,
                issue_number=issue_number,
                source_id=source_id,
                signal_kind=signal_kind,
            )
            unrelated = tuple(
                event for event in protected if target is None or event.event_id != target.event_id
            )
            if unrelated:
                blockers.append(
                    "other protected legacy authority is pending; transfer must be unambiguous"
                )
        except Exception as exc:
            blockers.append(f"legacy authority preflight failed: {type(exc).__name__}: {exc}")
        return blockers, already_transferred

    def _retire_legacy_authority_for_v2(
        self,
        *,
        event_key: str,
        issue_number: int,
        source_id: str,
        signal_kind: str = "task",
    ) -> Mapping[str, Any]:
        state_path, backup_path = _legacy_state_paths(self.root)
        raw = _load_json_mapping(state_path, "legacy state")
        if raw.get("paused") is not True:
            raise RuntimeError("legacy durable state is not paused")
        if raw.get("pending_worker") or raw.get("pending_decision"):
            raise RuntimeError("legacy has in-flight worker/decision authority")
        target, protected, already_transferred = _find_transfer_target(
            raw,
            event_key=event_key,
            issue_number=issue_number,
            source_id=source_id,
            signal_kind=signal_kind,
        )
        if already_transferred:
            raise RuntimeError("authority is already transferred")
        assert target is not None
        if len(protected) != 1 or protected[0].event_id != target.event_id:
            raise RuntimeError("other protected legacy authority is pending")

        retired = [
            normalize_legacy_event_key(value)
            for value in (raw.get("retired_event_keys") or [])
            if str(value or "")
        ]
        if event_key not in retired:
            retired.append(event_key)
        raw["retired_event_keys"] = retired[-LEGACY_MAX_RETIRED_EVENT_KEYS:]
        pending = raw.get("pending_events") or []
        raw["pending_events"] = [
            value
            for value in pending
            if DurableEvent.from_legacy_mapping(value).event_id != event_key
        ]
        transfer = {
            "schema_version": 1,
            "at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
            "event_key": event_key,
            "issue_number": issue_number,
            "source_id": str(source_id),
            "event": target.event,
            "action": target.action,
            "signal_kind": target.signal_kind,
            "signal_text_digest": canonical_digest(str(target.signal_text or "")),
            "disposition": "RETIRED_FOR_PLATFORM_V2_TRANSFER",
            "completed": False,
        }
        transfers = list(raw.get("platform_v2_authority_transfers") or [])
        transfers.append(transfer)
        raw["platform_v2_authority_transfers"] = transfers[-50:]
        raw["last_platform_v2_authority_transfer"] = transfer

        primary_stat = state_path.stat()
        _atomic_write_json_preserving_metadata(state_path, raw)
        _atomic_write_json_preserving_metadata(
            backup_path,
            raw,
            fallback_stat=primary_stat,
        )
        return transfer

    def _verify_transferred_legacy_health(
        self,
        *,
        event_key: str,
        attempts: int = 30,
    ) -> None:
        last_error = "legacy health endpoint unavailable"
        for _ in range(max(1, attempts)):
            try:
                health = self.health_probe()
                if health.get("status") != "ok":
                    raise RuntimeError("legacy health endpoint is not healthy")
                if health.get("paused") is not True:
                    raise RuntimeError("legacy controller is not paused after transfer")
                if health.get("pending_worker") or health.get("pending_decision"):
                    raise RuntimeError("legacy has in-flight worker/decision after transfer")
                if health.get("last_startup_reconcile_error"):
                    raise RuntimeError("legacy startup reconciliation reports an error")
                if not health.get("last_startup_reconcile_success_at"):
                    raise RuntimeError("legacy startup reconciliation has not completed")
                state_path, _backup_path = _legacy_state_paths(self.root)
                raw = _load_json_mapping(state_path, "legacy state")
                protected = tuple(
                    event
                    for event in _legacy_pending_events(raw)
                    if event.signal_kind in PROTECTED_AUTHORITY_SIGNAL_KINDS
                )
                if protected:
                    ids = ",".join(event.event_id for event in protected)
                    raise RuntimeError(
                        "protected authority reappeared after transfer reconciliation: " + ids
                    )
                retired = _legacy_key_set(raw, "retired_event_keys")
                completed = _legacy_key_set(raw, "completed_authority_event_keys")
                if event_key not in retired:
                    raise RuntimeError("transferred authority is not durably retired")
                if event_key in completed:
                    raise RuntimeError("transferred authority was incorrectly marked completed")
                return
            except Exception as exc:
                last_error = f"{type(exc).__name__}: {exc}"
                self.sleep(0.5)
        raise RuntimeError(f"legacy transfer verification timed out: {last_error}")

    def _legacy_transfer_evidence_blockers(
        self,
        *,
        event_key: str,
        issue_number: int,
        source_id: str,
    ) -> list[str]:
        blockers: list[str] = []
        try:
            state_path, _backup_path = _legacy_state_paths(self.root)
            raw = _load_json_mapping(state_path, "legacy state")
            retired = _legacy_key_set(raw, "retired_event_keys")
            completed = _legacy_key_set(raw, "completed_authority_event_keys")
            if event_key not in retired:
                blockers.append("legacy has not retired the exact authority for Platform-v2 transfer")
            if event_key in completed:
                blockers.append("legacy authority is marked completed rather than transferred")
            for event in _legacy_pending_events(raw):
                if event.event_id == event_key:
                    blockers.append("transferred legacy authority is still pending")
            transfers = raw.get("platform_v2_authority_transfers") or []
            if not isinstance(transfers, list):
                blockers.append("legacy authority transfer evidence is malformed")
            else:
                matches = [
                    value
                    for value in transfers
                    if isinstance(value, Mapping)
                    and str(value.get("event_key") or "") == event_key
                    and value.get("issue_number") == issue_number
                    and str(value.get("source_id") or "") == str(source_id)
                    and value.get("disposition") == "RETIRED_FOR_PLATFORM_V2_TRANSFER"
                    and value.get("completed") is False
                ]
                if len(matches) != 1:
                    blockers.append("exact legacy transfer evidence is unavailable or ambiguous")
        except Exception as exc:
            blockers.append(f"legacy transfer evidence unavailable: {type(exc).__name__}: {exc}")
        return blockers

    def _v2_authority_retirement_preflight(
        self,
        *,
        event_key: str,
        issue_number: int,
        source_id: str,
    ) -> tuple[list[str], bool]:
        blockers: list[str] = []
        try:
            legacy, v2 = self._observations()
            if not legacy.loaded or not legacy.active:
                blockers.append("legacy writer must be loaded and active before V2 authority retirement")
            if not v2.loaded:
                blockers.append("Platform-v2 service is not loaded")
            if v2.active:
                blockers.append("Platform-v2 must be inactive before V2 authority retirement")
        except Exception as exc:
            blockers.append(f"service observation failed: {type(exc).__name__}: {exc}")

        try:
            health = self.health_probe()
            if health.get("status") != "ok":
                blockers.append("legacy health endpoint is not healthy")
            if health.get("paused") is not True:
                blockers.append("legacy controller must be paused before V2 authority retirement")
            if health.get("pending_worker"):
                blockers.append("legacy worker is in flight")
            if health.get("pending_decision"):
                blockers.append("legacy pending decision is in flight")
        except Exception as exc:
            blockers.append(f"legacy health preflight failed: {type(exc).__name__}: {exc}")

        blockers.extend(
            self._legacy_transfer_evidence_blockers(
                event_key=event_key,
                issue_number=issue_number,
                source_id=source_id,
            )
        )

        already_complete = False
        try:
            inspection = inspect_v2_authority_retirement(
                root=self.root,
                event_id=event_key,
                issue_number=issue_number,
                source_id=source_id,
            )
            blockers.extend(inspection.blockers)
            already_complete = inspection.already_complete
        except Exception as exc:
            blockers.append(
                f"V2 authority retirement preflight failed: {type(exc).__name__}: {exc}"
            )
        return blockers, already_complete

    def retire_v2_authority(
        self,
        *,
        event_key: str,
        issue_number: int,
        source_id: str,
        execute: bool = False,
    ) -> OperatorReport:
        accepted_main = self._rollback_accepted_main()
        blockers, already_complete = self._v2_authority_retirement_preflight(
            event_key=event_key,
            issue_number=issue_number,
            source_id=source_id,
        )
        if blockers:
            return OperatorReport(
                OperatorDisposition.BLOCKED,
                WriterAuthority.LEGACY,
                tuple(sorted(set(blockers))),
                accepted_main,
            )
        if not execute:
            return OperatorReport(
                OperatorDisposition.V2_AUTHORITY_RETIREMENT_READY,
                WriterAuthority.LEGACY,
                (),
                accepted_main,
                (
                    OperatorEvent(
                        1,
                        (
                            "V2_AUTHORITY_ALREADY_RETIRED"
                            if already_complete
                            else "V2_AUTHORITY_RETIREMENT_PREFLIGHT"
                        ),
                        WriterAuthority.LEGACY,
                        event_key,
                    ),
                ),
            )
        if self.geteuid() != 0:
            return OperatorReport(
                OperatorDisposition.BLOCKED,
                WriterAuthority.LEGACY,
                ("live V2 authority retirement requires a root operator",),
                accepted_main,
            )

        try:
            record = retire_terminal_v2_authority(
                root=self.root,
                event_id=event_key,
                issue_number=issue_number,
                source_id=source_id,
            )
            post, complete = self._v2_authority_retirement_preflight(
                event_key=event_key,
                issue_number=issue_number,
                source_id=source_id,
            )
            if post or not complete:
                raise RuntimeError(
                    "post-retirement verification failed: "
                    + "; ".join(post or ("retirement evidence is not complete",))
                )
            return OperatorReport(
                OperatorDisposition.V2_AUTHORITY_RETIREMENT_COMPLETE,
                WriterAuthority.LEGACY,
                (),
                accepted_main,
                (
                    OperatorEvent(
                        1,
                        (
                            "V2_AUTHORITY_ALREADY_RETIRED"
                            if already_complete
                            else "V2_AUTHORITY_RETIRED_NONEXECUTED"
                        ),
                        WriterAuthority.LEGACY,
                        record.retirement_id,
                    ),
                ),
            )
        except Exception as exc:
            try:
                legacy, v2 = self._observations()
                if legacy.active and not v2.active:
                    return OperatorReport(
                        OperatorDisposition.FAILED_SAFE_LEGACY,
                        WriterAuthority.LEGACY,
                        (f"{type(exc).__name__}: {exc}",),
                        accepted_main,
                    )
            except Exception:
                pass
            return OperatorReport(
                OperatorDisposition.BLOCKED,
                WriterAuthority.NONE,
                (f"V2 authority retirement failed: {type(exc).__name__}: {exc}",),
                accepted_main,
            )

    def transfer_authority(
        self,
        *,
        event_key: str,
        issue_number: int,
        source_id: str,
        signal_kind: str = "task",
        execute: bool = False,
    ) -> OperatorReport:
        accepted_main = self._rollback_accepted_main()
        blockers, already_transferred = self._transfer_preflight(
            event_key=event_key,
            issue_number=issue_number,
            source_id=source_id,
            signal_kind=signal_kind,
        )
        if blockers:
            return OperatorReport(
                OperatorDisposition.BLOCKED,
                WriterAuthority.LEGACY,
                tuple(blockers),
                accepted_main,
            )
        if not execute:
            return OperatorReport(
                OperatorDisposition.AUTHORITY_TRANSFER_READY,
                WriterAuthority.LEGACY,
                (),
                accepted_main,
                (
                    OperatorEvent(
                        1,
                        "AUTHORITY_ALREADY_TRANSFERRED" if already_transferred else "AUTHORITY_TRANSFER_PREFLIGHT",
                        WriterAuthority.LEGACY,
                        event_key,
                    ),
                ),
            )
        if self.geteuid() != 0:
            return OperatorReport(
                OperatorDisposition.BLOCKED,
                WriterAuthority.LEGACY,
                ("live authority transfer requires a root operator",),
                accepted_main,
            )
        if already_transferred:
            try:
                self._verify_transferred_legacy_health(event_key=event_key)
            except Exception as exc:
                return OperatorReport(
                    OperatorDisposition.FAILED_SAFE_LEGACY,
                    WriterAuthority.LEGACY,
                    (f"{type(exc).__name__}: {exc}",),
                    accepted_main,
                )
            return OperatorReport(
                OperatorDisposition.AUTHORITY_TRANSFER_COMPLETE,
                WriterAuthority.LEGACY,
                (),
                accepted_main,
                (
                    OperatorEvent(1, "AUTHORITY_ALREADY_TRANSFERRED", WriterAuthority.LEGACY, event_key),
                ),
            )

        events: list[OperatorEvent] = []
        authority = WriterAuthority.LEGACY

        def record(kind: str, detail: str = "") -> None:
            events.append(OperatorEvent(len(events) + 1, kind, authority, detail))

        try:
            self.services.stop(LEGACY_SERVICE)
            self._wait_service(LEGACY_SERVICE, active=False)
            authority = WriterAuthority.NONE
            if self.services.observe(V2_SERVICE).active:
                raise RuntimeError("Platform-v2 became active during authority transfer")
            record("AUTHORITY_TRANSFER_WRITER_NONE", "legacy writer observably inactive")

            transfer = self._retire_legacy_authority_for_v2(
                event_key=event_key,
                issue_number=issue_number,
                source_id=source_id,
                signal_kind=signal_kind,
            )
            record("AUTHORITY_RETIRED_FOR_V2_TRANSFER", json.dumps(transfer, sort_keys=True))

            self.services.start(LEGACY_SERVICE)
            self._wait_service(LEGACY_SERVICE, active=True)
            if self.services.observe(V2_SERVICE).active:
                raise RuntimeError("Platform-v2 became active while legacy restarted")
            authority = WriterAuthority.LEGACY
            self._verify_transferred_legacy_health(event_key=event_key)
            record("AUTHORITY_TRANSFER_LEGACY_READY", "startup reconciliation preserved transfer suppression")
            return OperatorReport(
                OperatorDisposition.AUTHORITY_TRANSFER_COMPLETE,
                authority,
                (),
                accepted_main,
                tuple(events),
            )
        except Exception as exc:
            try:
                legacy_now, v2_now = self._observations()
                if v2_now.active:
                    return OperatorReport(
                        OperatorDisposition.BLOCKED,
                        WriterAuthority.NONE,
                        (
                            f"authority transfer failed: {type(exc).__name__}: {exc}",
                            "Platform-v2 unexpectedly active; manual incident handling required",
                        ),
                        accepted_main,
                        tuple(events),
                    )
                if legacy_now.active:
                    try:
                        self.services.stop(LEGACY_SERVICE)
                        self._wait_service(LEGACY_SERVICE, active=False)
                    except Exception as stop_exc:
                        return OperatorReport(
                            OperatorDisposition.BLOCKED,
                            WriterAuthority.NONE,
                            (
                                f"authority transfer failed: {type(exc).__name__}: {exc}",
                                f"failed to restore NONE boundary: {type(stop_exc).__name__}: {stop_exc}",
                            ),
                            accepted_main,
                            tuple(events),
                        )
                authority = WriterAuthority.NONE
                record("AUTHORITY_TRANSFER_FAILED_SAFE_NONE", f"{type(exc).__name__}: {exc}")
                return OperatorReport(
                    OperatorDisposition.FAILED_SAFE_NONE,
                    authority,
                    (f"{type(exc).__name__}: {exc}",),
                    accepted_main,
                    tuple(events),
                )
            except Exception as observe_exc:
                return OperatorReport(
                    OperatorDisposition.BLOCKED,
                    WriterAuthority.NONE,
                    (
                        f"authority transfer failed: {type(exc).__name__}: {exc}",
                        f"post-failure observation failed: {type(observe_exc).__name__}: {observe_exc}",
                    ),
                    accepted_main,
                    tuple(events),
                )

    def _rollback_accepted_main(self) -> str:
        try:
            template, _ = load_activation_template(self.activation_template)
            return template.accepted_main_sha
        except Exception:
            pass
        try:
            raw = json.loads(self.activation_evidence.read_text(encoding="utf-8"))
            value = str(raw.get("accepted_main_sha") or "") if isinstance(raw, Mapping) else ""
            if _SHA40_RE.fullmatch(value):
                return value
        except Exception:
            pass
        try:
            value = self._head()
            return value if _SHA40_RE.fullmatch(value) else ""
        except Exception:
            return ""

    def rollback(self, *, execute: bool = False) -> OperatorReport:
        accepted_main = self._rollback_accepted_main()
        legacy, v2 = self._observations()
        blockers = self._unit_contract_blockers(legacy, v2)
        if legacy.active and v2.active:
            blockers.append("dual active writers observed; manual incident handling required")
        if blockers:
            return OperatorReport(
                OperatorDisposition.BLOCKED,
                WriterAuthority.NONE,
                tuple(blockers),
                accepted_main,
            )
        if not execute:
            authority = (
                WriterAuthority.V2
                if v2.active
                else WriterAuthority.LEGACY
                if legacy.active
                else WriterAuthority.NONE
            )
            return OperatorReport(
                OperatorDisposition.PREFLIGHT_READY,
                authority,
                (),
                accepted_main,
            )
        if self.geteuid() != 0:
            return OperatorReport(
                OperatorDisposition.BLOCKED,
                WriterAuthority.NONE,
                ("live rollback requires a root operator",),
                accepted_main,
            )

        events: list[OperatorEvent] = []
        authority = (
            WriterAuthority.V2
            if v2.active
            else WriterAuthority.LEGACY
            if legacy.active
            else WriterAuthority.NONE
        )
        retired = ""

        def record(kind: str, detail: str = "") -> None:
            events.append(OperatorEvent(len(events) + 1, kind, authority, detail))

        if authority is WriterAuthority.LEGACY:
            try:
                self._verify_legacy_health()
            except Exception as exc:
                return OperatorReport(
                    OperatorDisposition.FAILED_SAFE_LEGACY,
                    authority,
                    (f"{type(exc).__name__}: {exc}",),
                    accepted_main,
                    tuple(events),
                )
            return OperatorReport(
                OperatorDisposition.ROLLBACK_COMPLETE,
                authority,
                (),
                accepted_main,
                tuple(events),
            )

        try:
            if v2.active:
                self.services.stop(V2_SERVICE)
                self._wait_service(V2_SERVICE, active=False)
            self.services.disable(V2_SERVICE)
            if self.services.observe(LEGACY_SERVICE).active:
                raise RuntimeError("legacy writer became active before rollback NONE boundary")
            authority = WriterAuthority.NONE
            record("ROLLBACK_WRITER_NONE", "v2 observably inactive before legacy start")
            retired = self._retire_activation_evidence()
            if retired:
                record("ACTIVATION_EVIDENCE_RETIRED", retired)

            self.services.enable(LEGACY_SERVICE)
            self.services.start(LEGACY_SERVICE)
            self._wait_service(LEGACY_SERVICE, active=True)
            if self.services.observe(V2_SERVICE).active:
                raise RuntimeError("v2 writer became active during legacy rollback")
            self._verify_legacy_health()
            authority = WriterAuthority.LEGACY
            record("ROLLBACK_LEGACY_READY", "startup reconciliation completed before dispatch")
            return OperatorReport(
                OperatorDisposition.ROLLBACK_COMPLETE,
                authority,
                (),
                accepted_main,
                tuple(events),
                activation_evidence_path=str(self.activation_evidence),
                retired_activation_evidence_path=retired,
            )
        except Exception as exc:
            try:
                if self.services.observe(LEGACY_SERVICE).active:
                    self.services.stop(LEGACY_SERVICE)
                    self._wait_service(LEGACY_SERVICE, active=False)
                self.services.disable(LEGACY_SERVICE)
            except Exception as cleanup_exc:
                return OperatorReport(
                    OperatorDisposition.BLOCKED,
                    WriterAuthority.NONE,
                    (
                        f"rollback failed: {type(exc).__name__}: {exc}",
                        f"legacy cleanup failed: {type(cleanup_exc).__name__}: {cleanup_exc}",
                    ),
                    accepted_main,
                    tuple(events),
                    retired_activation_evidence_path=retired,
                )
            authority = WriterAuthority.NONE
            record("ROLLBACK_FAILED_SAFE_NONE", f"{type(exc).__name__}: {exc}")
            return OperatorReport(
                OperatorDisposition.FAILED_SAFE_NONE,
                authority,
                (f"{type(exc).__name__}: {exc}",),
                accepted_main,
                tuple(events),
                retired_activation_evidence_path=retired,
            )
