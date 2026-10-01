package io.github.nidaba.skyforge.neoforge1211;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Actual-client proof of one distant Sable body moving laterally through the DH/SSRD render path. */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID, value = Dist.CLIENT)
final class SkyforgeWbyWave1LateralFlightClientAcceptance {
    private static final long CLIENT_TIMEOUT_NANOS = 180_000_000_000L;
    private static final int VANILLA_RENDER_DISTANCE_CHUNKS = 4;
    private static final int PRE_ARM_STABLE_TICKS = 10;
    private static final int FINAL_STABLE_TICKS = 5;
    private static final int MINIMUM_MOTION_SAMPLES = 8;
    private static final double MAX_TRANSFORM_ERROR_BLOCKS = 8.0;
    private static final double REQUIRED_SERVER_LATERAL_DISPLACEMENT = 48.0;
    private static final double REQUIRED_CLIENT_LATERAL_DISPLACEMENT = 32.0;

    private static long firstTickNanos = Long.MIN_VALUE;
    private static int preArmStableTicks;
    private static int finalStableTicks;
    private static int motionSamples;
    private static boolean clientArmed;
    private static boolean motionObserved;
    private static boolean ssrdObservedBeforeMotion;
    private static boolean ssrdObservedDuringMotion;
    private static boolean complete;
    private static Vec3 preMotionRenderPosition;
    private static Vec3 finalRenderPosition;
    private static double maxTransformErrorBlocks;
    private static double maxServerLateralDisplacementBlocks;
    private static double maxClientLateralDisplacementBlocks;
    private static double minimumHorizontalDistanceBlocks = Double.POSITIVE_INFINITY;

    private SkyforgeWbyWave1LateralFlightClientAcceptance() {}

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean(SkyforgeWbyWave1LateralFlightLifecycleAcceptance.ENABLE_PROPERTY)
                || !SkyforgeAutomatedAcceptanceHarness.clientMode()
                || complete) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (firstTickNanos == Long.MIN_VALUE) {
            firstTickNanos = System.nanoTime();
        }
        if (System.nanoTime() - firstTickNanos > CLIENT_TIMEOUT_NANOS) {
            fail("WBY Wave 1 lateral-flight actual-client proof did not qualify within 180 seconds"
                    + "; fixtureReady=" + SkyforgeWbyWave1LateralFlightLifecycleAcceptance.fixtureReady()
                    + "; clientArmed=" + clientArmed
                    + "; motionObserved=" + motionObserved
                    + "; motionSamples=" + motionSamples
                    + "; maxServerDisplacement=" + maxServerLateralDisplacementBlocks
                    + "; maxClientDisplacement=" + maxClientLateralDisplacementBlocks
                    + "; maxTransformError=" + maxTransformErrorBlocks);
            return;
        }

        LocalPlayer player = minecraft.player;
        if (minecraft.level == null || player == null || minecraft.gameMode == null || minecraft.screen != null) {
            return;
        }

        SkyforgeWbyWave1LateralFlightLifecycleAcceptance.Snapshot snapshot =
                SkyforgeWbyWave1LateralFlightLifecycleAcceptance.snapshot();
        if (snapshot == null) {
            return;
        }

        try {
            requireClientMods();
            Object body = clientSubLevel(minecraft.level, snapshot.bodyId());
            if (body == null) {
                if (clientArmed) {
                    fail("moving WBY Sable body disappeared after client arm: " + snapshot.bodyId());
                }
                preArmStableTicks = 0;
                return;
            }

            boolean finalized = (Boolean) publicMethod(body, "isFinalized").invoke(body);
            if (!finalized) {
                if (clientArmed) {
                    fail("moving WBY Sable body lost finalized state after client arm: " + snapshot.bodyId());
                }
                preArmStableTicks = 0;
                return;
            }

            Vec3 renderPosition = renderPosition(body);
            double transformError = renderPosition.distanceTo(snapshot.serverPose());
            maxTransformErrorBlocks = Math.max(maxTransformErrorBlocks, transformError);
            if (clientArmed && transformError > MAX_TRANSFORM_ERROR_BLOCKS) {
                fail("moving WBY Sable render transform drifted from live server pose: errorBlocks="
                        + transformError + ", render=" + renderPosition + ", server=" + snapshot.serverPose());
                return;
            }

            lookAt(player, renderPosition);
            double horizontalDistance = Math.hypot(
                    renderPosition.x - player.getX(),
                    renderPosition.z - player.getZ());
            minimumHorizontalDistanceBlocks = Math.min(minimumHorizontalDistanceBlocks, horizontalDistance);
            boolean beyondVanilla = horizontalDistance > VANILLA_RENDER_DISTANCE_CHUNKS * 16.0 + 32.0;

            Object renderData = publicMethod(body, "getRenderData").invoke(body);
            boolean renderDataPresent = renderData != null;
            if (clientArmed && !renderDataPresent) {
                fail("moving WBY Sable body lost SSRD render data after client arm");
                return;
            }

            Class<?> stateClass = Class.forName("net.ranold.ssrd.SSRDState");
            boolean visibleThisFrame = stateClass.getField("SUBLEVELS_VISIBLE_THIS_FRAME").getBoolean(null);
            boolean projectionMatrixPresent = stateClass.getField("PURE_PROJ_MATRIX").get(null) != null;
            DistanceEvidence distance = ssrdDistanceEvidence(VANILLA_RENDER_DISTANCE_CHUNKS);

            boolean renderPathReady = beyondVanilla
                    && renderDataPresent
                    && projectionMatrixPresent
                    && "Distant Horizons".equals(distance.source())
                    && distance.chunks() > VANILLA_RENDER_DISTANCE_CHUNKS
                    && transformError <= MAX_TRANSFORM_ERROR_BLOCKS;

            if (!snapshot.motionStarted()) {
                ssrdObservedBeforeMotion |= visibleThisFrame;
                if (!renderPathReady || !ssrdObservedBeforeMotion) {
                    preArmStableTicks = 0;
                    return;
                }
                if (preMotionRenderPosition == null) {
                    preMotionRenderPosition = renderPosition;
                }
                preArmStableTicks++;
                if (!clientArmed && preArmStableTicks >= PRE_ARM_STABLE_TICKS) {
                    clientArmed = true;
                    preMotionRenderPosition = renderPosition;
                    SkyforgeWbyWave1LateralFlightLifecycleAcceptance.armMotionFromClient();
                }
                return;
            }

            motionObserved = true;
            motionSamples++;
            ssrdObservedDuringMotion |= visibleThisFrame;
            maxServerLateralDisplacementBlocks = Math.max(
                    maxServerLateralDisplacementBlocks,
                    snapshot.lateralDisplacementBlocks());
            if (preMotionRenderPosition != null) {
                maxClientLateralDisplacementBlocks = Math.max(
                        maxClientLateralDisplacementBlocks,
                        Math.abs(renderPosition.z - preMotionRenderPosition.z));
            }
            finalRenderPosition = renderPosition;

            if (!renderPathReady) {
                fail("moving WBY Sable body lost the qualified DH/SSRD render path"
                        + ": beyondVanilla=" + beyondVanilla
                        + ", renderData=" + renderDataPresent
                        + ", projection=" + projectionMatrixPresent
                        + ", distanceSource=" + distance.source()
                        + ", distanceChunks=" + distance.chunks()
                        + ", transformError=" + transformError);
                return;
            }

            if (!snapshot.motionComplete()) {
                finalStableTicks = 0;
                return;
            }

            boolean motionQualified = snapshot.sourceChunkTicketReleased()
                    && snapshot.boundedSableLivenessActive()
                    && maxServerLateralDisplacementBlocks >= REQUIRED_SERVER_LATERAL_DISPLACEMENT
                    && maxClientLateralDisplacementBlocks >= REQUIRED_CLIENT_LATERAL_DISPLACEMENT
                    && motionSamples >= MINIMUM_MOTION_SAMPLES
                    && ssrdObservedDuringMotion
                    && maxTransformErrorBlocks <= MAX_TRANSFORM_ERROR_BLOCKS;

            if (!motionQualified) {
                finalStableTicks = 0;
                return;
            }

            finalStableTicks++;
            if (finalStableTicks >= FINAL_STABLE_TICKS) {
                complete(minecraft, snapshot, distance);
            }
        } catch (ReflectiveOperationException | RuntimeException failure) {
            fail("WBY Wave 1 lateral-flight client proof failed: " + failure);
        }
    }

    private static void requireClientMods() {
        for (String modId : java.util.List.of(
                "create", "sable", "aeronautics", "sodium", "distanthorizons", "ssrd")) {
            if (!ModList.get().isLoaded(modId)) {
                throw new IllegalStateException("required WBY lateral-flight client mod not loaded: " + modId);
            }
        }
    }

    private static Object clientSubLevel(ClientLevel level, UUID bodyId)
            throws ReflectiveOperationException {
        Class<?> containerClass = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
        Object container = containerClass.getMethod("getContainer", ClientLevel.class).invoke(null, level);
        if (container == null) {
            return null;
        }
        return publicMethod(container, "getSubLevel", UUID.class).invoke(container, bodyId);
    }

    private static Vec3 renderPosition(Object body) throws ReflectiveOperationException {
        Object pose = publicMethod(body, "renderPose").invoke(body);
        Object position = publicMethod(pose, "position").invoke(pose);
        return new Vec3(
                ((Number) publicMethod(position, "x").invoke(position)).doubleValue(),
                ((Number) publicMethod(position, "y").invoke(position)).doubleValue(),
                ((Number) publicMethod(position, "z").invoke(position)).doubleValue());
    }

    private static DistanceEvidence ssrdDistanceEvidence(int vanillaChunks)
            throws ReflectiveOperationException {
        Class<?> limits = Class.forName("net.ranold.ssrd.client.DistanceLimits");
        Object effective = limits.getMethod("query", int.class).invoke(null, vanillaChunks);
        int chunks = ((Number) effective.getClass().getMethod("chunks").invoke(effective)).intValue();
        String source = String.valueOf(effective.getClass().getMethod("source").invoke(effective));
        return new DistanceEvidence(chunks, source);
    }

    private static void lookAt(LocalPlayer player, Vec3 target) {
        Vec3 delta = target.subtract(player.getEyePosition());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));
        player.yRotO = yaw;
        player.xRotO = pitch;
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.setYHeadRot(yaw);
    }

    private static Method publicMethod(Object target, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        return target.getClass().getMethod(name, parameterTypes);
    }

    private static void complete(
            Minecraft minecraft,
            SkyforgeWbyWave1LateralFlightLifecycleAcceptance.Snapshot snapshot,
            DistanceEvidence distance) {
        if (complete) {
            return;
        }
        complete = true;
        SkyforgeWbyWave1LateralFlightLifecycleAcceptance.markClientComplete();

        LinkedHashMap<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("actualClient", true);
        evidence.put("bodyId", snapshot.bodyId());
        evidence.put("truePhysicsMotion", true);
        evidence.put("motionStarted", snapshot.motionStarted());
        evidence.put("motionComplete", snapshot.motionComplete());
        evidence.put("initialServerPose", snapshot.initialServerPose());
        evidence.put("finalServerPose", snapshot.serverPose());
        evidence.put("preMotionRenderPosition", preMotionRenderPosition);
        evidence.put("finalRenderPosition", finalRenderPosition);
        evidence.put("commandedVelocity", snapshot.commandedVelocity());
        evidence.put("serverLateralDisplacementBlocks", maxServerLateralDisplacementBlocks);
        evidence.put("clientLateralDisplacementBlocks", maxClientLateralDisplacementBlocks);
        evidence.put("maxRenderTransformErrorBlocks", maxTransformErrorBlocks);
        evidence.put("renderTransformStayedBounded", maxTransformErrorBlocks <= MAX_TRANSFORM_ERROR_BLOCKS);
        evidence.put("motionSamples", motionSamples);
        evidence.put("ssrdObservedBeforeMotion", ssrdObservedBeforeMotion);
        evidence.put("ssrdObservedDuringMotion", ssrdObservedDuringMotion);
        evidence.put("minimumHorizontalDistanceBlocks", minimumHorizontalDistanceBlocks);
        evidence.put("vanillaRenderDistanceChunks", VANILLA_RENDER_DISTANCE_CHUNKS);
        evidence.put("stayedBeyondVanillaRenderDistance", minimumHorizontalDistanceBlocks > 96.0);
        evidence.put("sourceChunkTicketReleasedBeforeMotion", snapshot.sourceChunkTicketReleased());
        evidence.put("boundedSableLivenessUsed", snapshot.boundedSableLivenessActive());
        evidence.put("fixtureLivenessReleased", SkyforgeWbyWave1LateralFlightLifecycleAcceptance.fixtureLivenessReleased());
        evidence.put("ssrdProjectionMatrixPresent", true);
        evidence.put("ssrdEffectiveMaxChunks", distance.chunks());
        evidence.put("ssrdDistanceSource", distance.source());
        evidence.put("distantHorizonsLoaded", ModList.get().isLoaded("distanthorizons"));
        evidence.put("sodiumLoaded", ModList.get().isLoaded("sodium"));
        evidence.put("finalStableTicks", finalStableTicks);
        SkyforgeAutomatedAcceptanceHarness.completeClientCase(evidence);
        minecraft.stop();
    }

    private static void fail(String reason) {
        if (complete) {
            return;
        }
        complete = true;
        SkyforgeWbyWave1LateralFlightLifecycleAcceptance.markClientComplete();
        SkyforgeAutomatedAcceptanceHarness.failClientCase(reason);
        Minecraft.getInstance().stop();
    }

    private record DistanceEvidence(int chunks, String source) {}
}
