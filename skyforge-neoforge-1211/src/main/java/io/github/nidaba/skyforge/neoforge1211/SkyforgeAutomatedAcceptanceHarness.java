package io.github.nidaba.skyforge.neoforge1211;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.Objects;
import java.util.Properties;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
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
    static final String NONBLOCKING_EXPLICIT_WARMUP_PROPERTY =
            "skyforge.dev.acceptanceNonblockingExplicitWarmup";
    static final String TICKET_RADIUS_PROPERTY = "skyforge.dev.acceptanceTicketRadius";

    private static final String MODE_SERVER = "server";
    private static final String MODE_CLIENT = "client";
    private static final int DEFAULT_RADIUS = 2;
    private static final int DEFAULT_ACCEPTANCE_TICKET_RADIUS = 3;
    private static final TicketType<ChunkPos> ACCEPTANCE_TICKET = TicketType.create(
            "skyforge_acceptance",
            Comparator.comparingLong(ChunkPos::toLong));
    private static final long DEFAULT_TIMEOUT_SECONDS = 180L;
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeAutomatedAcceptanceHarness.class.getName());

    private static final Map<String, String> EVIDENCE = new LinkedHashMap<>();
    private static Set<Long> explicitWarmupChunkKeys = Set.of();
    private static boolean warmupTicketsInstalled;
    private static boolean warmupComplete;
    private static long warmupStartNanos = Long.MIN_VALUE;
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
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (!level.dimension().equals(Level.OVERWORLD)) {
                continue;
            }
            if (!warmupComplete) {
                if (warmupStartNanos == Long.MIN_VALUE) {
                    warmupStartNanos = SkyforgeRuntimePerformanceMetrics.start();
                }
                if (nonblockingExplicitWarmup()) {
                    warmupComplete = pollExplicitWarmup(level);
                } else {
                    warmOriginFootprint(level);
                    warmupComplete = true;
                }
                if (warmupComplete) {
                    SkyforgeRuntimePerformanceMetrics.recordSince(
                            "acceptance.warmOriginFootprint",
                            warmupStartNanos);
                    LOGGER.log(
                            System.Logger.Level.INFO,
                            "SKYFORGE AUTOMATED ACCEPTANCE WARMUP: case=" + caseId()
                                    + ", " + warmupDescription()
                                    + (nonblockingExplicitWarmup()
                                            ? ". Development harness ticketed the finite proof footprint and let the chunk scheduler complete it without serial getChunk forcing."
                                            : ". Development harness synchronously loaded and ticketed the finite proof footprint."));
                }
            }
        }

        // Nonblocking explicit warmup is part of the bounded acceptance case: if scheduling or
        // generation stalls, fail instead of hiding the wall time before the harness deadline.
        // Historical synchronous fixtures retain their accepted setup-time semantics.
        if (firstServerTickNanos == Long.MIN_VALUE
                && (warmupComplete || nonblockingExplicitWarmup())) {
            firstServerTickNanos = System.nanoTime();
        }

        long elapsedSeconds = firstServerTickNanos == Long.MIN_VALUE
                ? 0L
                : Math.max(
                        0L,
                        (System.nanoTime() - firstServerTickNanos) / 1_000_000_000L);
        long timeout = timeoutSeconds();
        if (elapsedSeconds > timeout) {
            fail(event.getServer(), "acceptance case exceeded " + timeout + " seconds without PASS");
        }
    }

    /**
     * Installs an explicit finite acceptance-only chunk footprint before warmup.
     *
     * <p>This exists for large non-square development proofs such as the production morphology
     * atlas. It does not alter ordinary Skyforge loading behavior; only the opt-in automated
     * acceptance harness consumes these keys.
     */
    static synchronized void installWarmupChunkKeys(Set<Long> chunkKeys) {
        if (!enabled()) {
            return;
        }
        Objects.requireNonNull(chunkKeys, "chunkKeys");
        if (warmupComplete) {
            throw new IllegalStateException("acceptance warmup footprint cannot change after warmup");
        }
        if (chunkKeys.isEmpty()) {
            throw new IllegalArgumentException("acceptance warmup footprint must not be empty");
        }
        TreeSet<Long> sorted = new TreeSet<>((left, right) -> {
            int leftX = ChunkPos.getX(left);
            int rightX = ChunkPos.getX(right);
            int x = Integer.compare(leftX, rightX);
            return x != 0 ? x : Integer.compare(ChunkPos.getZ(left), ChunkPos.getZ(right));
        });
        sorted.addAll(chunkKeys);
        explicitWarmupChunkKeys = Collections.unmodifiableSet(new LinkedHashSet<>(sorted));
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
        record(SkyforgeRuntimePerformanceMetrics.evidence());
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
        record(SkyforgeRuntimePerformanceMetrics.evidence());
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
        var chunkSource = level.getChunkSource();
        Set<Long> explicit = explicitWarmupChunkKeys;
        if (!explicit.isEmpty()) {
            for (long key : explicit) {
                warmChunk(level, chunkSource, new ChunkPos(ChunkPos.getX(key), ChunkPos.getZ(key)));
            }
        } else {
            int radius = radius();
            for (int chunkX = -radius; chunkX <= radius; chunkX++) {
                for (int chunkZ = -radius; chunkZ <= radius; chunkZ++) {
                    warmChunk(level, chunkSource, new ChunkPos(chunkX, chunkZ));
                }
            }
        }
    }

    /**
     * Installs explicit region tickets once, then observes scheduler-completed chunks without
     * synchronously forcing each one on the server thread.
     */
    private static boolean pollExplicitWarmup(ServerLevel level) {
        Set<Long> explicit = explicitWarmupChunkKeys;
        if (explicit.isEmpty()) {
            throw new IllegalStateException(
                    "nonblocking acceptance warmup requires an explicit finite chunk footprint");
        }

        var chunkSource = level.getChunkSource();
        if (!warmupTicketsInstalled) {
            for (long key : explicit) {
                ChunkPos pos = new ChunkPos(ChunkPos.getX(key), ChunkPos.getZ(key));
                chunkSource.addRegionTicket(
                        ACCEPTANCE_TICKET,
                        pos,
                        ticketRadius(),
                        pos);
            }
            warmupTicketsInstalled = true;
            SkyforgeRuntimePerformanceMetrics.recordSample(
                    "acceptance.explicitWarmupTickets",
                    explicit.size());
        }

        int available = 0;
        for (long key : explicit) {
            if (chunkSource.getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key)) != null) {
                available++;
            }
        }
        SkyforgeRuntimePerformanceMetrics.recordSample(
                "acceptance.explicitWarmupAvailableChunks",
                available);
        return available == explicit.size();
    }

    private static boolean nonblockingExplicitWarmup() {
        return Boolean.getBoolean(NONBLOCKING_EXPLICIT_WARMUP_PROPERTY)
                && !explicitWarmupChunkKeys.isEmpty();
    }

    private static void warmChunk(
            ServerLevel level,
            net.minecraft.server.level.ServerChunkCache chunkSource,
            ChunkPos pos) {
        // The harness itself is the independent load reason for this finite proof corpus.
        // A non-persistent development-only region ticket keeps each target stable between
        // resumable production quanta without repeatedly synchronously loading hundreds of
        // chunks on every server tick. Production code remains strictly getChunkNow-only.
        chunkSource.addRegionTicket(
                ACCEPTANCE_TICKET,
                pos,
                ticketRadius(),
                pos);
        level.getChunk(pos.x, pos.z);
    }

    private static String warmupDescription() {
        Set<Long> explicit = explicitWarmupChunkKeys;
        return explicit.isEmpty()
                ? "radiusChunks=" + radius()
                : "explicitChunks=" + explicit.size() + ", ticketRadius=" + ticketRadius();
    }

    private static int ticketRadius() {
        int value = Integer.getInteger(TICKET_RADIUS_PROPERTY, DEFAULT_ACCEPTANCE_TICKET_RADIUS);
        if (value < 0 || value > 3) {
            throw new IllegalArgumentException(
                    "acceptance ticket radius must be in [0,3], found " + value);
        }
        return value;
    }

    private static int radius() {
        int value = Integer.getInteger(RADIUS_PROPERTY, DEFAULT_RADIUS);
        if (value < 0 || value > 12) {
            throw new IllegalArgumentException("acceptance radius must be in [0,12], found " + value);
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
