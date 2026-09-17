from __future__ import annotations

import json
from pathlib import Path
import tempfile
import unittest

from v2.fence import FenceBusyError, WriterFence
from v2.ownership import ControllerIdentity, OwnershipToken


class ControllerIdentityTest(unittest.TestCase):
    def test_identity_is_normalized_and_nonempty(self) -> None:
        identity = ControllerIdentity("  controller-a  ")
        self.assertEqual(identity.controller_id, "controller-a")
        with self.assertRaises(ValueError):
            ControllerIdentity("   ")

    def test_generated_identities_are_distinct(self) -> None:
        first = ControllerIdentity.new()
        second = ControllerIdentity.new()
        self.assertNotEqual(first, second)


class OwnershipTokenTest(unittest.TestCase):
    def test_first_generation_and_advance_are_monotonic(self) -> None:
        identity = ControllerIdentity("controller-a")
        first = OwnershipToken.first(identity)
        second = first.advance()
        replacement = second.advance(ControllerIdentity("controller-b"))

        self.assertEqual(first.generation, 1)
        self.assertEqual(second.generation, 2)
        self.assertEqual(replacement.generation, 3)
        self.assertEqual(replacement.controller_id, "controller-b")
        self.assertNotEqual(first.digest, second.digest)
        self.assertNotEqual(second.digest, replacement.digest)

    def test_invalid_generation_fails_closed(self) -> None:
        for invalid in (0, -1, True, 1.5):
            with self.subTest(invalid=invalid):
                with self.assertRaises(ValueError):
                    OwnershipToken("controller-a", invalid)

    def test_token_is_immutable(self) -> None:
        token = OwnershipToken("controller-a", 1)
        with self.assertRaises(Exception):
            token.generation = 2


class EpochWriterFenceTest(unittest.TestCase):
    def test_fence_persists_owner_generation_metadata(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "writer.lock"
            token = OwnershipToken("controller-a", 7)
            fence = WriterFence.for_token(path, token)
            fence.acquire()
            try:
                metadata = json.loads(path.read_text(encoding="utf-8"))
                self.assertEqual(metadata["controller_id"], "controller-a")
                self.assertEqual(metadata["generation"], 7)
                self.assertGreater(metadata["pid"], 0)
            finally:
                fence.release()

    def test_epoch_fence_remains_exclusive(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "writer.lock"
            first = WriterFence.for_token(path, OwnershipToken("old", 4))
            second = WriterFence.for_token(path, OwnershipToken("new", 5))
            first.acquire()
            try:
                with self.assertRaises(FenceBusyError):
                    second.acquire()
            finally:
                first.release()
            second.acquire()
            second.release()

    def test_invalid_fence_identity_or_generation_fails_before_lock(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "writer.lock"
            with self.assertRaises(ValueError):
                WriterFence(path=path, controller_id="", generation=1)
            with self.assertRaises(ValueError):
                WriterFence(path=path, controller_id="controller", generation=0)


if __name__ == "__main__":
    unittest.main()
