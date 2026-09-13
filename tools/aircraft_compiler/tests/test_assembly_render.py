from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from test_assembly import specimen

from assembly import plan_assembly
from assembly_render import emit_assembly_outputs


class AssemblyRenderTests(unittest.TestCase):
    def test_outputs_are_emitted(self):
        plan = plan_assembly(specimen())
        with tempfile.TemporaryDirectory() as temp:
            out = Path(temp)
            emit_assembly_outputs(plan, out)
            self.assertTrue((out / "assembly_plan.json").is_file())
            self.assertIn("uniqueAssemblySiteCount=2", (out / "assembly_validation.txt").read_text())
            svg = (out / "assembly_mobile_review.svg").read_text()
            self.assertIn("coordinate-unique assembly sites", svg)
            self.assertIn(plan["digestSha256"], svg)


if __name__ == "__main__":
    unittest.main()
