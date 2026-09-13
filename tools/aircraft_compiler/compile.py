#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path

from assembly import AssemblyPlanError, plan_assembly
from assembly_render import emit_assembly_outputs
from blockspace import BlockspaceError, transcribe
from blockspace_render import emit_blockspace_outputs
from model import SpecError
from pilot_station import PilotStationError, realize_pilot_station
from pilot_station_render import emit_pilot_station_outputs
from propulsion import PropulsionRealizationError, realize_propulsion
from propulsion_render import emit_propulsion_outputs
from render import emit_outputs
from solver import solve
from surface_state import SurfaceStateError, resolve_surface_states
from surface_state_render import emit_surface_state_outputs
from tail_lowering import TailLoweringError, lower_tail_junction
from tail_lowering_render import emit_tail_lowering_outputs
from target_realizer import TargetRealizerError, preflight
from target_render import emit_target_outputs


def main() -> int:
    parser = argparse.ArgumentParser(description="Compile the bounded AIRCRAFT-001 analytical specimen")
    parser.add_argument("spec", type=Path)
    parser.add_argument("--blockspace-config", type=Path)
    parser.add_argument("--target-profile", type=Path)
    parser.add_argument("--propulsion-profile", type=Path)
    parser.add_argument("--surface-state-profile", type=Path)
    parser.add_argument("--tail-lowering-profile", type=Path)
    parser.add_argument("--pilot-station-profile", type=Path)
    parser.add_argument("--out", type=Path, default=Path("build/aircraft-compiler"))
    args = parser.parse_args()
    blockspace = assembly = target = propulsion = surface_state = tail_lowering = pilot_station = None
    try:
        spec = json.loads(args.spec.read_text(encoding="utf-8"))
        resolved = solve(spec)
        emit_outputs(resolved, args.out)
        if args.target_profile is not None and args.blockspace_config is None:
            raise TargetRealizerError("--target-profile requires --blockspace-config")
        if args.propulsion_profile is not None and args.target_profile is None:
            raise PropulsionRealizationError("--propulsion-profile requires --target-profile")
        if args.surface_state_profile is not None and args.propulsion_profile is None:
            raise SurfaceStateError("--surface-state-profile requires --propulsion-profile")
        if args.tail_lowering_profile is not None and args.surface_state_profile is None:
            raise TailLoweringError("--tail-lowering-profile requires --surface-state-profile")
        if args.pilot_station_profile is not None and args.tail_lowering_profile is None:
            raise PilotStationError("--pilot-station-profile requires --tail-lowering-profile")
        if args.blockspace_config is not None:
            config = json.loads(args.blockspace_config.read_text(encoding="utf-8"))
            blockspace = transcribe(resolved, config)
            if not blockspace["validation"]["passed"]:
                raise BlockspaceError("block-space transcription failed validation: " + json.dumps(blockspace["validation"], sort_keys=True))
            emit_blockspace_outputs(blockspace, resolved, args.out)
            assembly = plan_assembly(blockspace)
            if not assembly["validation"]["passed"]:
                raise AssemblyPlanError("assembly planning failed validation: " + json.dumps(assembly["validation"], sort_keys=True))
            emit_assembly_outputs(assembly, args.out)
            if args.target_profile is not None:
                target = preflight(assembly, json.loads(args.target_profile.read_text(encoding="utf-8")))
                emit_target_outputs(target, args.out)
                if args.propulsion_profile is not None:
                    propulsion = realize_propulsion(assembly, target, json.loads(args.propulsion_profile.read_text(encoding="utf-8")))
                    if not propulsion["validation"]["passed"]:
                        raise PropulsionRealizationError("propulsion realization failed validation: " + json.dumps(propulsion["topologyChecks"], sort_keys=True))
                    emit_propulsion_outputs(propulsion, args.out)
                    if args.surface_state_profile is not None:
                        surface_state = resolve_surface_states(target, propulsion, json.loads(args.surface_state_profile.read_text(encoding="utf-8")))
                        emit_surface_state_outputs(surface_state, args.out)
                        if args.tail_lowering_profile is not None:
                            tail_lowering = lower_tail_junction(target, surface_state, json.loads(args.tail_lowering_profile.read_text(encoding="utf-8")))
                            if not tail_lowering["validation"]["passed"]:
                                raise TailLoweringError("tail lowering failed validation: " + json.dumps(tail_lowering["topologyChecks"], sort_keys=True))
                            emit_tail_lowering_outputs(tail_lowering, args.out)
                            if args.pilot_station_profile is not None:
                                pilot_station = realize_pilot_station(target, propulsion, tail_lowering, json.loads(args.pilot_station_profile.read_text(encoding="utf-8")))
                                if not pilot_station["validation"]["passed"]:
                                    raise PilotStationError("pilot station realization failed validation: " + json.dumps(pilot_station["topologyChecks"], sort_keys=True))
                                emit_pilot_station_outputs(pilot_station, args.out)
    except (OSError, json.JSONDecodeError, SpecError, BlockspaceError, AssemblyPlanError, TargetRealizerError, PropulsionRealizationError, SurfaceStateError, TailLoweringError, PilotStationError, ValueError) as exc:
        raise SystemExit(f"aircraft compiler error: {exc}") from exc

    summary = {"assetId": resolved["assetId"], "compilerVersion": resolved["compilerVersion"], "digestSha256": resolved["digestSha256"], "validation": resolved["validation"], "output": str(args.out)}
    if blockspace is not None:
        summary["blockspace"] = {"assetId": blockspace["assetId"], "compilerVersion": blockspace["compilerVersion"], "digestSha256": blockspace["digestSha256"], "validation": blockspace["validation"]}
    if assembly is not None:
        summary["assembly"] = {"assetId": assembly["assetId"], "compilerVersion": assembly["compilerVersion"], "digestSha256": assembly["digestSha256"], "metrics": assembly["metrics"], "validation": assembly["validation"]}
    if target is not None:
        summary["targetPreflight"] = {"assetId": target["assetId"], "compilerVersion": target["compilerVersion"], "digestSha256": target["digestSha256"], "metrics": target["metrics"], "readiness": target["readiness"], "validation": target["validation"]}
    if propulsion is not None:
        summary["propulsion"] = {"assetId": propulsion["assetId"], "compilerVersion": propulsion["compilerVersion"], "digestSha256": propulsion["digestSha256"], "metrics": propulsion["metrics"], "readiness": propulsion["readiness"], "validation": propulsion["validation"]}
    if surface_state is not None:
        summary["surfaceState"] = {"assetId": surface_state["assetId"], "compilerVersion": surface_state["compilerVersion"], "digestSha256": surface_state["digestSha256"], "metrics": surface_state["metrics"], "readiness": surface_state["readiness"], "validation": surface_state["validation"]}
    if tail_lowering is not None:
        summary["tailLowering"] = {"assetId": tail_lowering["assetId"], "compilerVersion": tail_lowering["compilerVersion"], "digestSha256": tail_lowering["digestSha256"], "metrics": tail_lowering["metrics"], "readiness": tail_lowering["readiness"], "validation": tail_lowering["validation"]}
    if pilot_station is not None:
        summary["pilotStation"] = {"assetId": pilot_station["assetId"], "compilerVersion": pilot_station["compilerVersion"], "digestSha256": pilot_station["digestSha256"], "metrics": pilot_station["metrics"], "readiness": pilot_station["readiness"], "validation": pilot_station["validation"]}
    print(json.dumps(summary, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
