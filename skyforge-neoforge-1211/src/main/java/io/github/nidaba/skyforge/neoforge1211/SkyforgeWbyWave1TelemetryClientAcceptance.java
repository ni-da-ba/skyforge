package io.github.nidaba.skyforge.neoforge1211;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collections;
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
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ATIMeminfo;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.NVXGPUMemoryInfo;

/**
 * Actual-client telemetry sampler for the WBY-INT-0002 W1-A baseline.
 *
 * <p>This intentionally uses a fixed, repeatable camera profile: one stationary observer looking
 * directly at the single distant Sable body authored by the dedicated-server visibility fixture.
 * W1-B optimizer tranches can reuse the same sampler and compare like-for-like evidence.
 */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID, value = Dist.CLIENT)
final class SkyforgeWbyWave1TelemetryClientAcceptance {
    static final String ENABLE_PROPERTY = SkyforgeWbyWave1TelemetryServerAcceptance.ENABLE_PROPERTY;

    private static final long CLIENT_TIMEOUT_NANOS = 180_000_000_000L;
    private static final int WARMUP_QUALIFIED_TICKS = 40;
    private static final int REQUIRED_FRAME_SAMPLES = 240;
    private static final int DEFAULT_VANILLA_RENDER_DISTANCE_CHUNKS = 4;

    private static final List<Double> FRAME_MS = new ArrayList<>();
    private static final List<Long> USED_HEAP_BYTES = new ArrayList<>();
    private static final List<Integer> VISIBLE_SUBLEVEL_COUNTS = new ArrayList<>();

    private static long firstTickNanos = Long.MIN_VALUE;
    private static long lastFrameNanos = Long.MIN_VALUE;
    private static int qualifiedTicks;
    private static boolean sampling;
    private static boolean renderObserved;
    private static boolean complete;
    private static UUID selectedBodyId;
    private static Vec3 selectedRenderPosition;
    private static double selectedHorizontalDistance;
    private static int vanillaChunks;
    private static DistanceEvidence distanceEvidence;
    private static int dhChunks;

    private SkyforgeWbyWave1TelemetryClientAcceptance() {}

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)
                || !SkyforgeAutomatedAcceptanceHarness.clientMode()
                || complete) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (firstTickNanos == Long.MIN_VALUE) {
            firstTickNanos = System.nanoTime();
        }
        if (System.nanoTime() - firstTickNanos > CLIENT_TIMEOUT_NANOS) {
            fail("WBY telemetry client timed out"
                    + "; sampling=" + sampling
                    + "; qualifiedTicks=" + qualifiedTicks
                    + "; frameSamples=" + FRAME_MS.size()
                    + "; bodyId=" + selectedBodyId);
            return;
        }

        LocalPlayer player = minecraft.player;
        if (minecraft.level == null
                || player == null
                || minecraft.gameMode == null
                || minecraft.screen != null) {
            return;
        }

        try {
            requireClientMods();
            if (minecraft.hasSingleplayerServer()) {
                fail("WBY telemetry requires a real multiplayer client connected to a dedicated server");
                return;
            }

            vanillaChunks = Integer.getInteger(
                    "skyforge.dev.wbyWave1VanillaRenderDistanceChunks",
                    minecraft.options.renderDistance().get());
            distanceEvidence = ssrdDistanceEvidence(vanillaChunks);
            dhChunks = dhChunkDistance();

            BodyCandidate candidate = findDistantBody(minecraft.level, player.position(), vanillaChunks);
            if (candidate == null) {
                qualifiedTicks = 0;
                return;
            }

            selectedBodyId = candidate.bodyId();
            selectedRenderPosition = candidate.renderPosition();
            selectedHorizontalDistance = candidate.horizontalDistanceBlocks();
            lookAt(player, candidate.renderPosition());

            Class<?> stateClass = Class.forName("net.ranold.ssrd.SSRDState");
            Field visibleField = stateClass.getField("SUBLEVELS_VISIBLE_THIS_FRAME");
            renderObserved |= visibleField.getBoolean(null);
            boolean projectionMatrixPresent = stateClass.getField("PURE_PROJ_MATRIX").get(null) != null;

            boolean qualified = candidate.finalized()
                    && candidate.renderDataPresent()
                    && renderObserved
                    && projectionMatrixPresent
                    && candidate.horizontalDistanceBlocks() > vanillaChunks * 16.0 + 32.0
                    && "Distant Horizons".equals(distanceEvidence.source())
                    && distanceEvidence.chunks() > vanillaChunks
                    && dhChunks >= distanceEvidence.chunks();

            if (!qualified) {
                qualifiedTicks = 0;
                sampling = false;
                lastFrameNanos = Long.MIN_VALUE;
                FRAME_MS.clear();
                USED_HEAP_BYTES.clear();
                VISIBLE_SUBLEVEL_COUNTS.clear();
                return;
            }

            qualifiedTicks++;
            if (qualifiedTicks >= WARMUP_QUALIFIED_TICKS) {
                sampling = true;
                Runtime runtime = Runtime.getRuntime();
                USED_HEAP_BYTES.add(runtime.totalMemory() - runtime.freeMemory());
                VISIBLE_SUBLEVEL_COUNTS.add(countEligibleVisibleSublevels(
                        minecraft.level,
                        player.position(),
                        vanillaChunks,
                        distanceEvidence.chunks()));

                if (FRAME_MS.size() >= REQUIRED_FRAME_SAMPLES) {
                    complete(minecraft);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException failure) {
            fail("WBY telemetry client failed: " + failure);
        }
    }

    @SubscribeEvent
    static void onFrame(RenderFrameEvent.Post event) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)
                || !SkyforgeAutomatedAcceptanceHarness.clientMode()
                || complete
                || !sampling) {
            return;
        }

        long now = System.nanoTime();
        if (lastFrameNanos != Long.MIN_VALUE) {
            double frameMs = (now - lastFrameNanos) / 1_000_000.0;
            if (Double.isFinite(frameMs) && frameMs > 0.0 && frameMs < 10_000.0) {
                FRAME_MS.add(frameMs);
            }
        }
        lastFrameNanos = now;
    }

    private static BodyCandidate findDistantBody(
            ClientLevel level,
            Vec3 playerPosition,
            int vanillaRenderDistanceChunks) throws ReflectiveOperationException {
        Class<?> containerClass = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
        Object container = containerClass.getMethod("getContainer", ClientLevel.class).invoke(null, level);
        if (container == null) {
            return null;
        }

        Object value = publicMethod(container, "getAllSubLevels").invoke(container);
        if (!(value instanceof List<?> subLevels)) {
            return null;
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
            double horizontalDistance = Math.hypot(
                    renderPosition.x - playerPosition.x,
                    renderPosition.z - playerPosition.z);
            if (horizontalDistance <= vanillaRenderDistanceChunks * 16.0 + 32.0) {
                continue;
            }
            Object renderData = publicMethod(body, "getRenderData").invoke(body);
            BodyCandidate candidate = new BodyCandidate(
                    bodyId,
                    renderPosition,
                    horizontalDistance,
                    true,
                    renderData != null);
            if (best == null || candidate.horizontalDistanceBlocks() < best.horizontalDistanceBlocks()) {
                best = candidate;
            }
        }
        return best;
    }

    private static int countEligibleVisibleSublevels(
            ClientLevel level,
            Vec3 playerPosition,
            int vanillaRenderDistanceChunks,
            int ssrdMaxChunks) throws ReflectiveOperationException {
        Class<?> containerClass = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
        Object container = containerClass.getMethod("getContainer", ClientLevel.class).invoke(null, level);
        if (container == null) {
            return 0;
        }
        Object value = publicMethod(container, "getAllSubLevels").invoke(container);
        if (!(value instanceof List<?> subLevels)) {
            return 0;
        }

        int count = 0;
        double minBlocks = vanillaRenderDistanceChunks * 16.0;
        double maxBlocks = ssrdMaxChunks * 16.0;
        for (Object body : subLevels) {
            if (!(Boolean) publicMethod(body, "isFinalized").invoke(body)) {
                continue;
            }
            if (publicMethod(body, "getRenderData").invoke(body) == null) {
                continue;
            }
            Vec3 position = renderPosition(body);
            double horizontalDistance = Math.hypot(
                    position.x - playerPosition.x,
                    position.z - playerPosition.z);
            if (horizontalDistance > minBlocks && horizontalDistance <= maxBlocks) {
                count++;
            }
        }
        return count;
    }

    private static Vec3 renderPosition(Object body) throws ReflectiveOperationException {
        Object pose = publicMethod(body, "renderPose").invoke(body);
        Object position = publicMethod(pose, "position").invoke(pose);
        return new Vec3(
                ((Number) publicMethod(position, "x").invoke(position)).doubleValue(),
                ((Number) publicMethod(position, "y").invoke(position)).doubleValue(),
                ((Number) publicMethod(position, "z").invoke(position)).doubleValue());
    }

    private static DistanceEvidence ssrdDistanceEvidence(int vanillaRenderDistanceChunks)
            throws ReflectiveOperationException {
        Class<?> limits = Class.forName("net.ranold.ssrd.client.DistanceLimits");
        Object effective = limits.getMethod("query", int.class).invoke(null, vanillaRenderDistanceChunks);
        int chunks = ((Number) effective.getClass().getMethod("chunks").invoke(effective)).intValue();
        String source = String.valueOf(effective.getClass().getMethod("source").invoke(effective));
        return new DistanceEvidence(chunks, source);
    }

    private static int dhChunkDistance() throws ReflectiveOperationException {
        Class<?> delayed = Class.forName("com.seibel.distanthorizons.api.DhApi$Delayed");
        Object configs = delayed.getField("configs").get(null);
        if (configs == null) {
            throw new IllegalStateException("Distant Horizons delayed config API unavailable");
        }
        Object graphics = publicMethod(configs, "graphics").invoke(configs);
        Object chunkRenderDistance = publicMethod(graphics, "chunkRenderDistance").invoke(graphics);
        Object value = publicMethod(chunkRenderDistance, "getValue").invoke(chunkRenderDistance);
        if (!(value instanceof Number number)) {
            throw new IllegalStateException("DH chunk render distance was not numeric: " + value);
        }
        return number.intValue();
    }

    private static void requireClientMods() {
        for (String modId : List.of(
                "create", "sable", "aeronautics", "sodium", "distanthorizons", "ssrd")) {
            if (!ModList.get().isLoaded(modId)) {
                throw new IllegalStateException("required WBY telemetry client mod not loaded: " + modId);
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

    private static void complete(Minecraft minecraft) {
        if (complete) {
            return;
        }
        if (selectedBodyId == null
                || selectedRenderPosition == null
                || distanceEvidence == null
                || FRAME_MS.size() < REQUIRED_FRAME_SAMPLES
                || USED_HEAP_BYTES.isEmpty()
                || VISIBLE_SUBLEVEL_COUNTS.isEmpty()) {
            return;
        }

        complete = true;
        VramEvidence vram = vramEvidence();
        LinkedHashMap<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("actualClient", true);
        evidence.put("actualMultiplayer", !minecraft.hasSingleplayerServer());
        evidence.put("benchmarkCameraProfile", "stationary-distant-body-v1");
        evidence.put("bodyId", selectedBodyId);
        evidence.put("renderPosition", selectedRenderPosition);
        evidence.put("horizontalDistanceBlocks", selectedHorizontalDistance);
        evidence.put("frameSamples", FRAME_MS.size());
        evidence.put("frameTimeMedianMs", percentile(FRAME_MS, 0.50));
        evidence.put("frameTimeP95Ms", percentile(FRAME_MS, 0.95));
        evidence.put("frameTimeMaxMs", Collections.max(FRAME_MS));
        evidence.put("clientHeapSamples", USED_HEAP_BYTES.size());
        evidence.put("clientHeapMedianBytes", percentileLongs(USED_HEAP_BYTES, 0.50));
        evidence.put("clientHeapP95Bytes", percentileLongs(USED_HEAP_BYTES, 0.95));
        evidence.put("clientHeapMaxBytes", Collections.max(USED_HEAP_BYTES));
        evidence.put("visibleSableSublevelsMedian", percentileInts(VISIBLE_SUBLEVEL_COUNTS, 0.50));
        evidence.put("visibleSableSublevelsMax", Collections.max(VISIBLE_SUBLEVEL_COUNTS));
        evidence.put("vanillaRenderDistanceChunks", vanillaChunks);
        evidence.put("dhRenderDistanceChunks", dhChunks);
        evidence.put("ssrdEffectiveMaxChunks", distanceEvidence.chunks());
        evidence.put("ssrdDistanceSource", distanceEvidence.source());
        evidence.put("ssrdSublevelRenderObserved", renderObserved);
        evidence.put("vramTelemetryAvailable", vram.available());
        evidence.put("vramTelemetrySource", vram.source());
        evidence.put("vramTotalBytes", vram.totalBytes());
        evidence.put("vramAvailableBytes", vram.availableBytes());
        evidence.put("vramUsedBytes", vram.usedBytes());
        evidence.put("glVendor", safeGlString(GL11.GL_VENDOR));
        evidence.put("glRenderer", safeGlString(GL11.GL_RENDERER));
        evidence.put("sodiumLoaded", ModList.get().isLoaded("sodium"));
        evidence.put("distantHorizonsLoaded", ModList.get().isLoaded("distanthorizons"));
        evidence.put("telemetryBaselineQualified", true);

        SkyforgeAutomatedAcceptanceHarness.completeClientCase(evidence);
        minecraft.stop();
    }

    private static VramEvidence vramEvidence() {
        try {
            var caps = GL.getCapabilities();
            if (caps.GL_NVX_gpu_memory_info) {
                long total = ((long) GL11.glGetInteger(
                        NVXGPUMemoryInfo.GL_GPU_MEMORY_INFO_DEDICATED_VIDMEM_NVX)) * 1024L;
                long available = ((long) GL11.glGetInteger(
                        NVXGPUMemoryInfo.GL_GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX)) * 1024L;
                return new VramEvidence(true, "GL_NVX_gpu_memory_info", total, available,
                        Math.max(0L, total - available));
            }
            if (caps.GL_ATI_meminfo) {
                IntBuffer values = BufferUtils.createIntBuffer(4);
                GL11.glGetIntegerv(ATIMeminfo.GL_TEXTURE_FREE_MEMORY_ATI, values);
                long available = ((long) values.get(0)) * 1024L;
                return new VramEvidence(true, "GL_ATI_meminfo", -1L, available, -1L);
            }
        } catch (RuntimeException ignored) {
            // Capability is recorded explicitly below instead of inventing VRAM data.
        }
        return new VramEvidence(false, "unavailable", -1L, -1L, -1L);
    }

    private static String safeGlString(int token) {
        try {
            String value = GL11.glGetString(token);
            return value == null ? "unavailable" : value;
        } catch (RuntimeException failure) {
            return "unavailable";
        }
    }

    private static double percentile(List<Double> values, double quantile) {
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int index = Math.max(0, Math.min(sorted.size() - 1,
                (int) Math.ceil(quantile * sorted.size()) - 1));
        return sorted.get(index);
    }

    private static long percentileLongs(List<Long> values, double quantile) {
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int index = Math.max(0, Math.min(sorted.size() - 1,
                (int) Math.ceil(quantile * sorted.size()) - 1));
        return sorted.get(index);
    }

    private static int percentileInts(List<Integer> values, double quantile) {
        List<Integer> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int index = Math.max(0, Math.min(sorted.size() - 1,
                (int) Math.ceil(quantile * sorted.size()) - 1));
        return sorted.get(index);
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
        SkyforgeAutomatedAcceptanceHarness.failClientCase(reason);
        Minecraft.getInstance().stop();
    }

    private record BodyCandidate(
            UUID bodyId,
            Vec3 renderPosition,
            double horizontalDistanceBlocks,
            boolean finalized,
            boolean renderDataPresent) {}

    private record DistanceEvidence(int chunks, String source) {}

    private record VramEvidence(
            boolean available,
            String source,
            long totalBytes,
            long availableBytes,
            long usedBytes) {}
}
