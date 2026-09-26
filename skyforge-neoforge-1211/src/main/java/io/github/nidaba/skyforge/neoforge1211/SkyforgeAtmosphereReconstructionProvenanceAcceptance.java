package io.github.nidaba.skyforge.neoforge1211;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Final bounded Bootstrap atmosphere evidence for issue #495.
 *
 * <p>This fixture observes one real Aerodynamics4MC gameplay sample through
 * {@link SkyforgeAtmosphereView}, then supplies that exact immutable sample to the same pure
 * consumer policies used by the accepted C6 hawk and C7 Reliable Gliders runtime adapters.
 * Nothing here creates, caches, remaps, persists, or renders atmosphere state.
 *
 * <p>The workflow runs this fixture twice against the same dedicated-server world. Boot A creates
 * the world; boot B reopens it. Cross-boot acceptance requires reacquisition of trusted provider
 * authority and provenance, not byte-identical evolving weather.
 */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID)
final class SkyforgeAtmosphereReconstructionProvenanceAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.atmosphereReconstructionProvenance";
    static final String BOOT_PROPERTY = "skyforge.dev.atmosphereEvidenceBoot";
    static final String OUTPUT_PROPERTY = "skyforge.dev.atmosphereEvidenceOutput";

    private static final int[] XZ_OFFSETS = {-128, -64, 0, 64, 128};
    private static final long MIN_SETTLE_TICKS = 80L;
    private static final long MAX_WAIT_TICKS = 1200L;
    private static final double GLIDER_BASELINE_Y = -0.05;

    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeAtmosphereReconstructionProvenanceAcceptance.class.getName());

    private static long firstPlayerTick = Long.MIN_VALUE;
    private static SkyforgeAtmosphereView atmosphere;
    private static boolean fowlPlayBindingVerified;
    private static boolean reliableGlidersBindingVerified;
    private static boolean proofComplete;

    private SkyforgeAtmosphereReconstructionProvenanceAcceptance() {}

    @SubscribeEvent
    static void onServerTickPost(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)
                || !SkyforgeAutomatedAcceptanceHarness.serverMode()
                || proofComplete) {
            return;
        }

        ServerLevel level = event.getServer().overworld();
        if (level.players().isEmpty()) {
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

        try {
            ensureBindings();
        } catch (ReflectiveOperationException failure) {
            fail(event, "optional consumer/provider API binding failed: " + failure);
            return;
        }

        ServerPlayer anchor = level.players().get(0);
        double centerX = Math.floor(anchor.getX() / 64.0) * 64.0 + 32.0;
        double centerZ = Math.floor(anchor.getZ() / 64.0) * 64.0 + 32.0;
        Vec3 readinessPosition = new Vec3(centerX, 120.0, centerZ);
        SkyforgeAtmosphereView.Sample readiness = atmosphere.sample(level, readinessPosition);

        if (!trusted(readiness)) {
            if (age >= MAX_WAIT_TICKS) {
                fail(event, "real A4MC atmosphere never became trusted within "
                        + MAX_WAIT_TICKS
                        + " ticks; last="
                        + readiness);
            }
            return;
        }

        try {
            acquire(level, anchor, centerX, centerZ, gameTick);
        } catch (IOException | RuntimeException failure) {
            fail(event, "reconstruction/provenance evidence failed: " + failure);
        }
    }

    private static void ensureBindings() throws ReflectiveOperationException {
        if (atmosphere == null) {
            atmosphere = SkyforgeA4mcAtmosphereBridge.create();
        }
        if (!fowlPlayBindingVerified) {
            SkyforgeFowlPlayHawkBridge.create();
            fowlPlayBindingVerified = true;
        }
        if (!reliableGlidersBindingVerified) {
            SkyforgeReliableGlidersBridge.create();
            reliableGlidersBindingVerified = true;
        }
    }

    private static void acquire(
            ServerLevel level,
            ServerPlayer anchor,
            double centerX,
            double centerZ,
            long gameTick)
            throws IOException {
        Candidate selected = null;
        int queryCount = 0;

        for (int zOffset : XZ_OFFSETS) {
            for (int xOffset : XZ_OFFSETS) {
                Vec3 position = new Vec3(centerX + xOffset, 120.0, centerZ + zOffset);
                SkyforgeAtmosphereView.Sample sample = atmosphere.sample(level, position);
                queryCount++;
                if (!trusted(sample)) {
                    throw new IllegalStateException(
                            "canonical consumer lattice returned untrusted atmosphere at "
                                    + position
                                    + ": "
                                    + sample);
                }
                if (selected == null
                        || sample.updraftMetersPerSecond()
                                > selected.sample().updraftMetersPerSecond()) {
                    selected = new Candidate(position, sample);
                }
            }
        }

        if (selected == null) {
            throw new IllegalStateException("canonical consumer lattice produced no sample");
        }

        SkyforgeAtmosphereView.Sample replay = atmosphere.sample(level, selected.position());
        queryCount++;
        if (!selected.sample().equals(replay)) {
            throw new IllegalStateException(
                    "selected atmosphere identity changed within the same server tick first="
                            + selected.sample()
                            + " replay="
                            + replay);
        }

        SkyforgeAtmosphereView.Sample sample = selected.sample();
        String sampleDigest = sampleDigest(selected.position(), sample);

        SkyforgeThermalSoaringDecision.State hawkBefore =
                SkyforgeThermalSoaringDecision.State.inactive();
        SkyforgeThermalSoaringDecision.State hawkAfter =
                SkyforgeThermalSoaringDecision.update(
                        hawkBefore,
                        sample.trustedForGameplay(),
                        sample.updraftMetersPerSecond(),
                        gameTick);

        double gliderAfter = SkyforgeGliderLiftCoupling.apply(
                GLIDER_BASELINE_Y,
                sample.trustedForGameplay(),
                sample.updraftMetersPerSecond());

        if (!Double.isFinite(gliderAfter)) {
            throw new IllegalStateException("glider consumer produced non-finite vertical result");
        }

        String boot = bootId();
        String providerVersion = modVersion("aerodynamics4mc");
        String fowlPlayVersion = modVersion("fowlplay");
        String reliableGlidersVersion = modVersion("reliable_gliders");

        Path output = outputPath();
        Files.createDirectories(output.getParent());
        Files.writeString(
                output,
                encodeArtifact(
                        boot,
                        level,
                        anchor,
                        gameTick,
                        centerX,
                        centerZ,
                        selected,
                        sampleDigest,
                        hawkBefore,
                        hawkAfter,
                        gliderAfter,
                        queryCount,
                        providerVersion,
                        fowlPlayVersion,
                        reliableGlidersVersion),
                StandardCharsets.UTF_8);

        proofComplete = true;
        LinkedHashMap<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("boot", boot);
        evidence.put("serverWorldSampling", true);
        evidence.put("realA4mcGameplayProvider", true);
        evidence.put("fowlPlayConsumerBinding", true);
        evidence.put("reliableGlidersConsumerBinding", true);
        evidence.put("consumerCount", 2);
        evidence.put("sharedAuthoritativeSampleDigest", sampleDigest);
        evidence.put("sameTickReplayExact", true);
        evidence.put("authority", sample.authority());
        evidence.put("sourceLevel", sample.sourceLevel());
        evidence.put("skyforgeAtmospherePersistence", false);
        evidence.put("queryCount", queryCount);
        evidence.put("artifactPath", output);
        SkyforgeAutomatedAcceptanceHarness.completeServerCase(level.getServer(), evidence);

        LOGGER.log(
                System.Logger.Level.INFO,
                "SKYFORGE_ATMOSPHERE_RECONSTRUCTION_PROVENANCE PASS boot="
                        + boot
                        + " digest="
                        + sampleDigest
                        + " authority="
                        + sample.authority()
                        + " source="
                        + sample.sourceLevel()
                        + " updraft="
                        + sample.updraftMetersPerSecond()
                        + " hawkSoaring="
                        + hawkAfter.soaring()
                        + " gliderY="
                        + gliderAfter);
    }

    private static boolean trusted(SkyforgeAtmosphereView.Sample sample) {
        return sample.trustedForGameplay()
                && !"NONE".equals(sample.sourceLevel())
                && !"NONE".equals(sample.authority());
    }

    private static String encodeArtifact(
            String boot,
            ServerLevel level,
            ServerPlayer anchor,
            long gameTick,
            double centerX,
            double centerZ,
            Candidate selected,
            String sampleDigest,
            SkyforgeThermalSoaringDecision.State hawkBefore,
            SkyforgeThermalSoaringDecision.State hawkAfter,
            double gliderAfter,
            int queryCount,
            String providerVersion,
            String fowlPlayVersion,
            String reliableGlidersVersion) {
        SkyforgeAtmosphereView.Sample sample = selected.sample();
        StringBuilder json = new StringBuilder(16_384);
        json.append("{\n");
        json.append("  \"schema_version\": 1,\n");
        json.append("  \"artifact_kind\": \"SKYFORGE_ATMOSPHERE_RECONSTRUCTION_BOOT\",\n");
        json.append("  \"boot\": ").append(quote(boot)).append(",\n");
        json.append("  \"provider_identity\": {\n");
        json.append("    \"mod_id\": \"aerodynamics4mc\",\n");
        json.append("    \"version\": ").append(quote(providerVersion)).append(",\n");
        json.append("    \"api\": \"AeroMinecraftWindApi.sampleGameplay(ServerLevel, Vec3)\"\n");
        json.append("  },\n");
        json.append("  \"skyforge_source_sha\": ").append(quote(sourceSha())).append(",\n");
        json.append("  \"specimen\": {\n");
        json.append("    \"world_seed\": ").append(level.getSeed()).append(",\n");
        json.append("    \"dimension\": ").append(quote(level.dimension().toString())).append(",\n");
        json.append("    \"acquisition_game_tick\": ").append(gameTick).append(",\n");
        json.append("    \"anchor_player\": ").append(quote(anchor.getGameProfile().getName())).append(",\n");
        json.append("    \"center_x\": ").append(number(centerX)).append(",\n");
        json.append("    \"center_z\": ").append(number(centerZ)).append(",\n");
        json.append("    \"selection\": \"MAX_SIGNED_VERTICAL_AIR_ON_CANONICAL_Y120_5X5_LATTICE\",\n");
        json.append("    \"selection_query_count\": 25\n");
        json.append("  },\n");
        json.append("  \"authoritative_sample_digest\": ").append(quote(sampleDigest)).append(",\n");
        json.append("  \"authoritative_sample\": {\n");
        appendPosition(json, selected.position(), 4);
        appendSample(json, sample, 4);
        json.append("  },\n");
        json.append("  \"same_tick_replay\": {\n");
        json.append("    \"exact_equal\": true,\n");
        json.append("    \"sample_digest\": ").append(quote(sampleDigest)).append("\n");
        json.append("  },\n");
        json.append("  \"consumer_observations\": [\n");
        json.append("    {\n");
        json.append("      \"consumer\": \"HAWK_THERMAL_DECISION\",\n");
        json.append("      \"runtime_contract\": \"SkyforgeWaveC6SoaringFaunaDevRuntime\",\n");
        json.append("      \"external_binding\": \"fowlplay@").append(escape(fowlPlayVersion)).append("\",\n");
        json.append("      \"sample_digest\": ").append(quote(sampleDigest)).append(",\n");
        json.append("      \"trusted_input\": ").append(sample.trustedForGameplay()).append(",\n");
        json.append("      \"updraft_input_mps\": ").append(number(sample.updraftMetersPerSecond())).append(",\n");
        json.append("      \"soaring_before\": ").append(hawkBefore.soaring()).append(",\n");
        json.append("      \"soaring_after\": ").append(hawkAfter.soaring()).append("\n");
        json.append("    },\n");
        json.append("    {\n");
        json.append("      \"consumer\": \"RELIABLE_GLIDER_LIFT\",\n");
        json.append("      \"runtime_contract\": \"SkyforgeWaveC7GliderLiftDevRuntime\",\n");
        json.append("      \"external_binding\": \"reliable_gliders@").append(escape(reliableGlidersVersion)).append("\",\n");
        json.append("      \"sample_digest\": ").append(quote(sampleDigest)).append(",\n");
        json.append("      \"trusted_input\": ").append(sample.trustedForGameplay()).append(",\n");
        json.append("      \"updraft_input_mps\": ").append(number(sample.updraftMetersPerSecond())).append(",\n");
        json.append("      \"baseline_vertical_blocks_per_tick\": ").append(number(GLIDER_BASELINE_Y)).append(",\n");
        json.append("      \"result_vertical_blocks_per_tick\": ").append(number(gliderAfter)).append("\n");
        json.append("    }\n");
        json.append("  ],\n");
        json.append("  \"shared_truth\": {\n");
        json.append("    \"consumer_count\": 2,\n");
        json.append("    \"single_sample_digest\": ").append(quote(sampleDigest)).append(",\n");
        json.append("    \"contradictory_weather_authority\": false\n");
        json.append("  },\n");
        json.append("  \"ownership\": {\n");
        json.append("    \"server_world_sampling\": true,\n");
        json.append("    \"skyforge_persists_atmosphere\": false,\n");
        json.append("    \"provider_persistence_owner\": \"aerodynamics4mc\",\n");
        json.append("    \"rendering_backend_dependency\": false\n");
        json.append("  },\n");
        json.append("  \"query_count\": ").append(queryCount).append("\n");
        json.append("}\n");
        return json.toString();
    }

    private static void appendPosition(StringBuilder json, Vec3 position, int indent) {
        String pad = " ".repeat(indent);
        json.append(pad)
                .append("\"position\": [")
                .append(number(position.x)).append(", ")
                .append(number(position.y)).append(", ")
                .append(number(position.z)).append("],\n");
    }

    private static void appendSample(
            StringBuilder json,
            SkyforgeAtmosphereView.Sample sample,
            int indent) {
        String pad = " ".repeat(indent);
        json.append(pad).append("\"trusted_for_gameplay\": ").append(sample.trustedForGameplay()).append(",\n");
        json.append(pad).append("\"mean\": [")
                .append(number(sample.meanX())).append(", ")
                .append(number(sample.meanY())).append(", ")
                .append(number(sample.meanZ())).append("],\n");
        json.append(pad).append("\"gust\": [")
                .append(number(sample.gustX())).append(", ")
                .append(number(sample.gustY())).append(", ")
                .append(number(sample.gustZ())).append("],\n");
        json.append(pad).append("\"effective\": [")
                .append(number(sample.effectiveX())).append(", ")
                .append(number(sample.effectiveY())).append(", ")
                .append(number(sample.effectiveZ())).append("],\n");
        json.append(pad).append("\"signed_vertical_air\": ")
                .append(number(sample.updraftMetersPerSecond())).append(",\n");
        json.append(pad).append("\"turbulence\": ").append(number(sample.turbulenceIntensity())).append(",\n");
        json.append(pad).append("\"shear\": ").append(number(sample.windShearMagnitudePerBlock())).append(",\n");
        json.append(pad).append("\"pressure_proxy\": ").append(number(sample.pressure())).append(",\n");
        json.append(pad).append("\"confidence\": ").append(number(sample.confidence())).append(",\n");
        json.append(pad).append("\"source_level\": ").append(quote(sample.sourceLevel())).append(",\n");
        json.append(pad).append("\"authority\": ").append(quote(sample.authority())).append(",\n");
        json.append(pad).append("\"epochs\": {")
                .append("\"l1\": ").append(sample.l1Epoch()).append(", ")
                .append("\"world_delta\": ").append(sample.worldDeltaEpoch()).append(", ")
                .append("\"l2\": ").append(sample.l2Epoch()).append("}\n");
    }

    private static String sampleDigest(Vec3 position, SkyforgeAtmosphereView.Sample sample) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String canonical = Double.toHexString(position.x)
                    + ","
                    + Double.toHexString(position.y)
                    + ","
                    + Double.toHexString(position.z)
                    + "|"
                    + sample.trustedForGameplay()
                    + "|"
                    + Double.toHexString(sample.meanX())
                    + ","
                    + Double.toHexString(sample.meanY())
                    + ","
                    + Double.toHexString(sample.meanZ())
                    + "|"
                    + Double.toHexString(sample.gustX())
                    + ","
                    + Double.toHexString(sample.gustY())
                    + ","
                    + Double.toHexString(sample.gustZ())
                    + "|"
                    + Double.toHexString(sample.pressure())
                    + "|"
                    + Double.toHexString(sample.turbulenceIntensity())
                    + "|"
                    + Double.toHexString(sample.updraftMetersPerSecond())
                    + "|"
                    + Double.toHexString(sample.windShearMagnitudePerBlock())
                    + "|"
                    + Double.toHexString(sample.confidence())
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
            return HexFormat.of().formatHex(
                    digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static String bootId() {
        String value = System.getProperty(BOOT_PROPERTY, "").trim().toUpperCase(Locale.ROOT);
        if (!value.equals("A") && !value.equals("B")) {
            throw new IllegalStateException(
                    "atmosphere reconstruction evidence boot must be A or B, found '" + value + "'");
        }
        return value;
    }

    private static String modVersion(String modId) {
        return ModList.get()
                .getModContainerById(modId)
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
            throw new IllegalStateException("atmosphere evidence output has no parent: " + output);
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

    private static String quote(String value) {
        return "\"" + escape(value) + "\"";
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private static void fail(ServerTickEvent.Post event, String reason) {
        proofComplete = true;
        SkyforgeAutomatedAcceptanceHarness.fail(event.getServer(), reason);
    }

    private record Candidate(Vec3 position, SkyforgeAtmosphereView.Sample sample) {}
}
