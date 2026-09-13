from __future__ import annotations

import hashlib
import itertools
import json
from copy import deepcopy
from dataclasses import dataclass
from typing import Any

from aero_math import (
    aspect_ratio,
    dynamic_pressure,
    horizontal_tail_volume,
    induced_drag_coefficient,
    lift_force,
    mass_center,
    mean_aerodynamic_chord,
    single_trapezoid_area,
    taper_ratio,
    trapezoid_area,
    vertical_tail_volume,
    weight_force,
)
from model import MassItem, SpecError, TailPlanform, WingPlanform


@dataclass(frozen=True)
class Candidate:
    fuselage_length_m: float
    wing: WingPlanform
    horizontal_tail: TailPlanform
    vertical_tail: TailPlanform
    metrics: dict[str, float]
    mass_items: tuple[MassItem, ...]
    objective: tuple[Any, ...]


def _f(value: Any, name: str) -> float:
    try:
        return float(value)
    except (TypeError, ValueError) as exc:
        raise SpecError(f"{name} must be numeric") from exc


def _values(spec: dict, name: str) -> tuple[float, ...]:
    values = tuple(_f(v, name) for v in spec.get(name, []))
    if not values:
        raise SpecError(f"empty design domain: {name}")
    return values


def _mass_items(spec: dict, fuselage_length_m: float) -> tuple[MassItem, ...]:
    result: list[MassItem] = []
    for raw in spec["massLedger"]:
        mass = _f(raw["massKg"], f"massLedger.{raw['name']}.massKg")
        if "stationXM" in raw:
            x = _f(raw["stationXM"], f"massLedger.{raw['name']}.stationXM")
            source = "absolute_specimen_station"
        elif "stationFractionFuselage" in raw:
            fraction = _f(raw["stationFractionFuselage"], f"massLedger.{raw['name']}.stationFractionFuselage")
            x = fraction * fuselage_length_m
            source = "fraction_of_fuselage_length"
        else:
            raise SpecError(f"mass item {raw['name']} lacks a station")
        result.append(MassItem(str(raw["name"]), mass, x, source))
    return tuple(result)


def _tail_catalog_horizontal(domains: dict, tail_le_x: float) -> list[TailPlanform]:
    result = []
    for span, root, tip in itertools.product(
        _values(domains, "horizontalTailSpanM"),
        _values(domains, "horizontalTailRootChordM"),
        _values(domains, "horizontalTailTipChordM"),
    ):
        if tip <= 0.0 or root < tip:
            continue
        result.append(TailPlanform(span, root, tip, tail_le_x))
    return result


def _tail_catalog_vertical(domains: dict, tail_le_x: float) -> list[TailPlanform]:
    result = []
    for height, root, tip in itertools.product(
        _values(domains, "verticalTailHeightM"),
        _values(domains, "verticalTailRootChordM"),
        _values(domains, "verticalTailTipChordM"),
    ):
        if tip <= 0.0 or root < tip:
            continue
        result.append(TailPlanform(height, root, tip, tail_le_x))
    return result


def _best_tail_pair(
    wing: WingPlanform,
    wing_area: float,
    wing_mac: float,
    fuselage_length_m: float,
    domains: dict,
    refs: dict,
) -> tuple[TailPlanform, TailPlanform, dict[str, float]] | None:
    h_le = fuselage_length_m - _f(domains["horizontalTailLeadingEdgeInsetM"], "horizontalTailLeadingEdgeInsetM")
    v_le = fuselage_length_m - _f(domains["verticalTailLeadingEdgeInsetM"], "verticalTailLeadingEdgeInsetM")
    wing_ac_x = wing.leading_edge_x_m + 0.25 * wing_mac
    best = None
    for h in _tail_catalog_horizontal(domains, h_le):
        h_mac = mean_aerodynamic_chord(h.root_chord_m, h.tip_chord_m)
        h_area = trapezoid_area(h.span_or_height_m, h.root_chord_m, h.tip_chord_m)
        h_ac_x = h.leading_edge_x_m + 0.25 * h_mac
        h_arm = h_ac_x - wing_ac_x
        if h_arm <= 0.0:
            continue
        vh = horizontal_tail_volume(h_area, h_arm, wing_area, wing_mac)
        for v in _tail_catalog_vertical(domains, v_le):
            v_mac = mean_aerodynamic_chord(v.root_chord_m, v.tip_chord_m)
            v_area = single_trapezoid_area(v.span_or_height_m, v.root_chord_m, v.tip_chord_m)
            v_ac_x = v.leading_edge_x_m + 0.25 * v_mac
            v_arm = v_ac_x - wing_ac_x
            if v_arm <= 0.0:
                continue
            vv = vertical_tail_volume(v_area, v_arm, wing_area, wing.span_m)
            vh_ref = _f(refs["horizontalTailVolume"], "horizontalTailVolume")
            vv_ref = _f(refs["verticalTailVolume"], "verticalTailVolume")
            h_err = abs(vh - vh_ref) / vh_ref
            v_err = abs(vv - vv_ref) / vv_ref
            score = (
                round(h_err + v_err, 12),
                round(h_area + v_area, 12),
                h.span_or_height_m,
                h.root_chord_m,
                h.tip_chord_m,
                v.span_or_height_m,
                v.root_chord_m,
                v.tip_chord_m,
            )
            if best is None or score < best[0]:
                best = (score, h, v, {
                    "horizontalTailAreaM2": h_area,
                    "horizontalTailMacM": h_mac,
                    "horizontalTailArmM": h_arm,
                    "horizontalTailVolume": vh,
                    "horizontalTailReferenceErrorFraction": h_err,
                    "verticalTailAreaM2": v_area,
                    "verticalTailMacM": v_mac,
                    "verticalTailArmM": v_arm,
                    "verticalTailVolume": vv,
                    "verticalTailReferenceErrorFraction": v_err,
                })
    if best is None:
        return None
    return best[1], best[2], best[3]


def solve(spec: dict) -> dict:
    if str(spec.get("schemaVersion")) != "0.1":
        raise SpecError("AIRCRAFT-001 v0.1 requires schemaVersion 0.1")
    mission = spec["mission"]
    domains = spec["designDomains"]
    constraints = spec["constraints"]
    refs = spec["referenceTargets"]
    assumptions = spec["analyticalAssumptions"]

    gross_mass = _f(mission["grossMassKg"], "grossMassKg")
    rho = _f(mission["airDensityKgM3"], "airDensityKgM3")
    gravity = _f(mission["gravityMS2"], "gravityMS2")
    speed = _f(mission["cruiseSpeedMS"], "cruiseSpeedMS")
    cl = _f(mission["designLiftCoefficient"], "designLiftCoefficient")
    span_efficiency = _f(assumptions["spanEfficiency"], "spanEfficiency")
    q = dynamic_pressure(rho, speed)
    weight = weight_force(gross_mass, gravity)
    required_area = weight / (q * cl)

    feasible: list[Candidate] = []
    rejection_counts: dict[str, int] = {}

    def reject(reason: str) -> None:
        rejection_counts[reason] = rejection_counts.get(reason, 0) + 1

    for fuselage_length, span, root, tip, wing_le in itertools.product(
        _values(domains, "fuselageLengthM"),
        _values(domains, "wingSpanM"),
        _values(domains, "wingRootChordM"),
        _values(domains, "wingTipChordM"),
        _values(domains, "wingLeadingEdgeXM"),
    ):
        if root < tip or tip <= 0.0:
            reject("invalid_taper")
            continue
        if wing_le <= 0.0 or wing_le + root >= fuselage_length:
            reject("wing_not_contained_by_fuselage_station")
            continue
        wing = WingPlanform(span, root, tip, wing_le)
        area = trapezoid_area(span, root, tip)
        mac = mean_aerodynamic_chord(root, tip)
        ar = aspect_ratio(span, area)
        ar_min, ar_max = map(float, constraints["aspectRatioRange"])
        if not ar_min <= ar <= ar_max:
            reject("aspect_ratio")
            continue
        lift = lift_force(rho, speed, area, cl)
        lift_residual = abs(lift - weight) / weight
        if lift_residual > _f(constraints["maxCruiseLiftResidualFraction"], "maxCruiseLiftResidualFraction"):
            reject("cruise_lift_residual")
            continue

        masses = _mass_items(spec, fuselage_length)
        mass_sum = sum(item.mass_kg for item in masses)
        if abs(mass_sum - gross_mass) > 1e-9:
            raise SpecError(f"mass ledger {mass_sum} kg does not equal gross mass {gross_mass} kg")
        cg_x = mass_center((item.mass_kg, item.station_x_m) for item in masses)
        cg_mac = (cg_x - wing_le) / mac
        cg_min, cg_max = map(float, constraints["specimenCgMacFractionRange"])
        if not cg_min <= cg_mac <= cg_max:
            reject("specimen_cg_mac_fraction")
            continue

        tail = _best_tail_pair(wing, area, mac, fuselage_length, domains, refs)
        if tail is None:
            reject("tail_geometry")
            continue
        h, v, tail_metrics = tail
        if tail_metrics["horizontalTailReferenceErrorFraction"] > _f(constraints["maxHorizontalTailVolumeReferenceErrorFraction"], "maxHorizontalTailVolumeReferenceErrorFraction"):
            reject("horizontal_tail_volume_reference")
            continue
        if tail_metrics["verticalTailReferenceErrorFraction"] > _f(constraints["maxVerticalTailVolumeReferenceErrorFraction"], "maxVerticalTailVolumeReferenceErrorFraction"):
            reject("vertical_tail_volume_reference")
            continue

        cdi = induced_drag_coefficient(cl, ar, span_efficiency)
        complexity = (
            fuselage_length
            + span
            + 0.5 * (area + tail_metrics["horizontalTailAreaM2"] + tail_metrics["verticalTailAreaM2"])
        )
        metrics = {
            "dynamicPressurePa": q,
            "weightN": weight,
            "requiredWingAreaM2AtDesignCL": required_area,
            "wingAreaM2": area,
            "wingTaperRatio": taper_ratio(root, tip),
            "wingAspectRatio": ar,
            "wingMacM": mac,
            "cruiseLiftN": lift,
            "cruiseLiftResidualFraction": lift_residual,
            "analyticalInducedDragCoefficient": cdi,
            "cgXM": cg_x,
            "cgMacFraction": cg_mac,
            **tail_metrics,
        }
        objective = (
            round(lift_residual, 12),
            round(tail_metrics["horizontalTailReferenceErrorFraction"] + tail_metrics["verticalTailReferenceErrorFraction"], 12),
            round(cdi, 12),
            round(complexity, 12),
            fuselage_length,
            span,
            root,
            tip,
            wing_le,
            h.span_or_height_m,
            h.root_chord_m,
            h.tip_chord_m,
            v.span_or_height_m,
            v.root_chord_m,
            v.tip_chord_m,
        )
        feasible.append(Candidate(fuselage_length, wing, h, v, metrics, masses, objective))

    if not feasible:
        raise SpecError(f"no feasible aircraft candidate; rejection counts={rejection_counts}")
    chosen = min(feasible, key=lambda c: c.objective)

    resolved = {
        "schemaVersion": "aircraft-design-ir-0.1",
        "assetId": spec["assetId"],
        "compilerVersion": "aircraft-compiler-0.1",
        "configuration": deepcopy(spec["configuration"]),
        "mission": deepcopy(mission),
        "declaredAssumptions": deepcopy(assumptions),
        "referenceTargets": deepcopy(refs),
        "geometry": {
            "fuselage": {
                "lengthM": chosen.fuselage_length_m,
                "maxWidthM": _f(spec["geometryEnvelope"]["fuselageMaxWidthM"], "fuselageMaxWidthM"),
                "maxHeightM": _f(spec["geometryEnvelope"]["fuselageMaxHeightM"], "fuselageMaxHeightM"),
            },
            "wing": {
                "spanM": chosen.wing.span_m,
                "rootChordM": chosen.wing.root_chord_m,
                "tipChordM": chosen.wing.tip_chord_m,
                "leadingEdgeXM": chosen.wing.leading_edge_x_m,
                "mount": "high",
                "sweep": "zero_v0.1",
            },
            "horizontalTail": {
                "spanM": chosen.horizontal_tail.span_or_height_m,
                "rootChordM": chosen.horizontal_tail.root_chord_m,
                "tipChordM": chosen.horizontal_tail.tip_chord_m,
                "leadingEdgeXM": chosen.horizontal_tail.leading_edge_x_m,
            },
            "verticalTail": {
                "heightM": chosen.vertical_tail.span_or_height_m,
                "rootChordM": chosen.vertical_tail.root_chord_m,
                "tipChordM": chosen.vertical_tail.tip_chord_m,
                "leadingEdgeXM": chosen.vertical_tail.leading_edge_x_m,
            },
            "propellerEnvelope": deepcopy(spec["geometryEnvelope"]["propellerEnvelope"]),
        },
        "massLedger": [item.to_dict() for item in chosen.mass_items],
        "metrics": chosen.metrics,
        "solver": {
            "method": "deterministic_exhaustive_lexicographic_search",
            "candidateCountFeasible": len(feasible),
            "rejectionCounts": dict(sorted(rejection_counts.items())),
            "objectiveOrder": [
                "cruise_lift_residual",
                "tail_volume_reference_error_sum",
                "analytical_induced_drag_coefficient",
                "geometry_complexity_proxy",
                "lexical_geometry_tiebreak",
            ],
            "selectedObjective": list(chosen.objective),
        },
        "validation": {
            "passed": True,
            "scope": "analytical_geometry_and_balance_only",
            "doesNotProve": [
                "Create Aeronautics in-game lift or stability",
                "structural strength",
                "stall behavior",
                "dynamic stability",
                "control authority",
            ],
        },
        "targetBoundary": {
            "containsConcreteTargetResourceNames": False,
            "createAeronauticsAdapterApplied": False,
        },
    }
    canonical = json.dumps(resolved, sort_keys=True, separators=(",", ":"))
    resolved["digestSha256"] = hashlib.sha256(canonical.encode("utf-8")).hexdigest()
    return resolved
