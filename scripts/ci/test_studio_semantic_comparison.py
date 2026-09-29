from __future__ import annotations

from pathlib import Path
import shutil
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[2]
STUDIO = ROOT / "scripts" / "orchestrator" / "studio"


class StudioHydrologyComparisonContractTest(unittest.TestCase):
    def test_scene_adapter_requires_exact_comparison_binding(self):
        source = (STUDIO / "scene.js").read_text(encoding="utf-8")

        for contract in (
            "function compareHydrologyLayers(reference, candidate)",
            "hydrology comparison requires the same exact terrain semantic SHA",
            "hydrology comparison requires the same AUTH-0046 association",
            "hydrology comparison world frames do not match",
            "hydrology comparison cause-grid bindings do not match",
            "candidate hydrology comparison contains duplicate cause samples",
            "reference hydrology comparison contains duplicate cause samples",
            "hydrology comparison cause sample counts do not match",
            "candidate hydrology comparison is missing cause sample",
            "hydrology comparison cause coordinates do not match",
            "reference.source.reviewAuthority && candidate.source.reviewAuthority",
            "compareHydrologyLayers,",
        ):
            with self.subTest(contract=contract):
                self.assertIn(contract, source)

    def test_studio_labels_delta_direction_and_keeps_reference_layers(self):
        app = (STUDIO / "app.js").read_text(encoding="utf-8")
        markup = (STUDIO / "index.html").read_text(encoding="utf-8")
        styles = (STUDIO / "styles.css").read_text(encoding="utf-8")

        source = (STUDIO / "scene.js").read_text(encoding="utf-8")
        self.assertIn("candidateSample[field] - referenceSample[field]", source)
        self.assertIn("hydrologyDeltaColor(sample.deltas[potential])", app)
        self.assertIn("delta < 0 ? [112, 95, 186] : [210, 83, 67]", app)
        self.assertIn('id="comparison-artifact-select"', markup)
        self.assertIn('id="show-hydrology-delta"', markup)
        self.assertIn("Candidate − reference: purple − / gray 0 / orange +", markup)
        self.assertIn("channel, flow, response, and water layers show the reference", markup)
        self.assertIn("linear-gradient(90deg,#705fba 0%,#c3c5ca 50%,#d25343 100%)", styles)
        self.assertIn("UNBOUND LOCAL DIAGNOSTIC", app)

    def test_hydrology_comparison_behavior_in_ci_runtime(self):
        node = shutil.which("node")
        self.assertIsNotNone(node, "GitHub Actions runner must provide Node.js for Studio tests")
        script = ROOT / "scripts" / "ci" / "test_studio_hydrology_comparison.js"
        result = subprocess.run(
            [node, str(script)],
            check=False,
            capture_output=True,
            text=True,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("PASS Studio hydrology candidate comparison behavior", result.stdout)

    def test_studio_javascript_parses_in_ci_runtime(self):
        node = shutil.which("node")
        self.assertIsNotNone(node, "GitHub Actions runner must provide Node.js for syntax checks")
        for path in (STUDIO / "app.js", STUDIO / "scene.js"):
            result = subprocess.run(
                [node, "--check", str(path)],
                check=False,
                capture_output=True,
                text=True,
            )
            with self.subTest(path=path.name):
                self.assertEqual(result.returncode, 0, result.stderr)


if __name__ == "__main__":
    unittest.main()
