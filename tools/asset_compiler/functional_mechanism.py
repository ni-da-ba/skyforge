from __future__ import annotations

import hashlib
import json
from copy import deepcopy
from typing import Any

from model import SpecError

SCHEMA_VERSION = "mech-0.1"
PLAN_SCHEMA = "skyforge.functional-mechanism-plan.v1"
COMPILER_VERSION = "mech-0.1-fixed-world"
MECH_002_COMPILER_VERSION = "mech-0.2-fixed-world"
MECH_003_COMPILER_VERSION = "mech-0.3-fixed-world"
EXPECTED_TARGET_STACK = "C11_FLIGHT_EXACT_2026-09-05"
MECH_001_CAPABILITY = "CREATE_KINETIC_NETWORK_LIFECYCLE"
MECH_002_CAPABILITY = "CREATE_WATER_WHEEL_SOURCE_LIFECYCLE"
MECH_003_CAPABILITY = "CREATE_WORLD_ITEM_CUT_PRESS_LIFECYCLE"

_ALLOWED_MECHANISM_KEYS = {
    "orientation",
    "sourceRole",
    "sourceEnvironmentRole",
    "transmissionLength",
    "endpointRole",
    "mountingRole",
    "frontClearance",
    "workflowRole",
    "stagingRole",
    "powerRole",
    "operatorClearance",
}


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise SpecError(message)


def _canonical_digest(plan: dict[str, Any]) -> str:
    digest_input = deepcopy(plan)
    digest_input.pop("digestSha256", None)
    payload = json.dumps(digest_input, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(payload).hexdigest()


def _block_state(name: str, **properties: str) -> dict[str, Any]:
    state: dict[str, Any] = {"name": name}
    if properties:
        state["properties"] = dict(sorted(properties.items()))
    return state


def _validate_common_spec(spec: dict[str, Any]) -> dict[str, Any]:
    """Validate the bounded semantic envelope shared by fixed-world mechanism specimens."""
    _require(spec.get("schemaVersion") == SCHEMA_VERSION, f"expected schemaVersion {SCHEMA_VERSION}")
    _require(isinstance(spec.get("assetId"), str) and spec["assetId"], "assetId is required")
    _require(
        isinstance(spec.get("seed"), int) and not isinstance(spec.get("seed"), bool),
        "integer seed is required",
    )
    _require(
        spec.get("targetStackAuthority") == EXPECTED_TARGET_STACK,
        f"targetStackAuthority must be {EXPECTED_TARGET_STACK}",
    )

    mechanism = spec.get("mechanism")
    _require(isinstance(mechanism, dict), "mechanism object is required")
    unknown = sorted(set(mechanism) - _ALLOWED_MECHANISM_KEYS)
    _require(not unknown, f"unsupported mechanism fields: {unknown}")
    return mechanism


def _require_accepted_agent_c_capability(
    capability_ledger: dict[str, Any],
    capability_id: str,
    target_stack_authority: str,
) -> tuple[dict[str, Any], dict[str, Any]]:
    """Return accepted capability/evidence only when Agent C may consume it."""
    capabilities = capability_ledger.get("capabilities")
    _require(isinstance(capabilities, dict), "capability ledger has no capabilities object")
    capability = capabilities.get(capability_id)
    _require(isinstance(capability, dict), f"{capability_id} is missing from capability ledger")
    _require(capability.get("status") == "accepted", f"{capability_id} is not accepted")
    _require(capability.get("verification_level") == "L2", f"{capability_id} must be L2")
    _require(
        capability.get("target_stack_authority") == target_stack_authority,
        "platform target-stack authority does not match mechanism target",
    )
    authority = capability.get("production_authority_for_agents")
    _require(isinstance(authority, dict) and authority.get("C") is True, "Agent C lacks platform authority")
    evidence = capability.get("latest_accepted_evidence")
    _require(isinstance(evidence, dict) and evidence.get("result") == "PASS", "platform evidence is not PASS")
    _require(
        isinstance(evidence.get("workflow_run"), int) and not isinstance(evidence.get("workflow_run"), bool),
        "platform evidence workflow_run is missing",
    )
    _require(isinstance(evidence.get("commit"), str) and evidence["commit"], "platform evidence commit is missing")
    return capability, evidence


def _front_clearance(mechanism: dict[str, Any]) -> int:
    front_clearance = mechanism.get("frontClearance")
    _require(
        isinstance(front_clearance, int) and not isinstance(front_clearance, bool) and 1 <= front_clearance <= 6,
        "frontClearance must be an integer in [1, 6]",
    )
    return front_clearance


def _lower_mech_001_airflow_bench(mechanism: dict[str, Any]) -> dict[str, Any]:
    """Lower only the accepted MECH-001 qualification-source specimen."""
    _require(mechanism.get("orientation") == "east", "MECH-001 v0.1 supports only east orientation")
    _require(
        mechanism.get("sourceRole") == "qualified_kinetic_source",
        "MECH-001 sourceRole must remain qualified_kinetic_source",
    )
    _require("sourceEnvironmentRole" not in mechanism, "MECH-001 does not accept a source environment role")
    _require(mechanism.get("transmissionLength") == 1, "MECH-001 requires exactly one relay")
    _require(mechanism.get("endpointRole") == "airflow", "MECH-001 endpointRole must be airflow")
    _require(mechanism.get("mountingRole") == "masonry_pad", "MECH-001 mountingRole must be masonry_pad")
    front_clearance = _front_clearance(mechanism)

    placements: list[dict[str, Any]] = []
    for x in range(3):
        for z in range(3):
            placements.append({
                "id": f"support_{x}_{z}",
                "pos": [x, 0, z],
                "mechanicalRole": "mounting_support",
                "blockState": _block_state("minecraft:stone_bricks"),
            })

    placements.extend([
        {
            "id": "source",
            "pos": [0, 1, 1],
            "mechanicalRole": "kinetic_source",
            "blockState": _block_state("create:creative_motor", facing="east"),
        },
        {
            "id": "relay_0",
            "pos": [1, 1, 1],
            "mechanicalRole": "required_relay",
            "blockState": _block_state("create:shaft", axis="x"),
        },
        {
            "id": "endpoint",
            "pos": [2, 1, 1],
            "mechanicalRole": "airflow_endpoint",
            "blockState": _block_state("create:encased_fan", facing="east"),
        },
    ])

    occupied = {tuple(p["pos"]) for p in placements}
    _require(len(occupied) == len(placements), "compiled mechanism has overlapping placements")
    clearance_cells = [[x, 1, 1] for x in range(3, 3 + front_clearance)]
    _require(not occupied.intersection(map(tuple, clearance_cells)), "fan discharge clearance overlaps a placement")
    support_requirements = [
        {"placementId": "source", "supportBelow": [0, 0, 1]},
        {"placementId": "relay_0", "supportBelow": [1, 0, 1]},
        {"placementId": "endpoint", "supportBelow": [2, 0, 1]},
    ]
    return {
        "frontClearance": front_clearance,
        "placements": placements,
        "clearanceCells": clearance_cells,
        "supportRequirements": support_requirements,
    }


def _lower_mech_002_waterwheel_airflow_bench(mechanism: dict[str, Any]) -> dict[str, Any]:
    """Lower the bounded PLATFORM-009 environmental source contract into one workshop mechanism."""
    _require(mechanism.get("orientation") == "east", "MECH-002 v0.2 supports only east orientation")
    _require(
        mechanism.get("sourceRole") == "renewable_stationary_source",
        "MECH-002 sourceRole must be renewable_stationary_source",
    )
    _require(
        mechanism.get("sourceEnvironmentRole") == "bounded_falling_water",
        "MECH-002 sourceEnvironmentRole must be bounded_falling_water",
    )
    _require(mechanism.get("transmissionLength") == 1, "MECH-002 requires exactly one relay")
    _require(mechanism.get("endpointRole") == "airflow", "MECH-002 endpointRole must be airflow")
    _require(mechanism.get("mountingRole") == "masonry_pad", "MECH-002 mountingRole must be masonry_pad")
    front_clearance = _front_clearance(mechanism)

    placements: list[dict[str, Any]] = []
    for x in range(4):
        for z in range(5):
            placements.append({
                "id": f"pad_{x}_{z}",
                "pos": [x, 0, z],
                "mechanicalRole": "mounting_support",
                "blockState": _block_state("minecraft:stone_bricks"),
            })

    placements.extend([
        {
            "id": "flow_support",
            "pos": [1, 1, 1],
            "mechanicalRole": "environmental_support",
            "blockState": _block_state("minecraft:stone_bricks"),
        },
        {
            "id": "axle_pier_lower",
            "pos": [0, 1, 2],
            "mechanicalRole": "axle_mount_support",
            "blockState": _block_state("minecraft:stone_bricks"),
        },
        {
            "id": "axle_pier_upper",
            "pos": [0, 2, 2],
            "mechanicalRole": "axle_mount_support",
            "blockState": _block_state("minecraft:stone_bricks"),
        },
        {
            "id": "relay_support",
            "pos": [2, 1, 2],
            "mechanicalRole": "mounting_support",
            "blockState": _block_state("minecraft:stone_bricks"),
        },
        {
            "id": "endpoint_support",
            "pos": [3, 1, 2],
            "mechanicalRole": "mounting_support",
            "blockState": _block_state("minecraft:stone_bricks"),
        },
        {
            "id": "source",
            "pos": [1, 2, 2],
            "mechanicalRole": "kinetic_source",
            "blockState": _block_state("create:water_wheel", facing="east"),
        },
        {
            "id": "relay_0",
            "pos": [2, 2, 2],
            "mechanicalRole": "required_relay",
            "blockState": _block_state("create:shaft", axis="x"),
        },
        {
            "id": "endpoint",
            "pos": [3, 2, 2],
            "mechanicalRole": "airflow_endpoint",
            "blockState": _block_state("create:encased_fan", facing="east"),
        },
        {
            "id": "source_flow_0",
            "pos": [1, 2, 1],
            "mechanicalRole": "environmental_source_precondition",
            "blockState": _block_state("minecraft:water", level="8"),
        },
        {
            "id": "source_feeder",
            "pos": [1, 3, 1],
            "mechanicalRole": "environmental_source_feeder",
            "blockState": _block_state("minecraft:water", level="0"),
        },
        {
            "id": "chute_west_lower",
            "pos": [0, 2, 1],
            "mechanicalRole": "environmental_containment",
            "blockState": _block_state("minecraft:stone_bricks"),
        },
        {
            "id": "chute_east_lower",
            "pos": [2, 2, 1],
            "mechanicalRole": "environmental_containment",
            "blockState": _block_state("minecraft:stone_bricks"),
        },
        {
            "id": "chute_north_lower",
            "pos": [1, 2, 0],
            "mechanicalRole": "environmental_containment",
            "blockState": _block_state("minecraft:stone_bricks"),
        },
        {
            "id": "chute_west_upper",
            "pos": [0, 3, 1],
            "mechanicalRole": "environmental_containment",
            "blockState": _block_state("minecraft:stone_bricks"),
        },
        {
            "id": "chute_east_upper",
            "pos": [2, 3, 1],
            "mechanicalRole": "environmental_containment",
            "blockState": _block_state("minecraft:stone_bricks"),
        },
        {
            "id": "chute_north_upper",
            "pos": [1, 3, 0],
            "mechanicalRole": "environmental_containment",
            "blockState": _block_state("minecraft:stone_bricks"),
        },
        {
            "id": "chute_south_upper",
            "pos": [1, 3, 2],
            "mechanicalRole": "environmental_containment",
            "blockState": _block_state("minecraft:stone_bricks"),
        },
    ])

    occupied = {tuple(p["pos"]) for p in placements}
    _require(len(occupied) == len(placements), "compiled MECH-002 mechanism has overlapping placements")
    wheel_clearance = [[1, 1, 2], [1, 2, 3]]
    discharge_clearance = [[x, 2, 2] for x in range(4, 4 + front_clearance)]
    clearance_cells = wheel_clearance + discharge_clearance
    _require(not occupied.intersection(map(tuple, clearance_cells)), "MECH-002 moving clearance overlaps a placement")
    support_requirements = [
        {"placementId": "source_flow_0", "supportBelow": [1, 1, 1]},
        {"placementId": "axle_pier_upper", "supportBelow": [0, 1, 2]},
        {"placementId": "relay_0", "supportBelow": [2, 1, 2]},
        {"placementId": "endpoint", "supportBelow": [3, 1, 2]},
    ]
    return {
        "frontClearance": front_clearance,
        "placements": placements,
        "clearanceCells": clearance_cells,
        "supportRequirements": support_requirements,
    }



def _operator_clearance(mechanism: dict[str, Any]) -> int:
    clearance = mechanism.get("operatorClearance")
    _require(
        isinstance(clearance, int) and not isinstance(clearance, bool) and 1 <= clearance <= 3,
        "operatorClearance must be an integer in [1, 3]",
    )
    return clearance


def _lower_mech_003_portable_engine_workshop(mechanism: dict[str, Any]) -> dict[str, Any]:
    """Lower one compact beltless manual CUT/PRESS workshop cell."""
    _require(mechanism.get("orientation") == "east", "MECH-003 v0.3 supports only east orientation")
    _require(
        mechanism.get("workflowRole") == "manual_sequenced_processing",
        "MECH-003 workflowRole must be manual_sequenced_processing",
    )
    _require(
        mechanism.get("stagingRole") == "world_item_manual_handoff",
        "MECH-003 stagingRole must be world_item_manual_handoff",
    )
    _require(
        mechanism.get("powerRole") == "qualified_stationary_kinetic",
        "MECH-003 powerRole must be qualified_stationary_kinetic",
    )
    _require(mechanism.get("mountingRole") == "masonry_pad", "MECH-003 mountingRole must be masonry_pad")
    _require("sourceRole" not in mechanism, "MECH-003 does not accept a sourceRole")
    _require("sourceEnvironmentRole" not in mechanism, "MECH-003 does not accept a source environment role")
    _require("endpointRole" not in mechanism, "MECH-003 does not accept an endpointRole")
    _require("transmissionLength" not in mechanism, "MECH-003 does not accept transmissionLength")
    _require("frontClearance" not in mechanism, "MECH-003 does not accept frontClearance")
    operator_clearance = _operator_clearance(mechanism)

    placements: list[dict[str, Any]] = []
    pad_z_max = 2 + operator_clearance
    for x in range(7):
        for z in range(pad_z_max + 1):
            placements.append({
                "id": f"pad_{x}_{z}",
                "pos": [x, 0, z],
                "mechanicalRole": "mounting_support",
                "blockState": _block_state("minecraft:stone_bricks"),
            })

    placements.extend([
        {
            "id": "cut_power_source",
            "pos": [0, 1, 2],
            "mechanicalRole": "qualification_power_source",
            "blockState": _block_state("create:creative_motor", facing="east"),
        },
        {
            "id": "cut_power_relay",
            "pos": [1, 1, 2],
            "mechanicalRole": "qualification_power_relay",
            "blockState": _block_state("create:shaft", axis="x"),
        },
        {
            "id": "cut_station",
            "pos": [2, 1, 2],
            "mechanicalRole": "cut_station",
            "blockState": _block_state(
                "create:mechanical_saw", facing="up", axis_along_first="true", flipped="false"
            ),
        },
        {
            "id": "press_power_pier_lower",
            "pos": [4, 1, 2],
            "mechanicalRole": "station_support",
            "blockState": _block_state("minecraft:stone_bricks"),
        },
        {
            "id": "press_power_pier_upper",
            "pos": [4, 2, 2],
            "mechanicalRole": "station_support",
            "blockState": _block_state("minecraft:stone_bricks"),
        },
        {
            "id": "press_relay_pier_lower",
            "pos": [5, 1, 2],
            "mechanicalRole": "station_support",
            "blockState": _block_state("minecraft:stone_bricks"),
        },
        {
            "id": "press_relay_pier_upper",
            "pos": [5, 2, 2],
            "mechanicalRole": "station_support",
            "blockState": _block_state("minecraft:stone_bricks"),
        },
        {
            "id": "press_world_surface",
            "pos": [6, 1, 2],
            "mechanicalRole": "world_item_surface",
            "blockState": _block_state("minecraft:stone_bricks"),
        },
        {
            "id": "press_frame_left_lower",
            "pos": [6, 1, 1],
            "mechanicalRole": "station_support",
            "blockState": _block_state("minecraft:stone_bricks"),
        },
        {
            "id": "press_frame_left_upper",
            "pos": [6, 2, 1],
            "mechanicalRole": "station_support",
            "blockState": _block_state("minecraft:stone_bricks"),
        },
        {
            "id": "press_frame_right_lower",
            "pos": [6, 1, 3],
            "mechanicalRole": "station_support",
            "blockState": _block_state("minecraft:stone_bricks"),
        },
        {
            "id": "press_frame_right_upper",
            "pos": [6, 2, 3],
            "mechanicalRole": "station_support",
            "blockState": _block_state("minecraft:stone_bricks"),
        },
        {
            "id": "press_power_source",
            "pos": [4, 3, 2],
            "mechanicalRole": "qualification_power_source",
            "blockState": _block_state("create:creative_motor", facing="east"),
        },
        {
            "id": "press_power_relay",
            "pos": [5, 3, 2],
            "mechanicalRole": "qualification_power_relay",
            "blockState": _block_state("create:shaft", axis="x"),
        },
        {
            "id": "press_station",
            "pos": [6, 3, 2],
            "mechanicalRole": "press_station",
            "blockState": _block_state("create:mechanical_press", facing="east"),
        },
    ])

    occupied = {tuple(p["pos"]) for p in placements}
    _require(len(occupied) == len(placements), "compiled MECH-003 workshop has overlapping placements")

    station_clearance = [[2, 2, 2], [2, 3, 2], [6, 2, 2]]
    handoff_clearance: list[list[int]] = []
    for z in range(3, pad_z_max + 1):
        for x in range(1, 6):
            handoff_clearance.append([x, 1, z])
    handoff_clearance.append([6, 1, pad_z_max])
    clearance_cells = station_clearance + handoff_clearance
    _require(not occupied.intersection(map(tuple, clearance_cells)), "MECH-003 operator clearance overlaps a placement")

    support_requirements = [
        {"placementId": "cut_power_source", "supportBelow": [0, 0, 2]},
        {"placementId": "cut_power_relay", "supportBelow": [1, 0, 2]},
        {"placementId": "cut_station", "supportBelow": [2, 0, 2]},
        {"placementId": "press_power_source", "supportBelow": [4, 2, 2]},
        {"placementId": "press_power_relay", "supportBelow": [5, 2, 2]},
        {"placementId": "press_world_surface", "supportBelow": [6, 0, 2]},
    ]
    return {
        "operatorClearance": operator_clearance,
        "padZMax": pad_z_max,
        "placements": placements,
        "clearanceCells": clearance_cells,
        "handoffClearanceCells": handoff_clearance,
        "supportRequirements": support_requirements,
    }

def _platform_evidence(capability: dict[str, Any], evidence: dict[str, Any]) -> dict[str, Any]:
    return {
        "status": capability["status"],
        "verificationLevel": capability["verification_level"],
        "workflowRun": evidence["workflow_run"],
        "commit": evidence["commit"],
        "result": evidence["result"],
        "agentCAuthorized": True,
    }


def _compile_mech_001(
    spec: dict[str, Any], mechanism: dict[str, Any], capability: dict[str, Any], evidence: dict[str, Any]
) -> dict[str, Any]:
    lowered = _lower_mech_001_airflow_bench(mechanism)
    front_clearance = lowered["frontClearance"]
    plan: dict[str, Any] = {
        "schema": PLAN_SCHEMA,
        "assetId": spec["assetId"],
        "compilerVersion": COMPILER_VERSION,
        "seed": spec["seed"],
        "targetStackAuthority": EXPECTED_TARGET_STACK,
        "requiredPlatformCapability": MECH_001_CAPABILITY,
        "sourcePolicy": "qualified_test_source_not_gameplay_canon",
        "semanticInput": {
            "orientation": "east",
            "sourceRole": "qualified_kinetic_source",
            "transmissionLength": 1,
            "endpointRole": "airflow",
            "mountingRole": "masonry_pad",
            "frontClearance": front_clearance,
        },
        "envelope": {"min": [0, 0, 0], "max": [2 + front_clearance, 1, 2], "size": [3 + front_clearance, 2, 3]},
        "mountingRegion": {"min": [0, 0, 0], "max": [2, 0, 2]},
        "placements": lowered["placements"],
        "connectivity": {
            "nodes": ["source", "relay_0", "endpoint"],
            "edges": [["source", "relay_0"], ["relay_0", "endpoint"]],
            "severNode": "relay_0",
        },
        "supportRequirements": lowered["supportRequirements"],
        "clearanceCells": lowered["clearanceCells"],
        "runtimeExpectations": {
            "active": {"endpointSpeed": "nonzero", "hasSource": True, "hasNetwork": True},
            "severed": {"endpointSpeed": 0, "hasSource": False},
            "rebuilt": {"endpointSpeed": "nonzero", "hasSource": True, "hasNetwork": True},
        },
        "platformEvidence": _platform_evidence(capability, evidence),
        "validation": {"passed": True, "issues": []},
    }
    plan["digestSha256"] = _canonical_digest(plan)
    return plan


def _compile_mech_002(
    spec: dict[str, Any], mechanism: dict[str, Any], capability: dict[str, Any], evidence: dict[str, Any]
) -> dict[str, Any]:
    lowered = _lower_mech_002_waterwheel_airflow_bench(mechanism)
    front_clearance = lowered["frontClearance"]
    water_state = _block_state("minecraft:water", level="8")
    feeder_state = _block_state("minecraft:water", level="0")
    plan: dict[str, Any] = {
        "schema": PLAN_SCHEMA,
        "assetId": spec["assetId"],
        "compilerVersion": MECH_002_COMPILER_VERSION,
        "seed": spec["seed"],
        "targetStackAuthority": EXPECTED_TARGET_STACK,
        "requiredPlatformCapability": MECH_002_CAPABILITY,
        "sourcePolicy": "qualified_environmental_source_candidate_not_geography_canon",
        "semanticInput": {
            "orientation": "east",
            "sourceRole": "renewable_stationary_source",
            "sourceEnvironmentRole": "bounded_falling_water",
            "transmissionLength": 1,
            "endpointRole": "airflow",
            "mountingRole": "masonry_pad",
            "frontClearance": front_clearance,
        },
        "envelope": {"min": [0, 0, 0], "max": [3 + front_clearance, 3, 4], "size": [4 + front_clearance, 4, 5]},
        "mountingRegion": {"min": [0, 0, 0], "max": [3, 0, 4]},
        "placements": lowered["placements"],
        "connectivity": {
            "nodes": ["source", "relay_0", "endpoint"],
            "edges": [["source", "relay_0"], ["relay_0", "endpoint"]],
            "severNode": "relay_0",
        },
        "environmentalEnvelope": {
            "sourcePlacementId": "source",
            "disableCell": "source_feeder",
            "requiredCells": [
                {
                    "placementId": "source_feeder",
                    "offsetFromSource": [0, 1, -1],
                    "role": "persistent_water_feeder",
                    "expectedFlowVector": [0.0, 0.0, 0.0],
                    "disableState": _block_state("minecraft:air"),
                    "restoreState": feeder_state,
                },
                {
                    "placementId": "source_flow_0",
                    "offsetFromSource": [0, 0, -1],
                    "role": "falling_water_drive",
                    "expectedFlowVector": [0.0, -1.0, 0.0],
                    "disableState": _block_state("minecraft:air"),
                    "restoreState": water_state,
                },
            ],
        },
        "supportRequirements": lowered["supportRequirements"],
        "clearanceCells": lowered["clearanceCells"],
        "runtimeExpectations": {
            "active": {"sourceSpeed": -8.0, "endpointSpeed": -8.0, "hasSource": True, "hasNetwork": True, "stableTicks": 100},
            "environmentDisabled": {"sourceSpeed": 0.0, "endpointSpeed": 0.0, "hasSource": False, "feederPresent": False},
            "environmentRecovered": {"sourceSpeed": -8.0, "endpointSpeed": -8.0, "hasSource": True, "hasNetwork": True, "feederPresent": True},
        },
        "platformEvidence": _platform_evidence(capability, evidence),
        "validation": {"passed": True, "issues": []},
    }
    plan["digestSha256"] = _canonical_digest(plan)
    return plan



def _compile_mech_003(
    spec: dict[str, Any], mechanism: dict[str, Any], capability: dict[str, Any], evidence: dict[str, Any]
) -> dict[str, Any]:
    lowered = _lower_mech_003_portable_engine_workshop(mechanism)
    pad_z_max = lowered["padZMax"]
    plan: dict[str, Any] = {
        "schema": PLAN_SCHEMA,
        "assetId": spec["assetId"],
        "compilerVersion": MECH_003_COMPILER_VERSION,
        "seed": spec["seed"],
        "targetStackAuthority": EXPECTED_TARGET_STACK,
        "requiredPlatformCapability": MECH_003_CAPABILITY,
        "sourcePolicy": "qualification_power_not_gameplay_canon",
        "semanticInput": {
            "orientation": "east",
            "workflowRole": "manual_sequenced_processing",
            "stagingRole": "world_item_manual_handoff",
            "powerRole": "qualified_stationary_kinetic",
            "mountingRole": "masonry_pad",
            "operatorClearance": lowered["operatorClearance"],
        },
        "envelope": {"min": [0, 0, 0], "max": [6, 3, pad_z_max], "size": [7, 4, pad_z_max + 1]},
        "mountingRegion": {"min": [0, 0, 0], "max": [6, 0, pad_z_max]},
        "placements": lowered["placements"],
        "connectivity": {
            "nodes": [
                "cut_power_source", "cut_power_relay", "cut_station",
                "press_power_source", "press_power_relay", "press_station",
            ],
            "edges": [
                ["cut_power_source", "cut_power_relay"], ["cut_power_relay", "cut_station"],
                ["press_power_source", "press_power_relay"], ["press_power_relay", "press_station"],
            ],
            "severNode": "cut_power_relay",
        },
        "processingEnvelope": {
            "stations": [
                {"placementId": "cut_station", "role": "cut", "acquisitionMode": "fall_on_top"},
                {"placementId": "press_station", "role": "press", "acquisitionMode": "grounded_world_item"},
            ],
            "pressWorldSurfacePlacementId": "press_world_surface",
            "manualHandoff": {
                "fromPlacementId": "cut_station",
                "toPlacementId": "press_station",
                "mode": "world_item_restage",
                "retractionStagingCell": [3, 1, 3],
                "clearanceCells": lowered["handoffClearanceCells"],
            },
            "forbiddenTransportBlocks": [
                "create:belt", "create:depot", "create:andesite_funnel", "create:brass_funnel",
                "create:chute", "create:smart_chute", "create:mechanical_arm",
            ],
        },
        "productProcessingContract": {
            "recipeAuthority": "live_exact_stack_recipe_manager",
            "expectedStepRoles": ["cut", "press"],
            "loopCountAuthority": "live_recipe",
            "terminalResultPolicy": "live_weighted_result_pool",
            "singleAttemptSuccessRequired": False,
        },
        "supportRequirements": lowered["supportRequirements"],
        "clearanceCells": lowered["clearanceCells"],
        "runtimeExpectations": {
            "stationsPowered": {"cutSpeed": "nonzero", "pressSpeed": "nonzero", "hasSource": True},
            "handoff": {"worldItem": True, "automatedTransport": False},
            "terminal": {"sequencedComponentCleared": True, "resultMustBelongToLivePool": True},
        },
        "platformEvidence": _platform_evidence(capability, evidence),
        "validation": {"passed": True, "issues": []},
    }
    plan["digestSha256"] = _canonical_digest(plan)
    return plan

def compile_functional_mechanism(
    spec: dict[str, Any],
    capability_ledger: dict[str, Any],
) -> dict[str, Any]:
    """Compile one bounded accepted fixed-world functional mechanism specimen."""
    mechanism = _validate_common_spec(spec)
    source_role = mechanism.get("sourceRole")
    workflow_role = mechanism.get("workflowRole")
    capability_id = spec.get("requiredPlatformCapability")

    if workflow_role == "manual_sequenced_processing":
        _require(
            capability_id == MECH_003_CAPABILITY,
            f"requiredPlatformCapability must be {MECH_003_CAPABILITY}",
        )
        capability, evidence = _require_accepted_agent_c_capability(
            capability_ledger, MECH_003_CAPABILITY, EXPECTED_TARGET_STACK
        )
        return _compile_mech_003(spec, mechanism, capability, evidence)

    if source_role == "qualified_kinetic_source":
        _require(
            capability_id == MECH_001_CAPABILITY,
            f"requiredPlatformCapability must be {MECH_001_CAPABILITY}",
        )
        capability, evidence = _require_accepted_agent_c_capability(
            capability_ledger, MECH_001_CAPABILITY, EXPECTED_TARGET_STACK
        )
        return _compile_mech_001(spec, mechanism, capability, evidence)

    if source_role == "renewable_stationary_source":
        _require(
            capability_id == MECH_002_CAPABILITY,
            f"requiredPlatformCapability must be {MECH_002_CAPABILITY}",
        )
        capability, evidence = _require_accepted_agent_c_capability(
            capability_ledger, MECH_002_CAPABILITY, EXPECTED_TARGET_STACK
        )
        return _compile_mech_002(spec, mechanism, capability, evidence)

    raise SpecError(f"unsupported sourceRole: {source_role}")
