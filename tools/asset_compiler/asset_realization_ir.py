from __future__ import annotations

from dataclasses import dataclass, field
from typing import Callable

from model import Cell, VoxelModel


@dataclass(frozen=True)
class RealizationIntent:
    """Backend-neutral material/shape requirement at one occupied architectural cell.

    `families` describe semantic material character; `required_capabilities` describe the geometric or
    functional affordances the target realization must provide. Properties carry discrete geometry
    such as axis/facing/half/type but never a target resource identifier.
    """

    families: tuple[str, ...]
    required_capabilities: frozenset[str] = frozenset()
    properties: tuple[tuple[str, str], ...] = ()
    preferred_targets: tuple[str, ...] = ()

    def to_dict(self) -> dict:
        return {
            "families": list(self.families),
            "requiredCapabilities": sorted(self.required_capabilities),
            "properties": dict(self.properties),
            "preferredTargets": list(self.preferred_targets),
        }


@dataclass(frozen=True)
class RealizationIntentCell:
    role: str
    intent: RealizationIntent
    module: str | None = None

    def to_dict(self) -> dict:
        out = {"role": self.role, "intent": self.intent.to_dict()}
        if self.module:
            out["module"] = self.module
        return out


@dataclass
class RealizationIntentModel:
    cells: dict[tuple[int, int, int], RealizationIntentCell] = field(default_factory=dict)

    def set(self, x: int, y: int, z: int, role: str, intent: RealizationIntent, module: str | None = None) -> None:
        self.cells[(x, y, z)] = RealizationIntentCell(role=role, intent=intent, module=module)

    def to_dict(self) -> dict:
        return {
            "cells": [
                {"pos": list(pos), **cell.to_dict()}
                for pos, cell in sorted(self.cells.items())
            ]
        }


def project_voxel_intents(
    model: VoxelModel,
    provider: Callable[[Cell], RealizationIntent],
) -> RealizationIntentModel:
    """Erase target block identity at the architecture/realization boundary."""
    projected = RealizationIntentModel()
    for (x, y, z), cell in sorted(model.cells.items()):
        projected.set(x, y, z, cell.role, provider(cell), cell.module)
    return projected
