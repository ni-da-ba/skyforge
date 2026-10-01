"""Read-only hosted task preflight for Platform v2 R5C9."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
import subprocess
from typing import Any, Iterable, Sequence

from .identity import canonical_digest
from .task_authority import (
    GhTaskAuthorityHydrator,
    TaskAuthorityDisposition,
    TaskAuthorityHydrationResult,
    TaskAuthorityRemoteUnavailable,
)
from .task_event_composition import (
    TaskAuthorityEventRecord,
    TaskPipelineSeed,
    build_task_pipeline_seed,
)


class HostedTaskPreflightDisposition(str, Enum):
    READY_FOR_CLASSIFIER = "READY_FOR_CLASSIFIER"
    NOT_EXECUTABLE_V2 = "NOT_EXECUTABLE_V2"
    REJECTED = "REJECTED"


@dataclass(frozen=True)
class HostedTaskPreflightResult:
    disposition: HostedTaskPreflightDisposition
    reason: str
    event_id: str
    hydration_digest: str = ""
    seed: TaskPipelineSeed | None = None

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "disposition": self.disposition.value,
                "reason": self.reason,
                "event_id": self.event_id,
                "hydration_digest": self.hydration_digest,
                "seed_digest": self.seed.digest if self.seed else "",
            }
        )


class AcceptedMainRemoteUnavailable(RuntimeError):
    """Exact accepted-main GitHub truth could not be read safely."""


class AcceptedMainReadCommandValidator:
    def __init__(self, repo: str) -> None:
        repo = str(repo or "").strip()
        if repo.count("/") != 1 or any(not part for part in repo.split("/")):
            raise ValueError("repo must be owner/name")
        self.repo = repo

    def allowed(self) -> tuple[str, ...]:
        return (
            "gh",
            "api",
            f"repos/{self.repo}/commits/main",
            "--jq",
            ".sha",
        )

    def validate(self, args: Sequence[str]) -> tuple[str, ...]:
        command = tuple(str(value) for value in args)
        if command != self.allowed():
            raise ValueError(
                "command is outside the exact Platform-v2 accepted-main read allowlist"
            )
        return command


class GhAcceptedMainReader:
    def __init__(self, *, repo: str, runner=subprocess.run) -> None:
        self.validator = AcceptedMainReadCommandValidator(repo)
        self.runner = runner

    def read(self) -> str:
        command = self.validator.allowed()
        try:
            result = self.runner(
                list(command),
                check=True,
                text=True,
                capture_output=True,
                timeout=60,
            )
        except subprocess.SubprocessError as exc:
            raise AcceptedMainRemoteUnavailable(str(exc)) from exc
        sha = str(result.stdout or "").strip()
        if len(sha) != 40 or any(ch not in "0123456789abcdef" for ch in sha):
            raise AcceptedMainRemoteUnavailable(
                "accepted-main GitHub read returned malformed SHA"
            )
        return sha


def preflight_captured_task(
    *,
    record: TaskAuthorityEventRecord,
    trusted_actors: Iterable[str],
    repo: str,
    runner=subprocess.run,
) -> HostedTaskPreflightResult:
    """Freshly hydrate one captured task and build a classifier seed only.

    No classifier/provider call, worktree, effect, writer fence, or mutation is reachable.
    """

    hydrator = GhTaskAuthorityHydrator(
        reference=record.reference,
        trusted_actors=tuple(trusted_actors),
        runner=runner,
    )
    try:
        hydration = hydrator.hydrate()
    except TaskAuthorityRemoteUnavailable as exc:
        raise AcceptedMainRemoteUnavailable(
            f"task authority GitHub read unavailable: {exc}"
        ) from exc

    if hydration.disposition is TaskAuthorityDisposition.NOT_EXECUTABLE_V2:
        return HostedTaskPreflightResult(
            HostedTaskPreflightDisposition.NOT_EXECUTABLE_V2,
            hydration.reason,
            record.event_id,
            hydration_digest=hydration.digest,
        )
    if hydration.disposition is not TaskAuthorityDisposition.EXECUTABLE_V2:
        return HostedTaskPreflightResult(
            HostedTaskPreflightDisposition.REJECTED,
            hydration.reason,
            record.event_id,
            hydration_digest=hydration.digest,
        )

    current_main = GhAcceptedMainReader(repo=repo, runner=runner).read()
    seed = build_task_pipeline_seed(
        record=record,
        hydration=hydration,
        current_main=current_main,
    )
    return HostedTaskPreflightResult(
        HostedTaskPreflightDisposition.READY_FOR_CLASSIFIER,
        "fresh trusted task authority is ready for bounded classifier proposal",
        record.event_id,
        hydration_digest=hydration.digest,
        seed=seed,
    )
