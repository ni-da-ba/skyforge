from __future__ import annotations

import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from minecraft_block_entities import block_entity_payload_for, require_block_entity_payload
from model import BlockState, SpecError


class MinecraftBlockEntityPolicyTests(unittest.TestCase):
    def test_empty_vanilla_containers_have_explicit_payload_policy(self):
        for name in ("minecraft:barrel", "minecraft:chest"):
            payload = block_entity_payload_for(BlockState.of(name))
            self.assertIsNotNone(payload)
            assert payload is not None
            self.assertEqual(name, payload.block_entity_id)
            self.assertTrue(payload.empty_items)

    def test_non_block_entity_has_no_payload(self):
        self.assertIsNone(block_entity_payload_for(BlockState.of("minecraft:stone_bricks")))

    def test_required_unknown_payload_fails_closed(self):
        with self.assertRaises(SpecError):
            require_block_entity_payload(BlockState.of("minecraft:stone_bricks"))


if __name__ == "__main__":
    unittest.main()
