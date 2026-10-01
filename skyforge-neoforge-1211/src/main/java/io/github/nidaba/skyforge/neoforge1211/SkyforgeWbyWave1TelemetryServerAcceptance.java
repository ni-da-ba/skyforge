package io.github.nidaba.skyforge.neoforge1211;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Dedicated-server telemetry sampler for the WBY-INT-0002 W1-A baseline.
 *
 * <p>The existing visibility fixture owns the scene. This sampler waits until that fixture is
 * live, discards a warmup window, then records bounded server MSPT and chunk-count evidence without
 * stopping the server; the client benchmark owns terminal process shutdown.
 */
final class SkyforgeWbyWave1TelemetryServerAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.wbyWave1Telemetry";
    static final String RESULT_FILE_PROPERTY = "skyforge.dev.wbyWave1TelemetryServerResultFile";

    private static final int WARMUP_TICKS = 100;
    private static final int SAMPLE_TICKS = 200;

    private static long fixtureReadyTick = Long.MIN_VALUE;
    private static boolean written;
    private static final List<Double> MSPT_SAMPLES = new ArrayList<>();
    private static final List<Integer> LOADED_CHUNK_SAMPLES = new ArrayList<>();
    private static final List<Integer> FORCED_CHUNK_SAMPLES = new ArrayList<>();

    private SkyforgeWbyWave1TelemetryServerAcceptance() {}

    static void installFromSystemProperty() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(SkyforgeWbyWave1TelemetryServerAcceptance::onServerTickPost);
    }

    private static void onServerTickPost(ServerTickEvent.Post event) {
        if (written
                || !Boolean.getBoolean(ENABLE_PROPERTY)
                || !SkyforgeWbyWave1VisibilityLifecycleAcceptance.fixtureReady()) {
            return;
        }

        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        long now = level.getGameTime();
        if (fixtureReadyTick == Long.MIN_VALUE) {
            fixtureReadyTick = now;
            return;
        }
        if (now - fixtureReadyTick < WARMUP_TICKS) {
            return;
        }

        MSPT_SAMPLES.add(server.getAverageTickTimeNanos() / 1_000_000.0);
        LOADED_CHUNK_SAMPLES.add(level.getChunkSource().getLoadedChunksCount());
        FORCED_CHUNK_SAMPLES.add(level.getForcedChunks().size());

        if (MSPT_SAMPLES.size() >= SAMPLE_TICKS) {
            writeEvidence(server, level);
            written = true;
        }
    }

    private static void writeEvidence(MinecraftServer server, ServerLevel level) {
        SkyforgeWbyWave1VisibilityLifecycleAcceptance.Snapshot snapshot =
                SkyforgeWbyWave1VisibilityLifecycleAcceptance.snapshot();
        if (snapshot == null) {
            throw new IllegalStateException("WBY telemetry lost visibility fixture before server evidence write");
        }

        Properties p = new Properties();
        p.setProperty("status", "PASS");
        p.setProperty("dedicatedServer", String.valueOf(!server.isSingleplayer()));
        p.setProperty("bodyId", snapshot.bodyId().toString());
        p.setProperty("serverMsptSamples", String.valueOf(MSPT_SAMPLES.size()));
        p.setProperty("serverMsptMedian", String.valueOf(percentile(MSPT_SAMPLES, 0.50)));
        p.setProperty("serverMsptP95", String.valueOf(percentile(MSPT_SAMPLES, 0.95)));
        p.setProperty("serverMsptMax", String.valueOf(Collections.max(MSPT_SAMPLES)));
        p.setProperty("loadedChunksMedian", String.valueOf(percentileInts(LOADED_CHUNK_SAMPLES, 0.50)));
        p.setProperty("loadedChunksP95", String.valueOf(percentileInts(LOADED_CHUNK_SAMPLES, 0.95)));
        p.setProperty("loadedChunksMax", String.valueOf(Collections.max(LOADED_CHUNK_SAMPLES)));
        p.setProperty("forcedChunksMedian", String.valueOf(percentileInts(FORCED_CHUNK_SAMPLES, 0.50)));
        p.setProperty("forcedChunksMax", String.valueOf(Collections.max(FORCED_CHUNK_SAMPLES)));
        p.setProperty("connectedPlayers", String.valueOf(server.getPlayerList().getPlayerCount()));
        p.setProperty("fixtureHorizontalOffsetBlocks",
                String.valueOf(SkyforgeWbyWave1VisibilityLifecycleAcceptance.HORIZONTAL_OFFSET_BLOCKS));

        writeProperties(resultPath(), p, "Skyforge WBY Wave 1 server telemetry");
    }

    private static double percentile(List<Double> values, double quantile) {
        List<Double> sorted = new ArrayList<>(values);
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

    private static Path resultPath() {
        String configured = System.getProperty(RESULT_FILE_PROPERTY);
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("WBY telemetry requires system property " + RESULT_FILE_PROPERTY);
        }
        Path path = Path.of(configured).toAbsolutePath().normalize();
        if (path.getParent() == null) {
            throw new IllegalStateException("WBY telemetry server result path has no parent: " + path);
        }
        return path;
    }

    private static void writeProperties(Path path, Properties properties, String comment) {
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream output = Files.newOutputStream(
                    path,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE)) {
                properties.store(output, comment);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("failed to write WBY telemetry server evidence " + path, exception);
        }
    }
}
