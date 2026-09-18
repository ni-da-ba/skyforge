"""Durable external/manual producer ownership service for Platform v2 R5C13."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
import json
from pathlib import Path
import re
import shlex
import subprocess
from typing import Any, Mapping, Sequence

from .decision import SourcePRState
from .external import (
    ClaimAdmissionDecision,
    ClaimAdmissionDisposition,
    ClaimRetentionDecision,
    ClaimRetentionDisposition,
    ControllerIssueOwner,
    ExternalClaimRequest,
    ExternalIssueState,
    ExternalProducerClaim,
    classify_claim_admission,
    classify_claim_retention,
)
from .identity import canonical_digest
from .state_store import JsonStateStoreAdapter


CLAIM_COMMAND = "/skyforge-claim-external"
RELEASE_COMMAND = "/skyforge-release-external"
EXTERNAL_CLAIMS_RELATIVE_PATH = Path(".skyforge-platform-v2") / "external-claims.json"
EXTERNAL_CLAIMS_BACKUP_RELATIVE_PATH = (
    Path(".skyforge-platform-v2") / "external-claims.json.bak"
)
_ALLOWED_LANES = {
    "implementation": "Implementation",
    "authorship": "Authorship",
    "content": "Content",
    "music": "Music",
    "presentation": "Presentation",
    "audit": "Audit",
}
_BRANCH_RE = re.compile(r"^[A-Za-z0-9._/-]+$")


def _positive_int(value: Any, label: str) -> int:
    if isinstance(value, bool):
        raise ValueError(f"{label} must be a positive integer")
    try:
        number = int(value)
    except (TypeError, ValueError) as exc:
        raise ValueError(f"{label} must be a positive integer") from exc
    if number <= 0:
        raise ValueError(f"{label} must be a positive integer")
    return number


def _repo(value: Any) -> str:
    text = str(value or "").strip()
    if text.count("/") != 1 or any(not part for part in text.split("/")):
        raise ValueError("repo must be owner/name")
    return text


def _first_nonempty_line(body: Any) -> str:
    if not isinstance(body, str):
        return ""
    return next((line.strip() for line in body.splitlines() if line.strip()), "")


class ExternalControlKind(str, Enum):
    CLAIM = "CLAIM"
    RELEASE = "RELEASE"


@dataclass(frozen=True)
class ExternalControl:
    kind: ExternalControlKind
    issue_number: int
    actor: str
    trusted_actor: bool
    lane: str | None = None
    branch: str | None = None
    pr_number: int | None = None
    source_comment_id: int | None = None

    def __post_init__(self) -> None:
        if not isinstance(self.kind, ExternalControlKind):
            raise ValueError("kind must be ExternalControlKind")
        _positive_int(self.issue_number, "issue_number")
        if not isinstance(self.actor, str) or not self.actor.strip():
            raise ValueError("actor is required")
        if not isinstance(self.trusted_actor, bool):
            raise ValueError("trusted_actor must be boolean")
        if self.lane is not None and self.lane not in set(_ALLOWED_LANES.values()):
            raise ValueError("lane is unsupported")
        if self.branch is not None and not _BRANCH_RE.fullmatch(self.branch):
            raise ValueError("branch contains unsupported characters")
        if self.pr_number is not None:
            _positive_int(self.pr_number, "pr_number")
        if self.source_comment_id is not None:
            _positive_int(self.source_comment_id, "source_comment_id")

    @property
    def request(self) -> ExternalClaimRequest:
        if self.kind is not ExternalControlKind.CLAIM:
            raise ValueError("release control has no claim request")
        return ExternalClaimRequest(
            issue_number=self.issue_number,
            actor=self.actor,
            trusted_actor=self.trusted_actor,
            lane=self.lane,
            branch=self.branch,
            pr_number=self.pr_number,
        )

    def as_dict(self) -> dict[str, Any]:
        return {
            "kind": self.kind.value,
            "issue_number": self.issue_number,
            "actor": self.actor,
            "trusted_actor": self.trusted_actor,
            "lane": self.lane,
            "branch": self.branch,
            "pr_number": self.pr_number,
            "source_comment_id": self.source_comment_id,
        }

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())


def parse_external_control(
    *,
    event: str,
    payload: Mapping[str, Any],
    trusted_actors: Sequence[str],
) -> ExternalControl | None:
    if str(event or "").strip().lower() != "issue_comment":
        return None
    if not isinstance(payload, Mapping):
        raise ValueError("payload must be an object")
    if str(payload.get("action") or "").lower() != "created":
        return None

    issue = payload.get("issue") or {}
    comment = payload.get("comment") or {}
    if not isinstance(issue, Mapping) or not isinstance(comment, Mapping):
        raise ValueError("issue/comment must be objects")

    line = _first_nonempty_line(comment.get("body"))
    if not line:
        return None
    try:
        tokens = shlex.split(line)
    except ValueError as exc:
        if line.lower().startswith((CLAIM_COMMAND, RELEASE_COMMAND)):
            raise ValueError(f"invalid external control syntax: {exc}") from exc
        return None
    if not tokens:
        return None

    command = tokens[0].lower()
    if command not in {CLAIM_COMMAND, RELEASE_COMMAND}:
        return None

    issue_number = _positive_int(issue.get("number"), "external control issue number")
    user = comment.get("user") or {}
    if not isinstance(user, Mapping):
        raise ValueError("comment user must be an object")
    actor = str(user.get("login") or "").strip()
    if not actor:
        raise ValueError("external control actor is required")
    trusted = {str(value).strip().lower() for value in trusted_actors if str(value).strip()}
    is_trusted = actor.lower() in trusted
    comment_id = comment.get("id")
    source_comment_id = (
        _positive_int(comment_id, "source comment id")
        if comment_id is not None
        else None
    )

    if command == RELEASE_COMMAND:
        if len(tokens) != 1:
            raise ValueError("release external control takes no options")
        return ExternalControl(
            ExternalControlKind.RELEASE,
            issue_number,
            actor,
            is_trusted,
            source_comment_id=source_comment_id,
        )

    options: dict[str, str] = {}
    for token in tokens[1:]:
        if "=" not in token:
            raise ValueError(
                "claim options must use key=value syntax (supported: pr, lane, branch)"
            )
        key, value = token.split("=", 1)
        key = key.strip().lower()
        value = value.strip()
        if key not in {"pr", "lane", "branch"}:
            raise ValueError(f"unsupported claim option: {key}")
        if key in options:
            raise ValueError(f"duplicate claim option: {key}")
        if not value:
            raise ValueError(f"claim option {key} may not be empty")
        options[key] = value

    lane = None
    if "lane" in options:
        lane = _ALLOWED_LANES.get(options["lane"].lower())
        if lane is None:
            raise ValueError(f"unsupported external-producer lane: {options['lane']}")

    branch = options.get("branch")
    if branch is not None and not _BRANCH_RE.fullmatch(branch):
        raise ValueError("claim branch contains unsupported characters")

    pr_number = (
        _positive_int(options["pr"], "claim pr")
        if "pr" in options
        else None
    )

    return ExternalControl(
        ExternalControlKind.CLAIM,
        issue_number,
        actor,
        is_trusted,
        lane=lane,
        branch=branch,
        pr_number=pr_number,
        source_comment_id=source_comment_id,
    )


@dataclass(frozen=True)
class ExternalClaimLedger:
    claims: tuple[ExternalProducerClaim, ...] = ()

    def __post_init__(self) -> None:
        issues = [claim.issue_number for claim in self.claims]
        if len(set(issues)) != len(issues):
            raise ValueError("external claim ledger contains duplicate issue ownership")

    @classmethod
    def from_mapping(cls, raw: Any) -> "ExternalClaimLedger":
        if raw is None or raw == {}:
            return cls()
        if not isinstance(raw, Mapping) or raw.get("schema_version") != 1:
            raise ValueError("invalid external claim ledger")
        values = raw.get("claims")
        if not isinstance(values, list):
            raise ValueError("external claim ledger claims must be a list")
        return cls(
            tuple(
                sorted(
                    (
                        ExternalProducerClaim.from_legacy_mapping(value)
                        for value in values
                    ),
                    key=lambda claim: claim.issue_number,
                )
            )
        )

    @classmethod
    def from_legacy_projection(cls, projection: Mapping[str, Any]) -> "ExternalClaimLedger":
        if not isinstance(projection, Mapping):
            raise ValueError("legacy projection must be an object")
        raw = projection.get("external_producer_claims") or {}
        if not isinstance(raw, Mapping):
            raise ValueError("legacy external_producer_claims must be an object")
        claims: list[ExternalProducerClaim] = []
        for key, value in raw.items():
            if not isinstance(value, Mapping):
                raise ValueError("legacy external claim must be an object")
            semantic = dict(value)
            if semantic.get("issue_number") is None:
                semantic["issue_number"] = _positive_int(key, "legacy claim issue key")
            if str(semantic.get("state") or "active") != "active":
                continue
            claims.append(ExternalProducerClaim.from_legacy_mapping(semantic))
        return cls(tuple(sorted(claims, key=lambda claim: claim.issue_number)))

    def as_dict(self) -> dict[str, Any]:
        return {
            "schema_version": 1,
            "claims": [claim.as_dict() for claim in self.claims],
        }

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())

    def get(self, issue_number: int) -> ExternalProducerClaim | None:
        issue = _positive_int(issue_number, "issue_number")
        return next((claim for claim in self.claims if claim.issue_number == issue), None)

    def put(self, claim: ExternalProducerClaim) -> "ExternalClaimLedger":
        if not isinstance(claim, ExternalProducerClaim):
            raise ValueError("claim must be ExternalProducerClaim")
        values = [item for item in self.claims if item.issue_number != claim.issue_number]
        values.append(claim)
        return ExternalClaimLedger(tuple(sorted(values, key=lambda item: item.issue_number)))

    def release(self, issue_number: int) -> tuple["ExternalClaimLedger", bool]:
        issue = _positive_int(issue_number, "issue_number")
        values = tuple(claim for claim in self.claims if claim.issue_number != issue)
        return ExternalClaimLedger(values), len(values) != len(self.claims)


@dataclass(frozen=True)
class ExternalClaimStore:
    adapter: JsonStateStoreAdapter

    @classmethod
    def for_root(cls, root: Path) -> "ExternalClaimStore":
        root = Path(root)
        return cls(
            JsonStateStoreAdapter(
                path=root / EXTERNAL_CLAIMS_RELATIVE_PATH,
                backup_path=root / EXTERNAL_CLAIMS_BACKUP_RELATIVE_PATH,
            )
        )

    def load(self) -> ExternalClaimLedger:
        return ExternalClaimLedger.from_mapping(self.adapter.load().as_dict())

    def save(self, ledger: ExternalClaimLedger) -> ExternalClaimLedger:
        self.adapter.save(ledger.as_dict())
        return ledger


@dataclass(frozen=True)
class ExternalControlResult:
    accepted: bool
    reason: str
    ledger: ExternalClaimLedger
    admission: ClaimAdmissionDecision | None = None

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "accepted": self.accepted,
                "reason": self.reason,
                "ledger_digest": self.ledger.digest,
                "admission_digest": self.admission.digest if self.admission else "",
            }
        )


def apply_external_control(
    *,
    ledger: ExternalClaimLedger,
    control: ExternalControl,
    controller_owner: ControllerIssueOwner,
) -> ExternalControlResult:
    if control.kind is ExternalControlKind.RELEASE:
        if not control.trusted_actor:
            return ExternalControlResult(
                False,
                "external producer release actor is not trusted",
                ledger,
            )
        updated, existed = ledger.release(control.issue_number)
        return ExternalControlResult(
            existed,
            (
                "external producer claim explicitly released"
                if existed
                else "no external producer claim existed for issue"
            ),
            updated,
        )

    admission = classify_claim_admission(control.request, controller_owner)
    if admission.disposition is not ClaimAdmissionDisposition.ACCEPT:
        return ExternalControlResult(False, admission.reason, ledger, admission)

    prior = ledger.get(control.issue_number)
    claim = ExternalProducerClaim(
        issue_number=control.issue_number,
        claimed_by=control.actor,
        lane=control.lane if control.lane is not None else (prior.lane if prior else None),
        branch=(
            control.branch if control.branch is not None else (prior.branch if prior else None)
        ),
        pr_number=(
            control.pr_number
            if control.pr_number is not None
            else (prior.pr_number if prior else None)
        ),
    )
    return ExternalControlResult(
        True,
        "trusted external producer claim recorded",
        ledger.put(claim),
        admission,
    )


class ExternalClaimRemoteUnavailable(RuntimeError):
    """Exact remote claim-retention truth could not be established."""


class ExternalClaimReadCommandValidator:
    def __init__(self, *, repo: str, claim: ExternalProducerClaim) -> None:
        self.repo = _repo(repo)
        self.claim = claim

    def allowed(self) -> set[tuple[str, ...]]:
        if self.claim.pr_number is not None:
            return {
                (
                    "gh",
                    "pr",
                    "view",
                    str(self.claim.pr_number),
                    "--repo",
                    self.repo,
                    "--json",
                    "state,mergedAt",
                    "--jq=.",
                )
            }
        return {
            (
                "gh",
                "api",
                f"repos/{self.repo}/issues/{self.claim.issue_number}",
            )
        }

    def validate(self, args: Sequence[str]) -> tuple[str, ...]:
        command = tuple(str(value) for value in args)
        if command not in self.allowed():
            raise ValueError(
                "command is outside the exact Platform-v2 external-claim read allowlist"
            )
        return command


class GhExternalClaimObserver:
    def __init__(
        self,
        *,
        root: Path,
        repo: str,
        claim: ExternalProducerClaim,
        runner=subprocess.run,
    ) -> None:
        self.root = Path(root).resolve()
        self.repo = _repo(repo)
        self.claim = claim
        self.validator = ExternalClaimReadCommandValidator(repo=self.repo, claim=claim)
        self.runner = runner

    def _run(self, args: Sequence[str]) -> Mapping[str, Any]:
        command = self.validator.validate(args)
        try:
            result = self.runner(
                list(command),
                cwd=self.root,
                check=True,
                text=True,
                capture_output=True,
                timeout=60,
            )
        except subprocess.SubprocessError as exc:
            raise ExternalClaimRemoteUnavailable(str(exc)) from exc
        try:
            value = json.loads(result.stdout or "{}")
        except json.JSONDecodeError as exc:
            raise ExternalClaimRemoteUnavailable(
                "external claim read returned malformed JSON"
            ) from exc
        if not isinstance(value, Mapping):
            raise ExternalClaimRemoteUnavailable(
                "external claim read returned malformed shape"
            )
        return value

    def observe(self) -> ClaimRetentionDecision:
        if self.claim.pr_number is not None:
            try:
                value = self._run(
                    [
                        "gh",
                        "pr",
                        "view",
                        str(self.claim.pr_number),
                        "--repo",
                        self.repo,
                        "--json",
                        "state,mergedAt",
                        "--jq=.",
                    ]
                )
            except ExternalClaimRemoteUnavailable:
                return classify_claim_retention(
                    self.claim,
                    SourcePRState.UNKNOWN,
                    remote_pr_observation_available=False,
                )
            state = str(value.get("state") or "").upper()
            merged = bool(value.get("mergedAt"))
            if merged:
                pr_state = SourcePRState.MERGED
            elif state == "OPEN":
                pr_state = SourcePRState.OPEN
            elif state == "CLOSED":
                pr_state = SourcePRState.CLOSED
            else:
                return classify_claim_retention(
                    self.claim,
                    SourcePRState.UNKNOWN,
                    remote_pr_observation_available=False,
                )
            return classify_claim_retention(self.claim, pr_state)

        try:
            value = self._run(
                [
                    "gh",
                    "api",
                    f"repos/{self.repo}/issues/{self.claim.issue_number}",
                ]
            )
        except ExternalClaimRemoteUnavailable:
            return classify_claim_retention(
                self.claim,
                remote_issue_state=ExternalIssueState.UNKNOWN,
                remote_issue_observation_available=False,
            )
        state = str(value.get("state") or "").lower()
        if state == "open":
            issue_state = ExternalIssueState.OPEN
        elif state == "closed":
            issue_state = ExternalIssueState.CLOSED
        else:
            return classify_claim_retention(
                self.claim,
                remote_issue_state=ExternalIssueState.UNKNOWN,
                remote_issue_observation_available=False,
            )
        return classify_claim_retention(
            self.claim,
            remote_issue_state=issue_state,
        )


@dataclass(frozen=True)
class ExternalClaimRefreshResult:
    ledger: ExternalClaimLedger
    decisions: tuple[ClaimRetentionDecision, ...]

    @property
    def retired_issue_numbers(self) -> tuple[int, ...]:
        retired_digests = {
            decision.claim_digest
            for decision in self.decisions
            if decision.disposition is ClaimRetentionDisposition.RETIRE
        }
        return tuple(
            claim.issue_number
            for claim in []  # retained for API shape; callers use decisions + before ledger
            if claim.digest in retired_digests
        )


def refresh_external_claims(
    *,
    ledger: ExternalClaimLedger,
    root: Path,
    repo: str,
    runner=subprocess.run,
) -> ExternalClaimRefreshResult:
    current = ledger
    decisions: list[ClaimRetentionDecision] = []
    for claim in ledger.claims:
        decision = GhExternalClaimObserver(
            root=root,
            repo=repo,
            claim=claim,
            runner=runner,
        ).observe()
        decisions.append(decision)
        if decision.disposition is ClaimRetentionDisposition.RETIRE:
            current, _ = current.release(claim.issue_number)
    return ExternalClaimRefreshResult(current, tuple(decisions))
