from __future__ import annotations

import hashlib
import json
from copy import deepcopy
from typing import Any

from model import SpecError

SCHEMA_VERSION = "mech-0.1"
PLAN_SCHEMA = "skyforge.functional-mechanism-plan.v1"
COMPILER_VERSION = "mech-0.1-fixed-world"
EXPECTED_TARGET_STACK = "C11_FLIGHT_EXACT_2026-09-05"
EXPECTED_CAPABILITY = "CREATE_KINETIC_NETWORK_LIFECYCLE"

_ALLOWED_MECHANISM_KEYS = {
    "orientation",
    "sourceRole",
    "transmissionLength",
    "endpointRole",
    "mountingRole",
    "frontClearance",
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
    """Validate the bounded semantic envelope shared by fixed-world mechanism specimens.

    This validates only already-accepted schema fields. It does not select a concrete Create source
    or introduce new mechanism vocabulary.
    """
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


def _lower_mech_001_airflow_bench(mechanism: dict[str, Any]) -> dict[str, Any]:
    """Lower only the already-accepted MECH-001 semantic specimen to exact target state."""
    _require(mechanism.get("orientation") == "east", "MECH-001 v0.1 supports only east orientation")
    _require(
        mechanism.get("sourceRole") == "qualified_kinetic_source",
        "MECH-001 sourceRole must remain qualified_kinetic_source",
    )
    _require(mechanism.get("transmissionLength") == 1, "MECH-001 requires exactly one relay")
    _require(mechanism.get("endpointRole") == "airflow", "MECH-001 endpointRole must be airflow")
    _require(mechanism.get("mountingRole") == "masonry_pad", "MECH-001 mountingRole must be masonry_pad")
    front_clearance = mechanism.get("frontClearance")
    _require(
        isinstance(front_clearance, int) and not isinstance(front_clearance, bool) and 1 <= front_clearance <= 6,
        "frontClearance must be an integer in [1, 6]",
    )

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
    _require(
        not occupied.intersection(map(tuple, clearance_cells)),
        "fan discharge clearance overlaps a placement",
    )

    support_requirements = [
        {"placementId": "source", "supportBelow": [0, 0, 1]},
        {"placementId": "relay_0", "supportBelow": [1, 0, 1]},
        {"placementId": "endpoint", "supportBelow": [2, 0, 1]},
    ]
    by_id = {p["id"]: p for p in placements}
    for requirement in support_requirements:
        _require(requirement["placementId"] in by_id, "support requirement references unknown placement")
        _require(tuple(requirement["supportBelow"]) in occupied, "machinery support is missing")

    return {
        "frontClearance": front_clearance,
        "placements": placements,
        "clearanceCells": clearance_cells,
        "supportRequirements": support_requirements,
    }


def compile_functional_mechanism(
    spec: dict[str, Any],
    capability_ledger: dict[str, Any],
) -> dict[str, Any]:
    """Compile the accepted MECH-001 fixed-world airflow utility mechanism.

    The phases are deliberately separated so later mechanisms can reuse validation without acquiring
    MECH-001's target lowering. This function still accepts only the existing MECH-001 vocabulary.
    """
    mechanism = _validate_common_spec(spec)
    _require(
        spec.get("requiredPlatformCapability") == EXPECTED_CAPABILITY,
        f"requiredPlatformCapability must be {EXPECTED_CAPABILITY}",
    )
    capability, evidence = _require_accepted_agent_c_capability(
        capability_ledger,
        EXPECTED_CAPABILITY,
        EXPECTED_TARGET_STACK,
    )
    lowered = _lower_mech_001_airflow_bench(mechanism)
    front_clearance = lowered["frontClearance"]

    plan: dict[str, Any] = {
        "schema": PLAN_SCHEMA,
        "assetId": spec["assetId"],
        "compilerVersion": COMPILER_VERSION,
        "seed": spec["seed"],
        "targetStackAuthority": EXPECTED_TARGET_STACK,
        "requiredPlatformCapability": EXPECTED_CAPABILITY,
        "sourcePolicy": "qualified_test_source_not_gameplay_canon",
        "semanticInput": {
            "orientation": "east",
            "sourceRole": "qualified_kinetic_source",
            "transmissionLength": 1,
            "endpointRole": "airflow",
            "mountingRole": "masonry_pad",
            "frontClearance": front_clearance,
        },
        "envelope": {
            "min": [0, 0, 0],
            "max": [2 + front_clearance, 1, 2],
            "size": [3 + front_clearance, 2, 3],
        },
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
        "platformEvidence": {
            "status": capability["status"],
            "verificationLevel": capability["verification_level"],
            "workflowRun": evidence["workflow_run"],
            "commit": evidence["commit"],
            "result": evidence["result"],
            "agentCAuthorized": True,
        },
        "validation": {"passed": True, "issues": []},
    }
    plan["digestSha256"] = _canonical_digest(plan)
    return plan
