package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.model.skyisland.SkyBiomeCatalog;
import io.github.nidaba.skyforge.world.SkyBiomeSpatialField;
import io.github.nidaba.skyforge.world.SkyBiomeSpatialFieldCompiler;
import io.github.nidaba.skyforge.world.SkyIslandWorldCatalog;
import io.github.nidaba.skyforge.world.WorldBounds;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Dedicated-server proof that compiled SkyBiome field output survives save and reopen. */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID)
final class SkyforgeSkyBiomeZonePersistenceAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.skyBiomeZonePersistence";
    static final String MODE_PROPERTY = ENABLE_PROPERTY + ".mode";
    private static final WorldBounds PROVINCE_BOUNDS =
            new WorldBounds(-256.0, 256.0, 0.0, 512.0, -256.0, 256.0);
    private static final List<WorldBounds> CLOUD_SHELVES =
            List.of(new WorldBounds(-32.0, 32.0, 280.0, 320.0, -32.0, 32.0));
    private static final double LEE_RADIUS = 48.0;
    private static boolean complete;

    private SkyforgeSkyBiomeZonePersistenceAcceptance() {}

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)
                || complete
                || !SkyforgeAutomatedAcceptanceHarness.serverMode()
                || !SkyforgeAutomatedAcceptanceHarness.warmupComplete()) {
            return;
        }

        MinecraftServer server = event.getServer();
        try {
            ServerLevel level = server.getLevel(Level.OVERWORLD);
            if (level == null) {
                fail(server, "dedicated server has no Overworld");
                return;
            }
            String mode = Objects.requireNonNullElse(System.getProperty(MODE_PROPERTY), "write");
            SkyBiomeSpatialField field = compileField(level.getSeed());
            var data = SkyforgeSkyBiomeZoneSavedData.forLevel(level);

            if ("write".equals(mode)) {
                if (!data.assignments().isEmpty()) {
                    fail(server, "fresh write world already contains SkyBiome assignments");
                    return;
                }
                int created = data.assignField(field);
                if (created != field.zones().size()) {
                    fail(server, "compiled SkyBiome field did not create every authored assignment");
                    return;
                }
                verifyPersistedField(data, field);
                complete = true;
                SkyforgeAutomatedAcceptanceHarness.completeServerCase(
                        server,
                        Map.of(
                                "mode", mode,
                                "worldSeed", level.getSeed(),
                                "fieldFingerprint", fieldFingerprint(field),
                                "compiledZoneCount", field.zones().size(),
                                "assignmentCount", data.assignments().size(),
                                "assignmentCreated", created == field.zones().size(),
                                "containsOpenSky", contains(field, SkyBiomeCatalog.OPEN_SKY_ID),
                                "containsCloudShelf", contains(field, SkyBiomeCatalog.CLOUD_SHELF_ID),
                                "containsIslandLee", contains(field, SkyBiomeCatalog.ISLAND_LEE_ID)));
            } else if ("reload".equals(mode)) {
                int created = data.assignField(field);
                if (created != 0) {
                    fail(server, "recompiled SkyBiome field added assignments after reopening");
                    return;
                }
                verifyPersistedField(data, field);
                complete = true;
                SkyforgeAutomatedAcceptanceHarness.completeServerCase(
                        server,
                        Map.of(
                                "mode", mode,
                                "worldSeed", level.getSeed(),
                                "fieldFingerprint", fieldFingerprint(field),
                                "compiledZoneCount", field.zones().size(),
                                "assignmentCount", data.assignments().size(),
                                "assignmentRestored", true,
                                "containsOpenSky", contains(field, SkyBiomeCatalog.OPEN_SKY_ID),
                                "containsCloudShelf", contains(field, SkyBiomeCatalog.CLOUD_SHELF_ID),
                                "containsIslandLee", contains(field, SkyBiomeCatalog.ISLAND_LEE_ID)));
            } else {
                fail(server, "unsupported SkyBiome persistence acceptance mode: " + mode);
            }
        } catch (RuntimeException failure) {
            SkyforgeAutomatedAcceptanceHarness.fail(
                    server,
                    "SkyBiome persistence acceptance failed: "
                            + failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
    }

    private static SkyBiomeSpatialField compileField(long worldSeed) {
        SkyIslandWorldCatalog islands = SkyforgeNeoForge1211PopulationDevRuntime.catalog();
        return new SkyBiomeSpatialFieldCompiler().compile(
                worldSeed, PROVINCE_BOUNDS, islands, CLOUD_SHELVES, LEE_RADIUS);
    }

    private static void verifyPersistedField(
            SkyforgeSkyBiomeZoneSavedData data,
            SkyBiomeSpatialField field) {
        if (data.assignments().size() != field.zones().size()) {
            throw new IllegalStateException(
                    "persisted zone count differs from compiled field: "
                            + data.assignments().size() + " vs " + field.zones().size());
        }
        for (var zone : field.zones()) {
            var expected = zone.assignment();
            var stored = data.assignment(expected.zoneIdentity().zoneKey()).orElse(null);
            if (!expected.equals(stored)) {
                throw new IllegalStateException(
                        "persisted SkyBiome assignment differs from compiled field for zone "
                                + Long.toUnsignedString(expected.zoneIdentity().zoneKey()));
            }
        }
    }

    private static boolean contains(
            SkyBiomeSpatialField field,
            io.github.nidaba.skyforge.model.skyisland.SkyBiomeId id) {
        return field.zones().stream().anyMatch(zone -> zone.assignment().skyBiomeId().equals(id));
    }

    private static String fieldFingerprint(SkyBiomeSpatialField field) {
        String canonical = field.zones().stream()
                .map(zone -> String.format(
                        java.util.Locale.ROOT,
                        "%016x=%s",
                        zone.assignment().zoneIdentity().zoneKey(),
                        zone.assignment().skyBiomeId()))
                .sorted()
                .collect(Collectors.joining("\n"));
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", impossible);
        }
    }

    private static void fail(MinecraftServer server, String reason) {
        complete = true;
        SkyforgeAutomatedAcceptanceHarness.fail(server, reason);
    }
}
