#!/usr/bin/env python3
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / "docs/agent-state/BOOTSTRAP_PROVINCE_PLAN_497.json"
DR00 = ROOT / "docs/agent-state/DR00_SPECIMEN_LOCK.json"
DR10 = ROOT / "docs/agent-state/DR10_MATERIALS_EVIDENCE.json"

def load(path):
    return json.loads(path.read_text(encoding="utf-8"))

p = load(FIXTURE); dr00 = load(DR00); dr10 = load(DR10)
assert p == load(FIXTURE), "identical input must yield identical fixture assignment"
assert p["contract_id"] == "CONTENT-BOOTSTRAP-PROVINCE-497-001"
assert p["specimen_id"] == dr00["specimen_id"] == dr10["specimen_id"]
assert p["canonical_start"]["candidate_id"] == dr00["selection"]["selected_candidate_id"]
assert p["canonical_start"]["realized_volume_id"] == dr10["consumes"]["canonical_volume_id"]
a = p["canonical_start"]["authored_identity"]
d = dr00["authorship_provenance"]
for key in ("world_seed_unsigned", "province_key", "cluster_key", "island_key"):
    assert a[key] == d[key], f"exact provenance drift: {key}"
iron = p["roles"]["iron_closure"]
assert iron["metal"] == "IRON" and iron["eligibility"] == "AUTH-0093 peakOpportunity(IRON) > 0"
assert dr10["c20_starting_cluster"]["required_metal"] == "IRON"
assert dr10["c20_starting_cluster"]["geological_eligibility"] == iron["eligibility"]
assert iron["failure"] == "REPLAN_REQUIRED"
flight = p["roles"]["first_flight_resource_closure"]
assert set(flight["forbidden_prerequisites"]) == {"COPPER", "ZINC", "PETROLEUM", "COMPUTING"}
post = p["roles"]["post_flight_specialization"]
assert set(post["resources"]) == {"COPPER", "ZINC"}
assert not set(post["resources"]) & set(flight["required"]), "post-flight specialization leaked into first flight"
guild = p["roles"]["guild_destination"]
assert "CARGO_TRANSFER" in guild["required_capabilities"]
assert "AUTH-0096" in guild["site_witness"] and "AUTH-0097" in guild["site_witness"]
assert guild["failure"] == "REPLAN_REQUIRED"
freight = p["roles"]["first_freight_opportunity"]
assert freight["producer_settlement_id"] != freight["consumer_settlement_id"]
assert freight["producer_specialization"] == "COPPER_EXTRACTION"
assert freight["consumer_need"] == "ENGINEERING_COPPER_INPUT"
assert freight["consumer_capability"] == "CARGO_TRANSFER"
assert freight["physical_cargo_required"] and not freight["mandatory_tutorial_chore"]
assert p["determinism"]["replan"] and p["determinism"]["fail_closed"]
for forbidden in ("Minecraft block geometry", "walkability threshold", "buildability threshold", "runway threshold", "dock threshold"):
    assert forbidden in p["forbidden_embedded_authority"]
serialized = json.dumps(p, sort_keys=True)
for token in ("minWalk", "maxGrade", "minArea", "runwayLength", "minecraft:"):
    assert token not in serialized, f"backend/hidden threshold leaked into Content fixture: {token}"
print("BOOTSTRAP_PROVINCE_PLAN_497 PASS")
