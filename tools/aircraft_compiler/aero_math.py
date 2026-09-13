from __future__ import annotations

from math import pi
from typing import Iterable


def dynamic_pressure(rho_kg_m3: float, speed_m_s: float) -> float:
    """q = 1/2 rho V^2."""
    return 0.5 * rho_kg_m3 * speed_m_s**2


def lift_force(rho_kg_m3: float, speed_m_s: float, area_m2: float, cl: float) -> float:
    """L = q S C_L."""
    return dynamic_pressure(rho_kg_m3, speed_m_s) * area_m2 * cl


def weight_force(mass_kg: float, gravity_m_s2: float) -> float:
    return mass_kg * gravity_m_s2


def trapezoid_area(span_m: float, root_chord_m: float, tip_chord_m: float) -> float:
    """Area of a symmetric straight-taper wing/tail planform."""
    return 0.5 * span_m * (root_chord_m + tip_chord_m)


def single_trapezoid_area(height_or_span_m: float, root_chord_m: float, tip_chord_m: float) -> float:
    """Area of one trapezoidal surface (used for one vertical fin)."""
    return 0.5 * height_or_span_m * (root_chord_m + tip_chord_m)


def taper_ratio(root_chord_m: float, tip_chord_m: float) -> float:
    if root_chord_m <= 0.0:
        raise ValueError("root chord must be positive")
    return tip_chord_m / root_chord_m


def mean_aerodynamic_chord(root_chord_m: float, tip_chord_m: float) -> float:
    """MAC for a straight, linearly tapered planform."""
    lam = taper_ratio(root_chord_m, tip_chord_m)
    return (2.0 / 3.0) * root_chord_m * (1.0 + lam + lam * lam) / (1.0 + lam)


def aspect_ratio(span_m: float, area_m2: float) -> float:
    if area_m2 <= 0.0:
        raise ValueError("area must be positive")
    return span_m**2 / area_m2


def induced_drag_coefficient(cl: float, aspect_ratio_value: float, span_efficiency: float) -> float:
    """C_Di = C_L^2/(pi e AR); analytical comparison proxy only."""
    if aspect_ratio_value <= 0.0 or not 0.0 < span_efficiency <= 1.0:
        raise ValueError("aspect ratio and span efficiency must be positive; e <= 1")
    return cl**2 / (pi * span_efficiency * aspect_ratio_value)


def mass_center(items: Iterable[tuple[float, float]]) -> float:
    """One-dimensional center of mass from (mass, station) pairs."""
    pairs = list(items)
    total = sum(m for m, _ in pairs)
    if total <= 0.0:
        raise ValueError("total mass must be positive")
    return sum(m * x for m, x in pairs) / total


def horizontal_tail_volume(tail_area_m2: float, tail_arm_m: float, wing_area_m2: float, wing_mac_m: float) -> float:
    return tail_area_m2 * tail_arm_m / (wing_area_m2 * wing_mac_m)


def vertical_tail_volume(tail_area_m2: float, tail_arm_m: float, wing_area_m2: float, wing_span_m: float) -> float:
    return tail_area_m2 * tail_arm_m / (wing_area_m2 * wing_span_m)
