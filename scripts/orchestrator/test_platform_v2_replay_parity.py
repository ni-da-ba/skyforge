from __future__ import annotations

import json
from pathlib import Path
import tempfile
import unittest

from v2.parity import (
    corpus_digest,
    load_legacy_corpus,
    require_parity,
    run_parity,
)


CORPUS = Path(__file__).resolve().parent / "v2" / "fixtures" / "legacy_transition_cases.json"


class LegacyReplayCorpusTest(unittest.TestCase):
    def test_corpus_loads_with_explicit_legacy_provenance(self) -> None:
        cases = load_legacy_corpus(CORPUS)
        self.assertGreaterEqual(len(cases), 5)
        for case in cases:
            self.assertTrue(case.source.strip(), case.case_id)
            self.assertTrue(
                "test_skyforge_control_replay_runtime.py" in case.source
                or "skyforge_control_replay_base.py" in case.source,
                case.source,
            )

    def test_corpus_digest_is_order_independent_and_stable(self) -> None:
        cases = load_legacy_corpus(CORPUS)
        forward = corpus_digest(cases)
        reverse = corpus_digest(reversed(cases))
        self.assertEqual(forward, reverse)
        self.assertEqual(len(forward), 64)

    def test_v2_matches_all_accepted_legacy_cases(self) -> None:
        cases = load_legacy_corpus(CORPUS)
        results = run_parity(cases)
        require_parity(results)
        self.assertTrue(all(result.matches for result in results))

    def test_parity_failure_names_the_divergent_case(self) -> None:
        cases = load_legacy_corpus(CORPUS)

        def wrong_decider(snapshot):
            from v2 import TransitionKind, TransitionPlan
            return TransitionPlan(TransitionKind.NOOP, "intentional mismatch")

        results = run_parity(cases, decider=wrong_decider)
        with self.assertRaisesRegex(AssertionError, "legacy-exact-head-machine-green"):
            require_parity(results)

    def test_unsupported_schema_fails_closed(self) -> None:
        payload = json.loads(CORPUS.read_text(encoding="utf-8"))
        payload["schema_version"] = 999
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "corpus.json"
            path.write_text(json.dumps(payload), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "unsupported"):
                load_legacy_corpus(path)

    def test_unknown_enum_fails_closed(self) -> None:
        payload = json.loads(CORPUS.read_text(encoding="utf-8"))
        payload["cases"][0]["snapshot"]["ci_state"] = "MAGIC"
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "corpus.json"
            path.write_text(json.dumps(payload), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "unknown enum"):
                load_legacy_corpus(path)

    def test_duplicate_case_ids_fail_closed(self) -> None:
        payload = json.loads(CORPUS.read_text(encoding="utf-8"))
        payload["cases"].append(dict(payload["cases"][0]))
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "corpus.json"
            path.write_text(json.dumps(payload), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "duplicate"):
                load_legacy_corpus(path)


if __name__ == "__main__":
    unittest.main()
