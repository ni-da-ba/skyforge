import tempfile
import unittest
from pathlib import Path

from v2.authority_retirement import (
    V2AuthorityRetirementPhase,
    V2AuthorityRetirementStore,
    inspect_v2_authority_retirement,
    retire_v2_authority,
)
from v2.events import DurableEvent
from v2.hosted_admission import (
    HostedAdmissionLedger,
    HostedAdmissionOutcome,
    HostedAdmissionRecord,
    HostedAdmissionStore,
)
from v2.hosted_state import HostedIngressState, HostedStateStore
from v2.hosted_task_plan import (
    HostedTaskDispatchPlan,
    HostedTaskPlanLedger,
    HostedTaskPlanStatus,
    HostedTaskPlanStore,
)
from v2.hosted_worker_scheduler import (
    HostedWorkerScheduleRecord,
    HostedWorkerScheduleState,
    HostedWorkerSchedulerLedger,
    HostedWorkerSchedulerStore,
)
from v2.inbox import InboxState
from v2.task_authority import TaskAuthorityWakeReference
from v2.task_event_composition import (
    TaskAuthorityEventRecord,
    TaskAuthorityEventStore,
)


ISSUE = 1001
SOURCE_ID = "5764569724"
MAIN = "a" * 40


def prepare_terminal_authority(root: Path):
    event = DurableEvent(
        actionable=True,
        reason="trusted actor issued explicit Audit task",
        event="issue_comment",
        action="audit_signal",
        pr_number=ISSUE,
        source_id=SOURCE_ID,
        signal_kind="task",
        signal_text="[SKYFORGE TASK AUTHORITY]\n{}",
        observed_at="2026-09-21T17:00:00Z",
    )
    reference = TaskAuthorityWakeReference(
        repo="ni-da-ba/skyforge",
        issue_number=ISSUE,
        comment_id=int(SOURCE_ID),
        actor="ni-da-ba",
        body=event.signal_text,
        created_at=event.observed_at,
        updated_at=event.observed_at,
    )
    authority = TaskAuthorityEventRecord(
        event_id=event.event_id,
        reference=reference,
        delivery_id="delivery-1",
    )
    TaskAuthorityEventStore.for_root(root).capture(authority)

    HostedStateStore.for_root(root).save(
        HostedIngressState(
            inbox=InboxState(
                pending_events=(event,),
                owned_event_keys=(event.event_id,),
            )
        )
    )

    plan = HostedTaskDispatchPlan(
        event_id=event.event_id,
        issue_number=ISSUE,
        authority_record_digest=authority.digest,
        status=HostedTaskPlanStatus.CLAIMED,
        reason="claimed for hosted execution",
    )
    HostedTaskPlanStore.for_root(root).save(HostedTaskPlanLedger((plan,)))

    admission = HostedAdmissionRecord(
        plan_id=plan.plan_id,
        event_id=event.event_id,
        issue_number=ISSUE,
        classifier_request_id="b" * 64,
        classifier_run_id="c" * 64,
        classifier_decision_digest="d" * 64,
        hydration_digest="e" * 64,
        authority_digest="f" * 64,
        current_main=MAIN,
        attempt_number=1,
        outcome=HostedAdmissionOutcome.BLOCKED,
        reason="classifier objective differs from repository task authority",
    )
    HostedAdmissionStore.for_root(root).save(HostedAdmissionLedger((admission,)))
    return event, authority, plan, admission


class AuthorityRetirementTest(unittest.TestCase):
    def test_terminal_nonexecuted_authority_retires_without_completion(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            event, authority, plan, admission = prepare_terminal_authority(root)

            inspection = inspect_v2_authority_retirement(
                root=root,
                event_id=event.event_id,
                issue_number=ISSUE,
                source_id=SOURCE_ID,
            )
            self.assertTrue(inspection.ready)
            self.assertFalse(inspection.already_complete)
            self.assertEqual(inspection.record.phase, V2AuthorityRetirementPhase.PREPARED)

            result = retire_v2_authority(
                root=root,
                event_id=event.event_id,
                issue_number=ISSUE,
                source_id=SOURCE_ID,
            )
            self.assertEqual(result.phase, V2AuthorityRetirementPhase.COMPLETE)

            state = HostedStateStore.for_root(root).load()
            self.assertNotIn(event.event_id, [value.event_id for value in state.inbox.pending_events])
            self.assertIn(event.event_id, state.inbox.retired_event_keys)
            self.assertNotIn(event.event_id, state.inbox.completed_authority_event_keys)
            self.assertNotIn(event.event_id, state.inbox.owned_event_keys)
            self.assertIsNone(HostedTaskPlanStore.for_root(root).load().get(plan.plan_id))
            self.assertIsNone(HostedAdmissionStore.for_root(root).load().get(admission.record_id))

            # Forensic provenance remains durable and unchanged.
            self.assertEqual(
                TaskAuthorityEventStore.for_root(root).load().get(event.event_id),
                authority,
            )
            evidence = V2AuthorityRetirementStore.for_root(root).load().get(event.event_id)
            self.assertIsNotNone(evidence)
            self.assertEqual(evidence.phase, V2AuthorityRetirementPhase.COMPLETE)
            self.assertIs(evidence.as_dict()["completed"], False)
            self.assertIs(evidence.as_dict()["executed"], False)

            # Replay is idempotent.
            replay = retire_v2_authority(
                root=root,
                event_id=event.event_id,
                issue_number=ISSUE,
                source_id=SOURCE_ID,
            )
            self.assertEqual(replay, result)

    def test_retirement_blocks_when_scheduler_proves_worker_identity(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            event, _authority, _plan, admission = prepare_terminal_authority(root)
            HostedWorkerSchedulerStore.for_root(root).save(
                HostedWorkerSchedulerLedger(
                    (
                        HostedWorkerScheduleRecord(
                            attempt_id="1" * 64,
                            admission_record_id=admission.record_id,
                            state=HostedWorkerScheduleState.RECOVERY_REQUIRED,
                            reason="injected downstream evidence",
                        ),
                    )
                )
            )

            inspection = inspect_v2_authority_retirement(
                root=root,
                event_id=event.event_id,
                issue_number=ISSUE,
                source_id=SOURCE_ID,
            )
            self.assertFalse(inspection.ready)
            self.assertTrue(
                any("worker scheduler record exists" in item for item in inspection.blockers)
            )

    def test_retirement_blocks_identity_mismatch(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            event, _authority, _plan, _admission = prepare_terminal_authority(root)
            inspection = inspect_v2_authority_retirement(
                root=root,
                event_id=event.event_id,
                issue_number=ISSUE + 1,
                source_id=SOURCE_ID,
            )
            self.assertFalse(inspection.ready)
            self.assertTrue(any("issue differs" in item for item in inspection.blockers))


if __name__ == "__main__":
    unittest.main()
