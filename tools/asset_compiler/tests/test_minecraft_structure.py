from __future__ import annotations

import copy
import gzip
import json
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from guild_branch_interior import compile_guild_branch_v04
from minecraft_structure import encode_structure_nbt, inspect_export_header, structure_filename, write_structure_nbt

SPEC = ROOT / "specimens" / "bootstrap_guild_branch_v0.4.json"


class MinecraftStructureExportTests(unittest.TestCase):
    def compiled(self):
        spec = json.loads(SPEC.read_text(encoding="utf-8"))
        return compile_guild_branch_v04(copy.deepcopy(spec))

    def test_export_is_deterministic_gzip_nbt(self):
        compiled = self.compiled()
        a = encode_structure_nbt(compiled)
        b = encode_structure_nbt(compiled)
        self.assertEqual(a, b)
        self.assertEqual(b"\x1f\x8b", a[:2])
        header = inspect_export_header(a)
        self.assertGreater(header["uncompressedBytes"], header["compressedBytes"])
        raw = gzip.decompress(a)
        for marker in (b"DataVersion", b"size", b"palette", b"blocks", b"entities", b"minecraft:stone_bricks"):
            self.assertIn(marker, raw)

    def test_filename_and_file_write(self):
        compiled = self.compiled()
        name = structure_filename(compiled.summary["assetId"])
        self.assertEqual("guild_branch_bootstrap_temperate_v0_4.nbt", name)
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / name
            write_structure_nbt(path, compiled)
            self.assertTrue(path.exists())
            self.assertGreater(path.stat().st_size, 100)
            inspect_export_header(path.read_bytes())


if __name__ == "__main__":
    unittest.main()
