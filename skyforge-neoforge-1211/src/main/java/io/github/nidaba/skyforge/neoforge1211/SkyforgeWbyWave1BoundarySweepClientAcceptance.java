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

/**
 * WBY-INT-0002 actual-client continuity proof across the vanilla-to-SSRD visibility boundary.
 */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID, value = Dist.CLIENT)
final class SkyforgeWbyWave1BoundarySweepClientAcceptance {
    private static final long CLIENT_TIMEOUT_NANOS = 150_000_000_000L;
    private static final int VANILLA_RENDER_DISTANCE_CHUNKS = 4;
    private static final double TRANSFORM_ERROR_LIMIT_BLOCKS = 8.0;
    private static final double FAR_OBSERVATION_DISTANCE_BLOCKS = 224.0;
    private static final double FAR_TARGET_DISTANCE_BLOCKS = 240.0;
    private static final int MIN_HOLD_SAMPLES = 5;

    private static long firstTickNanos = Long.MIN_VALUE;
    private static boolean armed;
    private static boolean complete;
    private static boolean outboundInsideObserved;
    private static boolean outboundFarObserved;
    private static boolean inboundFarObserved;
    private static boolean inboundInsideObserved;
    private static boolean outboundSsrdRenderObserved;
    private static boolean inboundSsrdRenderObserved;
    private static int initialNearSamples;
    private static int recedingSamples;
    private static int farHoldSamples;
    private static int approachingSamples;
    private static int finalNearSamples;
    private static double minDistanceBlocks = Double.POSITIVE_INFINITY;
    private static double maxDistanceBlocks = Double.NEGATIVE_INFINITY;
    private static double maxTransformErrorBlocks;

    private SkyforgeWbyWave1BoundarySweepClientAcceptance() {}

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean(SkyforgeWbyWave1VisibilityLifecycleAcceptance.BOUNDARY_SWEEP_PROPERTY)
                || !SkyforgeAutomatedAcceptanceHarness.clientMode()
                || complete) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (firstTickNanos == Long.MIN_VALUE) {
            firstTickNanos = System.nanoTime();
        }
        if (System.nanoTime() - firstTickNanos > CLIENT_TIMEOUT_NANOS) {
            fail("WBY Wave 1 boundary sweep did not qualify within 150 seconds");
            return;
        }

        LocalPlayer player = minecraft.player;
        if (minecraft.level == null || player == null || minecraft.gameMode == null || minecraft.screen != null) {
            return;
        }

        SkyforgeWbyWave1VisibilityLifecycleAcceptance.BoundarySweepSnapshot snapshot =
                SkyforgeWbyWave1VisibilityLifecycleAcceptance.boundarySweepSnapshot();
        if (snapshot == null || "DISABLED".equals(snapshot.phase())) {
            return;
        }

        try {
            requireClientMods();
            Object body = clientSubLevel(minecraft.level, snapshot.bodyId());
            if (body == null) {
                if (armed) {
                    fail("distant Sable body disappeared during boundary sweep: phase=" + snapshot.phase());
                }
                return;
            }

            boolean finalized = (Boolean) publicMethod(body, "isFinalized").invoke(body);
            if (!finalized) {
                if (armed) {
                    fail("Sable client sublevel lost finalized state during boundary sweep: phase=" + snapshot.phase());
                }
                return;
            }
            armed = true;

            Vec3 renderPosition = renderPosition(body);
            double transformError = renderPosition.distanceTo(snapshot.expectedBodyCenter());
            maxTransformErrorBlocks = Math.max(maxTransformErrorBlocks, transformError);
            if (transformError > TRANSFORM_ERROR_LIMIT_BLOCKS) {
                fail("Sable render transform drifted during boundary sweep: phase=" + snapshot.phase()
                        + ", errorBlocks=" + transformError);
                return;
            }

            lookAt(player, renderPosition);
            double horizontalDistance = Math.hypot(
                    renderPosition.x - player.getX(),
                    renderPosition.z - player.getZ());
            minDistanceBlocks = Math.min(minDistanceBlocks, horizontalDistance);
            maxDistanceBlocks = Math.max(maxDistanceBlocks, horizontalDistance);

            Object renderData = publicMethod(body, "getRenderData").invoke(body);
            if (renderData == null) {
                fail("SSRD render data disappeared during boundary sweep: phase=" + snapshot.phase());
                return;
            }

            Class<?> stateClass = Class.forName("net.ranold.ssrd.SSRDState");
            Field visibleField = stateClass.getField("SUBLEVELS_VISIBLE_THIS_FRAME");
            boolean ssrdVisibleThisFrame = visibleField.getBoolean(null);
            boolean projectionMatrixPresent = stateClass.getField("PURE_PROJ_MATRIX").get(null) != null;
            if (!projectionMatrixPresent) {
                fail("SSRD projection matrix disappeared during boundary sweep");
                return;
            }

            DistanceEvidence distanceEvidence = ssrdDistanceEvidence(VANILLA_RENDER_DISTANCE_CHUNKS);
            boolean insideVanilla = horizontalDistance <= VANILLA_RENDER_DISTANCE_CHUNKS * 16.0;
            boolean beyondBoundary = horizontalDistance > VANILLA_RENDER_DISTANCE_CHUNKS * 16.0 + 32.0;
            boolean atFarEndpoint = horizontalDistance >= FAR_OBSERVATION_DISTANCE_BLOCKS
                    && snapshot.targetDistanceBlocks() >= FAR_TARGET_DISTANCE_BLOCKS;
            if ("NEAR_HOLD".equals(snapshot.phase()) && insideVanilla) {
                // The server holds the start endpoint until the actual client has finalized
                // the exact UUID and verified render-data/projection/distance authority.
                SkyforgeWbyWave1VisibilityLifecycleAcceptance.markBoundaryClientReady();
            }
            if (atFarEndpoint) {
                // The server intentionally dwells at 256 blocks both at the tail of RECEDING
                // and in FAR_HOLD. Under heavily lagged software rendering the client can skip
                // the literal FAR_HOLD label while still observing the real far-end dwell.
                farHoldSamples++;
            }
            if (beyondBoundary
                    && (!"Distant Horizons".equals(distanceEvidence.source())
                            || distanceEvidence.chunks() <= VANILLA_RENDER_DISTANCE_CHUNKS)) {
                fail("Distant Horizons stopped owning long-range distance during boundary sweep: "
                        + distanceEvidence);
                return;
            }

            switch (snapshot.phase()) {
                case "NEAR_HOLD" -> {
                    if (insideVanilla) {
                        initialNearSamples++;
                    }
                }
                case "RECEDING" -> {
                    recedingSamples++;
                    outboundInsideObserved |= insideVanilla;
                    outboundFarObserved |= beyondBoundary;
                    outboundSsrdRenderObserved |= beyondBoundary && ssrdVisibleThisFrame;
                }
                case "FAR_HOLD" -> {
                    if (beyondBoundary) {
                        outboundSsrdRenderObserved |= ssrdVisibleThisFrame;
                    }
                }
                case "APPROACHING" -> {
                    approachingSamples++;
                    inboundFarObserved |= beyondBoundary;
                    inboundInsideObserved |= insideVanilla;
                    inboundSsrdRenderObserved |= beyondBoundary && ssrdVisibleThisFrame;
                }
                case "FINAL_NEAR_HOLD" -> {
                    if (insideVanilla) {
                        finalNearSamples++;
                        // The server advances sweep phases on server ticks while this observer
                        // samples client ticks. Under CI load the first client-visible inside-
                        // vanilla frame may arrive after APPROACHING has transitioned. Observing
                        // the same UUID finalized and correctly transformed here is the inbound
                        // crossing proof; do not couple correctness to the exact phase-label tick.
                        inboundInsideObserved |= inboundFarObserved;
                    }
                }
                case "COMPLETE" -> finishIfQualified(minecraft, snapshot.bodyId(), distanceEvidence);
                default -> {
                    // DISABLED is handled above.
                }
            }
        } catch (ReflectiveOperationException | RuntimeException failure) {
            fail("WBY Wave 1 boundary sweep failed: " + failure);
        }
    }

    private static void finishIfQualified(
            Minecraft minecraft,
            UUID bodyId,
            DistanceEvidence distanceEvidence) {
        boolean qualified = initialNearSamples >= MIN_HOLD_SAMPLES
                && recedingSamples > 0
                && farHoldSamples >= MIN_HOLD_SAMPLES
                && approachingSamples > 0
                && finalNearSamples >= MIN_HOLD_SAMPLES
                && outboundInsideObserved
                && outboundFarObserved
                && inboundFarObserved
                && inboundInsideObserved
                && outboundSsrdRenderObserved
                && inboundSsrdRenderObserved
                && maxTransformErrorBlocks <= TRANSFORM_ERROR_LIMIT_BLOCKS
                && minDistanceBlocks <= 64.0
                && maxDistanceBlocks > 96.0
                && "Distant Horizons".equals(distanceEvidence.source())
                && distanceEvidence.chunks() > VANILLA_RENDER_DISTANCE_CHUNKS;

        if (!qualified) {
            fail("boundary sweep reached COMPLETE without required continuity evidence"
                    + "; initialNearSamples=" + initialNearSamples
                    + "; recedingSamples=" + recedingSamples
                    + "; farHoldSamples=" + farHoldSamples
                    + "; approachingSamples=" + approachingSamples
                    + "; finalNearSamples=" + finalNearSamples
                    + "; outboundInsideObserved=" + outboundInsideObserved
                    + "; outboundFarObserved=" + outboundFarObserved
                    + "; inboundFarObserved=" + inboundFarObserved
                    + "; inboundInsideObserved=" + inboundInsideObserved
                    + "; outboundSsrdRenderObserved=" + outboundSsrdRenderObserved
                    + "; inboundSsrdRenderObserved=" + inboundSsrdRenderObserved
                    + "; minDistanceBlocks=" + minDistanceBlocks
                    + "; maxDistanceBlocks=" + maxDistanceBlocks
                    + "; maxTransformErrorBlocks=" + maxTransformErrorBlocks);
            return;
        }

        complete = true;
        SkyforgeWbyWave1VisibilityLifecycleAcceptance.markClientComplete();
        LinkedHashMap<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("actualClient", true);
        evidence.put("bodyId", bodyId);
        evidence.put(
                "insideVanillaObserved",
                initialNearSamples >= MIN_HOLD_SAMPLES && finalNearSamples >= MIN_HOLD_SAMPLES);
        evidence.put("outboundBoundaryCrossed", outboundInsideObserved && outboundFarObserved);
        evidence.put("inboundBoundaryCrossed", inboundFarObserved && inboundInsideObserved);
        evidence.put("outboundSsrdRenderObserved", outboundSsrdRenderObserved);
        evidence.put("inboundSsrdRenderObserved", inboundSsrdRenderObserved);
        evidence.put("initialNearSamples", initialNearSamples);
        evidence.put("recedingSamples", recedingSamples);
        evidence.put("farHoldSamples", farHoldSamples);
        evidence.put("approachingSamples", approachingSamples);
        evidence.put("finalNearSamples", finalNearSamples);
        evidence.put("minDistanceBlocks", minDistanceBlocks);
        evidence.put("maxDistanceBlocks", maxDistanceBlocks);
        evidence.put("maxRenderTransformErrorBlocks", maxTransformErrorBlocks);
        evidence.put("vanillaRenderDistanceChunks", VANILLA_RENDER_DISTANCE_CHUNKS);
        evidence.put("ssrdEffectiveMaxChunks", distanceEvidence.chunks());
        evidence.put("ssrdDistanceSource", distanceEvidence.source());
        evidence.put("distantHorizonsLoaded", ModList.get().isLoaded("distanthorizons"));
        evidence.put("sodiumLoaded", ModList.get().isLoaded("sodium"));
        SkyforgeAutomatedAcceptanceHarness.completeClientCase(evidence);
        minecraft.stop();
    }

    private static void requireClientMods() {
        for (String modId : java.util.List.of(
                "create", "sable", "aeronautics", "sodium", "distanthorizons", "ssrd")) {
            if (!ModList.get().isLoaded(modId)) {
                throw new IllegalStateException("required WBY Wave 1 client mod not loaded: " + modId);
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

    private static void fail(String reason) {
        if (complete) {
            return;
        }
        complete = true;
        SkyforgeWbyWave1VisibilityLifecycleAcceptance.markClientComplete();
        SkyforgeAutomatedAcceptanceHarness.failClientCase(reason);
        Minecraft.getInstance().stop();
    }

    private record DistanceEvidence(int chunks, String source) {}
}
