package io.github.nidaba.skyforge.neoforge1211;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Bounded real-provider atmosphere evidence fixture for Bootstrap issue #495.
 *
 * <p>The probe deliberately samples only through {@link SkyforgeAtmosphereView}. It does not read
 * A4MC grids, solver buffers, diagnostics files, or client-local atmosphere state. A real external
 * client supplies the ServerPlayer anchor required by pinned A4MC 0.2.1; all measurements are made
 * by a dedicated server against ServerLevel on the authoritative server thread.
 */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID)
final class SkyforgeAtmosphereProbeVolumeAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.atmosphereProbeVolume";
    static final String OUTPUT_PROPERTY = "skyforge.dev.atmosphereProbeOutput";

    private static final int[] XZ_OFFSETS = {-128, -64, 0, 64, 128};
    private static final int[] Y_LEVELS = {80, 120, 160, 220};
    private static final long MIN_SETTLE_TICKS = 80L;
    private static final long MAX_WAIT_TICKS = 1200L;
    private static final int EXPECTED_SAMPLE_COUNT =
            XZ_OFFSETS.length * XZ_OFFSETS.length * Y_LEVELS.length;

    private static long firstPlayerTick = Long.MIN_VALUE;
    private static SkyforgeAtmosphereView atmosphere;
    private static volatile boolean proofComplete;

    private SkyforgeAtmosphereProbeVolumeAcceptance() {}

    @SubscribeEvent
    static void onServerTickPost(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)
                || !SkyforgeAutomatedAcceptanceHarness.serverMode()
                || proofComplete) {
            return;
        }

        ServerLevel level = event.getServer().overworld();
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) {
            return;
        }

        long gameTick = level.getGameTime();
        if (firstPlayerTick == Long.MIN_VALUE) {
            firstPlayerTick = gameTick;
        }
        long age = gameTick - firstPlayerTick;
        if (age < MIN_SETTLE_TICKS) {
            return;
        }

        if (atmosphere == null) {
            try {
                atmosphere = SkyforgeA4mcAtmosphereBridge.create();
            } catch (ReflectiveOperationException failure) {
                fail(event.getServer(), "pinned A4MC gameplay API binding failed: " + failure);
                return;
            }
        }

        ServerPlayer anchor = players.get(0);
        double centerX = Math.floor(anchor.getX() / 64.0) * 64.0 + 32.0;
        double centerZ = Math.floor(anchor.getZ() / 64.0) * 64.0 + 32.0;
        SkyforgeAtmosphereView.Sample readiness =
                atmosphere.sample(level, new Vec3(centerX, 120.0, centerZ));
        if (!readiness.trustedForGameplay() || "NONE".equals(readiness.sourceLevel())) {
            if (age >= MAX_WAIT_TICKS) {
                emitProviderDiagnostics(event.getServer());
                fail(event.getServer(), "real A4MC server atmosphere never became gameplay-trusted within "
                        + MAX_WAIT_TICKS
                        + " ticks; last="
                        + readiness);
            }
            return;
        }

        try {
            acquire(level, anchor, centerX, centerZ, gameTick);
        } catch (RuntimeException | IOException failure) {
            fail(event.getServer(), "atmosphere probe acquisition failed: " + failure);
        }
    }

    private static void acquire(
            ServerLevel level,
            ServerPlayer anchor,
            double centerX,
            double centerZ,
            long gameTick)
            throws IOException {
        Pass first = capturePass(level, centerX, centerZ);
        Pass replay = capturePass(level, centerX, centerZ);

        if (first.probes().size() != EXPECTED_SAMPLE_COUNT
                || replay.probes().size() != EXPECTED_SAMPLE_COUNT) {
            throw new IllegalStateException(
                    "unexpected probe count first="
                            + first.probes().size()
                            + " replay="
                            + replay.probes().size());
        }

        int trustedCount = 0;
        TreeSet<String> sourceLevels = new TreeSet<>();
        TreeSet<String> authorities = new TreeSet<>();
        for (int index = 0; index < EXPECTED_SAMPLE_COUNT; index++) {
            ProbeRecord left = first.probes().get(index);
            ProbeRecord right = replay.probes().get(index);
            if (!left.sameQuery(right) || !left.sample().equals(right.sample())) {
                throw new IllegalStateException(
                        "same-tick atmosphere replay diverged at index "
                                + index
                                + " first="
                                + left
                                + " replay="
                                + right);
            }
            SkyforgeAtmosphereView.Sample sample = left.sample();
            if (!sample.trustedForGameplay()) {
                throw new IllegalStateException(
                        "canonical probe returned untrusted gameplay atmosphere at " + left.position());
            }
            if ("NONE".equals(sample.sourceLevel()) || "NONE".equals(sample.authority())) {
                throw new IllegalStateException(
                        "trusted canonical probe lost source provenance at " + left.position());
            }
            trustedCount++;
            sourceLevels.add(sample.sourceLevel());
            authorities.add(sample.authority());
        }

        String digest = orderedDigest(first.probes());
        long aggregateNanos = first.aggregateNanos() + replay.aggregateNanos();
        long maxNanos = Math.max(first.maxNanos(), replay.maxNanos());
        int totalQueries = EXPECTED_SAMPLE_COUNT * 2;

        Path output = outputPath();
        Files.createDirectories(output.getParent());
        Files.writeString(
                output,
                encodeArtifact(
                        level,
                        anchor,
                        centerX,
                        centerZ,
                        gameTick,
                        first.probes(),
                        digest,
                        trustedCount,
                        sourceLevels,
                        authorities,
                        totalQueries,
                        aggregateNanos,
                        maxNanos),
                StandardCharsets.UTF_8);

        proofComplete = true;
        LinkedHashMap<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("actualClientPlayerAnchor", true);
        evidence.put("serverWorldSampling", true);
        evidence.put("realA4mcGameplayProvider", true);
        evidence.put("sampleCount", EXPECTED_SAMPLE_COUNT);
        evidence.put("trustedSampleCount", trustedCount);
        evidence.put("sameTickReplayExact", true);
        evidence.put("queryCount", totalQueries);
        evidence.put("orderedSampleDigest", digest);
        evidence.put("skyforgeAtmospherePersistence", false);
        evidence.put("artifactPath", output);
        SkyforgeAutomatedAcceptanceHarness.completeServerCase(level.getServer(), evidence);
        System.getLogger(SkyforgeAtmosphereProbeVolumeAcceptance.class.getName())
                .log(
                        System.Logger.Level.INFO,
                        "SKYFORGE_ATMOSPHERE_PROBE_VOLUME PASS samples="
                                + EXPECTED_SAMPLE_COUNT
                                + " queries="
                                + totalQueries
                                + " digest="
                                + digest
                                + " sources="
                                + sourceLevels
                                + " authorities="
                                + authorities);
    }

    private static Pass capturePass(ServerLevel level, double centerX, double centerZ) {
        ArrayList<ProbeRecord> probes = new ArrayList<>(EXPECTED_SAMPLE_COUNT);
        long aggregateNanos = 0L;
        long maxNanos = 0L;
        for (int y : Y_LEVELS) {
            for (int zOffset : XZ_OFFSETS) {
                for (int xOffset : XZ_OFFSETS) {
                    Vec3 position = new Vec3(centerX + xOffset, y, centerZ + zOffset);
                    long started = System.nanoTime();
                    SkyforgeAtmosphereView.Sample sample = atmosphere.sample(level, position);
                    long elapsed = Math.max(0L, System.nanoTime() - started);
                    aggregateNanos += elapsed;
                    maxNanos = Math.max(maxNanos, elapsed);
                    probes.add(new ProbeRecord(position, sample, elapsed));
                }
            }
        }
        return new Pass(List.copyOf(probes), aggregateNanos, maxNanos);
    }

    private static String orderedDigest(List<ProbeRecord> probes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (ProbeRecord probe : probes) {
                SkyforgeAtmosphereView.Sample sample = probe.sample();
                String canonical = hex(probe.position().x)
                        + ","
                        + hex(probe.position().y)
                        + ","
                        + hex(probe.position().z)
                        + "|"
                        + sample.trustedForGameplay()
                        + "|"
                        + hex(sample.meanX())
                        + ","
                        + hex(sample.meanY())
                        + ","
                        + hex(sample.meanZ())
                        + "|"
                        + hex(sample.gustX())
                        + ","
                        + hex(sample.gustY())
                        + ","
                        + hex(sample.gustZ())
                        + "|"
                        + hex(sample.pressure())
                        + "|"
                        + hex(sample.turbulenceIntensity())
                        + "|"
                        + hex(sample.updraftMetersPerSecond())
                        + "|"
                        + hex(sample.windShearMagnitudePerBlock())
                        + "|"
                        + hex(sample.confidence())
                        + "|"
                        + sample.sourceLevel()
                        + "|"
                        + sample.authority()
                        + "|"
                        + sample.l1Epoch()
                        + ","
                        + sample.worldDeltaEpoch()
                        + ","
                        + sample.l2Epoch()
                        + "\n";
                digest.update(canonical.getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static String encodeArtifact(
            ServerLevel level,
            ServerPlayer anchor,
            double centerX,
            double centerZ,
            long gameTick,
            List<ProbeRecord> probes,
            String digest,
            int trustedCount,
            TreeSet<String> sourceLevels,
            TreeSet<String> authorities,
            int totalQueries,
            long aggregateNanos,
            long maxNanos) {
        StringBuilder json = new StringBuilder(64_000);
        json.append("{\n");
        json.append("  \"schema_version\": 1,\n");
        json.append("  \"artifact_kind\": \"SKYFORGE_ATMOSPHERE_PROBE_VOLUME\",\n");
        json.append("  \"provider_identity\": {\n");
        json.append("    \"mod_id\": \"aerodynamics4mc\",\n");
        json.append("    \"version\": ").append(quote(providerVersion())).append(",\n");
        json.append("    \"api\": \"AeroMinecraftWindApi.sampleGameplay(ServerLevel, Vec3)\"\n");
        json.append("  },\n");
        json.append("  \"skyforge_source_sha\": ").append(quote(sourceSha())).append(",\n");
        json.append("  \"specimen\": {\n");
        json.append("    \"world_seed\": ").append(level.getSeed()).append(",\n");
        json.append("    \"dimension\": ").append(quote(level.dimension().toString())).append(",\n");
        json.append("    \"acquisition_game_tick\": ").append(gameTick).append(",\n");
        json.append("    \"anchor_player\": ").append(quote(anchor.getGameProfile().getName())).append(",\n");
        json.append("    \"anchor_position\": [")
                .append(number(anchor.getX())).append(", ")
                .append(number(anchor.getY())).append(", ")
                .append(number(anchor.getZ())).append("],\n");
        json.append("    \"lattice\": {\n");
        json.append("      \"center_x\": ").append(number(centerX)).append(",\n");
        json.append("      \"center_z\": ").append(number(centerZ)).append(",\n");
        json.append("      \"x_offsets\": [-128, -64, 0, 64, 128],\n");
        json.append("      \"z_offsets\": [-128, -64, 0, 64, 128],\n");
        json.append("      \"y_levels\": [80, 120, 160, 220],\n");
        json.append("      \"sample_count\": ").append(EXPECTED_SAMPLE_COUNT).append("\n");
        json.append("    }\n");
        json.append("  },\n");
        json.append("  \"samples\": [\n");
        for (int index = 0; index < probes.size(); index++) {
            appendProbe(json, probes.get(index), index + 1 < probes.size());
        }
        json.append("  ],\n");
        json.append("  \"ordered_sample_digest\": ").append(quote(digest)).append(",\n");
        json.append("  \"same_tick_replay\": {\n");
        json.append("    \"sample_count\": ").append(EXPECTED_SAMPLE_COUNT).append(",\n");
        json.append("    \"exact_equal\": true\n");
        json.append("  },\n");
        json.append("  \"authority_summary\": {\n");
        json.append("    \"sample_count\": ").append(EXPECTED_SAMPLE_COUNT).append(",\n");
        json.append("    \"trusted_sample_count\": ").append(trustedCount).append(",\n");
        json.append("    \"source_levels\": ").append(stringArray(sourceLevels)).append(",\n");
        json.append("    \"authorities\": ").append(stringArray(authorities)).append("\n");
        json.append("  },\n");
        json.append("  \"cost\": {\n");
        json.append("    \"query_count\": ").append(totalQueries).append(",\n");
        json.append("    \"aggregate_nanos\": ").append(aggregateNanos).append(",\n");
        json.append("    \"mean_nanos\": ").append(number(aggregateNanos / (double) totalQueries)).append(",\n");
        json.append("    \"max_nanos\": ").append(maxNanos).append("\n");
        json.append("  },\n");
        json.append("  \"ownership\": {\n");
        json.append("    \"server_world_sampling\": true,\n");
        json.append("    \"skyforge_persists_atmosphere\": false,\n");
        json.append("    \"provider_persistence_owner\": \"aerodynamics4mc\",\n");
        json.append("    \"rendering_backend_dependency\": false\n");
        json.append("  }\n");
        json.append("}\n");
        return json.toString();
    }

    private static void appendProbe(StringBuilder json, ProbeRecord probe, boolean trailingComma) {
        SkyforgeAtmosphereView.Sample sample = probe.sample();
        json.append("    {\n");
        json.append("      \"position\": [")
                .append(number(probe.position().x)).append(", ")
                .append(number(probe.position().y)).append(", ")
                .append(number(probe.position().z)).append("],\n");
        json.append("      \"mean\": [")
                .append(number(sample.meanX())).append(", ")
                .append(number(sample.meanY())).append(", ")
                .append(number(sample.meanZ())).append("],\n");
        json.append("      \"gust\": [")
                .append(number(sample.gustX())).append(", ")
                .append(number(sample.gustY())).append(", ")
                .append(number(sample.gustZ())).append("],\n");
        json.append("      \"effective\": [")
                .append(number(sample.effectiveX())).append(", ")
                .append(number(sample.effectiveY())).append(", ")
                .append(number(sample.effectiveZ())).append("],\n");
        json.append("      \"signed_vertical_air\": ")
                .append(number(sample.updraftMetersPerSecond())).append(",\n");
        json.append("      \"turbulence\": ").append(number(sample.turbulenceIntensity())).append(",\n");
        json.append("      \"shear\": ").append(number(sample.windShearMagnitudePerBlock())).append(",\n");
        json.append("      \"pressure_proxy\": ").append(number(sample.pressure())).append(",\n");
        json.append("      \"confidence\": ").append(number(sample.confidence())).append(",\n");
        json.append("      \"trusted_for_gameplay\": ").append(sample.trustedForGameplay()).append(",\n");
        json.append("      \"source_level\": ").append(quote(sample.sourceLevel())).append(",\n");
        json.append("      \"authority\": ").append(quote(sample.authority())).append(",\n");
        json.append("      \"epochs\": {")
                .append("\"l1\": ").append(sample.l1Epoch()).append(", ")
                .append("\"world_delta\": ").append(sample.worldDeltaEpoch()).append(", ")
                .append("\"l2\": ").append(sample.l2Epoch()).append("},\n");
        json.append("      \"query_nanos\": ").append(probe.queryNanos()).append("\n");
        json.append("    }").append(trailingComma ? "," : "").append("\n");
    }

    private static String providerVersion() {
        return ModList.get()
                .getModContainerById("aerodynamics4mc")
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
    }

    private static Path outputPath() {
        String configured = System.getProperty(OUTPUT_PROPERTY);
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("missing system property " + OUTPUT_PROPERTY);
        }
        Path output = Path.of(configured).toAbsolutePath().normalize();
        if (output.getParent() == null) {
            throw new IllegalStateException("atmosphere probe output has no parent: " + output);
        }
        return output;
    }

    private static String sourceSha() {
        String value = System.getenv("GITHUB_SHA");
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private static String number(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalStateException("non-finite artifact value " + value);
        }
        return String.format(Locale.ROOT, "%.17g", value);
    }

    private static String hex(double value) {
        return Double.toHexString(value);
    }

    private static String stringArray(Iterable<String> values) {
        StringBuilder builder = new StringBuilder("[");
        boolean first = true;
        for (String value : values) {
            if (!first) {
                builder.append(", ");
            }
            first = false;
            builder.append(quote(value));
        }
        return builder.append(']').toString();
    }

    private static String quote(String value) {
        String escaped = value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
        return "\"" + escaped + "\"";
    }

    /**
     * Failure-only upstream diagnostics. These commands inspect A4MC's own runtime to explain why
     * its public gameplay API stayed unavailable; their output is never admitted as Skyforge
     * atmosphere evidence and never substitutes for {@link SkyforgeAtmosphereView}.
     */
    private static void emitProviderDiagnostics(net.minecraft.server.MinecraftServer server) {
        try {
            var source = server.createCommandSourceStack().withSuppressedOutput();
            // Run once with ordinary console feedback as well so CI retains the provider state.
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "aero status");
            server.getCommands().performPrefixedCommand(source, "aero dumpdata");
        } catch (RuntimeException diagnosticFailure) {
            System.getLogger(SkyforgeAtmosphereProbeVolumeAcceptance.class.getName())
                    .log(
                            System.Logger.Level.WARNING,
                            "A4MC failure diagnostics could not be emitted: " + diagnosticFailure);
        }
    }

    private static void fail(net.minecraft.server.MinecraftServer server, String reason) {
        proofComplete = true;
        SkyforgeAutomatedAcceptanceHarness.fail(server, reason);
    }

    static boolean proofComplete() {
        return proofComplete;
    }

    private record ProbeRecord(
            Vec3 position,
            SkyforgeAtmosphereView.Sample sample,
            long queryNanos) {
        boolean sameQuery(ProbeRecord other) {
            return position.equals(other.position);
        }
    }

    private record Pass(List<ProbeRecord> probes, long aggregateNanos, long maxNanos) {}
}
