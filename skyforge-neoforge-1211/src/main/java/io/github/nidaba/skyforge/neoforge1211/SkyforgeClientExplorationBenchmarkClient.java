package io.github.nidaba.skyforge.neoforge1211;

import java.util.LinkedHashMap;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Actual-client half of the PERF-0502 exploration benchmark. */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID, value = Dist.CLIENT)
final class SkyforgeClientExplorationBenchmarkClient {
    private static final long CLIENT_TIMEOUT_NANOS = 120_000_000_000L;
    private static final int COMPLETION_SETTLE_TICKS = 20;

    private static long firstClientTickNanos = Long.MIN_VALUE;
    private static long firstFrameNanos = Long.MIN_VALUE;
    private static long previousFrameNanos = Long.MIN_VALUE;
    private static long frameCount;
    private static int completionTicks;
    private static boolean proofComplete;

    private SkyforgeClientExplorationBenchmarkClient() {}

    @SubscribeEvent
    static void onRenderFrame(RenderFrameEvent.Post event) {
        if (proofComplete || !SkyforgeClientExplorationBenchmark.enabled()) {
            return;
        }
        long now = System.nanoTime();
        if (firstFrameNanos == Long.MIN_VALUE) {
            firstFrameNanos = now;
        }
        if (previousFrameNanos != Long.MIN_VALUE) {
            long interval = Math.max(0L, now - previousFrameNanos);
            SkyforgeRuntimePerformanceMetrics.recordDistributionSample(
                    "clientExploration.clientFrameNanos", interval);
            String phase = SkyforgeClientExplorationBenchmark.currentPhaseKey();
            if (!"waiting".equals(phase) && !"complete".equals(phase)) {
                SkyforgeRuntimePerformanceMetrics.recordDistributionSample(
                        "clientExploration.clientFrameNanos." + phase, interval);
            }
        }
        previousFrameNanos = now;
        frameCount++;
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (proofComplete
                || !SkyforgeClientExplorationBenchmark.enabled()
                || !SkyforgeAutomatedAcceptanceHarness.clientMode()) {
            return;
        }
        long now = System.nanoTime();
        if (firstClientTickNanos == Long.MIN_VALUE) {
            firstClientTickNanos = now;
        }
        if (now - firstClientTickNanos > CLIENT_TIMEOUT_NANOS) {
            releaseMovementKeys(Minecraft.getInstance());
            SkyforgeAutomatedAcceptanceHarness.failClientCase(
                    "PERF-0502 actual-client exploration benchmark did not complete within 120 seconds");
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }

        drivePlayerInput(minecraft);

        if (!SkyforgeClientExplorationBenchmark.serverProofComplete()) {
            return;
        }
        if (++completionTicks < COMPLETION_SETTLE_TICKS) {
            return;
        }

        proofComplete = true;
        releaseMovementKeys(minecraft);
        long measuredWallNanos = firstFrameNanos == Long.MIN_VALUE
                ? 0L
                : Math.max(0L, now - firstFrameNanos);
        double averageFps = measuredWallNanos <= 0L
                ? 0.0d
                : frameCount * 1_000_000_000.0d / measuredWallNanos;
        Runtime runtime = Runtime.getRuntime();
        String softwareRendererEnv = System.getenv().getOrDefault("LIBGL_ALWAYS_SOFTWARE", "false");
        boolean softwareRendererExpected = "1".equals(softwareRendererEnv)
                || Boolean.parseBoolean(softwareRendererEnv);

        LinkedHashMap<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("clientExplorationClientPass", true);
        evidence.put("clientFrameCount", frameCount);
        evidence.put("clientMeasuredWallNanos", measuredWallNanos);
        evidence.put("clientAverageFps", averageFps);
        evidence.put("clientMemoryUsedBytes", runtime.totalMemory() - runtime.freeMemory());
        evidence.put("clientMemoryCommittedBytes", runtime.totalMemory());
        evidence.put("clientMemoryMaxBytes", runtime.maxMemory());
        evidence.put("clientSoftwareRendererEnv", softwareRendererEnv);
        evidence.put("clientSoftwareRendererExpected", softwareRendererExpected);
        evidence.putAll(SkyforgeRuntimePerformanceMetrics.evidence());

        SkyforgeAutomatedAcceptanceHarness.completeClientCase(evidence);
        minecraft.stop();
    }

    private static void drivePlayerInput(Minecraft minecraft) {
        boolean active = SkyforgeClientExplorationBenchmark.traversalInputActive();
        minecraft.options.keyUp.setDown(active);
        if (active && minecraft.player != null) {
            minecraft.player.setYRot(-90.0f);
            minecraft.player.setXRot(0.0f);
        }
    }

    private static void releaseMovementKeys(Minecraft minecraft) {
        minecraft.options.keyUp.setDown(false);
    }
}
