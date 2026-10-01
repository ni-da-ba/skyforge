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

/** Actual-client proof that a fresh-process reloaded WBY Sable body remains visible through DH/SSRD. */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID, value = Dist.CLIENT)
final class SkyforgeWbyWave1PersistenceReloadClientAcceptance {
    private static final long CLIENT_TIMEOUT_NANOS = 150_000_000_000L;
    private static final int REQUIRED_STABLE_TICKS = 20;
    private static final int VANILLA_RENDER_DISTANCE_CHUNKS = 4;

    private static long firstTickNanos = Long.MIN_VALUE;
    private static int stableTicks;
    private static boolean renderObserved;
    private static boolean complete;

    private SkyforgeWbyWave1PersistenceReloadClientAcceptance() {}

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (!"reload".equals(System.getProperty(SkyforgeWbyWave1PersistenceAcceptance.PHASE_PROPERTY, ""))
                || !SkyforgeAutomatedAcceptanceHarness.clientMode()
                || complete) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (firstTickNanos == Long.MIN_VALUE) {
            firstTickNanos = System.nanoTime();
        }
        if (System.nanoTime() - firstTickNanos > CLIENT_TIMEOUT_NANOS) {
            fail("WBY Wave 1 save/reload actual-client proof did not qualify within 150 seconds");
            return;
        }

        LocalPlayer player = minecraft.player;
        if (minecraft.level == null || player == null || minecraft.gameMode == null || minecraft.screen != null) {
            return;
        }

        SkyforgeWbyWave1PersistenceAcceptance.ReloadSnapshot snapshot =
                SkyforgeWbyWave1PersistenceAcceptance.reloadSnapshot();
        if (snapshot == null) {
            return;
        }

        try {
            requireClientMods();
            Object body = clientSubLevel(minecraft.level, snapshot.bodyId());
            if (body == null) {
                stableTicks = 0;
                return;
            }

            boolean finalized = (Boolean) publicMethod(body, "isFinalized").invoke(body);
            if (!finalized) {
                stableTicks = 0;
                return;
            }

            Vec3 renderPosition = renderPosition(body);
            double transformError = renderPosition.distanceTo(snapshot.expectedBodyCenter());
            boolean transformMatches = transformError <= 8.0;
            lookAt(player, renderPosition);
            double horizontalDistance = Math.hypot(
                    renderPosition.x - player.getX(),
                    renderPosition.z - player.getZ());
            boolean beyondVanilla = horizontalDistance > VANILLA_RENDER_DISTANCE_CHUNKS * 16.0 + 32.0;

            Object renderData = publicMethod(body, "getRenderData").invoke(body);
            boolean renderDataPresent = renderData != null;
            Class<?> stateClass = Class.forName("net.ranold.ssrd.SSRDState");
            Field visibleField = stateClass.getField("SUBLEVELS_VISIBLE_THIS_FRAME");
            renderObserved |= visibleField.getBoolean(null);
            boolean projectionMatrixPresent = stateClass.getField("PURE_PROJ_MATRIX").get(null) != null;
            DistanceEvidence distance = ssrdDistanceEvidence(VANILLA_RENDER_DISTANCE_CHUNKS);

            boolean qualified = snapshot.samePersistentUuid()
                    && snapshot.pointerVerified()
                    && snapshot.currentPhysicsHandleValid()
                    && snapshot.serverReloadTransformErrorBlocks() <= 8.0
                    && beyondVanilla
                    && transformMatches
                    && renderDataPresent
                    && renderObserved
                    && projectionMatrixPresent
                    && "Distant Horizons".equals(distance.source())
                    && distance.chunks() > VANILLA_RENDER_DISTANCE_CHUNKS;

            if (!qualified) {
                stableTicks = 0;
                return;
            }

            stableTicks++;
            if (stableTicks >= REQUIRED_STABLE_TICKS) {
                complete(
                        minecraft,
                        snapshot,
                        finalized,
                        renderPosition,
                        transformError,
                        horizontalDistance,
                        renderDataPresent,
                        projectionMatrixPresent,
                        distance);
            }
        } catch (ReflectiveOperationException | RuntimeException failure) {
            fail("WBY Wave 1 save/reload client proof failed: " + failure);
        }
    }

    private static void requireClientMods() {
        for (String modId : java.util.List.of(
                "create", "sable", "aeronautics", "sodium", "distanthorizons", "ssrd")) {
            if (!ModList.get().isLoaded(modId)) {
                throw new IllegalStateException("required WBY persistence client mod not loaded: " + modId);
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
            SkyforgeWbyWave1PersistenceAcceptance.ReloadSnapshot snapshot,
            boolean finalized,
            Vec3 renderPosition,
            double transformError,
            double horizontalDistance,
            boolean renderDataPresent,
            boolean projectionMatrixPresent,
            DistanceEvidence distance) {
        if (complete) {
            return;
        }
        complete = true;
        SkyforgeWbyWave1PersistenceAcceptance.markClientComplete();

        LinkedHashMap<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("reloaded", true);
        evidence.put("actualClient", true);
        evidence.put("bodyId", snapshot.bodyId());
        evidence.put("samePersistentUuid", snapshot.samePersistentUuid());
        evidence.put("persistencePointerVerified", snapshot.pointerVerified());
        evidence.put("currentPhysicsHandleValid", snapshot.currentPhysicsHandleValid());
        evidence.put("clientSubLevelFinalized", finalized);
        evidence.put("renderPosition", renderPosition);
        evidence.put("expectedBodyCenter", snapshot.expectedBodyCenter());
        evidence.put("serverReloadTransformErrorBlocks", snapshot.serverReloadTransformErrorBlocks());
        evidence.put("renderTransformErrorBlocks", transformError);
        evidence.put("renderTransformMatchesExpected", transformError <= 8.0);
        evidence.put("horizontalDistanceBlocks", horizontalDistance);
        evidence.put("vanillaRenderDistanceChunks", VANILLA_RENDER_DISTANCE_CHUNKS);
        evidence.put("beyondVanillaRenderDistance", true);
        evidence.put("ssrdRenderDataPresent", renderDataPresent);
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
        SkyforgeWbyWave1PersistenceAcceptance.markClientComplete();
        SkyforgeAutomatedAcceptanceHarness.failClientCase(reason);
    }

    private record DistanceEvidence(int chunks, String source) {}
}
