"""Typed immutable review-artifact manifests and verified retrieval."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import subprocess
from typing import Any, Mapping

from .identity import canonical_digest

MANIFEST_RELATIVE_PATH = Path("docs/agent-state/REVIEW_ARTIFACTS.json")
_SHA40_RE = re.compile(r"^[0-9a-f]{40}$")
_SHA256_RE = re.compile(r"^[0-9a-f]{64}$")
_ARTIFACT_ID_RE = re.compile(r"^[A-Za-z0-9_.:-]+$")


class ReviewArtifactKind(str, Enum):
    FILE = "FILE"
    INTERACTIVE_SPECIMEN = "INTERACTIVE_SPECIMEN"


class ReviewArtifactError(RuntimeError):
    pass


def _required(value: Any, label: str) -> str:
    text = str(value or "").strip()
    if not text:
        raise ValueError(f"{label} is required")
    return text


def _sha40(value: Any, label: str) -> str:
    text = _required(value, label).lower()
    if not _SHA40_RE.fullmatch(text):
        raise ValueError(f"{label} must be a lowercase 40-character Git SHA")
    return text


def _sha256(value: Any, label: str) -> str:
    text = _required(value, label).lower()
    if not _SHA256_RE.fullmatch(text):
        raise ValueError(f"{label} must be lowercase SHA-256 hex")
    return text


def _positive_int(value: Any, label: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value < 0:
        raise ValueError(f"{label} must be a nonnegative integer")
    return value


def _repository_path(value: Any) -> str:
    text = _required(value, "repository_path").replace("\\", "/")
    path = PurePosixPath(text)
    if path.is_absolute() or text.startswith("/") or any(part in {"", ".", ".."} for part in path.parts):
        raise ValueError("repository_path must be a normalized repository-relative path")
    normalized = str(path)
    if normalized != text:
        raise ValueError("repository_path must already be normalized")
    return normalized


def _json_mapping(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, Mapping):
        raise ValueError(f"{label} must be an object")
    try:
        encoded = json.dumps(
            dict(value),
            sort_keys=True,
            separators=(",", ":"),
            ensure_ascii=False,
            allow_nan=False,
        )
    except (TypeError, ValueError) as exc:
        raise ValueError(f"{label} must be JSON serializable") from exc
    decoded = json.loads(encoded)
    if not isinstance(decoded, dict):
        raise ValueError(f"{label} must canonicalize to an object")
    return decoded


def _string_tuple(value: Any, label: str) -> tuple[str, ...]:
    if value is None:
        return ()
    if not isinstance(value, list):
        raise ValueError(f"{label} must be a list")
    result = tuple(_required(item, label) for item in value)
    if len(set(result)) != len(result):
        raise ValueError(f"{label} must not contain duplicates")
    return result


@dataclass(frozen=True)
class FileArtifact:
    repository_path: str
    media_type: str
    byte_size: int
    sha256: str

    def __post_init__(self) -> None:
        object.__setattr__(self, "repository_path", _repository_path(self.repository_path))
        object.__setattr__(self, "media_type", _required(self.media_type, "media_type"))
        object.__setattr__(self, "byte_size", _positive_int(self.byte_size, "byte_size"))
        object.__setattr__(self, "sha256", _sha256(self.sha256, "sha256"))

    def as_dict(self) -> dict[str, object]:
        return {
            "source": "REPOSITORY_BLOB",
            "repository_path": self.repository_path,
            "media_type": self.media_type,
            "byte_size": self.byte_size,
            "sha256": self.sha256,
        }


@dataclass(frozen=True)
class InteractiveSpecimen:
    specimen_kind: str
    parameters: Mapping[str, Any]
    preparation_entry_points: tuple[str, ...]
    launch_entry_point: str
    review_actions: tuple[str, ...] = ()
    associated_artifact_ids: tuple[str, ...] = ()

    def __post_init__(self) -> None:
        object.__setattr__(self, "specimen_kind", _required(self.specimen_kind, "specimen_kind"))
        object.__setattr__(self, "parameters", _json_mapping(self.parameters, "parameters"))
        if not self.preparation_entry_points:
            raise ValueError("interactive specimen requires at least one preparation entry point")
        object.__setattr__(
            self,
            "preparation_entry_points",
            tuple(_required(value, "preparation_entry_points") for value in self.preparation_entry_points),
        )
        object.__setattr__(
            self,
            "launch_entry_point",
            _required(self.launch_entry_point, "launch_entry_point"),
        )
        object.__setattr__(
            self,
            "review_actions",
            tuple(_required(value, "review_actions") for value in self.review_actions),
        )
        object.__setattr__(
            self,
            "associated_artifact_ids",
            tuple(_required(value, "associated_artifact_ids") for value in self.associated_artifact_ids),
        )

    def as_dict(self) -> dict[str, object]:
        return {
            "specimen_kind": self.specimen_kind,
            "parameters": dict(self.parameters),
            "preparation_entry_points": list(self.preparation_entry_points),
            "launch_entry_point": self.launch_entry_point,
            "review_actions": list(self.review_actions),
            "associated_artifact_ids": list(self.associated_artifact_ids),
        }


@dataclass(frozen=True)
class ReviewArtifactRecord:
    artifact_id: str
    kind: ReviewArtifactKind
    source_sha: str
    title: str
    description: str
    file: FileArtifact | None = None
    interactive: InteractiveSpecimen | None = None

    def __post_init__(self) -> None:
        artifact_id = _required(self.artifact_id, "artifact_id")
        if not _ARTIFACT_ID_RE.fullmatch(artifact_id):
            raise ValueError("artifact_id contains unsupported URL/path characters")
        object.__setattr__(self, "artifact_id", artifact_id)
        if not isinstance(self.kind, ReviewArtifactKind):
            object.__setattr__(self, "kind", ReviewArtifactKind(str(self.kind)))
        object.__setattr__(self, "source_sha", _sha40(self.source_sha, "source_sha"))
        object.__setattr__(self, "title", _required(self.title, "title"))
        object.__setattr__(self, "description", _required(self.description, "description"))
        if self.kind is ReviewArtifactKind.FILE:
            if self.file is None or self.interactive is not None:
                raise ValueError("FILE artifact requires file metadata only")
        elif self.kind is ReviewArtifactKind.INTERACTIVE_SPECIMEN:
            if self.interactive is None or self.file is not None:
                raise ValueError("INTERACTIVE_SPECIMEN requires interactive metadata only")

    @property
    def digest(self) -> str:
        return canonical_digest(self.identity_payload())

    def identity_payload(self) -> dict[str, object]:
        return {
            "artifact_id": self.artifact_id,
            "kind": self.kind.value,
            "source_sha": self.source_sha,
            "title": self.title,
            "description": self.description,
            "file": self.file.as_dict() if self.file is not None else None,
            "interactive": self.interactive.as_dict() if self.interactive is not None else None,
        }

    def as_dict(self) -> dict[str, object]:
        return {**self.identity_payload(), "artifact_digest": self.digest}

    @classmethod
    def from_mapping(cls, raw: Any) -> "ReviewArtifactRecord":
        if not isinstance(raw, Mapping):
            raise ValueError("artifact record must be an object")
        kind = ReviewArtifactKind(str(raw.get("kind") or ""))
        file_raw = raw.get("file")
        interactive_raw = raw.get("interactive")
        file = None
        interactive = None
        if file_raw is not None:
            if not isinstance(file_raw, Mapping):
                raise ValueError("file metadata must be an object")
            if str(file_raw.get("source") or "") != "REPOSITORY_BLOB":
                raise ValueError("only REPOSITORY_BLOB file artifacts are supported")
            file = FileArtifact(
                repository_path=file_raw.get("repository_path"),
                media_type=file_raw.get("media_type"),
                byte_size=file_raw.get("byte_size"),
                sha256=file_raw.get("sha256"),
            )
        if interactive_raw is not None:
            if not isinstance(interactive_raw, Mapping):
                raise ValueError("interactive metadata must be an object")
            interactive = InteractiveSpecimen(
                specimen_kind=interactive_raw.get("specimen_kind"),
                parameters=interactive_raw.get("parameters") or {},
                preparation_entry_points=_string_tuple(
                    interactive_raw.get("preparation_entry_points"),
                    "preparation_entry_points",
                ),
                launch_entry_point=interactive_raw.get("launch_entry_point"),
                review_actions=_string_tuple(
                    interactive_raw.get("review_actions"),
                    "review_actions",
                ),
                associated_artifact_ids=_string_tuple(
                    interactive_raw.get("associated_artifact_ids"),
                    "associated_artifact_ids",
                ),
            )
        record = cls(
            artifact_id=raw.get("artifact_id"),
            kind=kind,
            source_sha=raw.get("source_sha"),
            title=raw.get("title"),
            description=raw.get("description"),
            file=file,
            interactive=interactive,
        )
        supplied = raw.get("artifact_digest")
        if supplied is not None and str(supplied) != record.digest:
            raise ValueError("artifact digest mismatch")
        return record


@dataclass(frozen=True)
class ReviewArtifactCatalog:
    records: tuple[ReviewArtifactRecord, ...]

    def __post_init__(self) -> None:
        ids = [record.artifact_id for record in self.records]
        if len(set(ids)) != len(ids):
            raise ValueError("duplicate artifact_id")
        known = set(ids)
        for record in self.records:
            if record.interactive is None:
                continue
            for associated in record.interactive.associated_artifact_ids:
                if not _ARTIFACT_ID_RE.fullmatch(associated):
                    raise ValueError("associated artifact id contains unsupported characters")
                if associated == record.artifact_id:
                    raise ValueError("artifact cannot associate itself")
                if associated not in known:
                    raise ValueError(
                        f"associated artifact is not registered: {associated}"
                    )

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())

    def as_dict(self) -> dict[str, object]:
        return {
            "schema_version": 1,
            "artifacts": [record.as_dict() for record in self.records],
        }

    def get(self, artifact_id: str) -> ReviewArtifactRecord | None:
        value = _required(artifact_id, "artifact_id")
        matches = [record for record in self.records if record.artifact_id == value]
        if len(matches) > 1:
            raise ValueError("duplicate artifact identity")
        return matches[0] if matches else None

    @classmethod
    def from_mapping(cls, raw: Any) -> "ReviewArtifactCatalog":
        if not isinstance(raw, Mapping) or raw.get("schema_version") != 1:
            raise ValueError("invalid review artifact catalog")
        values = raw.get("artifacts")
        if not isinstance(values, list):
            raise ValueError("review artifact catalog artifacts must be a list")
        return cls(tuple(ReviewArtifactRecord.from_mapping(value) for value in values))

    @classmethod
    def for_root(
        cls,
        root: Path,
        *,
        manifest_relative_path: Path = MANIFEST_RELATIVE_PATH,
    ) -> "ReviewArtifactCatalog":
        path = Path(root).resolve() / manifest_relative_path
        try:
            raw = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            raise ReviewArtifactError(f"review artifact manifest is unreadable: {path}") from exc
        try:
            return cls.from_mapping(raw)
        except ValueError as exc:
            raise ReviewArtifactError(f"review artifact manifest is invalid: {exc}") from exc


def _git(
    root: Path,
    *args: str,
    text: bool = True,
) -> subprocess.CompletedProcess:
    return subprocess.run(
        ["git", "-C", str(Path(root).resolve()), *args],
        capture_output=True,
        text=text,
        check=False,
    )


def validate_artifact_source(root: Path, record: ReviewArtifactRecord) -> None:
    result = _git(root, "cat-file", "-e", f"{record.source_sha}^{{commit}}")
    if result.returncode != 0:
        raise ReviewArtifactError(
            f"artifact source commit is unavailable for {record.artifact_id}"
        )


def retrieve_file_artifact(root: Path, record: ReviewArtifactRecord) -> bytes:
    if record.kind is not ReviewArtifactKind.FILE or record.file is None:
        raise ReviewArtifactError("artifact does not provide retrievable file bytes")
    validate_artifact_source(root, record)
    result = _git(
        root,
        "show",
        f"{record.source_sha}:{record.file.repository_path}",
        text=False,
    )
    if result.returncode != 0:
        raise ReviewArtifactError("artifact repository blob is unavailable")
    payload = bytes(result.stdout)
    if len(payload) != record.file.byte_size:
        raise ReviewArtifactError("artifact byte size does not match manifest")
    digest = hashlib.sha256(payload).hexdigest()
    if digest != record.file.sha256:
        raise ReviewArtifactError("artifact SHA-256 does not match manifest")
    return payload
