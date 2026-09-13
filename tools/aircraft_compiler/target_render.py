from __future__ import annotations

import html
import json
from pathlib import Path
from typing import Any


def emit_target_outputs(result: dict[str, Any], out_dir: Path) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "target_preflight.json").write_text(
        json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    r = result["readiness"]
    m = result["metrics"]
    lines = [
        "AIRCRAFT-001 v0.4 Minecraft target-realizer preflight",
        f"validationPassed={result['validation']['passed']}",
        f"staticCapabilityCoveragePassed={r['staticCapabilityCoveragePassed']}",
        f"schematicEmissionReady={r['schematicEmissionReady']}",
        f"runtimeQualificationReady={r['runtimeQualificationReady']}",
        f"flightQualified={r['flightQualified']}",
        f"assemblySiteCount={m['assemblySiteCount']}",
        f"mappedSiteCount={m['mappedSiteCount']}",
        f"unresolvedSiteCount={m['unresolvedSiteCount']}",
        f"unresolvedStateOrResourceCount={m['unresolvedStateOrResourceCount']}",
        f"companionFailureCount={m['companionFailureCount']}",
        f"runtimeObligationCount={m['runtimeObligationCount']}",
        "blockers=" + ",".join(r["blockers"]),
        f"digestSha256={result['digestSha256']}",
    ]
    (out_dir / "target_validation.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")

    blockers = r["blockers"] or ["none"]
    obligations = result["runtimeObligations"]
    rows = []
    y = 360
    for obligation in obligations[:8]:
        rows.append(
            f'<text x="48" y="{y}" font-family="system-ui, sans-serif" font-size="14">'
            f'• {html.escape(str(obligation["id"]))}: {html.escape(str(obligation["status"]))}</text>'
        )
        y += 25
    svg = f'''<svg xmlns="http://www.w3.org/2000/svg" width="860" height="760" viewBox="0 0 860 760">
<rect width="860" height="760" fill="white"/>
<text x="48" y="58" font-family="system-ui, sans-serif" font-size="26" font-weight="700">AIRCRAFT-001 v0.4 target-realizer preflight</text>
<text x="48" y="94" font-family="system-ui, sans-serif" font-size="15">{html.escape(result['targetProfileId'])}</text>
<text x="48" y="142" font-family="system-ui, sans-serif" font-size="19" font-weight="700">Static capability coverage: {str(r['staticCapabilityCoveragePassed']).upper()}</text>
<text x="48" y="174" font-family="system-ui, sans-serif" font-size="19" font-weight="700">Schematic emission ready: {str(r['schematicEmissionReady']).upper()}</text>
<text x="48" y="206" font-family="system-ui, sans-serif" font-size="19" font-weight="700">Flight qualified: {str(r['flightQualified']).upper()}</text>
<text x="48" y="254" font-family="system-ui, sans-serif" font-size="15">Sites: {m['mappedSiteCount']}/{m['assemblySiteCount']} capability-mapped</text>
<text x="48" y="280" font-family="system-ui, sans-serif" font-size="15">Contraption companion failures: {m['companionFailureCount']}</text>
<text x="48" y="306" font-family="system-ui, sans-serif" font-size="15">Blockers: {html.escape(', '.join(blockers))}</text>
<text x="48" y="340" font-family="system-ui, sans-serif" font-size="17" font-weight="700">Runtime obligations — intentionally unverified</text>
{''.join(rows)}
<text x="48" y="690" font-family="ui-monospace, monospace" font-size="11">digest: {html.escape(result['digestSha256'])}</text>
<text x="48" y="718" font-family="system-ui, sans-serif" font-size="13">A green static preflight is not a flight certificate.</text>
</svg>'''
    (out_dir / "target_mobile_review.svg").write_text(svg, encoding="utf-8")
