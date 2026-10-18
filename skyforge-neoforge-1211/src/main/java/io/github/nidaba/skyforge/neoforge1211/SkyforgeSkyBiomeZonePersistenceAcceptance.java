package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.model.skyisland.SkyBiomeCatalog;
import io.github.nidaba.skyforge.model.skyisland.SkyBiomeZoneAssignment;
import io.github.nidaba.skyforge.model.skyisland.SkyBiomeZoneIdentity;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Dedicated-server write/reopen proof for persistent SkyBiome zone identity. */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID)
final class SkyforgeSkyBiomeZonePersistenceAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.skyBiomeZonePersistence";
    static final String MODE_PROPERTY = ENABLE_PROPERTY + ".mode";
    static final long ZONE_KEY = 0x534B5942494F4D45L;
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
            var data = SkyforgeSkyBiomeZoneSavedData.forLevel(level);
            var identity = SkyBiomeZoneIdentity.of(level.getSeed(), ZONE_KEY);
            var expected = SkyBiomeZoneAssignment.of(identity, SkyBiomeCatalog.CLOUD_SHELF_ID);

            if ("write".equals(mode)) {
                if (data.assignment(ZONE_KEY).isPresent()) {
                    fail(server, "fresh write world already contains the acceptance zone");
                    return;
                }
                boolean assigned = data.assign(expected);
                if (!assigned || !expected.equals(data.assignment(ZONE_KEY).orElse(null))) {
                    fail(server, "SkyBiome write did not persist the expected authored assignment");
                    return;
                }
                complete = true;
                SkyforgeAutomatedAcceptanceHarness.completeServerCase(
                        server,
                        Map.of(
                                "mode", mode,
                                "worldSeed", level.getSeed(),
                                "zoneKey", String.format(Locale.ROOT, "%016x", ZONE_KEY),
                                "skyBiomeId", expected.skyBiomeId(),
                                "assignmentCreated", assigned,
                                "assignmentCount", data.assignments().size()));
            } else if ("reload".equals(mode)) {
                var loaded = data.assignment(ZONE_KEY);
                if (loaded.isEmpty() || !expected.equals(loaded.orElseThrow())) {
                    fail(server, "reopened world did not restore the same SkyBiome zone identity and ID");
                    return;
                }
                complete = true;
                SkyforgeAutomatedAcceptanceHarness.completeServerCase(
                        server,
                        Map.of(
                                "mode", mode,
                                "worldSeed", level.getSeed(),
                                "zoneKey", String.format("%016x", ZONE_KEY),
                                "skyBiomeId", loaded.orElseThrow().skyBiomeId(),
                                "assignmentRestored", true,
                                "assignmentCount", data.assignments().size()));
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

    private static void fail(MinecraftServer server, String reason) {
        complete = true;
        SkyforgeAutomatedAcceptanceHarness.fail(server, reason);
    }
}
