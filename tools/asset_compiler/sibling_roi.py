"""Bounded Stage-7 reuse/ROI evidence for issue #488 Guild siblings."""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from typing import Any


def flatten_config(value: Any, prefix: str = "") -> dict[str, Any]:
    """Flatten named config fields while treating lists as one semantic field."""
    if isinstance(value, dict):
        out: dict[str, Any] = {}
        for key in sorted(value):
            child = f"{prefix}.{key}" if prefix else str(key)
            out.update(flatten_config(value[key], child))
        return out
    return {prefix: value}


def module_family(name: str) -> str:
    """Collapse deterministic per-instance numeric suffixes to reusable grammar families."""
    return re.sub(r"_\d+$", "", name)


def build_report(base_spec: dict[str, Any], sibling_spec: dict[str, Any], base: dict[str, Any], sibling: dict[str, Any], base_adapter: dict[str, Any], sibling_adapter: dict[str, Any]) -> dict[str, Any]:
    base_fields = flatten_config(base_spec)
    sibling_fields = flatten_config(sibling_spec)
    union = sorted(set(base_fields) | set(sibling_fields))
    shared = sorted(set(base_fields) & set(sibling_fields))
    changed = [path for path in shared if base_fields[path] != sibling_fields[path]]
    unchanged = [path for path in shared if base_fields[path] == sibling_fields[path]]
    base_only = sorted(set(base_fields) - set(sibling_fields))
    sibling_only = sorted(set(sibling_fields) - set(base_fields))

    base_modules = set(base.get("moduleBlockCounts", {}))
    sibling_modules = set(sibling.get("moduleBlockCounts", {}))
    sibling_module_reuse = len(base_modules & sibling_modules) / max(len(sibling_modules), 1)
    base_module_families = {module_family(name) for name in base_modules}
    sibling_module_families = {module_family(name) for name in sibling_modules}
    sibling_module_family_reuse = len(base_module_families & sibling_module_families) / max(len(sibling_module_families), 1)
    config_reuse = len(unchanged) / max(len(union), 1)

    sibling_realized = int(sibling_adapter["realizedGeometry"]["targetCellCountAfter"])
    base_realized = int(base_adapter["realizedGeometry"]["targetCellCountAfter"])
    changed_design_fields = [p for p in changed if p not in {"assetId", "seed", "family.scale"}]

    return {
        "schemaVersion": "issue-488-sibling-roi-v0.1",
        "baseAssetId": base["assetId"],
        "siblingAssetId": sibling["assetId"],
        "sameCompilerVersion": base["compilerVersion"] == sibling["compilerVersion"],
        "compilerVersion": sibling["compilerVersion"],
        "configFieldCountUnion": len(union),
        "unchangedConfigFieldCount": len(unchanged),
        "changedConfigFields": changed,
        "changedDesignFields": changed_design_fields,
        "baseOnlyConfigFields": base_only,
        "siblingOnlyConfigFields": sibling_only,
        "configFieldReuseFraction": config_reuse,
        "baseModuleCount": len(base_modules),
        "siblingModuleCount": len(sibling_modules),
        "sharedModuleCount": len(base_modules & sibling_modules),
        "siblingModuleReuseFraction": sibling_module_reuse,
        "siblingOnlyModules": sorted(sibling_modules - base_modules),
        "baseOnlyModules": sorted(base_modules - sibling_modules),
        "baseModuleFamilyCount": len(base_module_families),
        "siblingModuleFamilyCount": len(sibling_module_families),
        "sharedModuleFamilyCount": len(base_module_families & sibling_module_families),
        "siblingModuleFamilyReuseFraction": sibling_module_family_reuse,
        "siblingOnlyModuleFamilies": sorted(sibling_module_families - base_module_families),
        "baseOnlyModuleFamilies": sorted(base_module_families - sibling_module_families),
        "baseDigestSha256": base["digestSha256"],
        "siblingDigestSha256": sibling["digestSha256"],
        "baseArchitectureCellCount": int(base["blockCount"]),
        "siblingArchitectureCellCount": int(sibling["blockCount"]),
        "baseRealizedCellCount": base_realized,
        "siblingRealizedCellCount": sibling_realized,
        "generatedRealizedCellsPerChangedDesignField": sibling_realized / max(len(changed_design_fields), 1),
        "baseResolvedParameters": base["layout"]["resolvedParameters"],
        "siblingResolvedParameters": sibling["layout"]["resolvedParameters"],
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Emit bounded Guild sibling reuse/ROI evidence")
    parser.add_argument("--base-spec", type=Path, required=True)
    parser.add_argument("--sibling-spec", type=Path, required=True)
    parser.add_argument("--base-output", type=Path, required=True)
    parser.add_argument("--sibling-output", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()

    load = lambda path: json.loads(path.read_text(encoding="utf-8"))
    report = build_report(
        load(args.base_spec),
        load(args.sibling_spec),
        load(args.base_output / "resolved.json"),
        load(args.sibling_output / "resolved.json"),
        load(args.base_output / "minecraft_adapter.json"),
        load(args.sibling_output / "minecraft_adapter.json"),
    )
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
