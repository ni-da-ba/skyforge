from __future__ import annotations

import json
from pathlib import Path
from typing import Any


def emit_tail_lowering_outputs(result: dict[str, Any], out_dir: Path) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "tail_lowering.json").write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    m = result["metrics"]
    r = result["readiness"]
    c = result["topologyChecks"]
    lines = [
        f"digestSha256={result['digestSha256']}",
        f"v06AerodynamicPlacementCount={m['v06AerodynamicPlacementCount']}",
        f"v07AerodynamicPlacementCount={m['v07AerodynamicPlacementCount']}",
        f"verticalTailCellCount={m['verticalTailCellCount']}",
        f"horizontalTailCellCount={m['horizontalTailCellCount']}",
        f"resolvedFormerConflictCount={m['resolvedFormerConflictCount']}",
        f"verticalLongitudinalFirstMomentPreserved={c['verticalLongitudinalFirstMomentPreserved']}",
        f"verticalRelativeShapePreserved={c['verticalRelativeShapePreserved']}",
        f"verticalTailInternallyConnected={c['verticalTailInternallyConnected']}",
        f"verticalRootFaceAttachedToHorizontalTail={c['verticalRootFaceAttachedToHorizontalTail']}",
        f"tailJunctionLoweringPassed={r['tailJunctionLoweringPassed']}",
        f"surfaceStateResolutionComplete={r['surfaceStateResolutionComplete']}",
        f"probeSchematicEmissionReady={r['probeSchematicEmissionReady']}",
        "staticBlockers=" + ",".join(r["staticBlockers"]),
        "runtimeBlockers=" + ",".join(r["runtimeBlockers"]),
    ]
    (out_dir / "tail_lowering_validation.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
