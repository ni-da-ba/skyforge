import importlib.util
import pathlib
import sys
import tempfile
import unittest
from datetime import datetime, timezone

MODULE_PATH = pathlib.Path(__file__).with_name("daily_value_report.py")
SPEC = importlib.util.spec_from_file_location("daily_value_report", MODULE_PATH)
assert SPEC and SPEC.loader
report = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = report
SPEC.loader.exec_module(report)


class MetricDeltaTests(unittest.TestCase):
    def test_metric_deltas_are_nonnegative_after_state_reset(self):
        current = {"worker_attempts": 1, "worker_handoffs": 1}
        previous = {"worker_attempts": 4, "worker_handoffs": 0}
        deltas = report.metric_deltas(current, previous)
        self.assertEqual(deltas["worker_attempts"], 0)
        self.assertEqual(deltas["worker_handoffs"], 1)


class ControllerPrTests(unittest.TestCase):
    def test_controller_pr_period_and_overnight_classification(self):
        start = datetime(2026, 9, 8, 0, 0, tzinfo=timezone.utc)
        end = datetime(2026, 9, 9, 0, 0, tzinfo=timezone.utc)
        prs = [
            {
                "number": 10,
                "title": "CODEX Audit: useful",
                "headRefName": "codex/audit-1",
                "state": "MERGED",
                "createdAt": "2026-09-08T04:30:00Z",  # 23:30 prior Central day
                "mergedAt": "2026-09-08T13:30:00Z",   # 08:30 Central
                "url": "https://example.invalid/10",
            },
            {
                "number": 11,
                "title": "manual branch",
                "headRefName": "audit/manual",
                "state": "MERGED",
                "createdAt": "2026-09-08T04:45:00Z",
                "mergedAt": "2026-09-08T05:00:00Z",
                "url": "https://example.invalid/11",
            },
        ]
        result = report._controller_prs(prs, start, end)
        self.assertEqual([item["number"] for item in result["project_created"]], [10, 11])
        self.assertEqual([item["number"] for item in result["project_merged"]], [10, 11])
        self.assertEqual([item["number"] for item in result["created"]], [10])
        self.assertEqual([item["number"] for item in result["merged"]], [10])
        self.assertEqual([item["number"] for item in result["overnight_created"]], [10])
        self.assertEqual(result["overnight_merged"], [])


class EvaluationSignalTests(unittest.TestCase):
    def test_sparse_early_sample_is_insufficient(self):
        signal, _ = report.evaluation_signal(
            elapsed_days=2,
            worker_attempts=2,
            worker_handoffs=1,
            worker_no_change=0,
            merged_prs=0,
            project_merged_prs=1,
            dispatch_failures=0,
            codex_blocks=0,
        )
        self.assertEqual(signal, "INSUFFICIENT_DATA")

    def test_repeated_merged_work_is_keep(self):
        signal, _ = report.evaluation_signal(
            elapsed_days=7,
            worker_attempts=5,
            worker_handoffs=4,
            worker_no_change=1,
            merged_prs=2,
            project_merged_prs=3,
            dispatch_failures=0,
            codex_blocks=1,
        )
        self.assertEqual(signal, "KEEP")

    def test_poor_worker_yield_is_rework(self):
        signal, _ = report.evaluation_signal(
            elapsed_days=4,
            worker_attempts=6,
            worker_handoffs=2,
            worker_no_change=4,
            merged_prs=1,
            project_merged_prs=3,
            dispatch_failures=0,
            codex_blocks=0,
        )
        self.assertEqual(signal, "REWORK")

    def test_week_without_useful_progress_is_cancel_candidate(self):
        signal, _ = report.evaluation_signal(
            elapsed_days=8,
            worker_attempts=5,
            worker_handoffs=1,
            worker_no_change=3,
            merged_prs=0,
            project_merged_prs=4,
            dispatch_failures=0,
            codex_blocks=0,
        )
        self.assertEqual(signal, "CANCEL_CANDIDATE")


class BuildReportTests(unittest.TestCase):
    def test_cost_and_daily_metric_baseline(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            state = {
                "metrics": {
                    "classifier_attempts": 5,
                    "classifier_noops": 2,
                    "worker_attempts": 2,
                    "worker_handoffs": 1,
                }
            }
            report_state = {
                "host_activated_at": "2026-09-08T00:00:00+00:00",
                "last_report_at": "2026-09-08T00:00:00+00:00",
                "last_metrics": {
                    "classifier_attempts": 2,
                    "classifier_noops": 1,
                    "worker_attempts": 1,
                    "worker_handoffs": 0,
                },
            }
            value = report.build_report(
                root=root,
                repo="ni-da-ba/skyforge",
                state=state,
                report_state=report_state,
                prs=[],
                now=datetime(2026, 9, 9, 0, 0, tzinfo=timezone.utc),
                hourly_usd=0.01,
            )
            self.assertEqual(value["metric_deltas"]["classifier_attempts"], 3)
            self.assertEqual(value["metric_deltas"]["worker_attempts"], 1)
            self.assertAlmostEqual(value["cost"]["period_estimate_usd"], 0.24, places=6)
            self.assertAlmostEqual(value["cost"]["cumulative_estimate_usd"], 0.24, places=6)


if __name__ == "__main__":
    unittest.main()
