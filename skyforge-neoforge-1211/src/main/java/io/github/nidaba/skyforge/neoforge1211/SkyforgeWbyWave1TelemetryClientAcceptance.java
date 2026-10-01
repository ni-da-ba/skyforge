package io.github.nidaba.skyforge.neoforge1211;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
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
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.NVXGPUMemoryInfo;

/**
 * Real-client half of the WBY-INT-0002 W1-A telemetry capture.
 *
 * <p>Sampling begins only after the accepted distant-Sable + SSRD + DH render contract is live.
 * Frame intervals are collected from actual rendered frames rather than 20 Hz client ticks.
 */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID, value = Dist.CLIENT)
final class SkyforgeWbyWave1TelemetryClientAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.wbyWave1TelemetryClient";
    static final String RESULT_FILE_PROPERTY = "skyforge.dev.wbyWave1TelemetryClientResultFile";

    private static final long TIMEOUT_NANOS = 180_000_000_000L;
    private static final int REQUIRED_QUALIFIED_TICKS = 20;
    private static final int FRAME_WARMUP = 30;
    private static final int FRAME_SAMPLES = 180;

    private static final List<Long> frameNanos = new ArrayList<>();
    private static final List<Long> heapUsedBytes = new ArrayList<>();
    private static final List<Long> clientLoadedChunks = new ArrayList<>();
    private static final List<Long> visibleSableSublevels = new ArrayList<>();
    private static final List<Long> vramUsedBytes = new ArrayList<>();

    private static long firstTickNanos = Long.MIN_VALUE;
    private static long previousFrameNanos;
    private static int qualifiedTicks;
    private static int renderedFramesSinceQualification;
    private static boolean ssrdRenderObserved;
    private static boolean sampling;
    private static boolean complete;
    private static boolean vramTelemetryAttempted;
    private static boolean vramMeasurementSupported;
    private static long vramTotalBytes;
    private static int vanillaChunks;
    private static int dhChunks;
    private static int ssrdChunks;
    private static String ssrdSource = "<unavailable>";
    private static UUID bodyId;

    private SkyforgeWbyWave1TelemetryClientAcceptance() {}

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (!enabled() || complete) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (firstTickNanos == Long.MIN_VALUE) {
            firstTickNanos = System.nanoTime();
        }
        if (System.nanoTime() - firstTickNanos > TIMEOUT_NANOS) {
            fail("WBY Wave 1 client telemetry timed out; qualifiedTicks="
                    + qualifiedTicks + ", frameSamples=" + frameNanos.size());
            return;
        }

        LocalPlayer player = minecraft.player;
        if (minecraft.level == null
                || player == null
                || minecraft.gameMode == null
                || minecraft.screen != null) {
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
            if (body == null || !(Boolean) publicMethod(body, "isFinalized").invoke(body)) {
                qualifiedTicks = 0;
                return;
            }

            Vec3 renderPosition = renderPosition(body);
            lookAt(player, renderPosition);
            Object renderData = publicMethod(body, "getRenderData").invoke(body);
            boolean renderDataPresent = renderData != null;

            vanillaChunks = minecraft.options.renderDistance().get();
            double horizontalDistance = Math.hypot(
                    renderPosition.x - player.getX(),
                    renderPosition.z - player.getZ());
            boolean beyondVanilla = horizontalDistance > vanillaChunks * 16.0 + 32.0;

            Class<?> stateClass = Class.forName("net.ranold.ssrd.SSRDState");
            Field visibleField = stateClass.getField("SUBLEVELS_VISIBLE_THIS_FRAME");
            ssrdRenderObserved |= visibleField.getBoolean(null);

            DistanceEvidence distance = ssrdDistanceEvidence(vanillaChunks);
            ssrdChunks = distance.chunks();
            ssrdSource = distance.source();
            dhChunks = distantHorizonsDistanceChunks();

            boolean qualified = renderDataPresent
                    && beyondVanilla
                    && ssrdRenderObserved
                    && "Distant Horizons".equals(ssrdSource)
                    && ssrdChunks > vanillaChunks
                    && dhChunks >= ssrdChunks;

            if (!qualified) {
                qualifiedTicks = 0;
                return;
            }

            bodyId = snapshot.bodyId();
            qualifiedTicks++;
            if (!sampling && qualifiedTicks >= REQUIRED_QUALIFIED_TICKS) {
                sampling = true;
                previousFrameNanos = 0L;
            }

            if (sampling) {
                sampleClientState(minecraft, ssrdChunks);
            }

            if (frameNanos.size() >= FRAME_SAMPLES) {
                complete(minecraft);
            }
        } catch (ReflectiveOperationException | RuntimeException failure) {
            fail("WBY Wave 1 client telemetry failed: " + failure);
        }
    }

    @SubscribeEvent
    static void onRenderedFrame(RenderFrameEvent.Post event) {
        if (!enabled() || !sampling || complete) {
            return;
        }
        long now = System.nanoTime();
        if (previousFrameNanos != 0L && now >= previousFrameNanos) {
            renderedFramesSinceQualification++;
            if (renderedFramesSinceQualification > FRAME_WARMUP) {
                long elapsed = now - previousFrameNanos;
                frameNanos.add(elapsed);
                SkyforgeRuntimePerformanceMetrics.recordDistributionSample(
                        "wbyWave1.clientFrameNanos", elapsed);
            }
        }
        previousFrameNanos = now;
    }

    private static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    private static void sampleClientState(Minecraft minecraft, int effectiveSsrdChunks)
            throws ReflectiveOperationException {
        long heap = Math.max(0L, Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
        long loaded = Math.max(0, minecraft.level.getChunkSource().getLoadedChunksCount());
        long visibleSublevels = visibleSublevelCount(
                minecraft.level,
                minecraft.player.position(),
                effectiveSsrdChunks);

        heapUsedBytes.add(heap);
        clientLoadedChunks.add(loaded);
        visibleSableSublevels.add(visibleSublevels);
        SkyforgeRuntimePerformanceMetrics.recordDistributionSample(
                "wbyWave1.clientHeapUsedBytes", heap);
        SkyforgeRuntimePerformanceMetrics.recordSample(
                "wbyWave1.clientLoadedChunks", loaded);
        SkyforgeRuntimePerformanceMetrics.recordSample(
                "wbyWave1.clientVisibleSableSublevels", visibleSublevels);

        sampleVram();
    }

    private static void sampleVram() {
        vramTelemetryAttempted = true;
        try {
            if (!GL.getCapabilities().GL_NVX_gpu_memory_info) {
                return;
            }
            long total = Math.max(
                    0,
                    GL11.glGetInteger(
                            NVXGPUMemoryInfo.GL_GPU_MEMORY_INFO_TOTAL_AVAILABLE_MEMORY_NVX))
                    * 1024L;
            long available = Math.max(
                    0,
                    GL11.glGetInteger(
                            NVXGPUMemoryInfo.GL_GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX))
                    * 1024L;
            if (total <= 0L || available < 0L || available > total) {
                return;
            }
            vramMeasurementSupported = true;
            vramTotalBytes = Math.max(vramTotalBytes, total);
            long used = total - available;
            vramUsedBytes.add(used);
            SkyforgeRuntimePerformanceMetrics.recordDistributionSample(
                    "wbyWave1.clientVramUsedBytes", used);
        } catch (RuntimeException ignored) {
            // Software CI renderers commonly do not expose vendor VRAM accounting.
        }
    }

    private static long visibleSublevelCount(
            ClientLevel level,
            Vec3 viewer,
            int maxChunks) throws ReflectiveOperationException {
        Class<?> containerClass = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
        Object container = containerClass.getMethod("getContainer", ClientLevel.class).invoke(null, level);
        if (container == null) {
            return 0L;
        }
        Object value = publicMethod(container, "getAllSubLevels").invoke(container);
        if (!(value instanceof List<?> subLevels)) {
            throw new IllegalStateException("Sable client getAllSubLevels did not return a List");
        }

        long count = 0L;
        double maxBlocks = maxChunks * 16.0;
        for (Object subLevel : subLevels) {
            if (!(Boolean) publicMethod(subLevel, "isFinalized").invoke(subLevel)) {
                continue;
            }
            if (publicMethod(subLevel, "getRenderData").invoke(subLevel) == null) {
                continue;
            }
            Vec3 position = renderPosition(subLevel);
            double horizontal = Math.hypot(position.x - viewer.x, position.z - viewer.z);
            if (horizontal <= maxBlocks) {
                count++;
            }
        }
        return count;
    }

    private static Object clientSubLevel(ClientLevel level, UUID expectedBodyId)
            throws ReflectiveOperationException {
        Class<?> containerClass = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
        Object container = containerClass.getMethod("getContainer", ClientLevel.class).invoke(null, level);
        if (container == null) {
            return null;
        }
        return publicMethod(container, "getSubLevel", UUID.class).invoke(container, expectedBodyId);
    }

    private static Vec3 renderPosition(Object body) throws ReflectiveOperationException {
        Object pose = publicMethod(body, "renderPose").invoke(body);
        Object position = publicMethod(pose, "position").invoke(pose);
        return new Vec3(
                ((Number) publicMethod(position, "x").invoke(position)).doubleValue(),
                ((Number) publicMethod(position, "y").invoke(position)).doubleValue(),
                ((Number) publicMethod(position, "z").invoke(position)).doubleValue());
    }

    private static DistanceEvidence ssrdDistanceEvidence(int vanillaRenderChunks)
            throws ReflectiveOperationException {
        Class<?> limits = Class.forName("net.ranold.ssrd.client.DistanceLimits");
        Object effective = limits.getMethod("query", int.class).invoke(null, vanillaRenderChunks);
        int chunks = ((Number) effective.getClass().getMethod("chunks").invoke(effective)).intValue();
        String source = String.valueOf(effective.getClass().getMethod("source").invoke(effective));
        return new DistanceEvidence(chunks, source);
    }

    private static int distantHorizonsDistanceChunks() throws ReflectiveOperationException {
        Class<?> quality = Class.forName(
                "com.seibel.distanthorizons.core.config.Config$Client$Advanced$Graphics$Quality");
        Object entry = quality.getField("lodChunkRenderDistanceRadius").get(null);
        Object value = entry.getClass().getMethod("get").invoke(entry);
        if (!(value instanceof Number number)) {
            throw new IllegalStateException("DH render-distance config was not numeric: " + value);
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
        complete = true;
        Properties p = new Properties();
        p.setProperty("status", "PASS");
        p.setProperty("telemetryProfile", "W1-A");
        p.setProperty("actualClient", "true");
        p.setProperty("bodyId", String.valueOf(bodyId));
        p.setProperty("frameTimeSamples", String.valueOf(frameNanos.size()));
        p.setProperty("frameTimeMedianNanos", String.valueOf(percentile(frameNanos, 50)));
        p.setProperty("frameTimeP95Nanos", String.valueOf(percentile(frameNanos, 95)));
        p.setProperty("frameTimeMedianMs", String.valueOf(nanosToMillis(percentile(frameNanos, 50))));
        p.setProperty("frameTimeP95Ms", String.valueOf(nanosToMillis(percentile(frameNanos, 95))));
        p.setProperty("clientHeapUsedMedianBytes", String.valueOf(percentile(heapUsedBytes, 50)));
        p.setProperty("clientHeapUsedP95Bytes", String.valueOf(percentile(heapUsedBytes, 95)));
        p.setProperty("clientLoadedChunksMedian", String.valueOf(percentile(clientLoadedChunks, 50)));
        p.setProperty("clientLoadedChunksMax", String.valueOf(maximum(clientLoadedChunks)));
        p.setProperty("visibleSableSublevelsMedian", String.valueOf(percentile(visibleSableSublevels, 50)));
        p.setProperty("visibleSableSublevelsMax", String.valueOf(maximum(visibleSableSublevels)));
        p.setProperty("vanillaRenderDistanceChunks", String.valueOf(vanillaChunks));
        p.setProperty("distantHorizonsDistanceChunks", String.valueOf(dhChunks));
        p.setProperty("ssrdEffectiveMaxChunks", String.valueOf(ssrdChunks));
        p.setProperty("ssrdDistanceSource", ssrdSource);
        p.setProperty("ssrdRenderObserved", String.valueOf(ssrdRenderObserved));
        p.setProperty("vramTelemetryAttempted", String.valueOf(vramTelemetryAttempted));
        p.setProperty("vramMeasurementSupported", String.valueOf(vramMeasurementSupported));
        p.setProperty("gpuVendor", safeGlString(GL11.GL_VENDOR));
        p.setProperty("gpuRenderer", safeGlString(GL11.GL_RENDERER));
        p.setProperty("gpuVersion", safeGlString(GL11.GL_VERSION));
        if (vramMeasurementSupported && !vramUsedBytes.isEmpty()) {
            p.setProperty("vramTotalBytes", String.valueOf(vramTotalBytes));
            p.setProperty("vramUsedMedianBytes", String.valueOf(percentile(vramUsedBytes, 50)));
            p.setProperty("vramUsedP95Bytes", String.valueOf(percentile(vramUsedBytes, 95)));
            p.setProperty("vramUsedMaxBytes", String.valueOf(maximum(vramUsedBytes)));
        }
        p.setProperty("clientTelemetryQualified", "true");
        writeResult(p);
        SkyforgeWbyWave1VisibilityLifecycleAcceptance.markClientComplete();
        minecraft.stop();
    }

    private static void fail(String reason) {
        if (complete) {
            return;
        }
        complete = true;
        Properties p = new Properties();
        p.setProperty("status", "FAIL");
        p.setProperty("failure", reason);
        p.setProperty("frameTimeSamples", String.valueOf(frameNanos.size()));
        writeResult(p);
        SkyforgeWbyWave1VisibilityLifecycleAcceptance.markClientComplete();
        Minecraft.getInstance().stop();
    }

    private static void writeResult(Properties properties) {
        String configured = System.getProperty(RESULT_FILE_PROPERTY);
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException(
                    "WBY telemetry client requires system property " + RESULT_FILE_PROPERTY);
        }
        Path path = Path.of(configured).toAbsolutePath().normalize();
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream output = Files.newOutputStream(
                    path,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE)) {
                properties.store(output, "Skyforge WBY Wave 1 client telemetry");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("failed to write WBY telemetry client result " + path, exception);
        }
    }

    private static long percentile(List<Long> values, int p) {
        ArrayList<Long> sorted = new ArrayList<>(values);
        sorted.sort(Long::compareTo);
        return SkyforgeRuntimePerformanceMetrics.nearestRankPercentile(sorted, p);
    }

    private static long maximum(List<Long> values) {
        long max = 0L;
        for (long value : values) {
            max = Math.max(max, value);
        }
        return max;
    }

    private static double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0;
    }

    private static String safeGlString(int token) {
        String value = GL11.glGetString(token);
        return value == null ? "<unavailable>" : value;
    }

    private static Method publicMethod(Object target, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        return target.getClass().getMethod(name, parameterTypes);
    }

    private record DistanceEvidence(int chunks, String source) {}
}
