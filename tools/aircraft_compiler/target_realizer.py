from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass
from typing import Any


class TargetRealizerError(ValueError):
    pass


@dataclass(frozen=True)
class Provider:
    provider_id: str
    resource_id: str | None
    capabilities: frozenset[str]
    evidence_level: str
    state_rule: str
    priority: int


def _provider(raw: dict[str, Any]) -> Provider:
    caps = raw.get("capabilities")
    if not isinstance(caps, list) or not caps or not all(isinstance(v, str) and v for v in caps):
        raise TargetRealizerError(f"invalid provider capabilities: {raw!r}")
    resource = raw.get("resourceId")
    if resource is not None and (not isinstance(resource, str) or ":" not in resource):
        raise TargetRealizerError(f"invalid provider resourceId: {resource!r}")
    return Provider(
        provider_id=str(raw["providerId"]),
        resource_id=resource,
        capabilities=frozenset(caps),
        evidence_level=str(raw["evidenceLevel"]),
        state_rule=str(raw.get("stateRule", "none")),
        priority=int(raw.get("priority", 100)),
    )


def _select_provider(required: frozenset[str], providers: list[Provider]) -> Provider | None:
    candidates = [provider for provider in providers if required <= provider.capabilities]
    if not candidates:
        return None
    # Semantic specificity is authoritative. A highly preferred multifunction block
    # must never replace a more exact provider merely because its priority is lower.
    # Priority is only a tie-breaker between equally specific capability supersets.
    return min(
        candidates,
        key=lambda p: (
            len(p.capabilities - required),
            p.priority,
            p.provider_id,
        ),
    )


def _validate_target_tuple(profile: dict[str, Any]) -> dict[str, str]:
    target = profile.get("target")
    if not isinstance(target, dict):
        raise TargetRealizerError("target profile must declare an exact target tuple")
    required = ("minecraft", "loader", "create", "sable", "simulated", "aeronautics")
    result: dict[str, str] = {}
    for key in required:
        value = target.get(key)
        if not isinstance(value, str) or not value:
            raise TargetRealizerError(f"target tuple missing {key}")
        result[key] = value
    return result


def preflight(assembly: dict[str, Any], profile: dict[str, Any]) -> dict[str, Any]:
    if assembly.get("schemaVersion") != "aircraft-assembly-plan-ir-0.3":
        raise TargetRealizerError("v0.4 requires aircraft-assembly-plan-ir-0.3")
    if not assembly.get("validation", {}).get("passed", False):
        raise TargetRealizerError("refusing target realization from invalid assembly plan")
    if profile.get("schemaVersion") != "aircraft-target-profile-0.4":
        raise TargetRealizerError("v0.4 requires aircraft-target-profile-0.4")

    target = _validate_target_tuple(profile)
    providers = [_provider(raw) for raw in profile.get("siteProviders", [])]
    if len({p.provider_id for p in providers}) != len(providers):
        raise TargetRealizerError("providerId values must be unique")

    placements: list[dict[str, Any]] = []
    unresolved_sites: list[dict[str, Any]] = []
    unresolved_state_rules: list[dict[str, Any]] = []
    for site in assembly.get("sites", []):
        required = frozenset(str(v) for v in site.get("capabilities", []))
        chosen = _select_provider(required, providers)
        coord = [int(site["x"]), int(site["y"]), int(site["z"])]
        if chosen is None:
            unresolved_sites.append(
                {"lattice": coord, "roles": list(site["roles"]), "requiredCapabilities": sorted(required)}
            )
            continue
        placement = {
            "lattice": coord,
            "roles": list(site["roles"]),
            "requiredCapabilities": sorted(required),
            "providerId": chosen.provider_id,
            "resourceId": chosen.resource_id,
            "evidenceLevel": chosen.evidence_level,
            "stateRule": chosen.state_rule,
        }
        placements.append(placement)
        if chosen.resource_id is None or chosen.state_rule.startswith("unresolved:"):
            unresolved_state_rules.append(placement)

    station_contracts = profile.get("stationProviders", {})
    stations: list[dict[str, Any]] = []
    unresolved_stations: list[dict[str, Any]] = []
    for station in assembly.get("stations", []):
        name = station["name"]
        raw = station_contracts.get(name)
        resolved = {
            "name": name,
            "lattice": list(station["lattice"]),
            "requiredCapabilities": list(station.get("capabilities", [])),
            "provider": raw,
        }
        stations.append(resolved)
        if raw is None or raw.get("status") != "source_verified":
            unresolved_stations.append(resolved)

    companion_checks: list[dict[str, Any]] = []
    companion_failures: list[dict[str, Any]] = []
    role_counts: dict[str, int] = {}
    for site in assembly.get("sites", []):
        for role in site.get("roles", []):
            role_counts[role] = role_counts.get(role, 0) + 1
    for requirement in profile.get("companionRequirements", []):
        role = str(requirement["role"])
        minimum = int(requirement["minimumCount"])
        actual = role_counts.get(role, 0)
        check = {
            "id": str(requirement["id"]),
            "role": role,
            "minimumCount": minimum,
            "actualCount": actual,
            "passed": actual >= minimum,
            "evidence": requirement.get("evidence"),
        }
        companion_checks.append(check)
        if not check["passed"]:
            companion_failures.append(check)

    runtime_obligations = []
    for raw in profile.get("runtimeObligations", []):
        obligation = dict(raw)
        obligation["status"] = "unverified"
        runtime_obligations.append(obligation)

    static_resource_ids = sorted(
        {p["resourceId"] for p in placements if p.get("resourceId") is not None}
    )
    all_sites_mapped = len(unresolved_sites) == 0 and len(placements) == len(assembly.get("sites", []))
    all_states_resolved = len(unresolved_state_rules) == 0
    all_companions_present = len(companion_failures) == 0
    required_station_names = set(profile.get("requiredStations", []))
    unresolved_required_stations = [
        station for station in unresolved_stations if station["name"] in required_station_names
    ]
    static_preflight_passed = all_sites_mapped
    schematic_emission_ready = bool(
        static_preflight_passed
        and all_states_resolved
        and all_companions_present
        and not unresolved_required_stations
    )

    blockers = []
    if unresolved_sites:
        blockers.append("unmapped_site_capability_combinations")
    if unresolved_state_rules:
        blockers.append("unresolved_blockstate_or_resource_rules")
    if companion_failures:
        blockers.append("unsatisfied_contraption_companion_requirements")
    if unresolved_required_stations:
        blockers.append("unresolved_required_station_providers")

    out: dict[str, Any] = {
        "schemaVersion": "aircraft-minecraft-realization-preflight-ir-0.4",
        "assetId": str(assembly["assetId"]).replace("v0_3_assembly", "v0_4_target_preflight"),
        "sourceAssemblyAssetId": assembly["assetId"],
        "sourceAssemblyDigestSha256": assembly["digestSha256"],
        "compilerVersion": "aircraft-target-realizer-preflight-0.4",
        "target": target,
        "targetProfileId": profile["profileId"],
        "evidencePolicy": profile.get("evidencePolicy", {}),
        "placements": placements,
        "stations": stations,
        "companionChecks": companion_checks,
        "runtimeObligations": runtime_obligations,
        "metrics": {
            "assemblySiteCount": len(assembly.get("sites", [])),
            "mappedSiteCount": len(placements),
            "unresolvedSiteCount": len(unresolved_sites),
            "unresolvedStateOrResourceCount": len(unresolved_state_rules),
            "companionFailureCount": len(companion_failures),
            "runtimeObligationCount": len(runtime_obligations),
            "runtimeObligationVerifiedCount": 0,
            "staticResourceIds": static_resource_ids,
        },
        "readiness": {
            "staticCapabilityCoveragePassed": static_preflight_passed,
            "schematicEmissionReady": schematic_emission_ready,
            "runtimeQualificationReady": False,
            "flightQualified": False,
            "blockers": blockers,
        },
        "validation": {
            "passed": static_preflight_passed,
            "scope": "target_version_and_capability_preflight_only",
            "doesNotProve": [
                "blockstate orientation correctness where state rules remain unresolved",
                "Create/Simulated/Aeronautics contraption assembly success",
                "runtime mass or center of mass",
                "runtime force magnitude or direction",
                "kinetic stress sufficiency",
                "control authority",
                "stable flight",
            ],
        },
        "unresolvedSites": unresolved_sites,
        "unresolvedStateOrResource": unresolved_state_rules,
        "unresolvedRequiredStations": unresolved_required_stations,
    }
    canonical = json.dumps(out, sort_keys=True, separators=(",", ":"))
    out["digestSha256"] = hashlib.sha256(canonical.encode("utf-8")).hexdigest()
    return out
