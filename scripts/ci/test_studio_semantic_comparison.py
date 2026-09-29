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
            "hydrology comparison contains duplicate field samples",
            "hydrology comparison field coordinates do not match",
            "hydrology comparison produced a non-finite ",
            "HYDROLOGY_FIELD_COMPARISON_SAMPLE",
            "fieldSamples: Object.freeze(fieldSamples)",
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
        self.assertIn('id="local-comparison-file"', markup)
        self.assertIn('id="load-local-comparison"', markup)
        self.assertIn('id="show-hydrology-delta"', markup)
        self.assertIn('id="comparison-mode"', markup)
        self.assertIn('value="surface"', markup)
        self.assertIn('value="water-surface"', markup)
        self.assertIn('value="water-depth"', markup)
        self.assertIn('id="comparison-controls" class="comparison-controls" hidden', markup)
        self.assertIn('id="legend-delta-label"', markup)
        self.assertIn('id="comparison-value-inspector"', markup)
        self.assertIn('id="comparison-reference-value"', markup)
        self.assertIn('id="comparison-candidate-value"', markup)
        self.assertIn('id="comparison-delta-value"', markup)
        self.assertIn('id="preview-bundled-comparison"', markup)
        self.assertIn("included S2 specimen · same data", app)
        self.assertIn("Walkthrough only: this specimen is compared with itself", app)
        self.assertIn("comparison.samples[0]", app)
        self.assertIn("function renderSelectedComparison(sample)", app)
        self.assertIn("sample.reference[field]", app)
        self.assertIn("sample.candidate[field]", app)
        self.assertIn("sample.surfaceDeltaY", app)
        self.assertIn("sample.waterSurfaceDeltaY", app)
        self.assertIn("sample.waterDepthDelta", app)
        self.assertIn('"not sampled"', app)
        self.assertIn("panel.hidden = true", app)
        self.assertIn("fieldComparisonDelta(sample, comparisonMode)", app)
        self.assertIn("HYDROLOGY_FIELD_COMPARISON_SAMPLE", app)
        self.assertIn("Target surface elevation", markup)
        self.assertIn("Water surface elevation", markup)
        self.assertIn("Water depth", markup)
        self.assertIn('binding: "UNBOUND_LOCAL"', app)
        self.assertIn('reviewAuthority: false,', app)
        self.assertIn('UNBOUND LOCAL DIAGNOSTIC — not review authority', app)
        self.assertIn('hydrologyComparison.comparison.reviewAuthority', app)
        self.assertIn('UNBOUND LOCAL DIAGNOSTIC comparison — not review authority', app)
        self.assertIn("Candidate − reference: purple − / gray 0 / orange +", markup)
        self.assertIn("channel, flow, response, and water layers show the reference", markup)
        self.assertIn("linear-gradient(90deg,#705fba 0%,#c3c5ca 50%,#d25343 100%)", styles)
        self.assertIn("UNBOUND LOCAL DIAGNOSTIC", app)

    def test_studio_renderer_surface_is_backend_neutral(self):
        scene = (STUDIO / "scene.js").read_text(encoding="utf-8")
        markup = (STUDIO / "index.html").read_text(encoding="utf-8")
        design = (ROOT / "docs" / "architecture" / "SKYFORGE_STUDIO_S0.md").read_text(
            encoding="utf-8"
        )

        for surface_name, surface in (
            ("scene projection", scene),
            ("Studio interface", markup),
            ("Studio architecture", design),
        ):
            for term in ("minecraft", "a4mc", "aerodynamics4mc", "voxel", "world blocks"):
                with self.subTest(surface=surface_name, term=term):
                    self.assertNotIn(term, surface.lower())

        for contract in (
            'id: "WORLD_XYZ"',
            'x: "+X world units"',
            'z: "+Z world units"',
            'units: "world units"',
            'label: "External atmosphere provider"',
            'semanticOwner: "External atmosphere provider"',
        ):
            with self.subTest(contract=contract):
                self.assertIn(contract, scene)
        self.assertNotIn("modId", scene)

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


    def test_world_brief_workspace_is_local_and_backend_neutral(self):
        app = (STUDIO / "app.js").read_text(encoding="utf-8")
        markup = (STUDIO / "index.html").read_text(encoding="utf-8")
        styles = (STUDIO / "styles.css").read_text(encoding="utf-8")

        for contract in (
            "SKYFORGE_STUDIO_WORLD_BRIEF",
            "SKYFORGE_STUDIO_BRIEF_LIBRARY",
            "window.localStorage",
            "skyforge-studio-world-brief-library-v1",
            "function exportBrief()",
            "function parseWorldBrief(value)",
        ):
            with self.subTest(contract=contract):
                self.assertIn(contract, app)
        self.assertIn("This Studio preview has no generator connected", markup)

        for control in (
            'aria-label="Studio workspace"',
            'data-workspace-view="brief"',
            'data-workspace-view="inspect"',
            'id="world-brief-library"',
            'id="world-brief-title"',
            'id="world-brief-intent"',
            'id="world-brief-form"',
            'id="world-brief-import-file"',
            'id="world-brief-download"',
            'aria-live="polite"',
            'id="generation-disabled-reason"',
        ):
            with self.subTest(control=control):
                self.assertIn(control, markup)
        self.assertRegex(markup, r'<button[^>]*disabled[^>]*aria-describedby="generation-disabled-reason"')
        self.assertIn("@media(max-width:850px)", styles)
        self.assertIn("@media(max-width:600px)", styles)
        self.assertIn(":focus-visible", styles)
        self.assertIn(".world-brief-view[hidden]{display:none}", styles)

        node = shutil.which("node")
        self.assertIsNotNone(node, "GitHub Actions runner must provide Node.js for Studio tests")
        script = ROOT / "scripts" / "ci" / "test_studio_world_brief.js"
        result = subprocess.run(
            [node, str(script)],
            check=False,
            capture_output=True,
            text=True,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("PASS Studio local world brief document behavior", result.stdout)

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
