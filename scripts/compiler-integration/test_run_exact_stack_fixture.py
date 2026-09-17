#!/usr/bin/env python3
from __future__ import annotations

import os
import shutil
import subprocess
import tempfile
import textwrap
import time
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RUNNER = ROOT / "scripts/compiler-integration/run-exact-stack-fixture.sh"
START = "TEST_FIXTURE START"
PASS = "TEST_FIXTURE PASS"
FAIL = "TEST_FIXTURE FAIL"

class ExactStackFixtureRunnerTest(unittest.TestCase):
    def run_case(self, body: str, *, grace: int = 1):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            shutil.copy2(RUNNER, root / "runner.sh")
            gradlew = root / "gradlew"
            gradlew.write_text("#!/usr/bin/env bash\nset -euo pipefail\n" + textwrap.dedent(body))
            gradlew.chmod(0o755)
            env = os.environ.copy()
            env["SKYFORGE_EXACT_STACK_POST_PASS_GRACE_SECONDS"] = str(grace)
            started = time.monotonic()
            result = subprocess.run(
                ["bash", "runner.sh", ":fake", "fixture.log", START, PASS, FAIL, "3", "3"],
                cwd=root, env=env, text=True, capture_output=True, timeout=10,
            )
            return result, time.monotonic() - started

    def test_pass_observes_grace_then_tears_down_live_fixture(self):
        result, elapsed = self.run_case(f'echo "{START}"\necho "{PASS}"\nsleep 30\n')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertGreaterEqual(elapsed, 1.0)
        self.assertLess(elapsed, 8.0)
        self.assertIn(PASS, result.stdout)

    def test_fail_after_pass_is_not_masked(self):
        result, _ = self.run_case(f'echo "{START}"\necho "{PASS}"\nsleep 0.2\necho "{FAIL}"\nexit 1\n')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn(FAIL, result.stderr)

    def test_nonzero_exit_after_pass_is_not_masked(self):
        result, _ = self.run_case(f'echo "{START}"\necho "{PASS}"\nsleep 0.2\nexit 7\n')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("FAIL_RUNTIME_EXIT", result.stderr)

    def test_clean_exit_without_pass_fails(self):
        result, _ = self.run_case(f'echo "{START}"\nsleep 0.2\nexit 0\n')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("FAIL_RUNTIME_EXIT", result.stderr)

if __name__ == "__main__":
    unittest.main()
