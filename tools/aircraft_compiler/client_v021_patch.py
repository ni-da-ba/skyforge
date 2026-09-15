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
    switch_anchor = '''                case 3 -> awaitSeatMount(minecraft, player, snapshot);\n                case 4 -> awaitSeatDismount(minecraft, player, snapshot);\n                default -> fail("invalid AIRCRAFT-001 v0.20 client stage " + stage);\n'''
    switch_injected = '''                case 3 -> awaitSeatMount(minecraft, player, snapshot);\n                case 4 -> awaitSeatDismount(minecraft, player, snapshot);\n                case 5 -> awaitNaturalPilotTracking(minecraft, player, snapshot);\n                case 6 -> awaitPilotTracking(minecraft, player);\n                default -> fail("invalid AIRCRAFT-001 client stage " + stage);\n'''
    source = _replace_once(source, switch_anchor, switch_injected, "v0.21 client stage")

    release_signature_anchor = '''    private static void awaitReleasePacket(\n            Minecraft minecraft,\n            LocalPlayer player,\n            SkyforgeAircraftCompilerPilotClientBridge.Snapshot snapshot) {\n'''
    release_signature_injected = '''    private static void awaitReleasePacket(\n            Minecraft minecraft,\n            LocalPlayer player,\n            SkyforgeAircraftCompilerPilotClientBridge.Snapshot snapshot)\n            throws ReflectiveOperationException {\n'''
    source = _replace_once(
        source,
        release_signature_anchor,
        release_signature_injected,
        "v0.21 release method reflection contract",
    )

    release_wait_anchor = '''        if (!SkyforgeAircraftCompilerPilotClientRuntimeAcceptance.releasePacketObserved()) {\n            if (stageTicks > snapshot.releasePacketSettleTicks()) {\n                fail("server never observed the real client SteeringWheelPacket release command");\n            }\n            return;\n        }\n'''
    release_wait_injected = '''        if (!SkyforgeAircraftCompilerPilotClientRuntimeAcceptance.releasePacketObserved()) {\n            // Simulated may auto-stop an out-of-range hold in the same client tick that emits its\n            // active update. Re-establish the production hold through real useItemOn only after the\n            // server has observed active state, then drive the ordinary use-release callback. This\n            // makes the stop packet causally later without constructing packets or mutating server\n            // wheel state. All positioning here remains pre-v0.21 measurement setup.\n            if (stageTicks >= 4 && stageTicks % 4 == 0) {\n                retryServerObservedSteeringRelease(minecraft, player, snapshot);\n            }\n            if (stageTicks > snapshot.releasePacketSettleTicks()) {\n                fail("server never observed the real client SteeringWheelPacket release command");\n            }\n            return;\n        }\n'''
    source = _replace_once(source, release_wait_anchor, release_wait_injected, "v0.21 ordered release retry")

    complete_anchor = '''    private static void complete(Minecraft minecraft) {\n'''
    tracking_and_release_methods = '''    private static void retryServerObservedSteeringRelease(\n            Minecraft minecraft,\n            LocalPlayer player,\n            SkyforgeAircraftCompilerPilotClientBridge.Snapshot snapshot)\n            throws ReflectiveOperationException {\n        if (minecraft.screen != null) {\n            return;\n        }\n        BlockPos wheelPos = snapshot.steeringWheelPos();\n        if (!minecraft.level.getBlockState(wheelPos).getBlock().getClass().getName().endsWith("SteeringWheelBlock")) {\n            return;\n        }\n\n        positionClientAtRenderedSeat(minecraft, player, snapshot);\n        if (!holdInteractionActive()) {\n            Vec3 wheelPlotHit = new Vec3(\n                    wheelPos.getX() + STEERING_WHEEL_VISUAL_X,\n                    wheelPos.getY() + STEERING_WHEEL_VISUAL_Y,\n                    wheelPos.getZ() + 0.5);\n            lookAt(player, projectOutOfSubLevel(minecraft.level, wheelPlotHit));\n            BlockHitResult hit = new BlockHitResult(wheelPlotHit, Direction.UP, wheelPos, false);\n            minecraft.hitResult = hit;\n            minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);\n        }\n        if (holdInteractionActive()) {\n            invokeSimulatedUseRelease();\n        }\n    }\n\n    private static void awaitNaturalPilotTracking(\n            Minecraft minecraft,\n            LocalPlayer player,\n            SkyforgeAircraftCompilerPilotClientBridge.Snapshot snapshot)\n            throws ReflectiveOperationException {\n        // Sable acquires independent LocalPlayer tracking from ordinary sublevel collision. Once\n        // acquired, its LocalPlayer#sendPosition mixin temporarily projects the packet into plot\n        // coordinates; the server movement mixin then adopts the matching tracking sublevel. A\n        // bounded jump-key pulse exercises that production path after real Create dismount. No\n        // player position/delta mutation or Sable tracking setter is used, and measurement does not\n        // begin until the client is naturally tracking the compiled aircraft parent.\n        minecraft.options.keyShift.setDown(false);\n        if (stageTicks == 2 || stageTicks == 12 || stageTicks == 22 || stageTicks == 32) {\n            minecraft.options.keyJump.setDown(true);\n        } else {\n            minecraft.options.keyJump.setDown(false);\n        }\n\n        Object trackingSubLevel = clientTrackingSubLevel(player);\n        Object expectedSubLevel = clientContainingSubLevel(minecraft.level, snapshot.pilotSeatPos());\n        java.util.UUID trackingId = subLevelUniqueId(trackingSubLevel);\n        java.util.UUID expectedId = subLevelUniqueId(expectedSubLevel);\n        if (trackingId != null && trackingId.equals(expectedId)) {\n            minecraft.options.keyJump.setDown(false);\n            SkyforgeAircraftCompilerPilotTrackingRuntimeAcceptance.armClientMeasurement();\n            advanceStage();\n            return;\n        }\n\n        if (stageTicks > 40) {\n            minecraft.options.keyJump.setDown(false);\n            fail("v0.21 client did not naturally acquire Sable tracking through collision/movement packet path"\n                    + " expectedParentUuid=" + expectedId\n                    + " clientTrackingUuid=" + trackingId\n                    + " clientPosition=" + player.position()\n                    + " onGround=" + player.onGround());\n        }\n    }\n\n    private static Object clientTrackingSubLevel(LocalPlayer player) throws ReflectiveOperationException {\n        Class<?> sableClass = Class.forName("dev.ryanhcode.sable.Sable");\n        Object helper = sableClass.getField("HELPER").get(null);\n        Method method = helper.getClass().getMethod(\n                "getTrackingSubLevel", Class.forName("net.minecraft.world.entity.Entity"));\n        return method.invoke(helper, player);\n    }\n\n    private static Object clientContainingSubLevel(Level level, BlockPos plotPosition)\n            throws ReflectiveOperationException {\n        Class<?> sableClass = Class.forName("dev.ryanhcode.sable.Sable");\n        Object helper = sableClass.getField("HELPER").get(null);\n        Method method = helper.getClass().getMethod(\n                "getContaining", Level.class, double.class, double.class);\n        return method.invoke(\n                helper,\n                level,\n                plotPosition.getX() + 0.5,\n                plotPosition.getZ() + 0.5);\n    }\n\n    private static java.util.UUID subLevelUniqueId(Object subLevel) throws ReflectiveOperationException {\n        if (subLevel == null) {\n            return null;\n        }\n        Object value = subLevel.getClass().getMethod("getUniqueId").invoke(subLevel);\n        if (value == null) {\n            throw new IllegalStateException("Sable sub-level unique ID is null");\n        }\n        try {\n            // Sable is loaded by the mod classloader, so even a UUID-shaped return value may not\n            // satisfy an instanceof check against the harness classloader's java.util.UUID. Parse\n            // the canonical textual form instead; this preserves persistent UUID semantics without\n            // relying on runtime class identity.\n            return java.util.UUID.fromString(String.valueOf(value));\n        } catch (IllegalArgumentException invalidUuid) {\n            throw new IllegalStateException("Sable sub-level unique ID is not a canonical UUID: " + value, invalidUuid);\n        }\n    }\n\n    private static void awaitPilotTracking(Minecraft minecraft, LocalPlayer player) {\n        if (SkyforgeAircraftCompilerPilotTrackingRuntimeAcceptance.passed()) {\n            complete(minecraft);\n            return;\n        }\n        if (stageTicks > SkyforgeAircraftCompilerPilotTrackingRuntimeAcceptance.clientSettleTicks()) {\n            fail("v0.21 server did not qualify natural Sable player tracking and inherited parent translation"\n                    + " parentDeltaX=" + SkyforgeAircraftCompilerPilotTrackingRuntimeAcceptance.measuredParentDeltaX()\n                    + " playerDeltaX=" + SkyforgeAircraftCompilerPilotTrackingRuntimeAcceptance.measuredPlayerDeltaX()\n                    + " clientPosition=" + player.position());\n        }\n    }\n\n'''
    source = _replace_once(
        source,
        complete_anchor,
        tracking_and_release_methods + complete_anchor,
        "v0.21 client tracking/release methods",
    )

    dismount_anchor = '''        if (serverDismounted && !player.isPassenger()) {\n            minecraft.options.keyShift.setDown(false);\n            complete(minecraft);\n            return;\n        }\n'''
    dismount_injected = '''        if (serverDismounted && !player.isPassenger()) {\n            minecraft.options.keyShift.setDown(false);\n            if (Boolean.getBoolean(SkyforgeAircraftCompilerPilotTrackingRuntimeAcceptance.ENABLE_PROPERTY)) {\n                advanceStage();\n            } else {\n                complete(minecraft);\n            }\n            return;\n        }\n'''
    source = _replace_once(source, dismount_anchor, dismount_injected, "v0.21 post-dismount handoff")

    evidence_anchor = '''        evidence.put("playerSableTrackingQualified", false);\n        evidence.put("completedControlsPersistenceQualified", false);\n'''
    evidence_injected = '''        boolean trackingQualified = Boolean.getBoolean(SkyforgeAircraftCompilerPilotTrackingRuntimeAcceptance.ENABLE_PROPERTY)\n                && SkyforgeAircraftCompilerPilotTrackingRuntimeAcceptance.passed();\n        evidence.put("playerSableTrackingQualified", trackingQualified);\n        evidence.put("inheritedParentTranslationQualified", trackingQualified);\n        evidence.put("completedControlsPersistenceQualified", false);\n'''
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
