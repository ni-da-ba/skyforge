from __future__ import annotations

from math import atan2, cos, degrees, exp, radians, sin, sqrt

from model import RGBA


def _srgb_to_linear(c: float) -> float:
    c /= 255.0
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def _linear_to_srgb(c: float) -> int:
    c = max(0.0, min(1.0, c))
    out = 12.92 * c if c <= 0.0031308 else 1.055 * (c ** (1 / 2.4)) - 0.055
    return int(round(out * 255.0))


def average_linear_rgba(samples: list[tuple[RGBA, float]]) -> RGBA:
    if not samples:
        return (0, 0, 0, 0)
    total = sum(max(0.0, w) for _c, w in samples)
    if total <= 1e-12:
        total = float(len(samples))
        samples = [(c, 1.0) for c, _w in samples]
    lr = lg = lb = la = 0.0
    for color, weight in samples:
        weight = max(0.0, weight)
        lr += _srgb_to_linear(color[0]) * weight
        lg += _srgb_to_linear(color[1]) * weight
        lb += _srgb_to_linear(color[2]) * weight
        la += (color[3] / 255.0) * weight
    return (
        _linear_to_srgb(lr / total),
        _linear_to_srgb(lg / total),
        _linear_to_srgb(lb / total),
        int(round(max(0.0, min(1.0, la / total)) * 255.0)),
    )


def rgba_to_lab(color: RGBA) -> tuple[float, float, float]:
    r = _srgb_to_linear(color[0])
    g = _srgb_to_linear(color[1])
    b = _srgb_to_linear(color[2])
    x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / 0.95047
    y = (0.2126729 * r + 0.7151522 * g + 0.0721750 * b)
    z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / 1.08883

    delta = 6 / 29

    def f(t: float) -> float:
        return t ** (1 / 3) if t > delta ** 3 else t / (3 * delta * delta) + 4 / 29

    fx, fy, fz = f(x), f(y), f(z)
    return 116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)


def delta_e_2000_lab(lab1: tuple[float, float, float], lab2: tuple[float, float, float]) -> float:
    # Sharma, Wu & Dalal (2005), kL=kC=kH=1.
    l1, a1, b1 = lab1
    l2, a2, b2 = lab2
    c1ab = sqrt(a1 * a1 + b1 * b1)
    c2ab = sqrt(a2 * a2 + b2 * b2)
    cbar = (c1ab + c2ab) / 2
    g = 0.5 * (1 - sqrt((cbar ** 7) / (cbar ** 7 + 25 ** 7))) if cbar > 0 else 0.0
    ap1, ap2 = (1 + g) * a1, (1 + g) * a2
    cp1 = sqrt(ap1 * ap1 + b1 * b1)
    cp2 = sqrt(ap2 * ap2 + b2 * b2)

    def hp(a: float, b: float) -> float:
        if abs(a) < 1e-15 and abs(b) < 1e-15:
            return 0.0
        angle = degrees(atan2(b, a))
        return angle + 360 if angle < 0 else angle

    hp1, hp2 = hp(ap1, b1), hp(ap2, b2)
    dl = l2 - l1
    dc = cp2 - cp1
    dh_angle = hp2 - hp1
    if cp1 * cp2 == 0:
        dh_angle = 0.0
    elif dh_angle > 180:
        dh_angle -= 360
    elif dh_angle < -180:
        dh_angle += 360
    dh = 2 * sqrt(cp1 * cp2) * sin(radians(dh_angle / 2))
    lbar = (l1 + l2) / 2
    cpbar = (cp1 + cp2) / 2
    if cp1 * cp2 == 0:
        hpbar = hp1 + hp2
    elif abs(hp1 - hp2) <= 180:
        hpbar = (hp1 + hp2) / 2
    elif hp1 + hp2 < 360:
        hpbar = (hp1 + hp2 + 360) / 2
    else:
        hpbar = (hp1 + hp2 - 360) / 2
    t = (
        1
        - 0.17 * cos(radians(hpbar - 30))
        + 0.24 * cos(radians(2 * hpbar))
        + 0.32 * cos(radians(3 * hpbar + 6))
        - 0.20 * cos(radians(4 * hpbar - 63))
    )
    sl = 1 + 0.015 * ((lbar - 50) ** 2) / sqrt(20 + (lbar - 50) ** 2)
    sc = 1 + 0.045 * cpbar
    sh = 1 + 0.015 * cpbar * t
    delta_theta = 30 * exp(-(((hpbar - 275) / 25) ** 2))
    rc = 2 * sqrt((cpbar ** 7) / (cpbar ** 7 + 25 ** 7)) if cpbar > 0 else 0.0
    rt = -rc * sin(radians(2 * delta_theta))
    vl = dl / sl
    vc = dc / sc
    vh = dh / sh
    return sqrt(max(0.0, vl * vl + vc * vc + vh * vh + rt * vc * vh))


def delta_e_2000(c1: RGBA, c2: RGBA) -> float:
    return delta_e_2000_lab(rgba_to_lab(c1), rgba_to_lab(c2))
