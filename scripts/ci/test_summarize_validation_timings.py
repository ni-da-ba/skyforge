import tempfile
from pathlib import Path
import unittest

from summarize_validation_timings import parse_duration, read_test_suites


class ValidationTimingSummaryTest(unittest.TestCase):
    def test_duration_parser_handles_gradle_units(self):
        self.assertAlmostEqual(parse_duration("1m2.5s"), 62.5)
        self.assertAlmostEqual(parse_duration("2.25s"), 2.25)
        self.assertAlmostEqual(parse_duration("125ms"), 0.125)
        self.assertIsNone(parse_duration("not-a-duration"))

    def test_reads_and_orders_junit_suite_times(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            results = root / "module" / "build" / "test-results" / "test"
            results.mkdir(parents=True)
            (results / "TEST-fast.xml").write_text(
                '<testsuite name="FastTest" tests="1" time="0.2"></testsuite>',
                encoding="utf-8",
            )
            (results / "TEST-slow.xml").write_text(
                '<testsuite name="SlowTest" tests="1" time="3.5"></testsuite>',
                encoding="utf-8",
            )
            self.assertEqual(read_test_suites(root), [("SlowTest", 3.5), ("FastTest", 0.2)])


if __name__ == "__main__":
    unittest.main()
