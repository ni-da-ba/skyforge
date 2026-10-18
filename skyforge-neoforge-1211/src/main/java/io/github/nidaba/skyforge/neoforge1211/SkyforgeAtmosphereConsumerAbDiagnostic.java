package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.WorldBounds;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * #1180 diagnostic-only hawk behavior A/B over the persisted DR-50 island and ordinary terrain.
 *
 * <p>The control arm loads Fowl Play without C6. The treatment arm loads the same cloned world with
 * C6 enabled. The diagnostic never changes atmosphere strength, soaring thresholds, schedules or
 * navigation itself; it only spawns reproducible batches and records movement plus C6 counters.
 */
final class SkyforgeAtmosphereConsumerAbDiagnostic {
    static final String ENABLE_PROPERTY = "skyforge.dev.atmosphereConsumerAbDiagnostic";
    static final String ARM_PROPERTY = "skyforge.dev.atmosphereConsumerAbArm";
    static final String OUTPUT_PROPERTY = "skyforge.dev.atmosphereConsumerAbOutput";

    private static final String FOWL_PLAY_MOD_ID = "fowlplay";
    private static final ResourceLocation HAWK_ID =
            ResourceLocation.fromNamespaceAndPath(FOWL_PLAY_MOD_ID, "hawk");
    private static final int BATCH_SIZE = 8;
    private static final int OBSERVATION_TICKS = 1200;
    private static final int ORDINARY_CENTER_X = 512;
    private static final int ORDINARY_CENTER_Z = 0;
    private static final int SPAWN_AGL = 48;
    private static final int[][] OFFSETS = {
        {-48, -48}, {-48, 0}, {-48, 48}, {0, -48},
        {0, 48}, {48, -48}, {48, 0}, {48, 48}
    };
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeAtmosphereConsumerAbDiagnostic.class.getName());

    private static boolean installed;
    private static boolean initialized;
    private static boolean completed;
    private static long startTick = Long.MIN_VALUE;
    private static final ArrayList<HawkObservation> observations = new ArrayList<>();

    private SkyforgeAtmosphereConsumerAbDiagnostic() {}

    static synchronized void installFromSystemProperty() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY) || installed) {
            return;
        }
        if (!ModList.get().isLoaded(FOWL_PLAY_MOD_ID)) {
            throw new IllegalStateException("#1180 diagnostic requires pinned Fowl Play");
        }
        String arm = arm();
        if (!arm.equals("control") && !arm.equals("treatment")) {
            throw new IllegalArgumentException(
                    "#1180 diagnostic arm must be control or treatment, found " + arm);
        }
        SkyforgeAutomatedAcceptanceHarness.installWarmupChunkKeys(diagnosticTickingChunks());
        NeoForge.EVENT_BUS.addListener(SkyforgeAtmosphereConsumerAbDiagnostic::onServerTickPost);
        installed = true;
        LOGGER.log(System.Logger.Level.INFO, "SF-IMP-1180 hawk A/B diagnostic armed arm=" + arm);
    }

    private static void onServerTickPost(ServerTickEvent.Post event) {
        if (completed || !SkyforgeAutomatedAcceptanceHarness.serverMode()) {
            return;
        }
        ServerLevel level = event.getServer().overworld();
        if (!initialized) {
            if (arm().equals("treatment")
                    && !SkyforgeWaveC6SoaringFaunaDevRuntime.diagnosticsReady()) {
                return;
            }
            initialize(level);
            return;
        }

        long elapsed = level.getGameTime() - startTick;
        update(level);
        if (elapsed >= OBSERVATION_TICKS) {
            finish(level);
        }
    }

    private static void initialize(ServerLevel level) {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.single();
        WorldBounds bounds = fixture.volume().bounds();

        var source = level.getServer().createCommandSourceStack().withSuppressedOutput();
        level.getServer().getCommands().performPrefixedCommand(source, "gamerule doMobSpawning false");
        level.getServer().getCommands().performPrefixedCommand(source, "time set 2000");

        spawnBatch(level, "island", 0, 0, bounds);
        spawnBatch(level, "ordinary", ORDINARY_CENTER_X, ORDINARY_CENTER_Z, bounds);
        if (observations.size() != BATCH_SIZE * 2) {
            fail(level, "expected " + (BATCH_SIZE * 2) + " hawks, found " + observations.size());
            return;
        }

        startTick = level.getGameTime();
        initialized = true;
        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-1180 hawk A/B observation started arm="
                        + arm()
                        + " hawks="
                        + observations.size()
                        + " gameTick="
                        + startTick);
    }

    private static java.util.Set<Long> diagnosticTickingChunks() {
        LinkedHashSet<Long> chunks = new LinkedHashSet<>();
        addTickingSquare(chunks, 0, 0, 7);
        addTickingSquare(chunks, ORDINARY_CENTER_X, ORDINARY_CENTER_Z, 7);
        return java.util.Set.copyOf(chunks);
    }

    private static void addTickingSquare(
            LinkedHashSet<Long> chunks,
            int centerX,
            int centerZ,
            int chunkRadius) {
        int centerChunkX = Math.floorDiv(centerX, 16);
        int centerChunkZ = Math.floorDiv(centerZ, 16);
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                chunks.add(ChunkPos.asLong(centerChunkX + dx, centerChunkZ + dz));
            }
        }
    }

    private static void spawnBatch(
            ServerLevel level,
            String region,
            int centerX,
            int centerZ,
            WorldBounds islandBounds) {
        for (int index = 0; index < BATCH_SIZE; index++) {
            int x = centerX + OFFSETS[index][0];
            int z = centerZ + OFFSETS[index][1];
            int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            double y = surface + SPAWN_AGL;
            Mob hawk = summonHawk(level, x + 0.5, y, z + 0.5);
            hawk.setPersistenceRequired();
            // Fowl Play ultimately owns behavior. Seeding the entity RNG only removes irrelevant
            // stochastic divergence between cloned-world control/treatment arms.
            hawk.getRandom().setSeed(0x11800000L + index + (region.equals("ordinary") ? 1000L : 0L));
            observations.add(new HawkObservation(
                    region,
                    index,
                    hawk,
                    x + 0.5,
                    y,
                    z + 0.5,
                    centerX,
                    centerZ,
                    islandBounds));
        }
    }

    private static Mob summonHawk(ServerLevel level, double x, double y, double z) {
        var source = level.getServer().createCommandSourceStack().withSuppressedOutput();
        String command = String.format(Locale.ROOT, "summon %s %.3f %.3f %.3f", HAWK_ID, x, y, z);
        level.getServer().getCommands().performPrefixedCommand(source, command);
        AABB box = new AABB(x - 1.0, y - 1.0, z - 1.0, x + 1.0, y + 1.0, z + 1.0);
        List<Mob> candidates = level.getEntitiesOfClass(
                Mob.class,
                box,
                mob -> BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).equals(HAWK_ID)
                        && observations.stream().noneMatch(existing -> existing.hawk == mob));
        if (candidates.size() != 1) {
            throw new IllegalStateException(
                    "#1180 expected exactly one newly summoned hawk at "
                            + x + "," + y + "," + z + " found=" + candidates.size());
        }
        return candidates.getFirst();
    }

    private static void update(ServerLevel level) {
        for (HawkObservation observation : observations) {
            observation.observe(level);
        }
    }

    private static void finish(ServerLevel level) {
        try {
            Path output = outputPath();
            Files.createDirectories(output.getParent());
            Files.writeString(output, encode(level), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            fail(level, "failed to write #1180 evidence: " + failure);
            return;
        }

        int adapted = 0;
        int transitions = 0;
        int steering = 0;
        int islandEdgeDepartures = 0;
        int islandBelowEnvelope = 0;
        int islandNearTerrainAfterDeparture = 0;
        for (HawkObservation observation : observations) {
            var c6 = SkyforgeWaveC6SoaringFaunaDevRuntime.diagnosticsFor(observation.hawk);
            if (c6.adapted()) adapted++;
            transitions += c6.transitions();
            steering += c6.steeringCommands();
            if (observation.region.equals("island")) {
                if (observation.edgeDeparted) islandEdgeDepartures++;
                if (observation.belowIslandEnvelope) islandBelowEnvelope++;
                if (observation.nearTerrainAfterDeparture) islandNearTerrainAfterDeparture++;
            }
        }

        if (arm().equals("control") && adapted != 0) {
            fail(level, "control arm unexpectedly adapted " + adapted + " hawks through C6");
            return;
        }
        if (arm().equals("treatment") && adapted != observations.size()) {
            fail(level, "treatment arm adapted only " + adapted + "/" + observations.size() + " hawks");
            return;
        }

        long islandMoving = observations.stream()
                .filter(observation -> observation.region.equals("island"))
                .filter(HawkObservation::moved)
                .count();
        long ordinaryMoving = observations.stream()
                .filter(observation -> observation.region.equals("ordinary"))
                .filter(HawkObservation::moved)
                .count();
        if (islandMoving == 0L || ordinaryMoving == 0L) {
            fail(
                    level,
                    "invalid hawk A/B fixture: stock AI did not demonstrably move in both regions"
                            + " islandMoving="
                            + islandMoving
                            + " ordinaryMoving="
                            + ordinaryMoving);
            return;
        }

        completed = true;
        LinkedHashMap<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("arm", arm());
        evidence.put("hawkCount", observations.size());
        evidence.put("islandMovingHawks", islandMoving);
        evidence.put("ordinaryMovingHawks", ordinaryMoving);
        evidence.put("c6Adapted", adapted);
        evidence.put("c6Transitions", transitions);
        evidence.put("c6SteeringCommands", steering);
        evidence.put("islandEdgeDepartures", islandEdgeDepartures);
        evidence.put("islandBelowEnvelope", islandBelowEnvelope);
        evidence.put("islandNearTerrainAfterDeparture", islandNearTerrainAfterDeparture);
        evidence.put("artifactPath", outputPath());
        LOGGER.log(
                System.Logger.Level.INFO,
                "SF_IMP_1180_HAWK_AB PASS arm="
                        + arm()
                        + " adapted="
                        + adapted
                        + " transitions="
                        + transitions
                        + " steering="
                        + steering
                        + " islandEdgeDepartures="
                        + islandEdgeDepartures
                        + " islandBelowEnvelope="
                        + islandBelowEnvelope
                        + " islandNearTerrainAfterDeparture="
                        + islandNearTerrainAfterDeparture);
        SkyforgeAutomatedAcceptanceHarness.completeServerCase(level.getServer(), evidence);
    }

    private static String encode(ServerLevel level) {
        StringBuilder json = new StringBuilder(32_000);
        json.append("{\n");
        json.append("  \"schema_version\": 1,\n");
        json.append("  \"artifact_kind\": \"SKYFORGE_SF_IMP_1180_HAWK_AB_ARM\",\n");
        json.append("  \"arm\": ").append(quote(arm())).append(",\n");
        json.append("  \"world_seed\": ").append(level.getSeed()).append(",\n");
        json.append("  \"start_game_tick\": ").append(startTick).append(",\n");
        json.append("  \"observation_ticks\": ").append(OBSERVATION_TICKS).append(",\n");
        json.append("  \"spawn_agl_blocks\": ").append(SPAWN_AGL).append(",\n");
        json.append("  \"hawks\": [\n");
        for (int i = 0; i < observations.size(); i++) {
            HawkObservation o = observations.get(i);
            var c6 = SkyforgeWaveC6SoaringFaunaDevRuntime.diagnosticsFor(o.hawk);
            json.append("    {\n");
            json.append("      \"region\": ").append(quote(o.region)).append(",\n");
            json.append("      \"index\": ").append(o.index).append(",\n");
            json.append("      \"spawn\": [")
                    .append(number(o.spawnX)).append(", ")
                    .append(number(o.spawnY)).append(", ")
                    .append(number(o.spawnZ)).append("],\n");
            json.append("      \"samples\": ").append(o.samples).append(",\n");
            json.append("      \"min_y\": ").append(number(o.minY)).append(",\n");
            json.append("      \"mean_y\": ").append(number(o.meanY())).append(",\n");
            json.append("      \"max_y\": ").append(number(o.maxY)).append(",\n");
            json.append("      \"final_y\": ").append(number(o.finalY)).append(",\n");
            json.append("      \"max_horizontal_distance\": ")
                    .append(number(o.maxHorizontalDistance)).append(",\n");
            json.append("      \"max_displacement_from_spawn\": ")
                    .append(number(o.maxDisplacementFromSpawn)).append(",\n");
            json.append("      \"edge_departed\": ").append(o.edgeDeparted).append(",\n");
            json.append("      \"below_island_envelope\": ").append(o.belowIslandEnvelope).append(",\n");
            json.append("      \"near_terrain_after_departure\": ")
                    .append(o.nearTerrainAfterDeparture).append(",\n");
            json.append("      \"removed\": ").append(o.removed).append(",\n");
            json.append("      \"removal_reason\": ").append(quote(o.removalReason)).append(",\n");
            json.append("      \"c6_present\": ").append(c6.present()).append(",\n");
            json.append("      \"c6_adapted\": ").append(c6.adapted()).append(",\n");
            json.append("      \"c6_disabled\": ").append(c6.disabled()).append(",\n");
            json.append("      \"c6_soaring_final\": ").append(c6.soaring()).append(",\n");
            json.append("      \"c6_transitions\": ").append(c6.transitions()).append(",\n");
            json.append("      \"c6_steering_commands\": ").append(c6.steeringCommands()).append("\n");
            json.append("    }").append(i + 1 < observations.size() ? "," : "").append("\n");
        }
        json.append("  ]\n");
        json.append("}\n");
        return json.toString();
    }

    private static void fail(ServerLevel level, String reason) {
        completed = true;
        SkyforgeAutomatedAcceptanceHarness.fail(level.getServer(), reason);
    }

    private static Path outputPath() {
        String configured = System.getProperty(OUTPUT_PROPERTY);
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("missing system property " + OUTPUT_PROPERTY);
        }
        Path path = Path.of(configured).toAbsolutePath().normalize();
        if (path.getParent() == null) {
            throw new IllegalStateException("#1180 output has no parent: " + path);
        }
        return path;
    }

    private static String arm() {
        return System.getProperty(ARM_PROPERTY, "").trim().toLowerCase(Locale.ROOT);
    }

    private static String number(double value) {
        if (!Double.isFinite(value)) {
            return "null";
        }
        return String.format(Locale.ROOT, "%.17g", value);
    }

    private static String quote(String value) {
        String escaped = value == null
                ? ""
                : value.replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r");
        return "\"" + escaped + "\"";
    }

    private static final class HawkObservation {
        final String region;
        final int index;
        final Mob hawk;
        final double spawnX;
        final double spawnY;
        final double spawnZ;
        final int centerX;
        final int centerZ;
        final WorldBounds islandBounds;
        double minY;
        double maxY;
        double sumY;
        double finalY;
        double maxHorizontalDistance;
        double maxDisplacementFromSpawn;
        int samples;
        boolean edgeDeparted;
        boolean belowIslandEnvelope;
        boolean nearTerrainAfterDeparture;
        boolean removed;
        String removalReason = "";

        HawkObservation(
                String region,
                int index,
                Mob hawk,
                double spawnX,
                double spawnY,
                double spawnZ,
                int centerX,
                int centerZ,
                WorldBounds islandBounds) {
            this.region = region;
            this.index = index;
            this.hawk = hawk;
            this.spawnX = spawnX;
            this.spawnY = spawnY;
            this.spawnZ = spawnZ;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.islandBounds = islandBounds;
            this.minY = spawnY;
            this.maxY = spawnY;
            this.finalY = spawnY;
        }

        void observe(ServerLevel level) {
            if (hawk.isRemoved()) {
                removed = true;
                Entity.RemovalReason reason = hawk.getRemovalReason();
                removalReason = reason == null ? "unknown" : reason.toString();
                return;
            }
            double x = hawk.getX();
            double y = hawk.getY();
            double z = hawk.getZ();
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            sumY += y;
            finalY = y;
            samples++;
            maxHorizontalDistance = Math.max(
                    maxHorizontalDistance,
                    Math.hypot(x - centerX, z - centerZ));
            maxDisplacementFromSpawn = Math.max(
                    maxDisplacementFromSpawn,
                    Math.sqrt(
                            Math.pow(x - spawnX, 2.0)
                                    + Math.pow(y - spawnY, 2.0)
                                    + Math.pow(z - spawnZ, 2.0)));
            if (region.equals("island")) {
                if (x < islandBounds.minimumX()
                        || x > islandBounds.maximumX()
                        || z < islandBounds.minimumZ()
                        || z > islandBounds.maximumZ()) {
                    edgeDeparted = true;
                }
                if (y < islandBounds.minimumY()) {
                    belowIslandEnvelope = true;
                }
                if (edgeDeparted) {
                    int surface = level.getHeight(
                            Heightmap.Types.WORLD_SURFACE,
                            (int) Math.floor(x),
                            (int) Math.floor(z));
                    if (y <= surface + 12.0) {
                        nearTerrainAfterDeparture = true;
                    }
                }
            } else if (maxHorizontalDistance > islandBounds.maximumX()) {
                edgeDeparted = true;
            }
        }

        boolean moved() {
            return maxDisplacementFromSpawn > 1.0;
        }

        double meanY() {
            return samples == 0 ? spawnY : sumY / samples;
        }
    }
}
