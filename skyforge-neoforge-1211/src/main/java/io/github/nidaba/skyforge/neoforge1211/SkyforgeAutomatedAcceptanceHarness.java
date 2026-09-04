package io.github.nidaba.skyforge.neoforge1211;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Development-only acceptance harness for self-running Skyforge Minecraft proofs.
 *
 * <p>The harness intentionally lives outside production behavior. A ModDev run opts in through
 * system properties, then this class:
 *
 * <ul>
 *   <li>synchronously warms only the finite origin proof footprint so no human player/teleport is
 *       required to make the relevant chunks available;</li>
 *   <li>collects machine-readable PASS evidence from the existing self-checking runtime fixtures;</li>
 *   <li>stops dedicated-server cases automatically after PASS;</li>
 *   <li>supports one quick-play client verification case without requiring UI navigation; and</li>
 *   <li>fails boundedly instead of hanging forever when a proof never reaches its marker.</li>
 * </ul>
 *
 * <p>This is test orchestration, not a production chunk-loading policy. Ordinary Skyforge runtime
 * paths still do not force unavailable chunks.
 */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID)
final class SkyforgeAutomatedAcceptanceHarness {
    static final String ENABLE_PROPERTY = "skyforge.dev.acceptanceHarness";
    static final String CASE_PROPERTY = "skyforge.dev.acceptanceCase";
    static final String MODE_PROPERTY = "skyforge.dev.acceptanceMode";
    static final String RESULT_FILE_PROPERTY = "skyforge.dev.acceptanceResultFile";
    static final String RADIUS_PROPERTY = "skyforge.dev.acceptanceRadius";
    static final String TIMEOUT_SECONDS_PROPERTY = "skyforge.dev.acceptanceTimeoutSeconds";

    private static final String MODE_SERVER = "server";
    private static final String MODE_CLIENT = "client";
    private static final int DEFAULT_RADIUS = 2;
    private static final long DEFAULT_TIMEOUT_SECONDS = 180L;
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeAutomatedAcceptanceHarness.class.getName());

    private static final Map<String, String> EVIDENCE = new LinkedHashMap<>();
    private static boolean warmupComplete;
    private static boolean completionRequested;
    private static long firstServerTickNanos = Long.MIN_VALUE;

    private SkyforgeAutomatedAcceptanceHarness() {}

    static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    static boolean serverMode() {
        return enabled() && MODE_SERVER.equals(System.getProperty(MODE_PROPERTY, MODE_SERVER));
    }

    static boolean clientMode() {
        return enabled() && MODE_CLIENT.equals(System.getProperty(MODE_PROPERTY, MODE_SERVER));
    }

    static String caseId() {
        return System.getProperty(CASE_PROPERTY, "unspecified");
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (!enabled() || completionRequested) {
            return;
        }
        if (firstServerTickNanos == Long.MIN_VALUE) {
            firstServerTickNanos = System.nanoTime();
        }

        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (!level.dimension().equals(Level.OVERWORLD)) {
                continue;
            }
            if (!warmupComplete) {
                warmOriginFootprint(level);
                warmupComplete = true;
                LOGGER.log(
                        System.Logger.Level.INFO,
                        "SKYFORGE AUTOMATED ACCEPTANCE WARMUP: case=" + caseId()
                                + ", radiusChunks=" + radius()
                                + ". Development harness synchronously loaded the finite proof footprint.");
            }
        }

        long elapsedSeconds = Math.max(
                0L,
                (System.nanoTime() - firstServerTickNanos) / 1_000_000_000L);
        long timeout = timeoutSeconds();
        if (elapsedSeconds > timeout) {
            fail(event.getServer(), "acceptance case exceeded " + timeout + " seconds without PASS");
        }
    }

    static synchronized void record(Map<String, ?> values) {
        if (!enabled()) {
            return;
        }
        Objects.requireNonNull(values, "values");
        for (var entry : values.entrySet()) {
            EVIDENCE.put(
                    Objects.requireNonNull(entry.getKey(), "evidence key"),
                    String.valueOf(Objects.requireNonNull(entry.getValue(), "evidence value")));
        }
    }

    static synchronized void completeServerCase(
            MinecraftServer server,
            Map<String, ?> values) {
        Objects.requireNonNull(server, "server");
        if (!serverMode()) {
            return;
        }
        record(values);
        complete("PASS");
        completionRequested = true;
        LOGGER.log(
                System.Logger.Level.INFO,
                "SKYFORGE AUTOMATED ACCEPTANCE PASS: case=" + caseId()
                        + ", evidence=" + EVIDENCE);
        server.halt(false);
    }

    static synchronized void completeClientCase(Map<String, ?> values) {
        if (!clientMode()) {
            return;
        }
        record(values);
        complete("PASS");
        completionRequested = true;
        LOGGER.log(
                System.Logger.Level.INFO,
                "SKYFORGE AUTOMATED ACCEPTANCE CLIENT PASS: case=" + caseId()
                        + ", evidence=" + EVIDENCE);
    }

    static synchronized void fail(MinecraftServer server, String reason) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(reason, "reason");
        EVIDENCE.put("failure", reason);
        complete("FAIL");
        completionRequested = true;
        server.halt(false);
        throw new IllegalStateException(
                "SKYFORGE AUTOMATED ACCEPTANCE FAIL: case=" + caseId() + ", reason=" + reason);
    }

    static synchronized void failClientCase(String reason) {
        Objects.requireNonNull(reason, "reason");
        EVIDENCE.put("failure", reason);
        complete("FAIL");
        completionRequested = true;
        throw new IllegalStateException(
                "SKYFORGE AUTOMATED ACCEPTANCE CLIENT FAIL: case=" + caseId() + ", reason=" + reason);
    }

    private static void warmOriginFootprint(ServerLevel level) {
        int radius = radius();
        for (int chunkX = -radius; chunkX <= radius; chunkX++) {
            for (int chunkZ = -radius; chunkZ <= radius; chunkZ++) {
                level.getChunk(chunkX, chunkZ);
            }
        }
    }

    private static int radius() {
        int value = Integer.getInteger(RADIUS_PROPERTY, DEFAULT_RADIUS);
        if (value < 0 || value > 8) {
            throw new IllegalArgumentException("acceptance radius must be in [0,8], found " + value);
        }
        return value;
    }

    private static long timeoutSeconds() {
        long value = Long.getLong(TIMEOUT_SECONDS_PROPERTY, DEFAULT_TIMEOUT_SECONDS);
        if (value <= 0L || value > 900L) {
            throw new IllegalArgumentException("acceptance timeout must be in (0,900], found " + value);
        }
        return value;
    }

    private static void complete(String status) {
        EVIDENCE.put("case", caseId());
        EVIDENCE.put("status", status);
        Path path = resultPath();
        try {
            Files.createDirectories(path.getParent());
            Properties properties = new Properties();
            properties.putAll(EVIDENCE);
            try (OutputStream output = Files.newOutputStream(
                    path,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE)) {
                properties.store(output, "Skyforge automated acceptance result");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("failed to write automated acceptance result " + path, exception);
        }
    }

    private static Path resultPath() {
        String configured = System.getProperty(RESULT_FILE_PROPERTY);
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException(
                    "automated acceptance requires system property " + RESULT_FILE_PROPERTY);
        }
        Path path = Path.of(configured).toAbsolutePath().normalize();
        Path parent = path.getParent();
        if (parent == null) {
            throw new IllegalStateException("acceptance result path has no parent: " + path);
        }
        return path;
    }
}
