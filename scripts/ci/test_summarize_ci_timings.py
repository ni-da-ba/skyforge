import tempfile
import unittest
from pathlib import Path

from summarize_ci_timings import _gradle_tasks, _parse_duration, _task_category_totals


class SummarizeCiTimingsTest(unittest.TestCase):
    def test_duration_parser(self):
        self.assertEqual(1.25, _parse_duration("1.25s"))
        self.assertEqual(0.25, _parse_duration("250ms"))
        self.assertEqual(62.5, _parse_duration("1m 2.5s"))
        self.assertIsNone(_parse_duration("n/a"))

    def test_gradle_profile_rows_and_categories(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            profile = root / "build/reports/profile/profile-test.html"
            profile.parent.mkdir(parents=True)
            profile.write_text(
                """
                <html><body><table>
                  <tr><th>Task</th><th>Duration</th></tr>
                  <tr><td>:skyforge-neoforge-1211:compileJava</td><td class="numeric">2.5s</td></tr>
                  <tr><td>:skyforge-neoforge-1211:test</td><td class="numeric">4.0s</td></tr>
                  <tr><td>:skyforge-reference:fixedSeedCorpus</td><td class="numeric">1m 1.0s</td></tr>
                </table></body></html>
                """,
                encoding="utf-8",
            )
            tasks = _gradle_tasks(root)
            self.assertEqual(
                [
                    (61.0, ":skyforge-reference:fixedSeedCorpus"),
                    (4.0, ":skyforge-neoforge-1211:test"),
                    (2.5, ":skyforge-neoforge-1211:compileJava"),
                ],
                tasks,
            )
            totals = _task_category_totals(tasks)
            self.assertEqual(2.5, totals["compilation"])
            self.assertEqual(4.0, totals["tests"])
            self.assertEqual(61.0, totals["evidence"])


if __name__ == "__main__":
    unittest.main()