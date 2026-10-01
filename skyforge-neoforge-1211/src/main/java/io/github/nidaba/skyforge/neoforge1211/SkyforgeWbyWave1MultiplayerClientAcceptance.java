package io.github.nidaba.skyforge.neoforge1211;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
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
 * Actual-client half of the WBY-INT-0002 multiplayer remote-craft proof.
 *
 * <p>The dedicated-server fixture independently proves that a second real client is physically
 * beside the body. This observer deliberately does not require that remote player's vanilla entity
 * to be tracked at long range; it tests only the WBY Sable/SSRD/DH visibility contract.
 */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID, value = Dist.CLIENT)
final class SkyforgeWbyWave1MultiplayerClientAcceptance {
    private static final long CLIENT_TIMEOUT_NANOS = 300_000_000_000L;
    private static final int REQUIRED_STABLE_TICKS = 20;
    private static final int VANILLA_RENDER_DISTANCE_CHUNKS = 4;
    private static final double MIN_DISTANT_HORIZONTAL_BLOCKS = 96.0;
    private static final double MAX_FIXTURE_HORIZONTAL_BLOCKS = 192.0;

    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeWbyWave1MultiplayerClientAcceptance.class.getName());

    private static long firstTickNanos = Long.MIN_VALUE;
    private static int diagnosticTicks;
    private static int stableTicks;
    private static boolean ssrdRenderObserved;
    private static boolean complete;
    private static UUID selectedBodyId;
    private static double observerBodyDistanceBlocks = Double.NaN;

    private SkyforgeWbyWave1MultiplayerClientAcceptance() {}

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean(SkyforgeWbyWave1MultiplayerLifecycleAcceptance.ENABLE_PROPERTY)
                || !"observer".equals(System.getProperty("skyforge.dev.wbyWave1MultiplayerRole", ""))
                || !SkyforgeAutomatedAcceptanceHarness.clientMode()
                || complete) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (firstTickNanos == Long.MIN_VALUE) {
            firstTickNanos = System.nanoTime();
            LOGGER.log(
                    System.Logger.Level.INFO,
                    "WBY MULTIPLAYER OBSERVER ACTIVE: role="
                            + System.getProperty("skyforge.dev.wbyWave1MultiplayerRole", "")
                            + ", clientMode=" + SkyforgeAutomatedAcceptanceHarness.clientMode());
        }
        if (System.nanoTime() - firstTickNanos > CLIENT_TIMEOUT_NANOS) {
            fail("WBY multiplayer observer did not qualify within 300 seconds"
                    + "; stableTicks=" + stableTicks
                    + "; ssrdRenderObserved=" + ssrdRenderObserved
                    + "; selectedBodyId=" + selectedBodyId
                    + "; observerBodyDistanceBlocks=" + observerBodyDistanceBlocks);
            return;
        }

        LocalPlayer observer = minecraft.player;
        if (minecraft.level == null
                || observer == null
                || minecraft.gameMode == null
                || minecraft.screen != null) {
            return;
        }

        try {
            requireClientMods();
            if (minecraft.hasSingleplayerServer()) {
                fail("WBY multiplayer observer unexpectedly ran against an integrated server");
                return;
            }

            BodyCandidate body = findQualifyingBody(minecraft.level, observer.position());
            diagnosticTicks++;
            if (diagnosticTicks == 1 || diagnosticTicks % 40 == 0) {
                logDiagnostics(minecraft.level, observer.position(), body);
            }
            if (body == null) {
                stableTicks = 0;
                return;
            }

            selectedBodyId = body.bodyId();
            observerBodyDistanceBlocks = body.observerHorizontalDistanceBlocks();
            lookAt(observer, body.renderPosition());

            Class<?> stateClass = Class.forName("net.ranold.ssrd.SSRDState");
            Field visibleField = stateClass.getField("SUBLEVELS_VISIBLE_THIS_FRAME");
            ssrdRenderObserved |= visibleField.getBoolean(null);
            boolean projectionMatrixPresent = stateClass.getField("PURE_PROJ_MATRIX").get(null) != null;
            DistanceEvidence distance = ssrdDistanceEvidence(VANILLA_RENDER_DISTANCE_CHUNKS);

            boolean qualified = body.renderDataPresent()
                    && body.finalized()
                    && observerBodyDistanceBlocks > MIN_DISTANT_HORIZONTAL_BLOCKS
                    && observerBodyDistanceBlocks < MAX_FIXTURE_HORIZONTAL_BLOCKS
                    && ssrdRenderObserved
                    && projectionMatrixPresent
                    && "Distant Horizons".equals(distance.source())
                    && distance.chunks() > VANILLA_RENDER_DISTANCE_CHUNKS;

            if (!qualified) {
                stableTicks = 0;
                return;
            }

            stableTicks++;
            if (stableTicks >= REQUIRED_STABLE_TICKS) {
                complete(minecraft, observer, body, distance);
            }
        } catch (ReflectiveOperationException | RuntimeException failure) {
            fail("WBY Wave 1 multiplayer observer proof failed: " + failure);
        }
    }

    private static BodyCandidate findQualifyingBody(ClientLevel level, Vec3 observerPosition)
            throws ReflectiveOperationException {
        Class<?> containerClass = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
        Object container = containerClass.getMethod("getContainer", ClientLevel.class).invoke(null, level);
        if (container == null) {
            return null;
        }
        Object value = publicMethod(container, "getAllSubLevels").invoke(container);
        if (!(value instanceof List<?> subLevels)) {
            throw new IllegalStateException("Sable client getAllSubLevels did not return a List");
        }

        BodyCandidate best = null;
        for (Object body : subLevels) {
            boolean finalized = (Boolean) publicMethod(body, "isFinalized").invoke(body);
            if (!finalized) {
                continue;
            }

            Object idValue = publicMethod(body, "getUniqueId").invoke(body);
            if (!(idValue instanceof UUID bodyId)) {
                continue;
            }

            Vec3 renderPosition = renderPosition(body);
            double observerDistance = horizontalDistance(observerPosition, renderPosition);
            if (observerDistance <= MIN_DISTANT_HORIZONTAL_BLOCKS
                    || observerDistance >= MAX_FIXTURE_HORIZONTAL_BLOCKS) {
                continue;
            }

            Object renderData = publicMethod(body, "getRenderData").invoke(body);
            BodyCandidate candidate = new BodyCandidate(
                    bodyId,
                    renderPosition,
                    observerDistance,
                    true,
                    renderData != null);
            if (best == null
                    || candidate.observerHorizontalDistanceBlocks()
                            < best.observerHorizontalDistanceBlocks()) {
                best = candidate;
            }
        }
        return best;
    }

    private static void logDiagnostics(
            ClientLevel level,
            Vec3 observerPosition,
            BodyCandidate selected) {
        try {
            Class<?> containerClass = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
            Object container = containerClass.getMethod("getContainer", ClientLevel.class).invoke(null, level);
            int subLevelCount = -1;
            StringBuilder bodies = new StringBuilder();
            if (container != null) {
                Object value = publicMethod(container, "getAllSubLevels").invoke(container);
                if (value instanceof List<?> subLevels) {
                    subLevelCount = subLevels.size();
                    for (Object body : subLevels) {
                        Object id = publicMethod(body, "getUniqueId").invoke(body);
                        boolean finalized = (Boolean) publicMethod(body, "isFinalized").invoke(body);
                        Vec3 position = renderPosition(body);
                        Object renderData = publicMethod(body, "getRenderData").invoke(body);
                        if (!bodies.isEmpty()) {
                            bodies.append("; ");
                        }
                        bodies.append(id)
                                .append("@")
                                .append(String.format(
                                        java.util.Locale.ROOT,
                                        "%.2f",
                                        horizontalDistance(observerPosition, position)))
                                .append("b")
                                .append(",final=").append(finalized)
                                .append(",renderData=").append(renderData != null);
                    }
                }
            }

            Class<?> stateClass = Class.forName("net.ranold.ssrd.SSRDState");
            boolean visible = stateClass.getField("SUBLEVELS_VISIBLE_THIS_FRAME").getBoolean(null);
            boolean projection = stateClass.getField("PURE_PROJ_MATRIX").get(null) != null;
            DistanceEvidence distance = ssrdDistanceEvidence(VANILLA_RENDER_DISTANCE_CHUNKS);
            LOGGER.log(
                    System.Logger.Level.INFO,
                    "WBY MULTIPLAYER OBSERVER DIAGNOSTIC: subLevels=" + subLevelCount
                            + ", bodies=[" + bodies + "]"
                            + ", selected=" + (selected == null ? "none" : selected.bodyId())
                            + ", ssrdVisibleNow=" + visible
                            + ", ssrdVisibleEver=" + ssrdRenderObserved
                            + ", projection=" + projection
                            + ", distance=" + distance.chunks() + "@" + distance.source()
                            + ", stableTicks=" + stableTicks
                            + ", screen=" + (Minecraft.getInstance().screen == null
                                    ? "none"
                                    : Minecraft.getInstance().screen.getClass().getName()));
        } catch (ReflectiveOperationException | RuntimeException failure) {
            LOGGER.log(
                    System.Logger.Level.WARNING,
                    "WBY MULTIPLAYER OBSERVER DIAGNOSTIC FAILED: " + failure);
        }
    }

    private static Vec3 renderPosition(Object body) throws ReflectiveOperationException {
        Object pose = publicMethod(body, "renderPose").invoke(body);
        Object position = publicMethod(pose, "position").invoke(pose);
        return new Vec3(
                ((Number) publicMethod(position, "x").invoke(position)).doubleValue(),
                ((Number) publicMethod(position, "y").invoke(position)).doubleValue(),
                ((Number) publicMethod(position, "z").invoke(position)).doubleValue());
    }

    private static double horizontalDistance(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    private static DistanceEvidence ssrdDistanceEvidence(int vanillaChunks)
            throws ReflectiveOperationException {
        Class<?> limits = Class.forName("net.ranold.ssrd.client.DistanceLimits");
        Object effective = limits.getMethod("query", int.class).invoke(null, vanillaChunks);
        int chunks = ((Number) effective.getClass().getMethod("chunks").invoke(effective)).intValue();
        String source = String.valueOf(effective.getClass().getMethod("source").invoke(effective));
        return new DistanceEvidence(chunks, source);
    }

    private static void requireClientMods() {
        for (String modId : List.of(
                "create", "sable", "aeronautics", "sodium", "distanthorizons", "ssrd")) {
            if (!ModList.get().isLoaded(modId)) {
                throw new IllegalStateException("required WBY multiplayer client mod not loaded: " + modId);
            }
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
            LocalPlayer observer,
            BodyCandidate body,
            DistanceEvidence distance) {
        if (complete) {
            return;
        }
        complete = true;

        LinkedHashMap<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("actualClient", true);
        evidence.put("actualMultiplayer", !minecraft.hasSingleplayerServer());
        evidence.put("observerPlayerName", observer.getGameProfile().getName());
        evidence.put("distantSableBodyId", body.bodyId());
        evidence.put("distantSableStableTicks", stableTicks);
        evidence.put("observerBodyHorizontalDistanceBlocks", body.observerHorizontalDistanceBlocks());
        evidence.put("sableRenderDataPresent", body.renderDataPresent());
        evidence.put("sableFinalized", body.finalized());
        evidence.put("ssrdSublevelRenderObserved", ssrdRenderObserved);
        evidence.put("ssrdEffectiveMaxChunks", distance.chunks());
        evidence.put("ssrdDistanceSource", distance.source());
        evidence.put("distantHorizonsLoaded", ModList.get().isLoaded("distanthorizons"));
        evidence.put("sodiumLoaded", ModList.get().isLoaded("sodium"));
        evidence.put("multiplayerRemoteCraftObserverQualified", true);
        SkyforgeAutomatedAcceptanceHarness.completeClientCase(evidence);
        minecraft.stop();
    }

    private static void fail(String reason) {
        if (complete) {
            return;
        }
        complete = true;
        SkyforgeAutomatedAcceptanceHarness.failClientCase(reason);
        Minecraft.getInstance().stop();
    }

    private record BodyCandidate(
            UUID bodyId,
            Vec3 renderPosition,
            double observerHorizontalDistanceBlocks,
            boolean finalized,
            boolean renderDataPresent) {}

    private record DistanceEvidence(int chunks, String source) {}
}
