from __future__ import annotations

import collections
import copy
import hashlib
import json
from pathlib import Path
from typing import Any

from articulation import (
    choose_contiguous_patch,
    evaluate_ratio_profile,
    ratio,
    validate_depth_plane_contract,
)
from guild_branch_cohesion import compile_guild_branch_v07
from model import BlockState, CompiledAsset, SpecError


_PROFILE_DIR = Path(__file__).resolve().parent / "reference_profiles"


def _material(materials: dict[str, str], role: str, fallback: str | None = None) -> str:
    value = materials.get(role)
    if value is None and fallback is not None:
        value = materials.get(fallback)
    if not value:
        suffix = f" or fallback {fallback}" if fallback else ""
        raise SpecError(f"materialRoles.{role}{suffix} is required")
    return value


def _load_profile(profile_name: str) -> dict[str, Any]:
    if "/" in profile_name or "\\" in profile_name or profile_name.startswith("."):
        raise SpecError("articulation.referenceProfile must be a profile filename, not a path")
    path = _PROFILE_DIR / profile_name
    try:
        profile = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise SpecError(f"cannot load articulation reference profile {profile_name}: {exc}") from exc
    if profile.get("status") not in {"working_reference", "measured_reference"}:
        raise SpecError("articulation reference profile must declare working_reference or measured_reference status")
    return profile


def _average_public_window_width(model, bay_width: int) -> float:
    xs = sorted({
        x for (x, _y, _z), cell in model.cells.items()
        if cell.module == "public_window_recess_v06" and cell.role == "window"
    })
    groups: list[int] = []
    if xs:
        start = previous = xs[0]
        for value in xs[1:]:
            if value != previous + 1:
                groups.append(previous - start + 1)
                start = value
            previous = value
        groups.append(previous - start + 1)
    if not groups:
        raise SpecError("cannot measure public-window proportion: no recessed window groups found")
    return sum(groups) / len(groups) / bay_width


def compile_guild_branch_v08(spec: dict[str, Any]) -> CompiledAsset:
    """Promote learned facade craft into an explicit architectural-articulation stage.

    v0.8 does three things that were previously only implicit or conversational:
    1. evaluates bounded proportion metrics against a provenance-labelled reference profile;
    2. declares and validates a semantic depth-plane contract, then adds structural eave brackets at
       bay/post joints so depth serves the frame rather than floating as unrelated trim;
    3. permits one deterministic, semantically explained history intervention instead of random
       decorative irregularity.

    The profile shipped with this proof is an INTERNAL working reference derived from the accepted
    Guild grammar and our v0.4-v0.7 in-game observations. It is explicitly not presented as measured
    master-builder data. The interface is designed so measured donor/reference profiles can replace or
    augment it later without changing this lowering stage.
    """
    if spec.get("schemaVersion") != "0.8":
        raise SpecError("v0.8 articulation compiler requires schemaVersion=0.8")

    articulation_cfg = spec.get("articulation")
    if not isinstance(articulation_cfg, dict):
        raise SpecError("v0.8 requires articulation object")
    profile_name = articulation_cfg.get("referenceProfile")
    if not isinstance(profile_name, str) or not profile_name:
        raise SpecError("articulation.referenceProfile is required")
    declared_depth = articulation_cfg.get("depthPlanes")
    if not isinstance(declared_depth, dict):
        raise SpecError("articulation.depthPlanes is required")

    base_spec = copy.deepcopy(spec)
    base_spec["schemaVersion"] = "0.7"
    compiled = compile_guild_branch_v07(base_spec)
    if not compiled.summary["validation"]["passed"]:
        return compiled

    model = compiled.model
    summary = compiled.summary
    layout = summary["layout"]
    rp = layout["resolvedParameters"]
    materials = spec["materialRoles"]
    structure = spec["structure"]

    bays = int(rp["bayCount"])
    bay_w = int(rp["bayWidth"])
    hall_w = int(rp["hallWidth"])
    depth = int(rp["hallDepth"])
    wall_h = int(rp["wallHeight"])
    wing_w = int(rp["workingWingWidth"])
    door_x0, door_x1 = map(int, rp["publicEntranceSpan"])
    hall_z0 = 4
    hall_z1 = hall_z0 + depth - 1
    wing_x1 = hall_w + wing_w - 1

    roof_cfg = structure.get("roof", {})
    main_rise = int(roof_cfg.get("mainRise", 4))
    overhang = int(roof_cfg.get("overhang", 1))
    public_canopy_depth = int(structure.get("publicCanopy", {}).get("depth", 2))
    working_canopy_depth = int(structure.get("workingCanopy", {}).get("depth", 2))

    # PROPORTION / REFERENCE ANALYSIS ---------------------------------------
    # These metrics are deliberately semantic ratios, not raw coordinates. That makes a later
    # measured-reference profile comparable across sibling scales rather than tied to this specimen.
    metrics = {
        "bayWidthToWallHeight": ratio(bay_w, wall_h, "bayWidthToWallHeight"),
        "mainDepthToHallWidth": ratio(depth, hall_w, "mainDepthToHallWidth"),
        "workingWingWidthToHallWidth": ratio(wing_w, hall_w, "workingWingWidthToHallWidth"),
        "mainRoofRiseToDepth": ratio(main_rise, depth, "mainRoofRiseToDepth"),
        "roofOverhangToDepth": ratio(overhang, depth, "roofOverhangToDepth"),
        "publicCanopyDepthToWallHeight": ratio(public_canopy_depth, wall_h, "publicCanopyDepthToWallHeight"),
        "workingCanopyDepthToWallHeight": ratio(working_canopy_depth, wall_h, "workingCanopyDepthToWallHeight"),
        "publicEntranceWidthToBayWidth": ratio(door_x1 - door_x0 + 1, bay_w, "publicEntranceWidthToBayWidth"),
        "averagePublicWindowWidthToBayWidth": _average_public_window_width(model, bay_w),
    }
    profile = _load_profile(profile_name)
    ratio_report, ratio_issues = evaluate_ratio_profile(metrics, profile)

    # DEPTH-PLANE CONTRACT --------------------------------------------------
    # This names the lesson we have been converging on since v0.6: depth is a small ordered
    # vocabulary, not arbitrary offsets. The public facade maps directly onto these semantic planes.
    required_depth_order = ["recessedClosure", "wall", "trim", "canopy"]
    depth_issues = validate_depth_plane_contract(declared_depth, required_depth_order)

    actual_depth = {
        "recessedClosure": hall_z1 - 1,
        "wall": hall_z1,
        "trim": hall_z1 + 1,
        "canopy": hall_z1 + public_canopy_depth,
    }
    expected_actual = {
        name: hall_z1 + int(declared_depth[name])
        for name in required_depth_order
        if name in declared_depth
    }
    for name, value in expected_actual.items():
        if actual_depth[name] != value:
            depth_issues.append(
                f"public facade depth plane {name} realized at {actual_depth[name]}, expected {value}"
            )

    # STRUCTURAL JOINT ARTICULATION ----------------------------------------
    # Add one restrained bracket/corbel at each public structural post under the eave. This encodes
    # the master-builder lesson that detail belongs at transitions and load paths. The brackets align
    # with the existing bay rhythm instead of introducing independent decorative coordinates.
    bracket = BlockState.of(_material(materials, "exteriorTrimSlab", "structuralFrame"), type="top")
    post_xs = sorted(set(range(0, hall_w, bay_w)) | {hall_w - 1})
    for x in post_xs:
        model.set(x, wall_h - 1, hall_z1 + 1, "structural_frame", bracket, "public_eave_bracket_v08")
    # Mirror the same joint logic at existing working-face posts only; do not invent extra rhythm.
    working_post_zs = sorted({
        z for (x, _y, z), cell in model.cells.items()
        if x == wing_x1 and cell.role == "structural_frame" and cell.state.property_dict().get("axis") == "y"
    }) if hasattr(next(iter(model.cells.values())).state, "property_dict") else []
    # BlockState intentionally keeps a minimal API. Fall back to module-based detection if a future
    # model revision does not expose property_dict.
    if not working_post_zs:
        working_post_zs = sorted({
            z for (x, _y, z), cell in model.cells.items()
            if x == wing_x1 and cell.role == "structural_frame" and cell.module in {
                "working_wing", "repair_jamb_v06", "freight_jamb_v06",
                "repair_portal_reveal_v07", "freight_portal_reveal_v07",
            }
        })
    for z in working_post_zs:
        model.set(wing_x1 + 1, wall_h - 1, z, "structural_frame", bracket, "working_eave_bracket_v08")

    # CONTROLLED HISTORY / IRREGULARITY ------------------------------------
    history_cfg = spec.get("history", {})
    interventions: list[dict[str, Any]] = []
    history_cells: list[tuple[int, int, int]] = []
    if history_cfg.get("enabled", False):
        max_visible = int(history_cfg.get("maxVisibleInterventions", 1))
        if max_visible != 1:
            raise SpecError("v0.8 proof permits exactly one visible history intervention")
        repair_cfg = history_cfg.get("repairPatch", {})
        if repair_cfg.get("enabled", False):
            if repair_cfg.get("face") != "west":
                raise SpecError("v0.8 proof supports history.repairPatch.face=west only")
            span = int(repair_cfg.get("span", 2))
            y0 = int(repair_cfg.get("yMin", 3))
            y1 = int(repair_cfg.get("yMax", 4))
            if y0 > y1:
                raise SpecError("history repair patch requires yMin <= yMax")
            eligible: list[int] = []
            for z in range(hall_z0 + 1, hall_z1):
                if all(
                    (cell := model.cells.get((0, y, z))) is not None and cell.role == "wall_infill"
                    for y in range(y0, y1 + 1)
                ):
                    eligible.append(z)
            patch_zs = choose_contiguous_patch(
                eligible,
                seed=int(spec.get("seed", 0)),
                key=f"{spec['assetId']}:west_repair_patch",
                span=span,
            )
            repair_state = BlockState.of(_material(materials, repair_cfg.get("materialRole", "foundation")))
            for z in patch_zs:
                for y in range(y0, y1 + 1):
                    model.set(0, y, z, "foundation", repair_state, "history_masonry_repair_v08")
                    history_cells.append((0, y, z))
            interventions.append({
                "type": "masonry_repair_patch",
                "explanation": "localized repair of damaged pale infill using durable masonry",
                "face": "west",
                "positions": [list(pos) for pos in history_cells],
            })

    layout["articulation"] = {
        "stage": "architectural_articulation",
        "referenceProfileId": profile.get("profileId"),
        "referenceProfileStatus": profile.get("status"),
        "referenceProfileProvenance": profile.get("provenance", {}),
        "metrics": {name: round(value, 6) for name, value in sorted(metrics.items())},
        "ratioReport": ratio_report,
        "depthPlanes": {name: int(declared_depth[name]) for name in required_depth_order if name in declared_depth},
        "actualPublicFacadePlanes": actual_depth,
        "historyInterventions": interventions,
    }
    layout["resolvedParameters"].update({
        "runtimeCorrectionVersion": "0.8",
        "articulationStage": "explicit",
        "proportionProfile": profile.get("profileId"),
        "controlledHistoryInterventions": len(interventions),
    })

    min_x = min(x for x, _y, _z in model.cells)
    min_y = min(y for _x, y, _z in model.cells)
    min_z = min(z for _x, _y, z in model.cells)
    max_x = max(x for x, _y, _z in model.cells)
    max_y = max(y for _x, y, _z in model.cells)
    max_z = max(z for _x, _y, z in model.cells)
    layout["bounds"] = {
        "min": [min_x, min_y, min_z],
        "max": [max_x, max_y, max_z],
        "size": [max_x - min_x + 1, max_y - min_y + 1, max_z - min_z + 1],
    }

    issues: list[str] = [*ratio_issues, *depth_issues]
    requested = set(spec.get("requestedAnchors", []))
    missing_anchors = sorted(requested - set(layout["anchors"]))
    if missing_anchors:
        issues.append(f"missing requested anchors: {', '.join(missing_anchors)}")
    if not any(cell.module == "public_eave_bracket_v08" for cell in model.cells.values()):
        issues.append("v0.8 public structural-joint brackets missing")
    if history_cfg.get("enabled", False) and len(interventions) != 1:
        issues.append(f"v0.8 expected exactly one history intervention, got {len(interventions)}")
    if interventions and not history_cells:
        issues.append("v0.8 history intervention recorded without geometry")

    canonical = json.dumps(
        {
            "assetId": spec["assetId"],
            "layout": layout,
            "cells": [
                {"pos": [x, y, z], **cell.to_dict()}
                for (x, y, z), cell in sorted(model.cells.items())
            ],
        },
        sort_keys=True,
        separators=(",", ":"),
    )
    summary.update({
        "schemaVersion": "0.8",
        "compilerVersion": "0.8",
        "assetId": spec["assetId"],
        "layout": layout,
        "blockCount": len(model.cells),
        "materialCounts": dict(sorted(collections.Counter(c.state.canonical() for c in model.cells.values()).items())),
        "roleCounts": dict(sorted(collections.Counter(c.role for c in model.cells.values()).items())),
        "moduleBlockCounts": dict(sorted(collections.Counter(c.module for c in model.cells.values() if c.module).items())),
        "digestSha256": hashlib.sha256(canonical.encode("utf-8")).hexdigest(),
        "validation": {"passed": not issues, "issues": issues},
    })
    return CompiledAsset(summary, model)
