from __future__ import annotations

from dataclasses import dataclass
from typing import Any


class SpecError(ValueError):
    pass


@dataclass(frozen=True)
class WingPlanform:
    span_m: float
    root_chord_m: float
    tip_chord_m: float
    leading_edge_x_m: float


@dataclass(frozen=True)
class TailPlanform:
    span_or_height_m: float
    root_chord_m: float
    tip_chord_m: float
    leading_edge_x_m: float


@dataclass(frozen=True)
class MassItem:
    name: str
    mass_kg: float
    station_x_m: float
    source: str

    def to_dict(self) -> dict[str, Any]:
        return {
            "name": self.name,
            "massKg": self.mass_kg,
            "stationXM": self.station_x_m,
            "source": self.source,
        }
