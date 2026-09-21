from __future__ import annotations

from dataclasses import dataclass
import json
from pathlib import Path
import random
import tempfile
import unittest

from v2.decision import SourcePRState
from v2.external import ExternalProducerClaim
from v2.external_service import ExternalClaimLedger, refresh_external_claims
from v2.effects import (
    EffectKind,
    EffectReconcileDisposition,
    EffectStatus,
    RemoteEffectIdentity,
    RemoteEffectObservation,
    RemoteEffectPresence,
    RemoteEffectRecord,
    reconcile_remote_effect,
)
from v2.events import DurableEvent
from v2.hosted_state import HostedIngressState, ingest_event
from v2.human_review import (
    DevelopmentApiHumanReviewSource,
    HumanReviewLedger,
    HumanReviewSubmission,
    HumanReviewVerdict,
    RepeatReviewDisposition,
    evaluate_repeat_review_readiness,
)
from v2.roadmap_recovery import ClosedActiveDisposition, RoadmapIssueState
from v2.roadmap_service import (
    HumanGateReviewDisposition,
    RoadmapAuthorityLedger,
    RoadmapBlockRecord,
    apply_human_gate_review,
    apply_roadmap_decision,
    bind_active_pr,
    reconcile_active_issue,
)
from v2.roadmap_shadow import (
    RoadmapControlObservation,
    RoadmapIssueTruth,
    ShadowRoadmapManifest,
    evaluate_roadmap_shadow,
)
from v2.worker import (
    WorkerLifecycleState,
    WorkerRecoveryDisposition,
    WorkerRecoveryObservation,
    classify_worker_recovery,
)


BRANCH = "platform/x6-lifecycle-worker"
REMOTE_EFFECT_ID = "comment:x6-lifecycle"
SHA_A = "a" * 40
SHA_B = "b" * 40


def lifecycle_manifest() -> ShadowRoadmapManifest:
    return ShadowRoadmapManifest.from_mapping(
        {
            "schema_version": 1,
            "roadmap_id": "x6-lifecycle-test",
            "enabled": True,
            "max_auto_claims_per_utc_day": 6,
            "nodes": [
                {
                    "id": "repair",
                    "kind": "task",
                    "lane": "Implementation",
                    "issue_number": 1001,
                    "priority": 100,
                    "max_runs": 1,
                    "prerequisites": [],
                    "objective_hint": "repair specimen",
                    "stop_boundary": "stop after machine qualification",
                },
                {
                    "id": "review",
                    "kind": "gate",
                    "lane": "Implementation",
                    "priority": 90,
                    "max_runs": 1,
                    "prerequisites": ["repair"],
                    "human_message": "inspect exact artifact; machines must not self-pass",
                },
            ],
        }
    )


def initial_roadmap(manifest: ShadowRoadmapManifest) -> RoadmapAuthorityLedger:
    return RoadmapAuthorityLedger(
        roadmap_id=manifest.roadmap_id,
        manifest_fingerprint=manifest.fingerprint,
        completed_runs=(("repair", 1),),
        blocked_nodes=(RoadmapBlockRecord("review", "awaiting exact human review"),),
    )


def event(name: str, *, signal_kind: str = "task") -> DurableEvent:
    number = {"a": 1101, "b": 1102, "machine": 1103, "reconcile": 1104}[name]
    return DurableEvent(
        actionable=True,
        reason=f"x6 {name} lifecycle observation",
        event="issues",
        action="edited",
        pr_number=number,
        source_id=f"x6:{name}",
        signal_kind=signal_kind,
        signal_text=f"x6-{name}",
    )


@dataclass(frozen=True)
class ModeledClaim:
    objective_id: str
    resource: str


class LifecycleMachine:
    ACTIONS = (
        "deliver_a",
        "deliver_b",
        "duplicate_a",
        "duplicate_b",
        "hold_a",
        "hold_b",
        "flush_a",
        "flush_b",
        "drop_a",
        "drop_b",
        "reconcile_missing",
        "effect_crash_after_execute",
        "effect_reconcile",
        "effect_conflict",
        "effect_replay",
        "pr_open",
        "pr_merged",
        "pr_closed",
        "worker_branch_drift",
        "worker_branch_restore",
        "worker_recover",
        "stale_worker_report",
        "machine_gate_signal",
        "human_accept",
        "human_reject",
        "human_replay",
        "claim_a",
        "claim_b",
        "claim_conflict",
        "release_a",
        "release_b",
        "release_conflict",
        "restart",
    )

    def __init__(self) -> None:
        self.manifest = lifecycle_manifest()
        self.roadmap = initial_roadmap(self.manifest)
        self.gate_reference = "PENDING"
        self.last_human_verdict: str | None = None
        self.last_review_id: str | None = None
        self.review_counter = 0

        self.ingress = HostedIngressState()
        self.events = {
            "a": event("a"),
            "b": event("b"),
            "machine": event("machine"),
            "reconcile": event("reconcile", signal_kind="restart_recommended"),
        }
        self.delivery_counter = 0
        self.last_delivery: dict[str, str] = {}
        self.model_seen_deliveries: set[str] = set()
        self.model_pending_event_ids: set[str] = set()
        self.held: set[str] = set()

        identity = RemoteEffectIdentity.create(
            attempt_id="x6-attempt-1",
            kind=EffectKind.POST_COMMENT,
            subject="x6-lifecycle-effect",
        )
        self.effect = RemoteEffectRecord.begin(identity)
        self.remote_effect_present = False
        self.effect_execute_count = 0

        self.worker = WorkerLifecycleState.from_legacy_mapping(
            {
                "branch": BRANCH,
                "stage": "handoff",
                "managed_pr": 1401,
                "worktree": "/tmp/x6-lifecycle-worker",
                "authority_key": "task:1001",
            }
        )
        self.worker_pr_state = SourcePRState.OPEN
        self.worker_merged = False
        self.worker_retired = False
        self.worker_branch_drift = False

        self.claims: dict[str, ModeledClaim] = {}
        self.conflict_blocks = 0
        self.max_parallel_claims = 0
        self.trace: list[str] = []

    def _deliver(self, name: str, *, exact_duplicate: bool = False) -> None:
        selected = self.events[name]
        if exact_duplicate and name in self.last_delivery:
            delivery_id = self.last_delivery[name]
        else:
            self.delivery_counter += 1
            delivery_id = f"x6-{name}-delivery-{self.delivery_counter}"
            self.last_delivery[name] = delivery_id

        was_seen = delivery_id in self.model_seen_deliveries
        semantic_seen = selected.event_id in self.model_pending_event_ids
        before_accepted = self.ingress.accepted_deliveries
        transition = ingest_event(
            self.ingress,
            selected,
            delivery_id=delivery_id,
        )
        self.ingress = transition.after

        if was_seen:
            assert transition.duplicate_delivery
            assert self.ingress.accepted_deliveries == before_accepted
        else:
            self.model_seen_deliveries.add(delivery_id)
            assert not transition.duplicate_delivery
            assert self.ingress.accepted_deliveries == before_accepted + 1
            if semantic_seen:
                assert transition.semantic_replay_suppressed
            self.model_pending_event_ids.add(selected.event_id)

    def _effect_observation(self) -> RemoteEffectObservation:
        if self.remote_effect_present:
            return RemoteEffectObservation(
                RemoteEffectPresence.PRESENT_EXACT,
                REMOTE_EFFECT_ID,
            )
        return RemoteEffectObservation(RemoteEffectPresence.ABSENT)

    def _effect_crash_after_execute(self) -> None:
        decision = reconcile_remote_effect(self.effect, self._effect_observation())
        if decision.disposition is EffectReconcileDisposition.EXECUTE:
            self.effect_execute_count += 1
            self.remote_effect_present = True
            # Simulate process loss after external success but before completion save.
            assert self.effect.status is EffectStatus.PENDING
        elif decision.disposition is EffectReconcileDisposition.MARK_COMPLETE:
            self.effect = self.effect.complete(decision.remote_identity)
        else:
            assert decision.disposition is EffectReconcileDisposition.NOOP_COMPLETE

    def _effect_reconcile(self) -> None:
        decision = reconcile_remote_effect(self.effect, self._effect_observation())
        if decision.disposition is EffectReconcileDisposition.EXECUTE:
            self.effect_execute_count += 1
            self.remote_effect_present = True
            self.effect = self.effect.complete(REMOTE_EFFECT_ID)
        elif decision.disposition is EffectReconcileDisposition.MARK_COMPLETE:
            self.effect = self.effect.complete(decision.remote_identity)
        else:
            assert decision.disposition is EffectReconcileDisposition.NOOP_COMPLETE

    def _effect_conflict(self) -> None:
        before = self.effect
        decision = reconcile_remote_effect(
            self.effect,
            RemoteEffectObservation(RemoteEffectPresence.PRESENT_CONFLICT),
        )
        if self.effect.status is EffectStatus.COMPLETE:
            assert decision.disposition is EffectReconcileDisposition.NOOP_COMPLETE
        else:
            assert decision.disposition is EffectReconcileDisposition.BLOCK
        assert self.effect == before

    def _worker_observation(self, *, force_stale: bool = False) -> WorkerRecoveryObservation:
        owner_present = not self.worker_retired and not force_stale
        managed_branch = BRANCH if owner_present else ""
        remote_branch = (
            "other/branch"
            if self.worker_branch_drift
            else BRANCH
        )
        return WorkerRecoveryObservation(
            controller_paused=True,
            worktree_available=True,
            managed_owner_present=owner_present,
            managed_branch=managed_branch,
            remote_pr_state=self.worker_pr_state,
            remote_pr_merged_at_present=self.worker_merged,
            remote_pr_branch=remote_branch,
        )

    def _recover_worker(self, *, force_stale: bool = False) -> None:
        decision = classify_worker_recovery(
            self.worker,
            self._worker_observation(force_stale=force_stale),
        )
        if self.worker_retired or force_stale or self.worker_branch_drift:
            assert decision.disposition is WorkerRecoveryDisposition.BLOCK
            return
        if self.worker_pr_state is SourcePRState.MERGED and self.worker_merged:
            assert decision.disposition is WorkerRecoveryDisposition.DETACH_MERGED_HANDOFF
            self.worker_retired = True
        elif self.worker_pr_state is SourcePRState.OPEN:
            assert decision.disposition is WorkerRecoveryDisposition.DETACH_OPEN_HANDOFF
            self.worker_retired = True
        else:
            assert decision.disposition is WorkerRecoveryDisposition.BLOCK

    def _human_review(self, verdict: str, *, replay: bool = False) -> None:
        if replay:
            if self.last_human_verdict is None or self.last_review_id is None:
                return
            selected = self.last_human_verdict
            review_id = self.last_review_id
        else:
            self.review_counter += 1
            selected = verdict
            review_id = f"x6-review-{self.review_counter}"

        before = self.roadmap
        result = apply_human_gate_review(
            ledger=self.roadmap,
            manifest=self.manifest,
            gate_id="review",
            verdict=selected,
            review_id=review_id,
        )
        self.roadmap = result.ledger

        if self.gate_reference == "ACCEPTED":
            expected = (
                HumanGateReviewDisposition.ALREADY_ACCEPTED
                if selected == "ACCEPTED"
                else HumanGateReviewDisposition.BLOCKED
            )
            assert result.disposition is expected
            assert self.roadmap == before
        elif selected == "ACCEPTED":
            assert result.disposition is HumanGateReviewDisposition.ACCEPTED
            self.gate_reference = "ACCEPTED"
        else:
            assert result.disposition is HumanGateReviewDisposition.CHANGES_REQUIRED
            self.gate_reference = "CHANGES_REQUIRED"

        self.last_human_verdict = selected
        self.last_review_id = review_id

    def _claim(self, objective_id: str, resource: str) -> None:
        prior = self.claims.get(resource)
        if prior is None:
            self.claims[resource] = ModeledClaim(objective_id, resource)
        elif prior.objective_id != objective_id:
            self.conflict_blocks += 1
        self.max_parallel_claims = max(self.max_parallel_claims, len(self.claims))

    def _release(self, objective_id: str, resource: str) -> None:
        prior = self.claims.get(resource)
        if prior is not None and prior.objective_id == objective_id:
            del self.claims[resource]

    def _restart_round_trip(self) -> None:
        ingress = HostedIngressState.from_mapping(self.ingress.as_dict())
        assert ingress.digest == self.ingress.digest
        self.ingress = ingress

        road = RoadmapAuthorityLedger.from_mapping(self.roadmap.as_dict())
        assert road.digest == self.roadmap.digest
        self.roadmap = road

        worker = WorkerLifecycleState.from_legacy_mapping(self.worker.as_dict())
        assert worker.digest == self.worker.digest
        self.worker = worker

        identity = RemoteEffectIdentity.create(
            attempt_id=self.effect.identity.attempt_id,
            kind=self.effect.identity.kind,
            subject=self.effect.identity.subject,
        )
        effect = RemoteEffectRecord(
            identity=identity,
            status=self.effect.status,
            remote_identity=self.effect.remote_identity,
        )
        assert effect.digest == self.effect.digest
        self.effect = effect

    def _assert_invariants(self) -> None:
        actual_pending = {value.event_id for value in self.ingress.inbox.pending_events}
        assert actual_pending == self.model_pending_event_ids
        assert set(self.ingress.seen_deliveries) == self.model_seen_deliveries
        assert self.ingress.accepted_deliveries == len(self.model_seen_deliveries)

        assert self.effect_execute_count <= 1
        if self.effect.status is EffectStatus.COMPLETE:
            assert self.remote_effect_present
            assert self.effect.remote_identity == REMOTE_EFFECT_ID

        completed = dict(self.roadmap.completed_runs)
        if self.gate_reference == "ACCEPTED":
            assert completed.get("review") == 1
            assert self.roadmap.block_for("review") is None
        else:
            assert completed.get("review", 0) == 0
            assert self.roadmap.block_for("review") is not None

        hydro = self.claims.get("hydrology")
        assert hydro is None or hydro.objective_id in {"A", "C"}
        assert len(self.claims) <= 2
        # Restart/reload at every transition is itself an X-6 invariant.
        self._restart_round_trip()

    def step(self, action: str) -> None:
        self.trace.append(action)
        if action == "deliver_a":
            self._deliver("a")
        elif action == "deliver_b":
            self._deliver("b")
        elif action == "duplicate_a":
            if "a" in self.last_delivery:
                self._deliver("a", exact_duplicate=True)
        elif action == "duplicate_b":
            if "b" in self.last_delivery:
                self._deliver("b", exact_duplicate=True)
        elif action == "hold_a":
            self.held.add("a")
        elif action == "hold_b":
            self.held.add("b")
        elif action == "flush_a":
            if "a" in self.held:
                self.held.remove("a")
                self._deliver("a")
        elif action == "flush_b":
            if "b" in self.held:
                self.held.remove("b")
                self._deliver("b")
        elif action == "drop_a":
            self.held.discard("a")
        elif action == "drop_b":
            self.held.discard("b")
        elif action == "reconcile_missing":
            self._deliver("reconcile")
        elif action == "effect_crash_after_execute":
            self._effect_crash_after_execute()
        elif action == "effect_reconcile":
            self._effect_reconcile()
        elif action == "effect_conflict":
            self._effect_conflict()
        elif action == "effect_replay":
            self._effect_reconcile()
        elif action == "pr_open":
            self.worker_pr_state = SourcePRState.OPEN
            self.worker_merged = False
        elif action == "pr_merged":
            self.worker_pr_state = SourcePRState.MERGED
            self.worker_merged = True
        elif action == "pr_closed":
            self.worker_pr_state = SourcePRState.CLOSED
            self.worker_merged = False
        elif action == "worker_branch_drift":
            self.worker_branch_drift = True
        elif action == "worker_branch_restore":
            self.worker_branch_drift = False
        elif action == "worker_recover":
            self._recover_worker()
        elif action == "stale_worker_report":
            if self.worker_retired:
                self._recover_worker(force_stale=True)
        elif action == "machine_gate_signal":
            before = self.roadmap.digest
            self._deliver("machine")
            assert self.roadmap.digest == before
        elif action == "human_accept":
            self._human_review("ACCEPTED")
        elif action == "human_reject":
            self._human_review("CHANGES_REQUIRED")
        elif action == "human_replay":
            self._human_review("ACCEPTED", replay=True)
        elif action == "claim_a":
            self._claim("A", "hydrology")
        elif action == "claim_b":
            self._claim("B", "canopy")
        elif action == "claim_conflict":
            self._claim("C", "hydrology")
        elif action == "release_a":
            self._release("A", "hydrology")
        elif action == "release_b":
            self._release("B", "canopy")
        elif action == "release_conflict":
            self._release("C", "hydrology")
        elif action == "restart":
            self._restart_round_trip()
        else:
            raise AssertionError(f"unknown action: {action}")
        self._assert_invariants()


class PlatformV2LifecycleStateMachineTest(unittest.TestCase):
    def test_seeded_adversarial_sequences_preserve_authority_invariants(self) -> None:
        # Fixed seed range makes CI reproducible while still exploring thousands of
        # cross-subsystem transition orderings. A failure prints the exact prefix.
        for seed in range(64):
            rng = random.Random(seed)
            machine = LifecycleMachine()
            for step_index in range(96):
                action = rng.choice(LifecycleMachine.ACTIONS)
                try:
                    machine.step(action)
                except Exception as exc:
                    trace = " -> ".join(machine.trace)
                    self.fail(
                        f"X-6 lifecycle failure seed={seed} step={step_index} "
                        f"action={action}; replay trace: {trace}; cause={exc!r}"
                    )

    def test_independent_claims_coexist_and_conflicting_claim_serializes(self) -> None:
        machine = LifecycleMachine()
        machine.step("claim_a")
        machine.step("claim_b")
        self.assertEqual(machine.max_parallel_claims, 2)
        before = dict(machine.claims)
        machine.step("claim_conflict")
        self.assertEqual(machine.claims, before)
        self.assertEqual(machine.conflict_blocks, 1)

    def test_crash_after_external_effect_never_reexecutes(self) -> None:
        machine = LifecycleMachine()
        machine.step("effect_crash_after_execute")
        self.assertEqual(machine.effect_execute_count, 1)
        self.assertEqual(machine.effect.status, EffectStatus.PENDING)
        machine.step("restart")
        machine.step("effect_reconcile")
        self.assertEqual(machine.effect.status, EffectStatus.COMPLETE)
        self.assertEqual(machine.effect_execute_count, 1)
        for _ in range(5):
            machine.step("effect_replay")
        self.assertEqual(machine.effect_execute_count, 1)

    def test_terminal_pr_and_stale_worker_cannot_resurrect_authority(self) -> None:
        machine = LifecycleMachine()
        machine.step("pr_merged")
        machine.step("worker_recover")
        self.assertTrue(machine.worker_retired)
        for _ in range(4):
            machine.step("stale_worker_report")
        self.assertTrue(machine.worker_retired)

        blocked = LifecycleMachine()
        blocked.step("pr_closed")
        blocked.step("worker_recover")
        self.assertFalse(blocked.worker_retired)

    def test_machine_gate_signal_cannot_self_accept_subjective_gate(self) -> None:
        machine = LifecycleMachine()
        for _ in range(4):
            machine.step("machine_gate_signal")
        self.assertEqual(machine.gate_reference, "PENDING")
        self.assertIsNotNone(machine.roadmap.block_for("review"))
        self.assertNotIn("review", dict(machine.roadmap.completed_runs))

        machine.step("human_reject")
        for _ in range(4):
            machine.step("machine_gate_signal")
        self.assertEqual(machine.gate_reference, "CHANGES_REQUIRED")
        self.assertIsNotNone(machine.roadmap.block_for("review"))

        machine.step("human_accept")
        self.assertEqual(machine.gate_reference, "ACCEPTED")
        machine.step("human_reject")
        self.assertEqual(machine.gate_reference, "ACCEPTED")

    def test_delivery_duplicates_reordering_and_missing_reconcile_converge(self) -> None:
        left = LifecycleMachine()
        left.step("deliver_a")
        left.step("deliver_b")
        left.step("duplicate_a")

        right = LifecycleMachine()
        right.step("deliver_b")
        right.step("deliver_a")
        right.step("duplicate_b")

        self.assertEqual(
            {event.event_id for event in left.ingress.inbox.pending_events},
            {event.event_id for event in right.ingress.inbox.pending_events},
        )

        missing = LifecycleMachine()
        missing.step("hold_a")
        missing.step("drop_a")
        self.assertNotIn(missing.events["a"].event_id, missing.model_pending_event_ids)
        missing.step("reconcile_missing")
        self.assertIn(
            missing.events["reconcile"].event_id,
            missing.model_pending_event_ids,
        )

    def test_unchanged_repeated_human_artifact_is_blocked_by_x4(self) -> None:
        prior = HumanReviewSubmission(
            source=DevelopmentApiHumanReviewSource(
                repo="ni-da-ba/skyforge",
                request_id="x6-review-prior-0001",
                actor="ni-da-ba",
                client="x6-test",
                submitted_at="2026-09-21T01:30:00Z",
            ),
            gate_id="review",
            artifact_id="artifact:x6",
            source_sha=SHA_A,
            verdict=HumanReviewVerdict.CHANGES_REQUIRED,
            findings=("repair required",),
            positive_findings=(),
            material_delta="initial specimen",
            next_boundary="repair specimen",
            deferred_product_work=True,
        )
        ledger = HumanReviewLedger((prior,))
        unchanged = evaluate_repeat_review_readiness(
            ledger,
            gate_id="review",
            artifact_id="artifact:x6",
            source_sha=SHA_A,
            material_delta="claims improvement but bytes are unchanged",
            prior_review_id=prior.review_id,
        )
        self.assertEqual(
            unchanged.disposition,
            RepeatReviewDisposition.BLOCKED_UNCHANGED_ARTIFACT,
        )
        changed = evaluate_repeat_review_readiness(
            ledger,
            gate_id="review",
            artifact_id="artifact:x6-v2",
            source_sha=SHA_B,
            material_delta="new recessed channel geometry",
            prior_review_id=prior.review_id,
        )
        self.assertEqual(
            changed.disposition,
            RepeatReviewDisposition.READY_NEW_MATERIAL_DELTA,
        )



    def test_out_of_band_active_completion_reconciles_once_but_managed_pr_delegates(self) -> None:
        manifest = ShadowRoadmapManifest.from_mapping(
            {
                "schema_version": 1,
                "roadmap_id": "x6-out-of-band",
                "enabled": True,
                "max_auto_claims_per_utc_day": 6,
                "nodes": [
                    {
                        "id": "task",
                        "kind": "task",
                        "lane": "Implementation",
                        "issue_number": 3001,
                        "priority": 100,
                        "max_runs": 1,
                        "prerequisites": [],
                        "objective_hint": "perform bounded task",
                        "stop_boundary": "stop after task",
                    }
                ],
            }
        )
        empty = RoadmapAuthorityLedger.empty(manifest)
        decision = evaluate_roadmap_shadow(
            manifest=manifest,
            state=empty.shadow_state(manifest),
            issue_truth={3001: RoadmapIssueTruth.OPEN},
            control=RoadmapControlObservation(),
        )
        claimed = apply_roadmap_decision(
            ledger=empty,
            manifest=manifest,
            decision=decision,
            utc_day="2026-09-21",
        ).ledger
        self.assertIsNotNone(claimed.active)

        completed = reconcile_active_issue(
            ledger=claimed,
            manifest=manifest,
            issue_state=RoadmapIssueState.CLOSED,
        )
        self.assertEqual(
            completed.disposition,
            ClosedActiveDisposition.COMPLETE_NODE,
        )
        self.assertIsNone(completed.ledger.active)
        self.assertEqual(dict(completed.ledger.completed_runs)["task"], 1)
        with self.assertRaises(ValueError):
            reconcile_active_issue(
                ledger=completed.ledger,
                manifest=manifest,
                issue_state=RoadmapIssueState.CLOSED,
            )

        bound = bind_active_pr(
            claimed,
            issue_number=3001,
            pr_number=3101,
        )
        delegated = reconcile_active_issue(
            ledger=bound,
            manifest=manifest,
            issue_state=RoadmapIssueState.CLOSED,
        )
        self.assertEqual(
            delegated.disposition,
            ClosedActiveDisposition.DELEGATE_PR,
        )
        self.assertEqual(delegated.ledger, bound)

    def test_terminal_external_claims_retire_once_while_ambiguous_truth_is_retained(self) -> None:
        ledger = ExternalClaimLedger(
            (
                ExternalProducerClaim(
                    issue_number=2001,
                    claimed_by="ni-da-ba",
                    lane="Implementation",
                    branch="external/2001",
                    pr_number=2101,
                ),
                ExternalProducerClaim(
                    issue_number=2002,
                    claimed_by="ni-da-ba",
                    lane="Implementation",
                    branch="external/2002",
                    pr_number=2102,
                ),
                ExternalProducerClaim(
                    issue_number=2003,
                    claimed_by="ni-da-ba",
                    lane="Implementation",
                    branch="external/2003",
                    pr_number=None,
                ),
            )
        )

        class Result:
            def __init__(self, value):
                self.stdout = json.dumps(value)
                self.stderr = ""

        def runner(args, **kwargs):
            command = tuple(args)
            if "2101" in command:
                return Result({"state": "OPEN", "mergedAt": None})
            if "2102" in command:
                return Result(
                    {
                        "state": "CLOSED",
                        "mergedAt": "2026-09-21T01:40:00Z",
                    }
                )
            if command[-1].endswith("/2003"):
                return Result({"state": "mystery"})
            raise AssertionError(command)

        with tempfile.TemporaryDirectory() as td:
            first = refresh_external_claims(
                ledger=ledger,
                root=Path(td),
                repo="ni-da-ba/skyforge",
                runner=runner,
            )
            self.assertEqual(first.retired_issue_numbers, (2002,))
            self.assertEqual(
                tuple(claim.issue_number for claim in first.ledger.claims),
                (2001, 2003),
            )

            replay = refresh_external_claims(
                ledger=first.ledger,
                root=Path(td),
                repo="ni-da-ba/skyforge",
                runner=runner,
            )
            self.assertEqual(replay.retired_issue_numbers, ())
            self.assertEqual(replay.ledger, first.ledger)

    def test_corrupt_durable_shapes_fail_closed_instead_of_resetting_authority(self) -> None:
        with self.assertRaises(ValueError):
            HostedIngressState.from_mapping(
                {"schema_version": 1, "inbox": "corrupt"}
            )
        manifest = lifecycle_manifest()
        raw = initial_roadmap(manifest).as_dict()
        raw["completed_runs"] = []
        with self.assertRaises(ValueError):
            RoadmapAuthorityLedger.from_mapping(raw)


if __name__ == "__main__":
    unittest.main()
