from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from compiler import compile_texture
from detail_math import optimal_discrete_budget
from render import emit_outputs
from targets.minecraft_skin import problem_from_spec

SPEC = ROOT / "specimens" / "guild_clerk_skin_v0.2.json"


class TextureDetailV02Tests(unittest.TestCase):
    def _spec(self):
        return json.loads(SPEC.read_text(encoding="utf-8"))

    def _compile(self):
        return compile_texture(problem_from_spec(self._spec()))

    def test_v02_is_deterministic_valid_and_topology_safe(self):
        a = self._compile()
        b = self._compile()
        self.assertEqual(a.summary["digestSha256"], b.summary["digestSha256"])
        self.assertTrue(a.summary["validation"]["passed"], a.summary["validation"]["issues"])
        self.assertEqual(a.summary["compilerVersion"], "texture-compiler-0.2")
        self.assertEqual(a.summary["optimizer"]["seamMismatches"], 0)
        self.assertGreater(a.summary["optimizer"]["hardSeamPairsCollapsed"], 900)

    def test_rate_distortion_budget_is_exact_and_semantic(self):
        c = self._compile().summary
        allocation = c["paletteAllocation"]
        self.assertEqual(allocation["mode"], "semantic_rate_distortion_dynamic_programming")
        self.assertEqual(set(allocation["allocation"]), {"face_hair", "garment_base", "lower_body", "overlay_detail"})
        reserved = len(allocation["reservedColors"])
        self.assertLessEqual(sum(allocation["allocation"].values()) + reserved, allocation["totalBudget"])
        self.assertLessEqual(c["paletteSize"], allocation["totalBudget"])
        a, cost = optimal_discrete_budget({"a": {1: 10.0, 2: 2.0}, "b": {1: 5.0, 2: 4.0}}, 3)
        self.assertEqual(a, {"a": 2, "b": 1})
        self.assertEqual(cost, 7.0)

    def test_multiscale_and_overlay_add_detail_without_losing_face(self):
        c = self._compile().summary
        self.assertGreater(c["multiscaleDetailWeight"], 0.0)
        self.assertTrue(c["metadata"]["overlayEnabled"])
        self.assertIn("laplacian_pyramid_multiscale_saliency", c["metadata"]["mathematicalAuthorities"])
        self.assertIn("rate_distortion_resource_allocation", c["metadata"]["mathematicalAuthorities"])
        self.assertLess(c["regionMetrics"]["head.front"]["meanWeightedPerceptualError"], 2.5)
        self.assertGreaterEqual(c["regionMetrics"]["torso.front"]["uniqueColors"], 5)
        self.assertGreater(c["regionMetrics"]["torso_overlay.front"]["uniqueColors"], 2)

    def test_second_layer_has_transparency_and_authored_pixels(self):
        compiled = self._compile()
        rows = compiled.region_pixels["torso_overlay.front"]
        alphas = {c[3] for row in rows for c in row}
        self.assertIn(0, alphas)
        self.assertIn(255, alphas)
        self.assertIn((0, 0, 0, 0), {tuple(c) for c in compiled.summary["palette"]})

    def test_v02_emits_overlay_and_pose_qa(self):
        compiled = self._compile()
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp)
            emit_outputs(compiled, out)
            for name in (
                "texture.png",
                "model_front.png",
                "model_front_base.png",
                "model_back.png",
                "model_side.png",
                "model_arms_raised.png",
                "head_closeup.png",
                "resolved.json",
                "validation.txt",
            ):
                self.assertTrue((out / name).exists(), name)


if __name__ == "__main__":
    unittest.main()
