from __future__ import annotations

import json
from html import escape
from pathlib import Path


ROLE_FILL = {
    "fuselage_spine": "#7f6a52",
    "wing_surface_intent": "#9fb4cc",
    "horizontal_tail_surface_intent": "#bcc9d5",
    "vertical_tail_surface_intent": "#bcc9d5",
    "wing_attach_intent": "#555555",
    "tail_attach_intent": "#555555",
}


def _text(x, y, value, size=18, weight=400, anchor="start", fill="#111"):
    return (
        f'<text x="{x}" y="{y}" font-family="sans-serif" font-size="{size}" '
        f'font-weight="{weight}" text-anchor="{anchor}" fill="{fill}">{escape(str(value))}</text>'
    )


def _line(x1, y1, x2, y2, stroke="#555", sw=1, dash=None):
    extra = f' stroke-dasharray="{dash}"' if dash else ""
    return (
        f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" '
        f'stroke="{stroke}" stroke-width="{sw}"{extra}/>'
    )


def _rect(x, y, width, height, fill, stroke="#222", sw=0.6, opacity=1.0):
    return (
        f'<rect x="{x}" y="{y}" width="{width}" height="{height}" fill="{fill}" '
        f'stroke="{stroke}" stroke-width="{sw}" opacity="{opacity}"/>'
    )


def _panel(cells, x0, y0, width, height, projection, title):
    points = []
    for cell in cells:
        if projection == "top":
            a, b = cell["x"], cell["z"]
        elif projection == "side":
            a, b = cell["x"], cell["y"]
        elif projection == "front":
            a, b = cell["z"], cell["y"]
        else:
            raise ValueError(projection)
        points.append((a, b, cell["role"]))
    if not points:
        return ""

    amin = min(point[0] for point in points)
    amax = max(point[0] for point in points)
    bmin = min(point[1] for point in points)
    bmax = max(point[1] for point in points)
    nx = amax - amin + 1
    ny = bmax - bmin + 1
    cell_size = min((width - 24) / max(1, nx), (height - 54) / max(1, ny))
    ox = x0 + (width - cell_size * nx) / 2
    oy = y0 + 42 + (height - 54 - cell_size * ny) / 2
    body = _text(x0 + 12, y0 + 26, title, 17, 700)

    role_order = {name: index for index, name in enumerate(ROLE_FILL)}
    chosen = {}
    for a, b, role in points:
        key = (a, b)
        if key not in chosen or role_order.get(role, 999) < role_order.get(chosen[key], 999):
            chosen[key] = role
    for (a, b), role in sorted(chosen.items()):
        px = ox + (a - amin) * cell_size
        py = oy + (bmax - b) * cell_size
        body += _rect(
            px,
            py,
            cell_size,
            cell_size,
            ROLE_FILL.get(role, "#dddddd"),
            sw=max(0.4, cell_size * 0.035),
        )
    return body


def mobile_blockspace_review(block: dict, design: dict) -> str:
    del design  # Continuous design identity is already bound by sourceDesignDigestSha256.
    metrics = block["metrics"]
    scale = block["coordinateSystem"]["blocksPerMeter"]
    cells = block["cells"]
    width, height = 1200, 980
    body = _text(36, 42, "AIRCRAFT-001 v0.2 — mathematical block-space transcription", 25, 700)
    body += _text(
        36,
        70,
        "Target-neutral lattice skeleton; concrete Create/Aeronautics blocks intentionally unresolved",
        15,
        400,
        fill="#555",
    )
    body += _panel(cells, 30, 92, 740, 335, "top", "TOP — occupied intent cells")
    body += _panel(cells, 30, 438, 740, 265, "side", "SIDE — occupied intent cells")
    body += _panel(cells, 790, 92, 380, 335, "front", "FRONT — occupied intent cells")

    x, y = 800, 470
    rows = [
        ("Scale", f"{scale:g} blocks / m"),
        ("Intent cells", metrics["cellCount"]),
        ("6-neighbor components", metrics["connectedComponents6Neighbor"]),
        ("Mirror symmetry", "PASS" if metrics["mirrorSymmetrySatisfied"] else "FAIL"),
        ("Prop disk clearance", "PASS" if metrics["propellerDiskClear"] else "FAIL"),
        ("CG lattice error", f"{metrics['cgStationQuantizationErrorBlocks']:.3f} blocks"),
        (
            "Wing span",
            f"{metrics['continuousWingSpanM']:.2f} m → {metrics['realizedWingSpanM']:.2f} m",
        ),
        (
            "H-tail span",
            f"{metrics['continuousHorizontalTailSpanM']:.2f} m → "
            f"{metrics['realizedHorizontalTailSpanM']:.2f} m",
        ),
        (
            "V-tail height",
            f"{metrics['continuousVerticalTailHeightM']:.2f} m → "
            f"{metrics['realizedVerticalTailHeightM']:.2f} m",
        ),
    ]
    body += _text(x, y, "Discrete QA", 19, 700)
    y += 30
    for key, value in rows:
        body += _text(x, y, key, 14, 600)
        body += _text(1160, y, value, 14, 400, anchor="end")
        y += 25

    body += _line(36, 744, 1164, 744, stroke="#bbbbbb")
    body += _text(36, 782, "What this proves", 18, 700)
    body += _text(36, 808, "• continuous planform → deterministic integer lattice", 15)
    body += _text(36, 832, "• bounded quantization error, symmetry, connectivity, propeller clearance", 15)
    body += _text(36, 856, "• capability intents remain separate from concrete target resources", 15)
    body += _text(36, 894, "What this does NOT prove", 18, 700)
    body += _text(
        36,
        920,
        "Create/Aeronautics block legality, runtime assembly, in-engine COM/forces, stability/control, structural strength",
        14,
        fill="#555",
    )
    status = "PASS" if block["validation"]["passed"] else "FAIL"
    body += _text(
        1160,
        952,
        status,
        22,
        700,
        anchor="end",
        fill="#1f6b3a" if status == "PASS" else "#9b2226",
    )
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" '
        f'viewBox="0 0 {width} {height}"><rect width="100%" height="100%" fill="#fff"/>{body}</svg>\n'
    )


def emit_blockspace_outputs(block: dict, design: dict, out: Path) -> None:
    out.mkdir(parents=True, exist_ok=True)
    (out / "blockspace_resolved.json").write_text(
        json.dumps(block, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    (out / "blockspace_mobile_review.svg").write_text(
        mobile_blockspace_review(block, design),
        encoding="utf-8",
    )
    validation = block["validation"]
    metrics = block["metrics"]
    text = (
        ("PASS\n" if validation["passed"] else "FAIL\n")
        + f"digestSha256={block['digestSha256']}\n"
        + f"cellCount={metrics['cellCount']}\n"
        + f"components={metrics['connectedComponents6Neighbor']}\n"
        + f"mirrorSymmetry={metrics['mirrorSymmetrySatisfied']}\n"
        + f"propellerDiskClear={metrics['propellerDiskClear']}\n"
        + f"cgStationQuantizationErrorBlocks={metrics['cgStationQuantizationErrorBlocks']:.12f}\n"
        + f"scope={validation['scope']}\n"
    )
    (out / "blockspace_validation.txt").write_text(text, encoding="utf-8")
