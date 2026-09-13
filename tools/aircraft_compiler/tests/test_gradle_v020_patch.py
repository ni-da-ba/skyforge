from __future__ import annotations

import unittest
from pathlib import Path

from gradle_v020_patch import patch_gradle_source


class GradleV020PatchTests(unittest.TestCase):
    def test_real_build_script_receives_exact_stack_prepare_and_client_runs(self) -> None:
        repo_root = Path(__file__).resolve().parents[3]
        source = (repo_root / "skyforge-neoforge-1211/build.gradle.kts").read_text(encoding="utf-8")
        patched = patch_gradle_source(source)
        self.assertIn('create("aircraftCompilerPilotWorldPrepareServer")', patched)
        self.assertIn('create("aircraftCompilerPilotClient")', patched)
        self.assertIn("sourceSet.set(waveC11Runtime)", patched)
        self.assertIn('programArgument("--quickPlaySingleplayer")', patched)
        self.assertIn('programArgument("aircraft-compiler-pilot-client")', patched)
        self.assertIn('systemProperty("skyforge.dev.aircraftCompilerPilotClient", "true")', patched)
        self.assertIn('systemProperty("skyforge.dev.aircraftCompilerPilotWorldPrepare", "true")', patched)
        self.assertLess(
            patched.index('create("aircraftCompilerPilotClient")'),
            patched.index("unitTest {"),
        )


if __name__ == "__main__":
    unittest.main()
