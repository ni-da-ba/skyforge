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

/** Actual-client proof that two distinct distant Sable craft remain concurrently renderable through DH/SSRD. */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID, value = Dist.CLIENT)
final class SkyforgeWbyWave1TwoCraftClientAcceptance {
    private static final long CLIENT_TIMEOUT_NANOS = 150_000_000_000L;
    private static final int REQUIRED_STABLE_TICKS = 20;
    private static final int VANILLA_RENDER_DISTANCE_CHUNKS = 4;
    private static final double MAX_TRANSFORM_ERROR_BLOCKS = 8.0;

    private static long firstTickNanos = Long.MIN_VALUE;
    private static int stableTicks;
    private static boolean renderObserved;
    private static boolean armed;
    private static boolean complete;

    private SkyforgeWbyWave1TwoCraftClientAcceptance() {}

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean(SkyforgeWbyWave1TwoCraftLifecycleAcceptance.ENABLE_PROPERTY)
                || !SkyforgeAutomatedAcceptanceHarness.clientMode()
                || complete) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (firstTickNanos == Long.MIN_VALUE) {
            firstTickNanos = System.nanoTime();
        }
        if (System.nanoTime() - firstTickNanos > CLIENT_TIMEOUT_NANOS) {
            fail("WBY Wave 1 two-craft actual-client proof did not qualify within 150 seconds"
                    + "; fixtureReady=" + SkyforgeWbyWave1TwoCraftLifecycleAcceptance.fixtureReady()
                    + "; stableTicks=" + stableTicks
                    + "; renderObserved=" + renderObserved
                    + "; armed=" + armed);
            return;
        }

        LocalPlayer player = minecraft.player;
        if (minecraft.level == null || player == null || minecraft.gameMode == null || minecraft.screen != null) {
            return;
        }

        SkyforgeWbyWave1TwoCraftLifecycleAcceptance.Snapshot snapshot =
                SkyforgeWbyWave1TwoCraftLifecycleAcceptance.snapshot();
        if (snapshot == null) {
            return;
        }

        try {
            requireClientMods();
            Object bodyA = clientSubLevel(minecraft.level, snapshot.bodyAId());
            Object bodyB = clientSubLevel(minecraft.level, snapshot.bodyBId());
            if (bodyA == null || bodyB == null) {
                if (armed) {
                    fail("one of two distant Sable bodies disappeared after simultaneous client observation"
                            + ": bodyA=" + (bodyA != null) + ", bodyB=" + (bodyB != null));
                }
                stableTicks = 0;
                return;
            }

            boolean finalizedA = (Boolean) publicMethod(bodyA, "isFinalized").invoke(bodyA);
            boolean finalizedB = (Boolean) publicMethod(bodyB, "isFinalized").invoke(bodyB);
            if (!finalizedA || !finalizedB) {
                if (armed) {
                    fail("one of two distant Sable bodies lost finalized client state"
                            + ": finalizedA=" + finalizedA + ", finalizedB=" + finalizedB);
                }
                stableTicks = 0;
                return;
            }
            armed = true;

            Vec3 renderA = renderPosition(bodyA);
            Vec3 renderB = renderPosition(bodyB);
            double errorA = renderA.distanceTo(snapshot.expectedBodyACenter());
            double errorB = renderB.distanceTo(snapshot.expectedBodyBCenter());
            if (errorA > MAX_TRANSFORM_ERROR_BLOCKS || errorB > MAX_TRANSFORM_ERROR_BLOCKS) {
                fail("two-craft render transform drift exceeded limit"
                        + ": errorA=" + errorA + ", errorB=" + errorB);
                return;
            }

            Vec3 midpoint = renderA.add(renderB).scale(0.5);
            lookAt(player, midpoint);

            double distanceA = horizontalDistance(renderA, player);
            double distanceB = horizontalDistance(renderB, player);
            double vanillaBlocks = VANILLA_RENDER_DISTANCE_CHUNKS * 16.0;
            boolean bothBeyondVanilla = distanceA > vanillaBlocks + 32.0
                    && distanceB > vanillaBlocks + 32.0;

            Object renderDataA = publicMethod(bodyA, "getRenderData").invoke(bodyA);
            Object renderDataB = publicMethod(bodyB, "getRenderData").invoke(bodyB);
            boolean bothRenderDataPresent = renderDataA != null && renderDataB != null;

            Class<?> stateClass = Class.forName("net.ranold.ssrd.SSRDState");
            Field visibleField = stateClass.getField("SUBLEVELS_VISIBLE_THIS_FRAME");
            renderObserved |= visibleField.getBoolean(null);
            boolean projectionMatrixPresent = stateClass.getField("PURE_PROJ_MATRIX").get(null) != null;

            DistanceEvidence distance = ssrdDistanceEvidence(VANILLA_RENDER_DISTANCE_CHUNKS);
            double bodySeparation = renderA.distanceTo(renderB);

            boolean qualified = !snapshot.bodyAId().equals(snapshot.bodyBId())
                    && bothBeyondVanilla
                    && bothRenderDataPresent
                    && renderObserved
                    && projectionMatrixPresent
                    && "Distant Horizons".equals(distance.source())
                    && distance.chunks() > VANILLA_RENDER_DISTANCE_CHUNKS
                    && bodySeparation > 16.0;

            if (!qualified) {
                stableTicks = 0;
                return;
            }

            stableTicks++;
            if (stableTicks >= REQUIRED_STABLE_TICKS) {
                complete(
                        minecraft,
                        snapshot,
                        renderA,
                        renderB,
                        errorA,
                        errorB,
                        distanceA,
                        distanceB,
                        bodySeparation,
                        projectionMatrixPresent,
                        distance);
            }
        } catch (ReflectiveOperationException | RuntimeException failure) {
            fail("WBY Wave 1 two-craft client proof failed: " + failure);
        }
    }

    private static void requireClientMods() {
        for (String modId : java.util.List.of(
                "create", "sable", "aeronautics", "sodium", "distanthorizons", "ssrd")) {
            if (!ModList.get().isLoaded(modId)) {
                throw new IllegalStateException("required WBY two-craft client mod not loaded: " + modId);
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

    private static double horizontalDistance(Vec3 target, LocalPlayer player) {
        return Math.hypot(target.x - player.getX(), target.z - player.getZ());
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
            SkyforgeWbyWave1TwoCraftLifecycleAcceptance.Snapshot snapshot,
            Vec3 renderA,
            Vec3 renderB,
            double errorA,
            double errorB,
            double distanceA,
            double distanceB,
            double bodySeparation,
            boolean projectionMatrixPresent,
            DistanceEvidence distance) {
        if (complete) {
            return;
        }
        complete = true;
        SkyforgeWbyWave1TwoCraftLifecycleAcceptance.markClientComplete();

        LinkedHashMap<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("actualClient", true);
        evidence.put("bodyAId", snapshot.bodyAId());
        evidence.put("bodyBId", snapshot.bodyBId());
        evidence.put("twoDistinctBodies", !snapshot.bodyAId().equals(snapshot.bodyBId()));
        evidence.put("bothClientSubLevelsFinalized", true);
        evidence.put("bodyARenderPosition", renderA);
        evidence.put("bodyBRenderPosition", renderB);
        evidence.put("bodyAExpectedCenter", snapshot.expectedBodyACenter());
        evidence.put("bodyBExpectedCenter", snapshot.expectedBodyBCenter());
        evidence.put("bodyATransformErrorBlocks", errorA);
        evidence.put("bodyBTransformErrorBlocks", errorB);
        evidence.put("bodyATransformMatchesExpected", errorA <= MAX_TRANSFORM_ERROR_BLOCKS);
        evidence.put("bodyBTransformMatchesExpected", errorB <= MAX_TRANSFORM_ERROR_BLOCKS);
        evidence.put("bodyAHorizontalDistanceBlocks", distanceA);
        evidence.put("bodyBHorizontalDistanceBlocks", distanceB);
        evidence.put("bodySeparationBlocks", bodySeparation);
        evidence.put("vanillaRenderDistanceChunks", VANILLA_RENDER_DISTANCE_CHUNKS);
        evidence.put("bothBeyondVanillaRenderDistance", true);
        evidence.put("bothSsrdRenderDataPresent", true);
        evidence.put("ssrdSublevelRenderObserved", renderObserved);
        evidence.put("ssrdProjectionMatrixPresent", projectionMatrixPresent);
        evidence.put("ssrdEffectiveMaxChunks", distance.chunks());
        evidence.put("ssrdDistanceSource", distance.source());
        evidence.put("distantHorizonsLoaded", ModList.get().isLoaded("distanthorizons"));
        evidence.put("sodiumLoaded", ModList.get().isLoaded("sodium"));
        evidence.put("stableQualifiedTicks", stableTicks);
        SkyforgeAutomatedAcceptanceHarness.completeClientCase(evidence);
        minecraft.stop();
    }

    private static void fail(String reason) {
        if (complete) {
            return;
        }
        complete = true;
        SkyforgeWbyWave1TwoCraftLifecycleAcceptance.markClientComplete();
        SkyforgeAutomatedAcceptanceHarness.failClientCase(reason);
        Minecraft.getInstance().stop();
    }

    private record DistanceEvidence(int chunks, String source) {}
}
