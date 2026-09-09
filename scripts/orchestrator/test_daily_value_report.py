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


class HostResourceTests(unittest.TestCase):
    def test_parse_meminfo_computes_available_memory_usage(self):
        values = report._parse_meminfo(
            "MemTotal:        2048000 kB\n"
            "MemAvailable:    1024000 kB\n"
        )
        self.assertEqual(values["memory_total_bytes"], 2048000 * 1024)
        self.assertEqual(values["memory_available_bytes"], 1024000 * 1024)
        self.assertAlmostEqual(values["memory_used_pct"], 50.0)

    def test_collect_host_resources_returns_disk_and_cpu_shape(self):
        with tempfile.TemporaryDirectory() as tmp:
            values = report.collect_host_resources(pathlib.Path(tmp))
            self.assertGreaterEqual(values["cpu_count"], 1)
            self.assertIn("load_1m_per_cpu", values)
            self.assertGreater(values["disk_total_bytes"], 0)
            self.assertGreaterEqual(values["disk_used_pct"], 0.0)


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
                "pending_events": [{"event": "push"}],
                "last_pending_event_compaction": {"before": 12, "after": 10},
                "metrics": {
                    "classifier_attempts": 5,
                    "classifier_noops": 2,
                    "worker_attempts": 2,
                    "worker_handoffs": 1,
                    "pending_event_high_water": 12,
                    "pending_event_protected_overflow": 1,
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
            self.assertIn("host_resources", value)
            self.assertIn("disk_used_pct", value["host_resources"])
            self.assertEqual(value["queue_pressure"]["pending_events"], 1)
            self.assertEqual(value["queue_pressure"]["high_water"], 12)
            self.assertEqual(value["queue_pressure"]["protected_overflow"], 1)
            self.assertEqual(
                value["queue_pressure"]["last_compaction"],
                {"before": 12, "after": 10},
            )


class MarkdownRenderTests(unittest.TestCase):
    def test_capacity_and_dispatch_failures_render_as_separate_rows(self):
        value = {
            "metric_deltas": {
                "codex_blocks": 2,
                "dispatch_failures": 3,
            },
            "controller_prs": {
                "project_created": [],
                "project_merged": [],
                "created": [],
                "merged": [],
                "open_now": [],
                "overnight_created": [],
                "overnight_merged": [],
            },
            "trailing_window": {
                "report_count": 1,
                "estimated_cost_usd": 0.01,
                "worker_attempts": 0,
                "worker_handoffs": 0,
                "worker_no_change": 0,
                "project_prs_created": 0,
                "project_prs_merged": 0,
                "controller_prs_created": 0,
                "controller_prs_merged": 0,
                "manual_wakes": 0,
                "audit_wakes": 0,
                "dispatch_latency_samples": 0,
                "dispatch_latency_ms_total": 0,
                "codex_blocks": 2,
                "dispatch_failures": 3,
            },
            "cost": {
                "period_estimate_usd": 0.01,
                "cumulative_estimate_usd": 0.01,
            },
            "evaluation": {"signal": "INSUFFICIENT_DATA", "reason": "test"},
            "report_date_central": "2026-09-08",
            "period_start": "2026-09-08T00:00:00+00:00",
            "period_end": "2026-09-09T00:00:00+00:00",
            "host_resources": {},
            "queue_pressure": {
                "pending_events": 7,
                "high_water": 12,
                "protected_overflow": 1,
                "last_compaction": {"before": 12, "after": 10},
            },
        }
        rendered = report._render_markdown(value)
        self.assertIn("| Codex capacity/auth blocks | 2 |\n", rendered)
        self.assertIn("| Dispatch failures | 3 |\n", rendered)
        self.assertIn("| Duplicate human gates suppressed | 0 |\n", rendered)
        self.assertIn("| Runtime refresh requests / completions | 0 / 0 |\n", rendered)
        self.assertIn("| Startup reconciliation failures | 0 |\n", rendered)
        self.assertIn("| State-backup recoveries | 0 |\n", rendered)
        self.assertIn("| Stale managed PR records retired | 0 |\n", rendered)
        self.assertIn("| Pending-event compactions | 0 |\n", rendered)
        self.assertIn("| Pending events compacted | 0 |\n", rendered)
        self.assertIn("Current pending events: **7**", rendered)
        self.assertIn("Queue high-water mark: **12**", rendered)
        self.assertIn("Protected-authority overflow above soft cap: **1**", rendered)
        self.assertNotIn("| 2 || Dispatch failures", rendered)


if __name__ == "__main__":
    unittest.main()
