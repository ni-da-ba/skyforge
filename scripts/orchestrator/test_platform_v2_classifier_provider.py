from __future__ import annotations

from pathlib import Path
import sys
import tempfile
import types
import unittest

from v2.classifier_provider import (
    ClassifierAdvanceDisposition,
    ClassifierProviderConfig,
    ClassifierProviderError,
    ClassifierRequest,
    ClassifierRunStatus,
    ClassifierRunStore,
    CodexClassifierProvider,
    advance_classifier,
    classifier_provider_config,
    parse_classifier_response,
)
from v2.decision import DecisionKind, WorkerTier


MAIN = "a" * 40


def request(**semantic_overrides):
    semantic = {
        "events": [{"event": "workflow_run", "action": "completed"}],
        "task_authority": {"issue": 860},
        "snapshot": {"open_prs": []},
    }
    semantic.update(semantic_overrides)
    return ClassifierRequest(current_main=MAIN, semantic_input=semantic)


class FakeProvider:
    def __init__(self, *, response=None, error=None):
        self.calls = 0
        self.response = response or (
            '{"decision":"NOOP","reason":"nothing executable"}'
        )
        self.error = error

    def classify(self, *, request, root, config):
        self.calls += 1
        if self.error is not None:
            raise self.error
        return self.response, parse_classifier_response(self.response)


class InjectedClassifierCrash(BaseException):
    pass


class CrashProvider:
    def __init__(self):
        self.calls = 0

    def classify(self, *, request, root, config):
        self.calls += 1
        raise InjectedClassifierCrash("controller died during classifier call")


class ClassifierProviderTest(unittest.TestCase):
    def test_defaults_match_legacy_classifier_policy(self):
        cfg = classifier_provider_config({})
        self.assertEqual(cfg.model, "gpt-5.6-luna")
        self.assertEqual(cfg.reasoning_effort, "low")
        custom = classifier_provider_config({
            "SKYFORGE_ORCHESTRATOR_MODEL": "fixture-model",
            "SKYFORGE_ORCHESTRATOR_REASONING": "high",
        })
        self.assertEqual(custom.model, "fixture-model")
        self.assertEqual(custom.reasoning_effort, "high")

    def test_prompt_declares_exact_decision_schema(self):
        prompt = request().prompt()
        self.assertIn('"decision":"NOOP|DISPATCH|HUMAN_GATE|MERGE"', prompt)
        self.assertIn("never widen its allowed_paths", prompt)
        self.assertIn("DISPATCH requires lane", prompt)
        self.assertIn("Never copy task_issue_numbers", prompt)
        self.assertIn("for issue-defined new work it must be null", prompt)
        self.assertIn("EXACTLY byte-for-byte", prompt)
        self.assertIn("do not summarize, normalize, shorten", prompt)

    def test_request_fingerprint_changes_with_semantic_input_or_main(self):
        first = request()
        again = request()
        changed = request(snapshot={"open_prs": [{"number": 1}]})
        moved = ClassifierRequest(
            current_main="b" * 40,
            semantic_input=first.semantic_input,
        )
        self.assertEqual(first.request_id, again.request_id)
        self.assertNotEqual(first.request_id, changed.request_id)
        self.assertNotEqual(first.request_id, moved.request_id)

    def test_parser_accepts_fenced_json_and_typed_dispatch_proposal(self):
        fence = chr(96) * 3
        text = (
            "preface\n"
            + fence
            + 'json\n{"decision":"DISPATCH","lane":"Implementation",'
            + '"objective":"bounded","stop_boundary":"stop",'
            + '"worker_tier":"LUNA","allowed_paths":["scripts/orchestrator/v2/example.py"],'
            + '"reason":"proposal only"}\n'
            + fence
            + "\nsuffix"
        )
        decision = parse_classifier_response(text)
        self.assertEqual(decision.kind, DecisionKind.DISPATCH)
        self.assertEqual(decision.worker_tier, WorkerTier.LUNA)
        self.assertEqual(decision.allowed_paths, ("scripts/orchestrator/v2/example.py",))

    def test_parser_rejects_missing_json_and_unknown_decision(self):
        with self.assertRaises(ValueError):
            parse_classifier_response("not json")
        with self.assertRaises(ValueError):
            parse_classifier_response('{"decision":"ROOT_ACCESS"}')

    def test_success_is_durable_and_not_recalled(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            store = ClassifierRunStore.for_root(root / "state")
            provider = FakeProvider()
            cfg = ClassifierProviderConfig("model", "low")
            req = request()

            first = advance_classifier(
                request=req,
                root=root,
                store=store,
                provider=provider,
                config=cfg,
            )
            self.assertEqual(first.disposition, ClassifierAdvanceDisposition.COMPLETE)
            self.assertEqual(first.record.status, ClassifierRunStatus.COMPLETE)
            self.assertEqual(first.record.decision.kind, DecisionKind.NOOP)
            self.assertTrue(first.record.raw_response_digest)
            self.assertEqual(provider.calls, 1)

            second = advance_classifier(
                request=req,
                root=root,
                store=store,
                provider=provider,
                config=cfg,
            )
            self.assertEqual(
                second.disposition,
                ClassifierAdvanceDisposition.ALREADY_COMPLETE,
            )
            self.assertEqual(provider.calls, 1)

    def test_crash_during_call_becomes_interrupted_without_second_call(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            store = ClassifierRunStore.for_root(root / "state")
            provider = CrashProvider()
            cfg = ClassifierProviderConfig("model", "low")
            req = request()

            with self.assertRaises(InjectedClassifierCrash):
                advance_classifier(
                    request=req,
                    root=root,
                    store=store,
                    provider=provider,
                    config=cfg,
                )
            self.assertEqual(
                store.load().get(req.request_id).status,
                ClassifierRunStatus.RUNNING,
            )
            self.assertEqual(provider.calls, 1)

            restarted = advance_classifier(
                request=req,
                root=root,
                store=store,
                provider=provider,
                config=cfg,
            )
            self.assertEqual(
                restarted.disposition,
                ClassifierAdvanceDisposition.RECOVERY_REQUIRED,
            )
            self.assertEqual(restarted.record.status, ClassifierRunStatus.INTERRUPTED)
            self.assertEqual(provider.calls, 1)

            again = advance_classifier(
                request=req,
                root=root,
                store=store,
                provider=provider,
                config=cfg,
            )
            self.assertEqual(
                again.disposition,
                ClassifierAdvanceDisposition.RECOVERY_REQUIRED,
            )
            self.assertEqual(provider.calls, 1)

    def test_classified_failure_is_durable_and_not_auto_retried(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            store = ClassifierRunStore.for_root(root / "state")
            provider = FakeProvider(
                error=ClassifierProviderError("provider_capacity", 900, "capacity")
            )
            req = request()
            cfg = ClassifierProviderConfig("model", "low")
            first = advance_classifier(
                request=req,
                root=root,
                store=store,
                provider=provider,
                config=cfg,
            )
            self.assertEqual(first.disposition, ClassifierAdvanceDisposition.FAILED)
            self.assertEqual(first.record.failure_kind, "provider_capacity")
            self.assertEqual(first.record.retry_after_seconds, 900)
            self.assertEqual(provider.calls, 1)

            again = advance_classifier(
                request=req,
                root=root,
                store=store,
                provider=provider,
                config=cfg,
            )
            self.assertEqual(again.disposition, ClassifierAdvanceDisposition.FAILED)
            self.assertEqual(provider.calls, 1)

    def test_provider_config_drift_rejected_for_same_request(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            store = ClassifierRunStore.for_root(root / "state")
            provider = FakeProvider()
            req = request()
            advance_classifier(
                request=req,
                root=root,
                store=store,
                provider=provider,
                config=ClassifierProviderConfig("model-a", "low"),
            )
            with self.assertRaisesRegex(ValueError, "identity drifted"):
                advance_classifier(
                    request=req,
                    root=root,
                    store=store,
                    provider=provider,
                    config=ClassifierProviderConfig("model-b", "low"),
                )
            self.assertEqual(provider.calls, 1)


class CodexClassifierAdapterTest(unittest.TestCase):
    def test_codex_adapter_is_ephemeral_read_only_and_typed(self):
        calls = {}
        module = types.ModuleType("openai_codex")

        class Sandbox:
            read_only = object()

        fence = chr(96) * 3

        class Result:
            final_response = (
                fence
                + 'json\n{"decision":"NOOP","reason":"bounded proposal"}\n'
                + fence
            )

        class Thread:
            def run(self, prompt, *, sandbox):
                calls["prompt"] = prompt
                calls["run_sandbox"] = sandbox
                return Result()

        class Codex:
            def __enter__(self):
                return self
            def __exit__(self, *args):
                return False
            def thread_start(self, **kwargs):
                calls["start"] = kwargs
                return Thread()

        module.Codex = Codex
        module.Sandbox = Sandbox
        prior = sys.modules.get("openai_codex")
        sys.modules["openai_codex"] = module
        try:
            with tempfile.TemporaryDirectory() as td:
                root = Path(td)
                raw, decision = CodexClassifierProvider().classify(
                    request=request(),
                    root=root,
                    config=ClassifierProviderConfig("fixture-model", "medium"),
                )
                self.assertIn("NOOP", raw)
                self.assertEqual(decision.kind, DecisionKind.NOOP)
                self.assertEqual(calls["start"]["cwd"], str(root.resolve()))
                self.assertEqual(calls["start"]["model"], "fixture-model")
                self.assertEqual(
                    calls["start"]["config"],
                    {"model_reasoning_effort": "medium"},
                )
                self.assertTrue(calls["start"]["ephemeral"])
                self.assertIs(calls["start"]["sandbox"], Sandbox.read_only)
                self.assertIs(calls["run_sandbox"], Sandbox.read_only)
                self.assertIn("no authority", calls["prompt"].lower())
        finally:
            if prior is None:
                sys.modules.pop("openai_codex", None)
            else:
                sys.modules["openai_codex"] = prior

    def test_hosted_runtime_does_not_import_classifier_provider(self):
        root = Path(__file__).resolve().parent
        source = (root / "platform_v2_hosted_runtime.py").read_text(encoding="utf-8")
        self.assertNotIn("classifier_provider", source)


if __name__ == "__main__":
    unittest.main()
