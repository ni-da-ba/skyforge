from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any


@dataclass(frozen=True)
class BlockState:
    name: str
    properties: tuple[tuple[str, str], ...] = ()

    @classmethod
    def of(cls, name: str, **properties: Any) -> "BlockState":
        props = tuple(sorted((str(k), str(v).lower() if isinstance(v, bool) else str(v)) for k, v in properties.items()))
        return cls(name, props)

    def to_dict(self) -> dict[str, Any]:
        out: dict[str, Any] = {"name": self.name}
        if self.properties:
            out["properties"] = dict(self.properties)
        return out

    def canonical(self) -> str:
        if not self.properties:
            return self.name
        return f"{self.name}[" + ",".join(f"{k}={v}" for k, v in self.properties) + "]"


@dataclass(frozen=True)
class Cell:
    role: str
    state: BlockState
    module: str | None = None

    def to_dict(self) -> dict[str, Any]:
        out = {"role": self.role, "blockState": self.state.to_dict()}
        if self.module:
            out["module"] = self.module
        return out


@dataclass
class VoxelModel:
    cells: dict[tuple[int, int, int], Cell] = field(default_factory=dict)

    def set(self, x: int, y: int, z: int, role: str, state: BlockState, module: str | None = None) -> None:
        self.cells[(x, y, z)] = Cell(role, state, module)

    def clear(self, x: int, y: int, z: int) -> None:
        self.cells.pop((x, y, z), None)


@dataclass(frozen=True)
class Volume:
    minimum: tuple[int, int, int]
    maximum: tuple[int, int, int]
    role: str

    def to_dict(self) -> dict[str, Any]:
        return {"min": list(self.minimum), "max": list(self.maximum), "role": self.role}


@dataclass
class CompiledAsset:
    summary: dict[str, Any]
    model: VoxelModel


class SpecError(ValueError):
    pass
