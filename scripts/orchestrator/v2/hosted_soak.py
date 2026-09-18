"""Disposable end-to-end hosted Platform-v2 soak and maturity evidence for R5C27.

This harness intentionally uses the real HostedV2Substrate and background execution
driver against a temporary Git repository, deterministic fake providers, and an
in-memory remote. It has no systemd/Caddy/service-manager surface and no live GitHub or
provider dependency.
"""

from __future__ import annotations

from contextlib import contextmanager
from dataclasses import dataclass
import hashlib
import hmac
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import time
from typing import Any, Mapping

import platform_v2_hosted_runtime as hosted

from .classifier_provider import (
    ClassifierProviderError,
    ClassifierRunStatus,
    ClassifierRunStore,
    parse_classifier_response,
)
from .effects import EffectKind, RemoteEffectObservation, RemoteEffectPresence
from .hosted_budget import HostedBudgetStore
from .hosted_completion import HostedCompletionStatus, HostedCompletionStore
from .hosted_execution_driver import (
    BudgetedClassifierProvider,
    BudgetedWorkerProvider,
    HostedExecutionDriver,
    ProductionHostedDependencyFactory,
)
from .hosted_execution_runtime import (
    HostedExecutionAdvanceDisposition,
    HostedExecutionGateDecision,
    HostedExecutionGateDisposition,
)
from .identity import canonical_digest
from .ordinary_effects import OrdinaryEffectStore
from .ordinary_pipeline import OrdinaryPipelineStore
from .ordinary_remote import OrdinaryEffectBinding
from .task_event_composition import TaskAuthorityEventStore
from .worker_provider import WorkerProviderError


REPO = "ni-da-ba/skyforge"
TRUSTED_ACTOR = "ni-da-ba"
FIXTURE_SECRET = "r5c27-disposable-webhook-secret-000000000000"
SOAK_DAY = "2026-09-18"
ROLLOVER_DAY = "2026-09-19"
_FIXED_GIT_DATE = "2026-09-18T12:00:00+00:00"
_TASK_ID_RE = re.compile(r"github-issue-(\d+)-comment-(\d+)$")


def _git(root: Path, *args: str, env: Mapping[str, str] | None = None) -> str:
    effective = dict(os.environ)
    if env:
        effective.update(env)
    result = subprocess.run(
        ["git", *args],
        cwd=root,
        text=True,
        capture_output=True,
        check=True,
        env=effective,
    )
    return result.stdout.strip()


@contextmanager
def _fixed_git_dates():
    keys = ("GIT_AUTHOR_DATE", "GIT_COMMITTER_DATE")
    old = {key: os.environ.get(key) for key in keys}
    for key in keys:
        os.environ[key] = _FIXED_GIT_DATE
    try:
        yield
    finally:
        for key, value in old.items():
            if value is None:
                os.environ.pop(key, None)
            else:
                os.environ[key] = value


def _make_repo(root: Path) -> str:
    _git(root, "init", "-b", "main")
    _git(root, "config", "user.email", "r5c27@example.invalid")
    _git(root, "config", "user.name", "Skyforge R5C27 Soak")
    (root / ".gitignore").write_text(
        ".skyforge-platform-v2/\n.skyforge-orchestrator/\n",
        encoding="utf-8",
    )
    target = root / "docs" / "operations"
    target.mkdir(parents=True)
    (target / "r5c27-base.txt").write_text("r5c27 deterministic base\n", encoding="utf-8")
    _git(root, "add", ".gitignore", "docs/operations/r5c27-base.txt")
    _git(
        root,
        "commit",
        "-m",
        "r5c27 deterministic base",
        env={
            "GIT_AUTHOR_DATE": _FIXED_GIT_DATE,
            "GIT_COMMITTER_DATE": _FIXED_GIT_DATE,
        },
    )
    return _git(root, "rev-parse", "HEAD")


def _legacy_state() -> dict[str, Any]:
    return {
        "paused": False,
        "blocked_kind": None,
        "pending_worker": None,
        "pending_decision": None,
        "managed": {},
        "pending_events": [],
        "external_producer_claims": {},
        "human_gate_records": {
            "dr-human-exploration-rereview": {
                "token": "deferred-r5c27-fixture",
                "target": "DR-70",
                "seeded_from_github": False,
            }
        },
        "roadmap": {
            "roadmap_id": "r5c27-disposable-soak",
            "active": None,
            "completed_runs": {},
            "blocked_nodes": {
                "dr-human-exploration-rereview": {
                    "reason": "deferred; not consumed by nonproduction soak"
                }
            },
            "manifest_fingerprint": "f" * 64,
        },
        "budget_day": SOAK_DAY,
        "classifier_calls_today": 2,
        "luna_worker_calls_today": 1,
        "worker_calls_today": 1,
        "luna_daily_limit_override": 50,
        "terra_daily_limit_override": 10,
    }


def _write_legacy(root: Path) -> None:
    state_dir = root / ".skyforge-orchestrator"
    state_dir.mkdir(parents=True, exist_ok=True)
    payload = json.dumps(_legacy_state(), sort_keys=True, indent=2) + "\n"
    for name in ("state.json", "state.json.bak"):
        (state_dir / name).write_text(payload, encoding="utf-8")


def _fixture_gate(main: str) -> HostedExecutionGateDecision:
    return HostedExecutionGateDecision(
        disposition=HostedExecutionGateDisposition.READY,
        blockers=(),
        activation_input_digest="a" * 64,
        production_activation_digest="b" * 64,
        accepted_main_sha=main,
    )


def _task_body(issue: int) -> str:
    authority = {
        "lane": "Implementation",
        "objective": f"R5C27 disposable bounded task {issue}",
        "stop_boundary": "merge boundary",
        "allowed_paths": ["docs/operations/**"],
        "protected_paths": ["scripts/orchestrator/**"],
        "auto_merge_eligible": True,
    }
    return (
        "AUDIT NEW TASK [SKYFORGE TASK AUTHORITY]\n"
        + json.dumps(authority, sort_keys=True, separators=(",", ":"))
    )


def _task_payload(issue: int, comment_id: int) -> dict[str, Any]:
    stamp = f"2026-09-18T12:{issue % 60:02d}:00Z"
    return {
        "action": "created",
        "repository": {"full_name": REPO},
        "issue": {
            "number": issue,
            "title": f"R5C27 disposable task {issue}",
            "body": "Disposable soak context.",
        },
        "comment": {
            "id": comment_id,
            "body": _task_body(issue),
            "created_at": stamp,
            "updated_at": stamp,
            "user": {"login": TRUSTED_ACTOR},
        },
    }


def _signed(payload: Mapping[str, Any], delivery: str) -> tuple[bytes, dict[str, str]]:
    raw = json.dumps(payload, sort_keys=True, separators=(",", ":")).encode("utf-8")
    digest = hmac.new(FIXTURE_SECRET.encode(), raw, hashlib.sha256).hexdigest()
    return raw, {
        "X-GitHub-Event": "issue_comment",
        "X-GitHub-Delivery": delivery,
        "X-Hub-Signature-256": "sha256=" + digest,
    }


class _Result:
    def __init__(self, args, stdout: str = "") -> None:
        self.args = args
        self.returncode = 0
        self.stdout = stdout
        self.stderr = ""


class DisposableRemoteWorld:
    """In-memory GitHub-shaped truth for the accepted strict adapters."""

    def __init__(self, *, main_sha: str) -> None:
        self.main_sha = main_sha
        self.issues: dict[int, dict[str, Any]] = {}
        self.comments: dict[int, dict[str, Any]] = {}
        self.prs: dict[int, dict[str, Any]] = {}
        self.changed_paths: dict[int, tuple[str, ...]] = {}
        self.remote_effect_executes: dict[str, int] = {}
        self.lifecycle_executes = {"ready": 0, "merge": 0}
        self.next_pr = 4200
        self.fail_next_pr_view = False

    def register_task(self, issue: int, comment_id: int) -> dict[str, Any]:
        payload = _task_payload(issue, comment_id)
        self.issues[issue] = dict(payload["issue"])
        self.issues[issue]["state"] = "open"
        self.comments[comment_id] = {
            **dict(payload["comment"]),
            "issue_url": f"https://api.github.com/repos/{REPO}/issues/{issue}",
        }
        return payload

    def create_pr(self, binding: OrdinaryEffectBinding, *, changed_paths: tuple[str, ...]) -> int:
        number = self.next_pr
        self.next_pr += 1
        scope = binding.scope
        self.prs[number] = {
            "number": number,
            "state": "OPEN",
            "mergedAt": None,
            "isDraft": True,
            "mergeStateStatus": "CLEAN",
            "statusCheckRollup": [{"status": "COMPLETED", "conclusion": "SUCCESS"}],
            "headRefName": scope.branch,
            "headRefOid": scope.expected_head_sha,
            "baseRefName": scope.base_ref,
            "title": scope.pr_title,
            "body": scope.pr_body,
            "reviewDecision": "",
        }
        self.changed_paths[number] = tuple(sorted(changed_paths))
        return number


class DisposableRunner:
    def __init__(self, root: Path, world: DisposableRemoteWorld) -> None:
        self.root = Path(root).resolve()
        self.world = world

    def __call__(self, args, **kwargs):
        command = tuple(str(value) for value in args)
        if command and command[0] == "git":
            return subprocess.run(args, **kwargs)

        if command[:2] == ("gh", "api"):
            path = command[2]
            if path == f"repos/{REPO}/commits/main":
                return _Result(args, self.world.main_sha + "\n")
            issue_match = re.fullmatch(rf"repos/{re.escape(REPO)}/issues/(\d+)", path)
            if issue_match:
                issue = self.world.issues[int(issue_match.group(1))]
                return _Result(args, json.dumps(issue))
            comment_match = re.fullmatch(
                rf"repos/{re.escape(REPO)}/issues/comments/(\d+)",
                path,
            )
            if comment_match:
                comment = self.world.comments[int(comment_match.group(1))]
                return _Result(args, json.dumps(comment))
            raise AssertionError(command)

        if command[:3] == ("gh", "pr", "view"):
            if self.world.fail_next_pr_view:
                self.world.fail_next_pr_view = False
                raise subprocess.CalledProcessError(
                    1,
                    list(command),
                    stderr="fixture managed PR truth unavailable",
                )
            number = int(command[3])
            return _Result(args, json.dumps(self.world.prs[number]))

        if command[:3] == ("gh", "pr", "diff"):
            number = int(command[3])
            return _Result(args, "\n".join(self.world.changed_paths[number]) + "\n")

        if command[:3] == ("gh", "pr", "ready"):
            number = int(command[3])
            self.world.lifecycle_executes["ready"] += 1
            self.world.prs[number]["isDraft"] = False
            return _Result(args)

        if command[:3] == ("gh", "pr", "merge"):
            number = int(command[3])
            self.world.lifecycle_executes["merge"] += 1
            pr = self.world.prs[number]
            pr["state"] = "MERGED"
            pr["mergedAt"] = "2026-09-18T18:00:00Z"
            pr["isDraft"] = False
            return _Result(args)

        raise AssertionError(command)


class _BoundRemote:
    def __init__(
        self,
        world: DisposableRemoteWorld,
        binding: OrdinaryEffectBinding,
        worktree: Path,
    ) -> None:
        self.world = world
        self.binding = binding
        self.worktree = Path(worktree)

    def observe(self, identity):
        if identity != self.binding.identity:
            raise ValueError("fixture effect identity mismatch")
        scope = self.binding.scope
        if identity.kind is EffectKind.PUSH_BRANCH:
            key = f"branch:{scope.branch}"
            value = getattr(self.world, "_branches", {}).get(key)
            if value is None:
                return RemoteEffectObservation(RemoteEffectPresence.ABSENT)
            return RemoteEffectObservation(
                RemoteEffectPresence.PRESENT_EXACT
                if value == scope.expected_head_sha
                else RemoteEffectPresence.PRESENT_CONFLICT,
                remote_identity=(
                    f"branch:{scope.branch}@{value}"
                    if value == scope.expected_head_sha
                    else ""
                ),
            )
        if identity.kind is EffectKind.CREATE_PR:
            matches = [
                pr
                for pr in self.world.prs.values()
                if pr["headRefName"] == scope.branch
                and pr["baseRefName"] == scope.base_ref
            ]
            if not matches:
                return RemoteEffectObservation(RemoteEffectPresence.ABSENT)
            if len(matches) != 1:
                return RemoteEffectObservation(RemoteEffectPresence.PRESENT_CONFLICT)
            pr = matches[0]
            exact = (
                pr["headRefOid"] == scope.expected_head_sha
                and pr["title"] == scope.pr_title
                and pr["body"] == scope.pr_body
            )
            if not exact:
                return RemoteEffectObservation(RemoteEffectPresence.PRESENT_CONFLICT)
            state = "MERGED" if pr.get("mergedAt") else str(pr["state"]).upper()
            return RemoteEffectObservation(
                RemoteEffectPresence.PRESENT_EXACT,
                remote_identity=f"pr:{pr['number']}:{state}@{scope.expected_head_sha}",
            )
        raise AssertionError(identity.kind)

    def execute(self, identity):
        if identity != self.binding.identity:
            raise ValueError("fixture effect identity mismatch")
        self.world.remote_effect_executes[identity.effect_id] = (
            self.world.remote_effect_executes.get(identity.effect_id, 0) + 1
        )
        scope = self.binding.scope
        if identity.kind is EffectKind.PUSH_BRANCH:
            if not hasattr(self.world, "_branches"):
                self.world._branches = {}
            self.world._branches[f"branch:{scope.branch}"] = scope.expected_head_sha
            return f"branch:{scope.branch}@{scope.expected_head_sha}"
        if identity.kind is EffectKind.CREATE_PR:
            changed = subprocess.run(
                ["git", "diff-tree", "--no-commit-id", "--name-only", "-r", "HEAD"],
                cwd=self.worktree,
                check=True,
                text=True,
                capture_output=True,
            ).stdout.splitlines()
            number = self.world.create_pr(
                self.binding,
                changed_paths=tuple(path.strip() for path in changed if path.strip()),
            )
            return f"pr:{number}:OPEN@{scope.expected_head_sha}"
        raise AssertionError(identity.kind)


class DisposableRemoteFactory:
    def __init__(self, world: DisposableRemoteWorld) -> None:
        self.world = world

    def __call__(self, worktree: Path, binding: OrdinaryEffectBinding):
        return _BoundRemote(self.world, binding, Path(worktree))


class DeterministicClassifier:
    def __init__(self, *, fail: bool = False) -> None:
        self.calls = 0
        self.fail = fail

    def classify(self, *, request, root, config):
        self.calls += 1
        if self.fail:
            raise ClassifierProviderError(
                "fixture_provider_failure",
                300,
                "injected disposable classifier failure",
            )
        authority = request.semantic_input.get("task_authority") or {}
        raw = json.dumps(
            {
                "decision": "DISPATCH",
                "lane": authority.get("lane"),
                "objective": authority.get("objective"),
                "stop_boundary": authority.get("stop_boundary"),
                "worker_tier": "LUNA",
                "allowed_paths": list(authority.get("allowed_paths") or ()),
                "reason": "deterministic disposable soak proposal",
            },
            separators=(",", ":"),
        )
        return raw, parse_classifier_response(raw)


class DeterministicWorker:
    def __init__(self, *, fail: bool = False) -> None:
        self.calls = 0
        self.fail = fail

    def run(self, *, spec, worktree: Path, config):
        self.calls += 1
        if self.fail:
            raise WorkerProviderError(
                "fixture_worker_failure",
                300,
                "injected disposable worker failure",
            )
        match = _TASK_ID_RE.search(spec.task_id)
        if match is None:
            raise RuntimeError("fixture worker could not recover issue identity")
        issue = int(match.group(1))
        target = Path(worktree) / "docs" / "operations" / f"r5c27-task-{issue}.txt"
        target.write_text(
            f"deterministic disposable output for issue {issue}\n",
            encoding="utf-8",
        )
        return f"R5C27 disposable task {issue} complete"


def _wait_for(predicate, *, timeout: float = 8.0, label: str = "condition") -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if predicate():
            return
        time.sleep(0.02)
    raise RuntimeError(f"timed out waiting for {label}")


def _completion_count(root: Path) -> int:
    return sum(
        record.status is HostedCompletionStatus.CLEANED
        for record in HostedCompletionStore.for_root(root).load().records
    )


def _make_runtime(root: Path, gate: HostedExecutionGateDecision) -> hosted.HostedV2Substrate:
    return hosted.HostedV2Substrate(
        root,
        repo=REPO,
        require_webhook_secret=True,
        startup_reconcile=True,
        webhook_secret=FIXTURE_SECRET,
        trusted_actors=(TRUSTED_ACTOR,),
        production_execution_requested=True,
        execution_gate=gate,
    )


def _make_driver(
    *,
    runtime: hosted.HostedV2Substrate,
    root: Path,
    classifier: DeterministicClassifier,
    worker: DeterministicWorker,
    world: DisposableRemoteWorld,
    runner: DisposableRunner,
    periodic_seconds: int,
) -> HostedExecutionDriver:
    store = HostedBudgetStore.for_root(root)
    legacy_reader = lambda _root: _legacy_state()
    classifier_provider = BudgetedClassifierProvider(
        root=root,
        store=store,
        delegate=classifier,
        day_reader=lambda: SOAK_DAY,
        legacy_reader=legacy_reader,
    )
    worker_provider = BudgetedWorkerProvider(
        root=root,
        store=store,
        delegate=worker,
        day_reader=lambda: SOAK_DAY,
        legacy_reader=legacy_reader,
    )
    factory = ProductionHostedDependencyFactory(
        root=root,
        environ={},
        budget_store=store,
        quota_reader=lambda: None,
        day_reader=lambda: SOAK_DAY,
        legacy_reader=legacy_reader,
        classifier_provider=classifier_provider,
        worker_provider=worker_provider,
    )
    # Bind both read-only/mutation GitHub-shaped lifecycle calls and accepted fake
    # ordinary remote effects to the disposable world.
    original_build = factory.build

    def build():
        deps = original_build()
        return type(deps)(
            classifier_provider=deps.classifier_provider,
            worker_provider=deps.worker_provider,
            classifier_local_budget=deps.classifier_local_budget,
            worker_local_budget=deps.worker_local_budget,
            classifier_provider_quota=deps.classifier_provider_quota,
            worker_provider_quota=deps.worker_provider_quota,
            classifier_config=deps.classifier_config,
            worker_config=deps.worker_config,
            attempt_number=deps.attempt_number,
            external_claims=deps.external_claims,
            runner=runner,
            remote_factory=DisposableRemoteFactory(world),
        )

    factory.build = build
    driver = HostedExecutionDriver(
        runtime=runtime,
        dependencies=factory,
        periodic_seconds=periodic_seconds,
        max_boundaries_per_wake=32,
    )
    runtime.attach_execution_driver(driver)
    return driver


def _ingest(runtime, world: DisposableRemoteWorld, issue: int, comment: int, delivery: str):
    payload = world.register_task(issue, comment)
    raw, headers = _signed(payload, delivery)
    return runtime.handle_webhook(headers=headers, raw=raw), payload


def _stable_effect_ids(root: Path) -> tuple[str, ...]:
    return tuple(sorted(record.identity.effect_id for record in OrdinaryEffectStore.for_root(root).load().records))


def _stable_task_projection(root: Path) -> tuple[dict[str, Any], ...]:
    completions = HostedCompletionStore.for_root(root).load().records
    admissions_by_attempt = {}
    # Admission singleton is intentionally cleared; reconstruct stable task/attempt
    # identities from append-only ordinary-pipeline handoffs instead.
    handoffs = OrdinaryPipelineStore.for_root(root).load().reconstructible_managed_handoffs()
    handoff_by_attempt = {handoff.scope.attempt_id: handoff for handoff in handoffs}
    result = []
    for completion in completions:
        handoff = handoff_by_attempt[completion.attempt_id]
        result.append(
            {
                "event_id": completion.event_id,
                "issue_number": completion.issue_number,
                "attempt_id": completion.attempt_id,
                "handoff_digest": handoff.digest,
                "status": completion.status.value,
                "changed_paths": list(handoff.changed_paths),
            }
        )
    return tuple(sorted(result, key=lambda value: value["issue_number"]))


@dataclass(frozen=True)
class HostedSoakReport:
    stable: Mapping[str, Any]
    operational: Mapping[str, Any]

    @property
    def digest(self) -> str:
        return canonical_digest(dict(self.stable))

    def as_dict(self) -> dict[str, Any]:
        return {
            "schema_version": 1,
            "disposition": "PASS",
            "stable_evidence": dict(self.stable),
            "operational_observation": dict(self.operational),
            "evidence_digest": self.digest,
        }


def run_disposable_hosted_soak(root: Path) -> HostedSoakReport:
    """Run three reusable hosted tasks plus replay, restart, periodic, and failure cases."""

    root = Path(root).resolve()
    root.mkdir(parents=True, exist_ok=True)
    with _fixed_git_dates():
        base = _make_repo(root)
        _write_legacy(root)
        gate = _fixture_gate(base)
        world = DisposableRemoteWorld(main_sha=base)
        runner = DisposableRunner(root, world)
        classifier = DeterministicClassifier()
        worker = DeterministicWorker()
        restart_count = 0
        duplicate_suppressed = 0
        semantic_replay_suppressed = 0

        budget_store = HostedBudgetStore.for_root(root)
        initial_budget = budget_store.ensure_day(day=SOAK_DAY, legacy_state=_legacy_state())

        # Task 1: duplicate delivery + restart between remote handoff and lifecycle.
        app = _make_runtime(root, gate)
        (first, payload1) = _ingest(app, world, 9101, 19101, "r5c27-task1")
        if first[0] != 202 or not first[1]["accepted"]:
            raise RuntimeError("task1 ingress was not accepted")
        raw1, headers1 = _signed(payload1, "r5c27-task1")
        duplicate_status, duplicate = app.handle_webhook(headers=headers1, raw=raw1)
        if duplicate_status != 200 or not duplicate.get("duplicate"):
            raise RuntimeError("same-delivery replay was not suppressed")
        duplicate_suppressed += 1

        driver = _make_driver(
            runtime=app,
            root=root,
            classifier=classifier,
            worker=worker,
            world=world,
            runner=runner,
            periodic_seconds=60,
        )
        driver.start()
        _wait_for(
            lambda: driver.snapshot().last_disposition
            == HostedExecutionAdvanceDisposition.REMOTE_HANDOFF_ADVANCED.value,
            label="task1 remote handoff",
        )
        driver.stop()
        restart_count += 1

        app = _make_runtime(root, gate)
        driver = _make_driver(
            runtime=app,
            root=root,
            classifier=classifier,
            worker=worker,
            world=world,
            runner=runner,
            periodic_seconds=60,
        )
        driver.start()
        _wait_for(lambda: _completion_count(root) == 1, label="task1 cleanup")
        _wait_for(
            lambda: driver.snapshot().last_disposition
            == HostedExecutionAdvanceDisposition.IDLE.value,
            label="task1 reusable idle",
        )

        raw1b, headers1b = _signed(payload1, "r5c27-task1-semantic-replay")
        replay_status, replay = app.handle_webhook(headers=headers1b, raw=raw1b)
        if replay_status != 202 or not replay.get("semantic_replay_suppressed"):
            raise RuntimeError("semantic replay after completion was not suppressed")
        semantic_replay_suppressed += 1
        budget_after_task1 = budget_store.load()

        # Task 2: webhook drives to remote handoff; no second event is sent. The
        # bounded periodic wake must finish lifecycle/cleanup and return to IDLE.
        driver.stop()
        restart_count += 1
        app = _make_runtime(root, gate)
        driver = _make_driver(
            runtime=app,
            root=root,
            classifier=classifier,
            worker=worker,
            world=world,
            runner=runner,
            periodic_seconds=1,
        )
        driver.start()
        _wait_for(
            lambda: driver.snapshot().last_disposition
            == HostedExecutionAdvanceDisposition.IDLE.value,
            label="task2 pre-ingress idle",
        )
        (second, _payload2) = _ingest(app, world, 9102, 19102, "r5c27-task2")
        if second[0] != 202 or not second[1]["accepted"]:
            raise RuntimeError("task2 ingress was not accepted")
        _wait_for(
            lambda: driver.snapshot().last_disposition
            == HostedExecutionAdvanceDisposition.REMOTE_HANDOFF_ADVANCED.value,
            label="task2 remote handoff",
        )
        wake_at_handoff = driver.snapshot().wake_count
        _wait_for(lambda: _completion_count(root) == 2, timeout=5, label="task2 periodic cleanup")
        _wait_for(
            lambda: driver.snapshot().last_disposition
            == HostedExecutionAdvanceDisposition.IDLE.value,
            label="task2 periodic idle",
        )
        if driver.snapshot().wake_count <= wake_at_handoff:
            raise RuntimeError("task2 completion did not require a later periodic wake")
        budget_after_task2 = budget_store.load()
        time.sleep(1.15)
        budget_after_idle = budget_store.load()
        if budget_after_idle != budget_after_task2:
            raise RuntimeError("idle periodic wake changed provider budget")

        # Task 3: managed-PR truth fails once after remote handoff. The failed
        # reconciliation must not repeat provider/push/create work; periodic wake recovers.
        (third, _payload3) = _ingest(app, world, 9103, 19103, "r5c27-task3")
        if third[0] != 202 or not third[1]["accepted"]:
            raise RuntimeError("task3 ingress was not accepted")
        _wait_for(
            lambda: driver.snapshot().last_disposition
            == HostedExecutionAdvanceDisposition.REMOTE_HANDOFF_ADVANCED.value,
            label="task3 remote handoff",
        )
        remote_before_failure = dict(world.remote_effect_executes)
        provider_before_failure = (classifier.calls, worker.calls)
        world.fail_next_pr_view = True
        driver.wake()
        _wait_for(
            lambda: driver.snapshot().last_disposition
            == HostedExecutionAdvanceDisposition.MANAGED_PR_ADVANCED.value,
            label="task3 injected reconciliation block",
        )
        if world.remote_effect_executes != remote_before_failure:
            raise RuntimeError("managed read interruption repeated ordinary remote effect")
        if (classifier.calls, worker.calls) != provider_before_failure:
            raise RuntimeError("managed read interruption repeated provider spend")
        _wait_for(lambda: _completion_count(root) == 3, timeout=5, label="task3 recovery")
        _wait_for(
            lambda: driver.snapshot().last_disposition
            == HostedExecutionAdvanceDisposition.IDLE.value,
            label="task3 recovery idle",
        )
        driver.stop()

        day1_budget = budget_store.load()
        rollover = budget_store.ensure_day(
            day=ROLLOVER_DAY,
            legacy_state={**_legacy_state(), "budget_day": ROLLOVER_DAY},
        )
        if rollover.classifier_calls or rollover.luna_worker_calls or rollover.terra_worker_calls:
            raise RuntimeError("UTC-day rollover did not reset local fallback ledger")
        if rollover.seeded_from_legacy:
            raise RuntimeError("UTC-day rollover incorrectly re-seeded stale legacy counters")

        task_projection = _stable_task_projection(root)
        if len(task_projection) != 3 or any(
            task["status"] != HostedCompletionStatus.CLEANED.value
            for task in task_projection
        ):
            raise RuntimeError("not all sequential tasks reached reusable CLEANED completion")
        effects = _stable_effect_ids(root)
        if any(count != 1 for count in world.remote_effect_executes.values()):
            raise RuntimeError("ordinary remote effect executed more than once")

        state = app.store.load()
        stable = {
            "fixture_kind": "NONPRODUCTION_DISPOSABLE",
            "base_sha": base,
            "tasks": list(task_projection),
            "effect_ids": list(effects),
            "ordinary_effect_execute_counts": sorted(world.remote_effect_executes.values()),
            "lifecycle_execute_counts": dict(world.lifecycle_executes),
            "provider_calls": {
                "classifier": classifier.calls,
                "worker": worker.calls,
            },
            "budget_seed": {
                "classifier_calls": initial_budget.classifier_calls,
                "luna_worker_calls": initial_budget.luna_worker_calls,
                "terra_worker_calls": initial_budget.terra_worker_calls,
                "seeded_from_legacy": initial_budget.seeded_from_legacy,
            },
            "budget_after_task1": {
                "classifier_calls": budget_after_task1.classifier_calls,
                "luna_worker_calls": budget_after_task1.luna_worker_calls,
                "terra_worker_calls": budget_after_task1.terra_worker_calls,
            },
            "budget_day1_final": {
                "classifier_calls": day1_budget.classifier_calls,
                "luna_worker_calls": day1_budget.luna_worker_calls,
                "terra_worker_calls": day1_budget.terra_worker_calls,
            },
            "budget_rollover": {
                "day": rollover.day,
                "classifier_calls": rollover.classifier_calls,
                "luna_worker_calls": rollover.luna_worker_calls,
                "terra_worker_calls": rollover.terra_worker_calls,
                "seeded_from_legacy": rollover.seeded_from_legacy,
            },
            "duplicate_delivery_suppressed": duplicate_suppressed,
            "semantic_replay_suppressed": semantic_replay_suppressed,
            "restart_count": restart_count,
            "final_pending_event_count": len(state.inbox.pending_events),
            "final_active_plan": False,
            "production_systemd_touched": False,
            "production_caddy_touched": False,
            "production_writer_authority_touched": False,
            "dr70_consumed_or_passed": False,
        }
        operational = {
            "driver_final_running": driver.snapshot().running,
            "driver_final_disposition": driver.snapshot().last_disposition,
            "accepted_deliveries": state.accepted_deliveries,
            "completion_records": len(HostedCompletionStore.for_root(root).load().records),
        }
        return HostedSoakReport(stable=stable, operational=operational)


@dataclass(frozen=True)
class ProviderFailureReport:
    classifier_calls: int
    classifier_budget_delta: int
    worker_calls: int
    remote_effect_count: int
    durable_status: str

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())

    def as_dict(self) -> dict[str, object]:
        return {
            "classifier_calls": self.classifier_calls,
            "classifier_budget_delta": self.classifier_budget_delta,
            "worker_calls": self.worker_calls,
            "remote_effect_count": self.remote_effect_count,
            "durable_status": self.durable_status,
        }


def run_disposable_provider_failure(root: Path) -> ProviderFailureReport:
    """Prove a failed classifier becomes durable and cannot be spent twice by restart/wake."""

    root = Path(root).resolve()
    root.mkdir(parents=True, exist_ok=True)
    with _fixed_git_dates():
        base = _make_repo(root)
        _write_legacy(root)
        gate = _fixture_gate(base)
        world = DisposableRemoteWorld(main_sha=base)
        runner = DisposableRunner(root, world)
        classifier = DeterministicClassifier(fail=True)
        worker = DeterministicWorker()
        budget_store = HostedBudgetStore.for_root(root)
        before = budget_store.ensure_day(day=SOAK_DAY, legacy_state=_legacy_state())

        app = _make_runtime(root, gate)
        (accepted, _payload) = _ingest(app, world, 9199, 19999, "r5c27-provider-failure")
        if accepted[0] != 202:
            raise RuntimeError("provider-failure task ingress rejected")
        driver = _make_driver(
            runtime=app,
            root=root,
            classifier=classifier,
            worker=worker,
            world=world,
            runner=runner,
            periodic_seconds=60,
        )
        driver.start()
        _wait_for(
            lambda: (
                (plan := app.task_plan_store.load().active) is not None
                and plan.seed is not None
                and (record := ClassifierRunStore.for_root(root).load().get(
                    plan.seed.classifier_request.request_id
                )) is not None
                and record.status is ClassifierRunStatus.FAILED
            ),
            label="durable failed classifier",
        )
        driver.stop()
        after_first = budget_store.load()

        # Restart/wake on the same immutable request must not call the provider again.
        app = _make_runtime(root, gate)
        driver = _make_driver(
            runtime=app,
            root=root,
            classifier=classifier,
            worker=worker,
            world=world,
            runner=runner,
            periodic_seconds=60,
        )
        driver.start()
        time.sleep(0.2)
        driver.wake()
        time.sleep(0.2)
        driver.stop()
        after_restart = budget_store.load()
        plan = app.task_plan_store.load().active
        assert plan is not None and plan.seed is not None
        record = ClassifierRunStore.for_root(root).load().get(
            plan.seed.classifier_request.request_id
        )
        if record is None or record.status is not ClassifierRunStatus.FAILED:
            raise RuntimeError("failed classifier state did not remain durable")
        if classifier.calls != 1:
            raise RuntimeError("failed immutable classifier request was called twice")
        if after_restart != after_first:
            raise RuntimeError("restart/wake re-consumed local provider budget")

        return ProviderFailureReport(
            classifier_calls=classifier.calls,
            classifier_budget_delta=after_restart.classifier_calls - before.classifier_calls,
            worker_calls=worker.calls,
            remote_effect_count=sum(world.remote_effect_executes.values()),
            durable_status=record.status.value,
        )


def run_fresh_disposable_soak() -> tuple[HostedSoakReport, ProviderFailureReport]:
    with tempfile.TemporaryDirectory(prefix="skyforge-r5c27-soak-") as td:
        root = Path(td) / "success"
        root.mkdir()
        report = run_disposable_hosted_soak(root)
        failure_root = Path(td) / "provider-failure"
        failure_root.mkdir()
        failure = run_disposable_provider_failure(failure_root)
        return report, failure
