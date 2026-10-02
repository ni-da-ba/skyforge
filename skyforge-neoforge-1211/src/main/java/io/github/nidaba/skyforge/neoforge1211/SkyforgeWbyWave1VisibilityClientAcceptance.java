package io.github.nidaba.skyforge.neoforge1211;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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

/** Actual-client half of WBY-INT-0002 distant Sable + DH/SSRD qualification. */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID, value = Dist.CLIENT)
final class SkyforgeWbyWave1VisibilityClientAcceptance {
    private static final long CLIENT_TIMEOUT_NANOS = 120_000_000_000L;
    private static final int REQUIRED_STABLE_TICKS = 20;
    private static final int DEFAULT_VANILLA_RENDER_DISTANCE_CHUNKS = 4;

    private static long firstClientTickNanos = Long.MIN_VALUE;
    private static int stableTicks;
    private static boolean renderObserved;
    private static boolean clientComplete;
    private static int fpsSamples;
    private static long fpsSum;
    private static int fpsMin = Integer.MAX_VALUE;
    private static int fpsMax = Integer.MIN_VALUE;

    private SkyforgeWbyWave1VisibilityClientAcceptance() {}

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean(SkyforgeWbyWave1VisibilityLifecycleAcceptance.ENABLE_PROPERTY)
                // Composite WBY cases reuse the server-side visibility fixture but own their
                // terminal client evidence. Do not let the baseline verifier stop Minecraft first.
                || Boolean.getBoolean(SkyforgeWbyWave1TrainSanityLifecycleAcceptance.ENABLE_PROPERTY)
                || Boolean.getBoolean(SkyforgeWbyWave1TelemetryClientAcceptance.ENABLE_PROPERTY)
                || !SkyforgeAutomatedAcceptanceHarness.clientMode()
                || clientComplete) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (firstClientTickNanos == Long.MIN_VALUE) {
            firstClientTickNanos = System.nanoTime();
        }
        if (System.nanoTime() - firstClientTickNanos > CLIENT_TIMEOUT_NANOS) {
            fail("WBY Wave 1 actual-client visibility did not qualify within 120 seconds"
                    + "; level=" + (minecraft.level != null)
                    + "; player=" + (minecraft.player != null)
                    + "; gameMode=" + (minecraft.gameMode != null)
                    + "; screen=" + (minecraft.screen == null ? "none" : minecraft.screen.getClass().getName())
                    + "; fixtureReady=" + SkyforgeWbyWave1VisibilityLifecycleAcceptance.fixtureReady()
                    + "; stableTicks=" + stableTicks
                    + "; renderObserved=" + renderObserved);
            return;
        }
        dismissDistantHorizonsUpdateScreen(minecraft);
        if (minecraft.level == null || player == null || minecraft.gameMode == null || minecraft.screen != null) {
            return;
        }

        SkyforgeWbyWave1VisibilityLifecycleAcceptance.Snapshot snapshot =
                SkyforgeWbyWave1VisibilityLifecycleAcceptance.snapshot();
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
            Vec3 expectedBodyCenter = snapshot.expectedBodyCenter();
            double renderTransformErrorBlocks = renderPosition.distanceTo(expectedBodyCenter);
            boolean renderTransformMatchesExpected = renderTransformErrorBlocks <= 8.0;
            lookAt(player, renderPosition);
            double horizontalDistance = Math.hypot(
                    renderPosition.x - player.getX(),
                    renderPosition.z - player.getZ());

            int vanillaChunks = Integer.getInteger(
                    "skyforge.dev.wbyWave1VanillaRenderDistanceChunks",
                    DEFAULT_VANILLA_RENDER_DISTANCE_CHUNKS);
            double vanillaBlocks = vanillaChunks * 16.0;
            boolean beyondVanilla = horizontalDistance > vanillaBlocks + 32.0;

            Object renderData = publicMethod(body, "getRenderData").invoke(body);
            boolean renderDataPresent = renderData != null;

            Class<?> stateClass = Class.forName("net.ranold.ssrd.SSRDState");
            Field visibleField = stateClass.getField("SUBLEVELS_VISIBLE_THIS_FRAME");
            renderObserved |= visibleField.getBoolean(null);
            boolean projectionMatrixPresent = stateClass.getField("PURE_PROJ_MATRIX").get(null) != null;

            DistanceEvidence distance = ssrdDistanceEvidence(vanillaChunks);
            sampleFps(minecraft);

            boolean qualified = beyondVanilla
                    && renderTransformMatchesExpected
                    && renderDataPresent
                    && renderObserved
                    && projectionMatrixPresent
                    && "Distant Horizons".equals(distance.source())
                    && distance.chunks() > vanillaChunks;

            if (!qualified) {
                stableTicks = 0;
                return;
            }

            stableTicks++;
            if (stableTicks >= REQUIRED_STABLE_TICKS) {
                complete(
                        minecraft,
                        snapshot.bodyId(),
                        finalized,
                        renderPosition,
                        expectedBodyCenter,
                        renderTransformErrorBlocks,
                        horizontalDistance,
                        vanillaChunks,
                        renderDataPresent,
                        projectionMatrixPresent,
                        distance);
            }
        } catch (ReflectiveOperationException | RuntimeException failure) {
            fail("WBY Wave 1 actual-client visibility failed: " + failure);
        }
    }

    private static void dismissDistantHorizonsUpdateScreen(Minecraft minecraft) {
        if (minecraft.screen == null) {
            return;
        }
        String screenClass = minecraft.screen.getClass().getName();
        if (screenClass.startsWith("com.seibel.distanthorizons.")
                && screenClass.contains(".updater.UpdateModScreen")) {
            // The CI specimen intentionally pins DH, so its interactive "new version available"
            // screen must not intercept --quickPlaySingleplayer. This is acceptance-harness-only
            // behavior; normal players still see the upstream update UI.
            minecraft.setScreen(null);
        }
    }

    private static void requireClientMods() {
        for (String modId : java.util.List.of(
                "create",
                "sable",
                "aeronautics",
                "sodium",
                "distanthorizons",
                "ssrd")) {
            if (!ModList.get().isLoaded(modId)) {
                throw new IllegalStateException("required WBY Wave 1 client mod not loaded: " + modId);
            }
        }
    }

    private static Object clientSubLevel(ClientLevel level, UUID bodyId)
            throws ReflectiveOperationException {
        Class<?> containerClass = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
        Method getContainer = containerClass.getMethod("getContainer", ClientLevel.class);
        Object container = getContainer.invoke(null, level);
        if (container == null) {
            return null;
        }
        return publicMethod(container, "getSubLevel", UUID.class).invoke(container, bodyId);
    }

    private static Vec3 renderPosition(Object body) throws ReflectiveOperationException {
        Object pose = publicMethod(body, "renderPose").invoke(body);
        Object position = publicMethod(pose, "position").invoke(pose);
        double x = ((Number) publicMethod(position, "x").invoke(position)).doubleValue();
        double y = ((Number) publicMethod(position, "y").invoke(position)).doubleValue();
        double z = ((Number) publicMethod(position, "z").invoke(position)).doubleValue();
        return new Vec3(x, y, z);
    }

    private static DistanceEvidence ssrdDistanceEvidence(int vanillaChunks)
            throws ReflectiveOperationException {
        Class<?> limits = Class.forName("net.ranold.ssrd.client.DistanceLimits");
        Object effective = limits.getMethod("query", int.class).invoke(null, vanillaChunks);
        int chunks = ((Number) effective.getClass().getMethod("chunks").invoke(effective)).intValue();
        String source = String.valueOf(effective.getClass().getMethod("source").invoke(effective));
        return new DistanceEvidence(chunks, source);
    }

    private static void sampleFps(Minecraft minecraft) {
        try {
            Method method = Minecraft.class.getMethod("getFps");
            Object value = method.invoke(Modifier.isStatic(method.getModifiers()) ? null : minecraft);
            if (value instanceof Number number) {
                int fps = number.intValue();
                if (fps >= 0) {
                    fpsSamples++;
                    fpsSum += fps;
                    fpsMin = Math.min(fpsMin, fps);
                    fpsMax = Math.max(fpsMax, fps);
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // FPS is supplementary evidence; render-path correctness is the acceptance gate.
        }
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
            UUID bodyId,
            boolean finalized,
            Vec3 renderPosition,
            Vec3 expectedBodyCenter,
            double renderTransformErrorBlocks,
            double horizontalDistance,
            int vanillaChunks,
            boolean renderDataPresent,
            boolean projectionMatrixPresent,
            DistanceEvidence distance) {
        if (clientComplete) {
            return;
        }
        clientComplete = true;
        SkyforgeWbyWave1VisibilityLifecycleAcceptance.markClientComplete();

        LinkedHashMap<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("actualClient", true);
        evidence.put("bodyId", bodyId);
        evidence.put("clientSubLevelFinalized", finalized);
        evidence.put("renderPosition", renderPosition);
        evidence.put("expectedBodyCenter", expectedBodyCenter);
        evidence.put("renderTransformErrorBlocks", renderTransformErrorBlocks);
        evidence.put("renderTransformMatchesExpected", renderTransformErrorBlocks <= 8.0);
        evidence.put("horizontalDistanceBlocks", horizontalDistance);
        evidence.put("vanillaRenderDistanceChunks", vanillaChunks);
        evidence.put("beyondVanillaRenderDistance", true);
        evidence.put("ssrdRenderDataPresent", renderDataPresent);
        evidence.put("ssrdSublevelRenderObserved", renderObserved);
        evidence.put("ssrdProjectionMatrixPresent", projectionMatrixPresent);
        evidence.put("ssrdEffectiveMaxChunks", distance.chunks());
        evidence.put("ssrdDistanceSource", distance.source());
        evidence.put("distantHorizonsLoaded", ModList.get().isLoaded("distanthorizons"));
        evidence.put("sodiumLoaded", ModList.get().isLoaded("sodium"));
        evidence.put("stableQualifiedTicks", stableTicks);
        evidence.put("fpsSamples", fpsSamples);
        if (fpsSamples > 0) {
            evidence.put("fpsAverage", ((double) fpsSum) / fpsSamples);
            evidence.put("fpsMin", fpsMin);
            evidence.put("fpsMax", fpsMax);
        }
        SkyforgeAutomatedAcceptanceHarness.completeClientCase(evidence);
        minecraft.stop();
    }

    private static void fail(String reason) {
        if (clientComplete) {
            return;
        }
        clientComplete = true;
        SkyforgeWbyWave1VisibilityLifecycleAcceptance.markClientComplete();
        SkyforgeAutomatedAcceptanceHarness.failClientCase(reason);
    }

    private record DistanceEvidence(int chunks, String source) {}
}
