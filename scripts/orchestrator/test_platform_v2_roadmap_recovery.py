from __future__ import annotations

import unittest

from v2.roadmap_recovery import (
    ClosedActiveDisposition,
    ClosedActiveRoadmapObservation,
    RoadmapIssueState,
    classify_closed_active_roadmap,
)


class ClosedActiveRoadmapParityTest(unittest.TestCase):
    # Mirrors test_closed_active_issue_without_pr_completes_full_node.
    def test_closed_issue_without_pr_completes_full_node(self) -> None:
        observation = ClosedActiveRoadmapObservation(
            node_id="phase1-closeout",
            issue_number=284,
            max_runs=2,
            issue_state=RoadmapIssueState.CLOSED,
            pr_number=None,
        )
        decision = classify_closed_active_roadmap(observation)
        self.assertEqual(
            decision.disposition,
            ClosedActiveDisposition.COMPLETE_NODE,
        )
        self.assertTrue(decision.clear_active)
        self.assertEqual(decision.completed_runs_target, 2)
        self.assertEqual(decision.closed_issue_number, 284)

    # Mirrors test_open_active_issue_delegates_to_existing_resolution.
    def test_open_issue_delegates_to_roadmap(self) -> None:
        observation = ClosedActiveRoadmapObservation(
            node_id="phase1-closeout",
            issue_number=284,
            max_runs=2,
            issue_state=RoadmapIssueState.OPEN,
        )
        decision = classify_closed_active_roadmap(observation)
        self.assertEqual(
            decision.disposition,
            ClosedActiveDisposition.DELEGATE_ROADMAP,
        )
        self.assertFalse(decision.clear_active)

    # Mirrors test_bound_pr_always_delegates_to_existing_pr_lifecycle.
    def test_bound_pr_always_delegates_to_pr_lifecycle(self) -> None:
        for issue_state in (
            RoadmapIssueState.OPEN,
            RoadmapIssueState.CLOSED,
            RoadmapIssueState.UNKNOWN,
        ):
            with self.subTest(issue_state=issue_state):
                observation = ClosedActiveRoadmapObservation(
                    node_id="phase1-closeout",
                    issue_number=284,
                    max_runs=2,
                    issue_state=issue_state,
                    pr_number=490,
                )
                decision = classify_closed_active_roadmap(observation)
                self.assertEqual(
                    decision.disposition,
                    ClosedActiveDisposition.DELEGATE_PR,
                )
                self.assertFalse(decision.clear_active)

    def test_unknown_issue_state_fails_closed(self) -> None:
        observation = ClosedActiveRoadmapObservation(
            node_id="phase1-closeout",
            issue_number=284,
            max_runs=2,
            issue_state=RoadmapIssueState.UNKNOWN,
        )
        decision = classify_closed_active_roadmap(observation)
        self.assertEqual(decision.disposition, ClosedActiveDisposition.BLOCK)

    def test_decision_digest_is_deterministic(self) -> None:
        observation = ClosedActiveRoadmapObservation(
            node_id="phase1-closeout",
            issue_number=284,
            max_runs=2,
            issue_state=RoadmapIssueState.CLOSED,
        )
        first = classify_closed_active_roadmap(observation)
        second = classify_closed_active_roadmap(observation)
        self.assertEqual(first, second)
        self.assertEqual(first.digest, second.digest)

    def test_malformed_observation_fails_closed(self) -> None:
        with self.assertRaises(ValueError):
            ClosedActiveRoadmapObservation(
                node_id="",
                issue_number=284,
                max_runs=2,
                issue_state=RoadmapIssueState.CLOSED,
            )
        with self.assertRaises(ValueError):
            ClosedActiveRoadmapObservation(
                node_id="phase",
                issue_number=0,
                max_runs=2,
                issue_state=RoadmapIssueState.CLOSED,
            )
        with self.assertRaises(ValueError):
            ClosedActiveRoadmapObservation(
                node_id="phase",
                issue_number=284,
                max_runs=0,
                issue_state=RoadmapIssueState.CLOSED,
            )


if __name__ == "__main__":
    unittest.main()
