#!/usr/bin/env python3
"""Skyforge orchestrator entrypoint with durable material-state coalescing.

The accepted pre-AUDIT-0021 controller is retained verbatim in
``skyforge_orchestrator_core.py``.  This entrypoint executes that core in this
module namespace, then tightens the event-to-classifier boundary without
forking the worker/handoff implementation.
"""

from pathlib import Path as _BootstrapPath

_CORE_PATH = _BootstrapPath(__file__).with_name("skyforge_orchestrator_core.py")
_CORE_SOURCE = _CORE_PATH.read_text()
_CORE_MAIN = '\nif __name__ == "__main__":\n    raise SystemExit(main())\n'
if _CORE_SOURCE.count(_CORE_MAIN) != 1:
    raise RuntimeError("Skyforge orchestrator core entrypoint shape changed; refuse overlay load")
exec(compile(_CORE_SOURCE.replace(_CORE_MAIN, "\n"), str(_CORE_PATH), "exec"), globals(), globals())

_BaseOrchestrator = Orchestrator
_BaseAuditSignalKind = _audit_signal_kind
DEFAULT_MATERIAL_SETTLE_SECONDS = 60
CONTROLLER_RUNTIME_PATHS = {
    "scripts/orchestrator/skyforge_orchestrator.py",
    "scripts/orchestrator/skyforge_orchestrator_core.py",
}


def _audit_signal_kind(body_lower: str) -> str | None:
    if body_lower.strip() == "/skyforge-reset-budget":
        return "budget_reset"
    return _BaseAuditSignalKind(body_lower)


class Orchestrator(_BaseOrchestrator):
    """Controller with one-classification-per-material-state semantics."""

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self._active_dispatch_events: list[EventDecision] = []
        self._candidate_material_fingerprint: str | None = None

    @staticmethod
    def _material_fingerprint(snapshot: dict[str, Any]) -> str:
        encoded = json.dumps(snapshot, sort_keys=True, separators=(",", ":")).encode("utf-8")
        return hashlib.sha256(encoded).hexdigest()

    def _material_state_snapshot(self) -> dict[str, Any]:
        prs = _json_cmd(
            [
                "gh", "pr", "list",
                "--repo", self.repo,
                "--state", "open",
                "--limit", "50",
                "--json", "number,isDraft,headRefName,headRefOid,baseRefName",
            ],
            cwd=self.root,
            timeout=60,
        )
        normalized_prs = [
            {
                "number": int(pr.get("number") or 0),
                "isDraft": bool(pr.get("isDraft")),
                "headRefName": str(pr.get("headRefName") or ""),
                "headRefOid": str(pr.get("headRefOid") or ""),
                "baseRefName": str(pr.get("baseRefName") or ""),
            }
            for pr in prs
            if isinstance(pr, dict)
        ]
        normalized_prs.sort(key=lambda item: item["number"])
        managed = []
        for lane, value in sorted((self.state.data.get("managed") or {}).items()):
            if not isinstance(value, dict):
                continue
            managed.append(
                {
                    "lane": str(lane),
                    "branch": str(value.get("branch") or ""),
                    "pr_number": int(value.get("pr_number") or 0),
                }
            )
        return {
            "main": _run(["git", "rev-parse", "HEAD"], cwd=self.root).stdout.strip(),
            "open_prs": normalized_prs,
            "controller_managed": managed,
            "classifier_policy": CLASSIFIER_POLICY_FINGERPRINT,
        }

    def _force_fresh_classification(self) -> bool:
        managed_numbers = {
            int(value.get("pr_number") or 0)
            for value in (self.state.data.get("managed") or {}).values()
            if isinstance(value, dict)
        }
        for event in self._active_dispatch_events:
            if event.action in {"manual_command", "audit_signal"}:
                return True
            if event.pr_number is not None and int(event.pr_number) in managed_numbers:
                return True
        return False

    def _settle_remaining(self, events: list[EventDecision]) -> int:
        settle = _env_int(
            "SKYFORGE_ORCHESTRATOR_MATERIAL_SETTLE_SECONDS",
            DEFAULT_MATERIAL_SETTLE_SECONDS,
            minimum=0,
        )
        if settle <= 0:
            return 0
        now = datetime.now(timezone.utc)
        remaining = 0
        for event in events:
            if event.event not in {"push", "pull_request", "reconcile"} or not event.head_sha:
                continue
            if not event.observed_at:
                continue
            try:
                observed = datetime.fromisoformat(str(event.observed_at).replace("Z", "+00:00"))
            except ValueError:
                continue
            if observed.tzinfo is None:
                observed = observed.replace(tzinfo=timezone.utc)
            age = max(0.0, (now - observed.astimezone(timezone.utc)).total_seconds())
            remaining = max(remaining, max(0, int(settle - age)))
        return remaining

    def dispatch(self, events: list[EventDecision]) -> None:
        record = self._decision_record()
        pending_worker = self.state.data.get("pending_worker")
        if not (record and isinstance(pending_worker, dict)):
            settle_remaining = self._settle_remaining(events)
            if settle_remaining > 0:
                self._metric("material_settle_deferrals")
                self._schedule_pending(max(1, settle_remaining))
                return
            # Quiescence is required for every repository head represented by the batch, not only
            # workflow_run events. This prevents a main push from being classified before its CI
            # has even finished and then being classified again on workflow completion.
            heads = {event.head_sha for event in events if event.head_sha}
            for head in heads:
                if not self.workflows_quiescent(head):
                    self._metric("all_head_quiescence_deferrals")
                    self._schedule_pending(max(30, self.debounce_seconds))
                    return

        self._active_dispatch_events = list(events)
        try:
            return super().dispatch(events)
        finally:
            self._active_dispatch_events = []

    def _codex_classifier(self, prompt: str) -> dict[str, Any]:
        snapshot = self._material_state_snapshot()
        fingerprint = self._material_fingerprint(snapshot)
        prior = self.state.data.get("last_material_classifier_fingerprint")
        if not self._force_fresh_classification() and prior == fingerprint:
            self._candidate_material_fingerprint = None
            self._metric("material_duplicate_batches_suppressed")
            return {
                "decision": "NOOP",
                "lane": None,
                "pr_number": None,
                "objective": None,
                "stop_boundary": None,
                "reusable_evidence": None,
                "worker_tier": None,
                "allowed_paths": None,
                "reason": "material repository state already classified; retire duplicate wake without model use",
                "human_message": None,
                "material_state_duplicate": True,
            }

        self._candidate_material_fingerprint = fingerprint
        try:
            return super()._codex_classifier(prompt)
        except Exception:
            self._candidate_material_fingerprint = None
            raise

    def _cache_decision(self, decision: dict[str, Any], events: list[EventDecision]) -> None:
        super()._cache_decision(decision, events)
        candidate = self._candidate_material_fingerprint
        self._candidate_material_fingerprint = None
        if candidate:
            with self._state_lock:
                self.state.data["last_material_classifier_fingerprint"] = candidate
                self.state.data["last_material_classifier_at"] = _utc_now()
                self.state.save()
            self._metric("material_states_classified")

    def reset_daily_budget(self, *, actor: str) -> None:
        with self._state_lock:
            self.state.data["budget_day"] = _utc_day()
            self.state.data["classifier_calls_today"] = 0
            self.state.data["luna_worker_calls_today"] = 0
            self.state.data["worker_calls_today"] = 0
            self.state.data["last_dispatch_epoch"] = 0.0
            if self.state.data.get("blocked_kind") == "local_budget":
                self.state.data["blocked_kind"] = None
                self.state.data["blocked_reason"] = None
                self.state.data["blocked_until_epoch"] = 0.0
            self.state.data["last_budget_reset_at"] = _utc_now()
            self.state.data["last_budget_reset_by"] = actor
            self.state.save()
        self._metric("manual_budget_resets")

    def enqueue(self, event: EventDecision) -> None:
        if event.action == "audit_signal" and event.signal_kind == "budget_reset":
            self._metric("events_seen")
            if not self.is_paused() or self.state.data.get("pending_worker"):
                self._metric("budget_reset_rejections")
                print(
                    "[orchestrator] budget reset rejected: pause controller and ensure no pending worker first",
                    flush=True,
                )
                return
            self.reset_daily_budget(actor="trusted-github")
            print("[orchestrator] trusted local daily budget reset applied; durable queue preserved", flush=True)
            return
        return super().enqueue(event)

    def health_snapshot(self) -> dict[str, Any]:
        status = super().health_snapshot()
        status["last_material_classifier_at"] = self.state.data.get("last_material_classifier_at")
        status["material_duplicate_batches_suppressed"] = int(
            (self.state.data.get("metrics") or {}).get("material_duplicate_batches_suppressed") or 0
        )
        status["last_budget_reset_at"] = self.state.data.get("last_budget_reset_at")
        return status


if __name__ == "__main__":
    raise SystemExit(main())
