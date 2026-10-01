"""Immutable controller identity and monotonic ownership generation contracts."""

from __future__ import annotations

from dataclasses import dataclass
import uuid

from .identity import canonical_digest


@dataclass(frozen=True)
class ControllerIdentity:
    """Stable identity for one controller process/deployment instance."""

    controller_id: str

    def __post_init__(self) -> None:
        normalized = str(self.controller_id).strip()
        if not normalized:
            raise ValueError("controller_id is required")
        object.__setattr__(self, "controller_id", normalized)

    @classmethod
    def new(cls) -> "ControllerIdentity":
        return cls(controller_id=str(uuid.uuid4()))


@dataclass(frozen=True)
class OwnershipToken:
    """Identity of the sole mutation owner for one monotonically increasing epoch."""

    controller_id: str
    generation: int

    def __post_init__(self) -> None:
        normalized = str(self.controller_id).strip()
        if not normalized:
            raise ValueError("controller_id is required")
        if isinstance(self.generation, bool) or not isinstance(self.generation, int):
            raise ValueError("generation must be an integer")
        if self.generation <= 0:
            raise ValueError("generation must be positive")
        object.__setattr__(self, "controller_id", normalized)

    @classmethod
    def first(cls, identity: ControllerIdentity) -> "OwnershipToken":
        return cls(controller_id=identity.controller_id, generation=1)

    def advance(self, identity: ControllerIdentity | None = None) -> "OwnershipToken":
        next_identity = identity or ControllerIdentity(self.controller_id)
        return OwnershipToken(
            controller_id=next_identity.controller_id,
            generation=self.generation + 1,
        )

    def as_dict(self) -> dict[str, object]:
        return {
            "controller_id": self.controller_id,
            "generation": self.generation,
        }

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())
