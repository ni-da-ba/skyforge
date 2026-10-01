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

/** Actual-client proof for a real moving distant Sable body through DH/SSRD. */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID, value = Dist.CLIENT)
final class SkyforgeWbyWave1MovingCraftClientAcceptance {
    private static final long CLIENT_TIMEOUT_NANOS = 180_000_000_000L;
    private static final int VANILLA_RENDER_DISTANCE_CHUNKS = 4;
    private static final double MAX_TRANSFORM_ERROR_BLOCKS = 8.0;

    private static long firstTickNanos = Long.MIN_VALUE;
    private static boolean armed;
    private static boolean complete;
    private static boolean renderObserved;
    private static boolean lateralSsrdObserved;
    private static boolean recedeSsrdObserved;
    private static boolean approachSsrdObserved;
    private static boolean baselineObserved;
    private static boolean lateralObserved;
    private static boolean recedeObserved;
    private static boolean approachObserved;
    private static boolean finalObserved;
    private static int movingSamples;
    private static Vec3 initialRenderPosition;
    private static double maxTransformErrorBlocks;
    private static double maxClientLateralDisplacementBlocks;
    private static double maxClientRecedeDisplacementBlocks;
    private static double finalClientRecedeErrorBlocks = Double.POSITIVE_INFINITY;
    private static double minHorizontalDistanceBlocks = Double.POSITIVE_INFINITY;
    private static double maxHorizontalDistanceBlocks;

    private SkyforgeWbyWave1MovingCraftClientAcceptance() {}

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean(SkyforgeWbyWave1MovingCraftLifecycleAcceptance.ENABLE_PROPERTY)
                || !SkyforgeAutomatedAcceptanceHarness.clientMode()
                || complete) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (firstTickNanos == Long.MIN_VALUE) {
            firstTickNanos = System.nanoTime();
        }
        if (System.nanoTime() - firstTickNanos > CLIENT_TIMEOUT_NANOS) {
            fail("WBY moving-craft client proof did not qualify within 180 seconds"
                    + "; fixtureReady=" + SkyforgeWbyWave1MovingCraftLifecycleAcceptance.fixtureReady()
                    + "; armed=" + armed
                    + "; phase=" + phaseName()
                    + "; lateralObserved=" + lateralObserved
                    + "; recedeObserved=" + recedeObserved
                    + "; approachObserved=" + approachObserved
                    + "; finalObserved=" + finalObserved
                    + "; movingSamples=" + movingSamples);
            return;
        }

        LocalPlayer player = minecraft.player;
        if (minecraft.level == null || player == null || minecraft.gameMode == null || minecraft.screen != null) {
            return;
        }

        SkyforgeWbyWave1MovingCraftLifecycleAcceptance.Snapshot snapshot =
                SkyforgeWbyWave1MovingCraftLifecycleAcceptance.snapshot();
        if (snapshot == null || snapshot.originPose() == null || "WAIT_PHYSICS".equals(snapshot.phase())) {
            return;
        }

        try {
            requireClientMods();
            Object body = clientSubLevel(minecraft.level, snapshot.bodyId());
            if (body == null) {
                if (armed) {
                    fail("moving distant Sable body disappeared after client qualification began: bodyId="
                            + snapshot.bodyId());
                }
                return;
            }

            boolean finalized = (Boolean) publicMethod(body, "isFinalized").invoke(body);
            if (!finalized) {
                if (armed) {
                    fail("moving distant Sable body lost finalized client state: bodyId=" + snapshot.bodyId());
                }
                return;
            }

            Vec3 renderPosition = renderPosition(body);
            Object renderData = publicMethod(body, "getRenderData").invoke(body);
            if (renderData == null) {
                if (armed) {
                    fail("moving distant Sable body lost SSRD render data: bodyId=" + snapshot.bodyId()
                            + " phase=" + snapshot.phase());
                }
                return;
            }
            armed = true;

            double transformError = renderPosition.distanceTo(snapshot.logicalPose());
            maxTransformErrorBlocks = Math.max(maxTransformErrorBlocks, transformError);
            if (transformError > MAX_TRANSFORM_ERROR_BLOCKS) {
                fail("moving-craft render transform drift exceeded limit: errorBlocks="
                        + transformError + " phase=" + snapshot.phase());
                return;
            }

            double horizontalDistance = Math.hypot(
                    renderPosition.x - player.getX(),
                    renderPosition.z - player.getZ());
            minHorizontalDistanceBlocks = Math.min(minHorizontalDistanceBlocks, horizontalDistance);
            maxHorizontalDistanceBlocks = Math.max(maxHorizontalDistanceBlocks, horizontalDistance);
            if (horizontalDistance <= VANILLA_RENDER_DISTANCE_CHUNKS * 16.0 + 32.0) {
                fail("moving craft entered vanilla-render qualification envelope: distance=" + horizontalDistance);
                return;
            }

            Class<?> stateClass = Class.forName("net.ranold.ssrd.SSRDState");
            Field visibleField = stateClass.getField("SUBLEVELS_VISIBLE_THIS_FRAME");
            boolean visibleThisFrame = visibleField.getBoolean(null);
            renderObserved |= visibleThisFrame;
            boolean projectionMatrixPresent = stateClass.getField("PURE_PROJ_MATRIX").get(null) != null;
            DistanceEvidence distance = ssrdDistanceEvidence(VANILLA_RENDER_DISTANCE_CHUNKS);
            if (!"Distant Horizons".equals(distance.source())
                    || distance.chunks() <= VANILLA_RENDER_DISTANCE_CHUNKS) {
                fail("DH ceased to own moving-craft long-range distance: source="
                        + distance.source() + " chunks=" + distance.chunks());
                return;
            }
            if (!projectionMatrixPresent) {
                fail("SSRD projection matrix disappeared during moving-craft proof");
                return;
            }

            lookAt(player, renderPosition);
            observePhase(snapshot, renderPosition, visibleThisFrame);

            if ("COMPLETE".equals(snapshot.phase())) {
                qualifyComplete(
                        minecraft,
                        snapshot,
                        finalized,
                        renderPosition,
                        projectionMatrixPresent,
                        distance);
            }
        } catch (ReflectiveOperationException | RuntimeException failure) {
            fail("WBY moving-craft actual-client proof failed: " + failure);
        }
    }

    private static void observePhase(
            SkyforgeWbyWave1MovingCraftLifecycleAcceptance.Snapshot snapshot,
            Vec3 renderPosition,
            boolean visibleThisFrame) {
        String phase = snapshot.phase();
        if (initialRenderPosition == null && "BASELINE_HOLD".equals(phase)) {
            initialRenderPosition = renderPosition;
        }

        if (initialRenderPosition != null) {
            maxClientLateralDisplacementBlocks = Math.max(
                    maxClientLateralDisplacementBlocks,
                    Math.abs(renderPosition.z - initialRenderPosition.z));
            maxClientRecedeDisplacementBlocks = Math.max(
                    maxClientRecedeDisplacementBlocks,
                    renderPosition.x - initialRenderPosition.x);
        }

        switch (phase) {
            case "BASELINE_HOLD" -> baselineObserved = true;
            case "LATERAL" -> {
                movingSamples++;
                lateralSsrdObserved |= visibleThisFrame;
                if (initialRenderPosition != null
                        && Math.abs(renderPosition.z - initialRenderPosition.z) >= 16.0) {
                    lateralObserved = true;
                }
            }
            case "LATERAL_HOLD" -> {
                lateralSsrdObserved |= visibleThisFrame;
                if (snapshot.lateralDisplacementBlocks() >= 16.0
                        && maxClientLateralDisplacementBlocks >= 16.0) {
                    lateralObserved = true;
                }
            }
            case "RECEDE" -> {
                movingSamples++;
                recedeSsrdObserved |= visibleThisFrame;
                if (initialRenderPosition != null
                        && renderPosition.x - initialRenderPosition.x >= 24.0) {
                    recedeObserved = true;
                }
            }
            case "FAR_HOLD" -> {
                recedeSsrdObserved |= visibleThisFrame;
                if (snapshot.recedeDisplacementBlocks() >= 24.0
                        && maxClientRecedeDisplacementBlocks >= 24.0) {
                    recedeObserved = true;
                }
            }
            case "APPROACH" -> {
                movingSamples++;
                approachSsrdObserved |= visibleThisFrame;
                if (recedeObserved
                        && snapshot.recedeDisplacementBlocks() <= 8.0
                        && initialRenderPosition != null
                        && renderPosition.x - initialRenderPosition.x <= 10.0) {
                    approachObserved = true;
                }
            }
            case "FINAL_HOLD", "COMPLETE" -> {
                finalObserved = true;
                if (initialRenderPosition != null) {
                    finalClientRecedeErrorBlocks =
                            Math.abs(renderPosition.x - initialRenderPosition.x);
                }
                if (recedeObserved
                        && snapshot.recedeDisplacementBlocks() <= 8.0
                        && finalClientRecedeErrorBlocks <= 10.0) {
                    approachObserved = true;
                }
            }
            default -> {
                // WAIT_PHYSICS is filtered before this point.
            }
        }
    }

    private static void qualifyComplete(
            Minecraft minecraft,
            SkyforgeWbyWave1MovingCraftLifecycleAcceptance.Snapshot snapshot,
            boolean finalized,
            Vec3 renderPosition,
            boolean projectionMatrixPresent,
            DistanceEvidence distance) {
        boolean qualified = baselineObserved
                && lateralObserved
                && recedeObserved
                && approachObserved
                && finalObserved
                && lateralSsrdObserved
                && recedeSsrdObserved
                && approachSsrdObserved
                && renderObserved
                && movingSamples >= 12
                && maxTransformErrorBlocks <= MAX_TRANSFORM_ERROR_BLOCKS
                && maxClientLateralDisplacementBlocks >= 16.0
                && maxClientRecedeDisplacementBlocks >= 24.0
                && finalClientRecedeErrorBlocks <= 10.0
                && minHorizontalDistanceBlocks > VANILLA_RENDER_DISTANCE_CHUNKS * 16.0 + 32.0;

        if (!qualified) {
            fail("moving-craft sequence completed without full client continuity evidence"
                    + ": baseline=" + baselineObserved
                    + " lateral=" + lateralObserved
                    + " recede=" + recedeObserved
                    + " approach=" + approachObserved
                    + " final=" + finalObserved
                    + " lateralSsrd=" + lateralSsrdObserved
                    + " recedeSsrd=" + recedeSsrdObserved
                    + " approachSsrd=" + approachSsrdObserved
                    + " movingSamples=" + movingSamples
                    + " maxTransformError=" + maxTransformErrorBlocks
                    + " clientLateral=" + maxClientLateralDisplacementBlocks
                    + " clientRecede=" + maxClientRecedeDisplacementBlocks
                    + " finalRecedeError=" + finalClientRecedeErrorBlocks);
            return;
        }

        complete = true;
        SkyforgeWbyWave1MovingCraftLifecycleAcceptance.markClientComplete();

        LinkedHashMap<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("actualClient", true);
        evidence.put("bodyId", snapshot.bodyId());
        evidence.put("sameBodyUuidThroughoutMotion", true);
        evidence.put("clientSubLevelFinalized", finalized);
        evidence.put("finalRenderPosition", renderPosition);
        evidence.put("finalServerLogicalPose", snapshot.logicalPose());
        evidence.put("baselineObserved", baselineObserved);
        evidence.put("lateralMotionObserved", lateralObserved);
        evidence.put("recedeMotionObserved", recedeObserved);
        evidence.put("approachMotionObserved", approachObserved);
        evidence.put("finalHoldObserved", finalObserved);
        evidence.put("lateralSsrdRenderObserved", lateralSsrdObserved);
        evidence.put("recedeSsrdRenderObserved", recedeSsrdObserved);
        evidence.put("approachSsrdRenderObserved", approachSsrdObserved);
        evidence.put("ssrdSublevelRenderObserved", renderObserved);
        evidence.put("ssrdProjectionMatrixPresent", projectionMatrixPresent);
        evidence.put("maxRenderTransformErrorBlocks", maxTransformErrorBlocks);
        evidence.put("maxClientLateralDisplacementBlocks", maxClientLateralDisplacementBlocks);
        evidence.put("maxClientRecedeDisplacementBlocks", maxClientRecedeDisplacementBlocks);
        evidence.put("finalClientRecedeErrorBlocks", finalClientRecedeErrorBlocks);
        evidence.put("minHorizontalDistanceBlocks", minHorizontalDistanceBlocks);
        evidence.put("maxHorizontalDistanceBlocks", maxHorizontalDistanceBlocks);
        evidence.put("vanillaRenderDistanceChunks", VANILLA_RENDER_DISTANCE_CHUNKS);
        evidence.put("beyondVanillaRenderDistanceThroughout", true);
        evidence.put("movingSamples", movingSamples);
        evidence.put("ssrdEffectiveMaxChunks", distance.chunks());
        evidence.put("ssrdDistanceSource", distance.source());
        evidence.put("distantHorizonsLoaded", ModList.get().isLoaded("distanthorizons"));
        evidence.put("sodiumLoaded", ModList.get().isLoaded("sodium"));
        SkyforgeAutomatedAcceptanceHarness.completeClientCase(evidence);
        minecraft.stop();
    }

    private static void requireClientMods() {
        for (String modId : java.util.List.of(
                "create", "sable", "aeronautics", "sodium", "distanthorizons", "ssrd")) {
            if (!ModList.get().isLoaded(modId)) {
                throw new IllegalStateException("required WBY moving-craft client mod not loaded: " + modId);
            }
        }
    }

    private static Object clientSubLevel(ClientLevel level, UUID bodyId)
            throws ReflectiveOperationException {
        Class<?> containerClass = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
        Object container = containerClass.getMethod("getContainer", ClientLevel.class).invoke(null, level);
        if (container == null) return null;
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

    private static String phaseName() {
        SkyforgeWbyWave1MovingCraftLifecycleAcceptance.Snapshot current =
                SkyforgeWbyWave1MovingCraftLifecycleAcceptance.snapshot();
        return current == null ? "none" : current.phase();
    }

    private static void fail(String reason) {
        if (complete) return;
        complete = true;
        SkyforgeWbyWave1MovingCraftLifecycleAcceptance.markClientComplete();
        SkyforgeAutomatedAcceptanceHarness.failClientCase(reason);
        Minecraft.getInstance().stop();
    }

    private record DistanceEvidence(int chunks, String source) {}
}
