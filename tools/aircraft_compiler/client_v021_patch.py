#!/usr/bin/env python3
from __future__ import annotations

import argparse
from pathlib import Path


class ClientPatchError(ValueError):
    pass


def _replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise ClientPatchError(f"{label}: expected exactly one anchor, found {count}")
    return source.replace(old, new, 1)


def patch_client_source(source: str) -> str:
    switch_anchor = '''                case 3 -> awaitSeatMount(minecraft, player, snapshot);
                case 4 -> awaitSeatDismount(minecraft, player, snapshot);
                default -> fail("invalid AIRCRAFT-001 v0.20 client stage " + stage);
'''
    switch_injected = '''                case 3 -> awaitSeatMount(minecraft, player, snapshot);
                case 4 -> awaitSeatDismount(minecraft, player, snapshot);
                case 5 -> awaitPilotTracking(minecraft, player);
                default -> fail("invalid AIRCRAFT-001 client stage " + stage);
'''
    source = _replace_once(source, switch_anchor, switch_injected, "v0.21 client stage")

    dismount_anchor = '''        if (serverDismounted && !player.isPassenger()) {
            minecraft.options.keyShift.setDown(false);
            complete(minecraft);
            return;
        }
'''
    dismount_injected = '''        if (serverDismounted && !player.isPassenger()) {
            minecraft.options.keyShift.setDown(false);
            if (Boolean.getBoolean(SkyforgeAircraftCompilerPilotTrackingRuntimeAcceptance.ENABLE_PROPERTY)) {
                SkyforgeAircraftCompilerPilotTrackingRuntimeAcceptance.armClientMeasurement();
                advanceStage();
            } else {
                complete(minecraft);
            }
            return;
        }
'''
    source = _replace_once(source, dismount_anchor, dismount_injected, "v0.21 post-dismount handoff")

    complete_anchor = '''    private static void complete(Minecraft minecraft) {
'''
    tracking_method = '''    private static void awaitPilotTracking(Minecraft minecraft, LocalPlayer player) {
        if (SkyforgeAircraftCompilerPilotTrackingRuntimeAcceptance.passed()) {
            complete(minecraft);
            return;
        }
        if (stageTicks > SkyforgeAircraftCompilerPilotTrackingRuntimeAcceptance.clientSettleTicks()) {
            fail("v0.21 server did not qualify natural Sable player tracking and inherited parent translation"
                    + " parentDeltaX=" + SkyforgeAircraftCompilerPilotTrackingRuntimeAcceptance.measuredParentDeltaX()
                    + " playerDeltaX=" + SkyforgeAircraftCompilerPilotTrackingRuntimeAcceptance.measuredPlayerDeltaX()
                    + " clientPosition=" + player.position());
        }
    }

'''
    source = _replace_once(source, complete_anchor, tracking_method + complete_anchor, "v0.21 client wait method")

    evidence_anchor = '''        evidence.put("playerSableTrackingQualified", false);
        evidence.put("completedControlsPersistenceQualified", false);
'''
    evidence_injected = '''        boolean trackingQualified = Boolean.getBoolean(SkyforgeAircraftCompilerPilotTrackingRuntimeAcceptance.ENABLE_PROPERTY)
                && SkyforgeAircraftCompilerPilotTrackingRuntimeAcceptance.passed();
        evidence.put("playerSableTrackingQualified", trackingQualified);
        evidence.put("inheritedParentTranslationQualified", trackingQualified);
        evidence.put("completedControlsPersistenceQualified", false);
'''
    source = _replace_once(source, evidence_anchor, evidence_injected, "v0.21 result evidence")
    return source


def main() -> int:
    parser = argparse.ArgumentParser(description="Patch AIRCRAFT-001 v0.21 tracking wait into disposable actual client")
    parser.add_argument("client_source", type=Path)
    args = parser.parse_args()
    source = args.client_source.read_text(encoding="utf-8")
    args.client_source.write_text(patch_client_source(source), encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
