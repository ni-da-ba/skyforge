from __future__ import annotations

from dataclasses import dataclass, field
from typing import Iterable

RGBA = tuple[int, int, int, int]
Texel = tuple[str, int, int]


class TextureSpecError(ValueError):
    pass


@dataclass(frozen=True)
class Region:
    region_id: str
    width: int
    height: int
    atlas_x: int
    atlas_y: int

    def __post_init__(self) -> None:
        if self.width < 1 or self.height < 1:
            raise TextureSpecError(f"region {self.region_id} must have positive dimensions")
        if self.atlas_x < 0 or self.atlas_y < 0:
            raise TextureSpecError(f"region {self.region_id} atlas origin must be nonnegative")

    def texels(self) -> Iterable[Texel]:
        for y in range(self.height):
            for x in range(self.width):
                yield (self.region_id, x, y)


@dataclass(frozen=True)
class SourcePatch:
    width: int
    height: int
    pixels: tuple[RGBA, ...]
    importance: tuple[float, ...]

    def __post_init__(self) -> None:
        expected = self.width * self.height
        if self.width < 1 or self.height < 1:
            raise TextureSpecError("source patch dimensions must be positive")
        if len(self.pixels) != expected or len(self.importance) != expected:
            raise TextureSpecError("source patch pixel/importance length mismatch")

    def pixel(self, x: int, y: int) -> RGBA:
        return self.pixels[y * self.width + x]

    def weight(self, x: int, y: int) -> float:
        return self.importance[y * self.width + x]


@dataclass(frozen=True)
class Seam:
    seam_id: str
    a: tuple[Texel, ...]
    b: tuple[Texel, ...]
    hard: bool = True
    weight: float = 3.0

    def __post_init__(self) -> None:
        if len(self.a) != len(self.b):
            raise TextureSpecError(f"seam {self.seam_id} has mismatched edge lengths")
        if not self.a:
            raise TextureSpecError(f"seam {self.seam_id} may not be empty")
        if self.weight < 0:
            raise TextureSpecError(f"seam {self.seam_id} weight must be nonnegative")


@dataclass(frozen=True)
class TextureProblem:
    asset_id: str
    atlas_width: int
    atlas_height: int
    regions: tuple[Region, ...]
    sources: dict[str, SourcePatch]
    seams: tuple[Seam, ...]
    max_colors: int
    locked_colors: tuple[RGBA, ...] = ()
    smoothness: float = 1.0
    contrast_beta: float = 0.015
    metadata: dict = field(default_factory=dict)

    def __post_init__(self) -> None:
        if self.atlas_width < 1 or self.atlas_height < 1:
            raise TextureSpecError("atlas dimensions must be positive")
        if self.max_colors < 1:
            raise TextureSpecError("max_colors must be positive")
        ids = [r.region_id for r in self.regions]
        if len(set(ids)) != len(ids):
            raise TextureSpecError("region ids must be unique")
        for region in self.regions:
            if region.region_id not in self.sources:
                raise TextureSpecError(f"missing source patch for {region.region_id}")
            if region.atlas_x + region.width > self.atlas_width or region.atlas_y + region.height > self.atlas_height:
                raise TextureSpecError(f"region {region.region_id} exceeds atlas bounds")


@dataclass
class CompiledTexture:
    summary: dict
    atlas: list[list[RGBA]]
    region_pixels: dict[str, list[list[RGBA]]]
