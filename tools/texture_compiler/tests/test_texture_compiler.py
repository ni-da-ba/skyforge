from __future__ import annotations

import json, struct, sys, tempfile, unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path: sys.path.insert(0,str(ROOT))
from compiler import compile_texture
from render import emit_outputs
from targets.explicit_atlas import problem_from_spec as explicit_problem
from targets.minecraft_skin import problem_from_spec as skin_problem
SKIN=ROOT/"specimens"/"guild_clerk_skin_v0.1.json"; GENERIC=ROOT/"specimens"/"generic_patch_v0.1.json"

class TextureCompilerTests(unittest.TestCase):
    def test_skin_compile_is_deterministic_and_valid(self):
        spec=json.loads(SKIN.read_text(encoding="utf-8")); a=compile_texture(skin_problem(spec)); b=compile_texture(skin_problem(spec)); self.assertTrue(a.summary["validation"]["passed"],a.summary["validation"]["issues"]); self.assertEqual(a.summary["digestSha256"],b.summary["digestSha256"]); self.assertEqual(a.summary["atlas"],{"width":64,"height":64}); self.assertLessEqual(a.summary["paletteSize"],spec["optimization"]["maxColors"]); self.assertEqual(a.summary["optimizer"]["seamMismatches"],0); self.assertGreater(a.summary["optimizer"]["hardSeamPairsCollapsed"],100)
    def test_compiler_core_is_not_skin_specific(self):
        spec=json.loads(GENERIC.read_text(encoding="utf-8")); compiled=compile_texture(explicit_problem(spec)); self.assertTrue(compiled.summary["validation"]["passed"]); self.assertEqual(compiled.summary["metadata"]["targetKind"],"explicit_atlas"); self.assertEqual(compiled.summary["atlas"],{"width":8,"height":4}); self.assertEqual(compiled.summary["optimizer"]["seamMismatches"],0)
    def test_skin_outputs_png_and_game_oriented_qa(self):
        spec=json.loads(SKIN.read_text(encoding="utf-8")); compiled=compile_texture(skin_problem(spec))
        with tempfile.TemporaryDirectory() as tmp:
            out=Path(tmp); emit_outputs(compiled,out)
            for name in ("texture.png","atlas.svg","model_front.svg","model_back.svg","model_side.svg","head_closeup.svg","model_front.png","model_back.png","model_side.png","head_closeup.png","resolved.json","validation.txt"): self.assertTrue((out/name).exists(),name)
            data=(out/"texture.png").read_bytes(); self.assertEqual(data[:8],b"\x89PNG\r\n\x1a\n"); width,height=struct.unpack(">II",data[16:24]); self.assertEqual((width,height),(64,64))
    def test_face_information_receives_higher_source_weight(self):
        spec=json.loads(SKIN.read_text(encoding="utf-8")); face=skin_problem(spec).sources["head.front"]; self.assertGreater(max(face.importance),4.0); self.assertGreater(max(face.importance),min(face.importance))

if __name__ == "__main__": unittest.main()
