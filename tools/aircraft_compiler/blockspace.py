from __future__ import annotations

import hashlib
import json
import math
from collections import deque
from dataclasses import dataclass
from typing import Any, Iterable


class BlockspaceError(ValueError):
    pass


@dataclass(frozen=True, order=True)
class Cell:
    x: int
    y: int
    z: int
    role: str

    def to_dict(self) -> dict[str, Any]:
        return {"x": self.x, "y": self.y, "z": self.z, "role": self.role}


def _q_nonnegative(value_m: float, scale: float) -> int:
    if value_m < 0.0:
        raise BlockspaceError("expected non-negative coordinate")
    return int(math.floor(value_m * scale + 0.5))


def _meter(index: int, scale: float) -> float:
    return index / scale


def _indices_between(lo_m: float, hi_m: float, scale: float) -> range:
    if hi_m < lo_m:
        lo_m, hi_m = hi_m, lo_m
    lo = math.ceil(lo_m * scale - 1e-12)
    hi = math.floor(hi_m * scale + 1e-12)
    return range(int(lo), int(hi) + 1)


def _surface_chord(root: float, tip: float, eta: float) -> float:
    eta_clamped = min(1.0, max(0.0, eta))
    return root + (tip - root) * eta_clamped


def _horizontal_surface_cells(
    *,
    span_m: float,
    root_chord_m: float,
    tip_chord_m: float,
    leading_edge_x_m: float,
    y_m: float,
    scale: float,
    role: str,
) -> set[Cell]:
    cells: set[Cell] = set()
    half_span = span_m / 2.0
    y = _q_nonnegative(y_m, scale)
    for z in _indices_between(-half_span, half_span, scale):
        z_m = abs(_meter(z, scale))
        eta = 0.0 if half_span == 0.0 else z_m / half_span
        if eta > 1.0 + 1e-12:
            continue
        chord = _surface_chord(root_chord_m, tip_chord_m, eta)
        for x in _indices_between(leading_edge_x_m, leading_edge_x_m + chord, scale):
            cells.add(Cell(x, y, z, role))
    return cells


def _vertical_surface_cells(
    *,
    height_m: float,
    root_chord_m: float,
    tip_chord_m: float,
    leading_edge_x_m: float,
    root_y_m: float,
    scale: float,
    role: str,
) -> set[Cell]:
    cells: set[Cell] = set()
    for y in _indices_between(root_y_m, root_y_m + height_m, scale):
        y_m = _meter(y, scale)
        eta = 0.0 if height_m == 0.0 else (y_m - root_y_m) / height_m
        if not -1e-12 <= eta <= 1.0 + 1e-12:
            continue
        chord = _surface_chord(root_chord_m, tip_chord_m, eta)
        for x in _indices_between(leading_edge_x_m, leading_edge_x_m + chord, scale):
            cells.add(Cell(x, y, 0, role))
    return cells


def _line_cells(a: tuple[int, int, int], b: tuple[int, int, int], role: str) -> set[Cell]:
    """Return an axis-aligned connector; reject hidden diagonal approximations."""
    diffs = [b[i] - a[i] for i in range(3)]
    nonzero = [i for i, delta in enumerate(diffs) if delta]
    if len(nonzero) > 1:
        raise BlockspaceError(f"connector must be axis-aligned, got {a}->{b}")
    if not nonzero:
        return {Cell(*a, role)}
    axis = nonzero[0]
    step = 1 if diffs[axis] > 0 else -1
    out: set[Cell] = set()
    p = list(a)
    while True:
        out.add(Cell(p[0], p[1], p[2], role))
        if tuple(p) == b:
            break
        p[axis] += step
    return out


def _occupied_coords(cells: Iterable[Cell]) -> set[tuple[int, int, int]]:
    return {(cell.x, cell.y, cell.z) for cell in cells}


def _connected_components(cells: Iterable[Cell]) -> int:
    coords = _occupied_coords(cells)
    if not coords:
        return 0
    unseen = set(coords)
    components = 0
    while unseen:
        components += 1
        start = next(iter(unseen))
        unseen.remove(start)
        queue: deque[tuple[int, int, int]] = deque([start])
        while queue:
            x, y, z = queue.popleft()
            for neighbor in (
                (x + 1, y, z),
                (x - 1, y, z),
                (x, y + 1, z),
                (x, y - 1, z),
                (x, y, z + 1),
                (x, y, z - 1),
            ):
                if neighbor in unseen:
                    unseen.remove(neighbor)
                    queue.append(neighbor)
    return components


def _extent(cells: Iterable[Cell], axis: str, scale: float) -> float:
    values = [getattr(cell, axis) for cell in cells]
    if not values:
        return 0.0
    # Cells are one lattice unit wide and centered at integer/scale coordinates.
    return (max(values) - min(values) + 1) / scale


def _mirror_complete(cells: Iterable[Cell], roles: set[str]) -> bool:
    subset = {(cell.x, cell.y, cell.z, cell.role) for cell in cells if cell.role in roles}
    return all((x, y, -z, role) in subset for x, y, z, role in subset)


def _propeller_clearance(
    cells: Iterable[Cell],
    *,
    center_x_m: float,
    center_y_m: float,
    diameter_m: float,
    scale: float,
    hub_radius_m: float,
) -> tuple[bool, list[dict[str, Any]]]:
    plane_x = _q_nonnegative(center_x_m, scale)
    radius = diameter_m / 2.0
    violations = []
    for cell in cells:
        if cell.x != plane_x:
            continue
        dy = _meter(cell.y, scale) - center_y_m
        dz = _meter(cell.z, scale)
        radial_distance = math.hypot(dy, dz)
        if hub_radius_m + 1e-12 < radial_distance <= radius + 1e-12:
            violations.append(
                {
                    "x": cell.x,
                    "y": cell.y,
                    "z": cell.z,
                    "role": cell.role,
                    "radiusM": radial_distance,
                }
            )
    return not violations, violations


def transcribe(resolved: dict, config: dict) -> dict:
    if str(config.get("schemaVersion")) != "aircraft-blockspace-config-0.2":
        raise BlockspaceError("v0.2 requires aircraft-blockspace-config-0.2")
    if resolved.get("schemaVersion") != "aircraft-design-ir-0.1":
        raise BlockspaceError("v0.2 currently consumes aircraft-design-ir-0.1")
    if config.get("sourceAssetId") != resolved.get("assetId"):
        raise BlockspaceError("block-space config sourceAssetId does not match design IR")

    scale = float(config["blocksPerMeter"])
    if scale <= 0.0:
        raise BlockspaceError("blocksPerMeter must be positive")
    mounts = config["mounts"]
    geometry = resolved["geometry"]

    wing = _horizontal_surface_cells(
        span_m=float(geometry["wing"]["spanM"]),
        root_chord_m=float(geometry["wing"]["rootChordM"]),
        tip_chord_m=float(geometry["wing"]["tipChordM"]),
        leading_edge_x_m=float(geometry["wing"]["leadingEdgeXM"]),
        y_m=float(mounts["wingYM"]),
        scale=scale,
        role="wing_surface_intent",
    )
    horizontal_tail = _horizontal_surface_cells(
        span_m=float(geometry["horizontalTail"]["spanM"]),
        root_chord_m=float(geometry["horizontalTail"]["rootChordM"]),
        tip_chord_m=float(geometry["horizontalTail"]["tipChordM"]),
        leading_edge_x_m=float(geometry["horizontalTail"]["leadingEdgeXM"]),
        y_m=float(mounts["horizontalTailYM"]),
        scale=scale,
        role="horizontal_tail_surface_intent",
    )
    vertical_tail = _vertical_surface_cells(
        height_m=float(geometry["verticalTail"]["heightM"]),
        root_chord_m=float(geometry["verticalTail"]["rootChordM"]),
        tip_chord_m=float(geometry["verticalTail"]["tipChordM"]),
        leading_edge_x_m=float(geometry["verticalTail"]["leadingEdgeXM"]),
        root_y_m=float(mounts["verticalTailRootYM"]),
        scale=scale,
        role="vertical_tail_surface_intent",
    )

    center_y = _q_nonnegative(float(mounts["fuselageCenterYM"]), scale)
    length_index = _q_nonnegative(float(geometry["fuselage"]["lengthM"]), scale)
    fuselage = {Cell(x, center_y, 0, "fuselage_spine") for x in range(0, length_index + 1)}

    connectors: set[Cell] = set()
    wing_attach_x = _q_nonnegative(
        float(geometry["wing"]["leadingEdgeXM"]) + 0.25 * float(geometry["wing"]["rootChordM"]),
        scale,
    )
    wing_y = _q_nonnegative(float(mounts["wingYM"]), scale)
    connectors |= _line_cells(
        (wing_attach_x, center_y, 0),
        (wing_attach_x, wing_y, 0),
        "wing_attach_intent",
    )

    horizontal_tail_attach_x = _q_nonnegative(
        float(geometry["horizontalTail"]["leadingEdgeXM"])
        + 0.25 * float(geometry["horizontalTail"]["rootChordM"]),
        scale,
    )
    horizontal_tail_y = _q_nonnegative(float(mounts["horizontalTailYM"]), scale)
    connectors |= _line_cells(
        (horizontal_tail_attach_x, center_y, 0),
        (horizontal_tail_attach_x, horizontal_tail_y, 0),
        "tail_attach_intent",
    )

    vertical_tail_attach_x = _q_nonnegative(
        float(geometry["verticalTail"]["leadingEdgeXM"])
        + 0.25 * float(geometry["verticalTail"]["rootChordM"]),
        scale,
    )
    vertical_tail_root_y = _q_nonnegative(float(mounts["verticalTailRootYM"]), scale)
    connectors |= _line_cells(
        (vertical_tail_attach_x, center_y, 0),
        (vertical_tail_attach_x, vertical_tail_root_y, 0),
        "tail_attach_intent",
    )

    cells = wing | horizontal_tail | vertical_tail | fuselage | connectors

    propeller = geometry["propellerEnvelope"]
    cg_x = float(resolved["metrics"]["cgXM"])
    anchors = {
        "propeller_axis": {
            "continuousM": [float(propeller["centerXM"]), float(propeller["centerYM"]), 0.0],
            "lattice": [
                _q_nonnegative(float(propeller["centerXM"]), scale),
                _q_nonnegative(float(propeller["centerYM"]), scale),
                0,
            ],
        },
        "cg_reference": {
            "continuousM": [cg_x, float(mounts["fuselageCenterYM"]), 0.0],
            "lattice": [_q_nonnegative(cg_x, scale), center_y, 0],
        },
        "pilot_station": {
            "continuousM": [
                float(config["semanticStations"]["pilotXM"]),
                float(mounts["fuselageCenterYM"]),
                0.0,
            ],
            "lattice": [
                _q_nonnegative(float(config["semanticStations"]["pilotXM"]), scale),
                center_y,
                0,
            ],
        },
        "cargo_station": {
            "continuousM": [
                float(config["semanticStations"]["cargoXM"]),
                float(mounts["fuselageCenterYM"]),
                0.0,
            ],
            "lattice": [
                _q_nonnegative(float(config["semanticStations"]["cargoXM"]), scale),
                center_y,
                0,
            ],
        },
    }

    wing_extent = _extent((cell for cell in cells if cell.role == "wing_surface_intent"), "z", scale)
    horizontal_tail_extent = _extent(
        (cell for cell in cells if cell.role == "horizontal_tail_surface_intent"),
        "z",
        scale,
    )
    vertical_tail_extent = _extent(
        (cell for cell in cells if cell.role == "vertical_tail_surface_intent"),
        "y",
        scale,
    )
    cg_quantization_error_blocks = (
        abs(_meter(anchors["cg_reference"]["lattice"][0], scale) - cg_x) * scale
    )
    components = _connected_components(cells)
    symmetric = _mirror_complete(cells, {"wing_surface_intent", "horizontal_tail_surface_intent"})
    propeller_clear, propeller_violations = _propeller_clearance(
        cells,
        center_x_m=float(propeller["centerXM"]),
        center_y_m=float(propeller["centerYM"]),
        diameter_m=float(propeller["diameterM"]),
        scale=scale,
        hub_radius_m=float(config["propellerHubRadiusM"]),
    )

    dimensional_errors = {
        "wingSpanBlocks": abs(wing_extent - float(geometry["wing"]["spanM"])) * scale,
        "horizontalTailSpanBlocks": (
            abs(horizontal_tail_extent - float(geometry["horizontalTail"]["spanM"])) * scale
        ),
        "verticalTailHeightBlocks": (
            abs(vertical_tail_extent - float(geometry["verticalTail"]["heightM"])) * scale
        ),
    }
    max_dimension_error_blocks = float(config["validation"]["maxDimensionErrorBlocks"])
    passed = (
        components == 1
        and symmetric
        and propeller_clear
        and cg_quantization_error_blocks
        <= float(config["validation"]["maxCgStationErrorBlocks"]) + 1e-12
        and all(error <= max_dimension_error_blocks + 1e-12 for error in dimensional_errors.values())
    )

    out = {
        "schemaVersion": "aircraft-blockspace-ir-0.2",
        "assetId": str(resolved["assetId"]).replace("v0_1", "v0_2_blockspace"),
        "sourceDesignAssetId": resolved["assetId"],
        "sourceDesignDigestSha256": resolved["digestSha256"],
        "compilerVersion": "aircraft-blockspace-0.2",
        "coordinateSystem": {
            "x": "nose_to_tail",
            "y": "up",
            "z": "starboard_positive",
            "cellCenters": "integer_lattice",
            "blocksPerMeter": scale,
        },
        "declaredTranscriptionAssumptions": {
            "surfaceRasterization": "cell_center_scanline_exact_linear_taper",
            "fuselageRepresentation": "centerline_structural_spine_only_v0.2",
            "connectors": "orthogonal_axis_aligned_only",
            "mounts": dict(mounts),
            "noMinecraftBlockIdentity": True,
        },
        "cells": [cell.to_dict() for cell in sorted(cells)],
        "anchors": anchors,
        "capabilityContract": {
            "targetFamily": config["targetFamily"],
            "concreteResourceResolution": "deferred_pending_exact_target_api_and_runtime_evidence",
            "roles": {
                "fuselage_spine": ["rigid_physics_member"],
                "wing_surface_intent": ["aerodynamic_lift_surface", "rigid_physics_member"],
                "horizontal_tail_surface_intent": ["aerodynamic_lift_surface", "rigid_physics_member"],
                "vertical_tail_surface_intent": ["aerodynamic_lift_surface", "rigid_physics_member"],
                "wing_attach_intent": ["rigid_load_path"],
                "tail_attach_intent": ["rigid_load_path"],
                "propeller_axis": ["rotational_thrust_producer", "shaft_power_input", "clearance_disk"],
                "pilot_station": ["vehicle_control_station"],
                "cargo_station": ["payload_volume_or_interface"],
            },
        },
        "metrics": {
            "cellCount": len(cells),
            "connectedComponents6Neighbor": components,
            "mirrorSymmetrySatisfied": symmetric,
            "propellerDiskClear": propeller_clear,
            "propellerDiskViolationCount": len(propeller_violations),
            "cgStationQuantizationErrorBlocks": cg_quantization_error_blocks,
            "continuousWingSpanM": float(geometry["wing"]["spanM"]),
            "realizedWingSpanM": wing_extent,
            "continuousHorizontalTailSpanM": float(geometry["horizontalTail"]["spanM"]),
            "realizedHorizontalTailSpanM": horizontal_tail_extent,
            "continuousVerticalTailHeightM": float(geometry["verticalTail"]["heightM"]),
            "realizedVerticalTailHeightM": vertical_tail_extent,
            "dimensionErrorBlocks": dimensional_errors,
        },
        "validation": {
            "passed": passed,
            "scope": "deterministic_blockspace_transcription_and_capability_contract_only",
            "doesNotProve": [
                "Create Aeronautics concrete block compatibility",
                "in-engine assembly connectivity",
                "in-engine center of mass",
                "in-engine aerodynamic forces",
                "flight stability or control authority",
                "structural strength",
            ],
            "propellerDiskViolations": propeller_violations,
        },
    }
    canonical = json.dumps(out, sort_keys=True, separators=(",", ":"))
    out["digestSha256"] = hashlib.sha256(canonical.encode("utf-8")).hexdigest()
    return out
