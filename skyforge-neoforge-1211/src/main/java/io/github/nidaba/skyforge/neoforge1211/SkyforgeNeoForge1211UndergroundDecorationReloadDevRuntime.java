package io.github.nidaba.skyforge.neoforge1211;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * SF-IMP-0062 save/reload verifier for one native cave-decoration mutation selected by the
 * preceding deterministic B run.
 *
 * <p>This mode deliberately installs no Skyforge terrain, admission, carver, or population binding.
 * It reads the B run's machine-readable sample position/state, opens the already-saved world, and
 * requires that the same Minecraft block state survives a full stop/reload. The client half then
 * observes that exact persisted state from its own ClientLevel.
 */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID)
final class SkyforgeNeoForge1211UndergroundDecorationReloadDevRuntime {
    static final String ENABLE_PROPERTY = "skyforge.dev.undergroundDecorationReload";
    static final String EXPECTED_RESULT_FILE_PROPERTY =
            "skyforge.dev.undergroundDecorationExpectedResultFile";

    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211UndergroundDecorationReloadDevRuntime.class.getName());

    private static volatile ClientExpectation clientExpectation;
    private static ExpectedSample expectedSample;
    private static boolean serverProofStarted;
    private static boolean serverProofComplete;

    private SkyforgeNeoForge1211UndergroundDecorationReloadDevRuntime() {}

    static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    static synchronized void installFromSystemProperty() {
        if (!enabled()) {
            return;
        }
        if (SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()
                || SkyforgePhysicalVolumeAdmissionStage.active()) {
            throw new IllegalStateException(
                    "SF-IMP-0062 reload proof must not install over a live Skyforge realization binding");
        }
        expectedSample = readExpectedSample();
        LOGGER.log(
                System.Logger.Level.INFO,
                "Skyforge SF-IMP-0062 reload verifier enabled. No terrain/admission/carver/population binding "
                        + "is installed; the saved B world must retain decoration sample "
                        + expectedSample.position() + " as " + expectedSample.stateText() + ".");
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (!enabled() || serverProofStarted || serverProofComplete || expectedSample == null) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (!level.dimension().equals(Level.OVERWORLD) || level.players().isEmpty()) {
                continue;
            }

            BlockPos position = expectedSample.position();
            var player = level.players().getFirst();
            int sampleChunkX = SectionPos.blockToSectionCoord(position.getX());
            int sampleChunkZ = SectionPos.blockToSectionCoord(position.getZ());
            if (level.getChunkSource().getChunkNow(sampleChunkX, sampleChunkZ) == null) {
                player.teleportTo(
                        position.getX() + 0.5,
                        Math.max(position.getY() + 16.0, 260.0),
                        position.getZ() + 0.5);
                continue;
            }
            verifyServerPersistence(level, event);
        }
    }

    private static synchronized void verifyServerPersistence(
            ServerLevel level,
            ServerTickEvent.Post event) {
        if (serverProofStarted || serverProofComplete || expectedSample == null) {
            return;
        }
        serverProofStarted = true;

        BlockPos position = expectedSample.position();
        String actualState = level.getBlockState(position).toString();
        if (!actualState.equals(expectedSample.stateText())) {
            SkyforgeAutomatedAcceptanceHarness.fail(
                    event.getServer(),
                    "SF-IMP-0062 persisted decoration mismatch at " + position
                            + ": expected=" + expectedSample.stateText()
                            + ", actual=" + actualState);
            return;
        }

        clientExpectation = new ClientExpectation(position.asLong(), expectedSample.stateText());
        serverProofComplete = true;
        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0062 RELOAD SERVER PASS: position=" + position
                        + ", state=" + actualState
                        + ". Native UNDERGROUND_DECORATION survived a full save/stop/reload without "
                        + "reinstalling Skyforge realization or population state.");

        SkyforgeAutomatedAcceptanceHarness.record(
                java.util.Map.of(
                        "reloadServerPass", true,
                        "persistedDecorationPos", Long.toString(position.asLong()),
                        "persistedDecorationState", actualState));
    }

    static ClientExpectation clientExpectation() {
        return clientExpectation;
    }

    private static ExpectedSample readExpectedSample() {
        String configured = System.getProperty(EXPECTED_RESULT_FILE_PROPERTY);
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException(
                    "SF-IMP-0062 reload proof requires system property " + EXPECTED_RESULT_FILE_PROPERTY);
        }
        Path path = Path.of(configured).toAbsolutePath().normalize();
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to read SF-IMP-0062 expected result " + path, exception);
        }
        if (!"PASS".equals(properties.getProperty("status"))) {
            throw new IllegalStateException("SF-IMP-0062 reload source result did not report PASS: " + path);
        }

        String packedText = Objects.requireNonNull(
                properties.getProperty("sampleDecorationPos"),
                "SF-IMP-0062 B result omitted sampleDecorationPos");
        String stateText = Objects.requireNonNull(
                properties.getProperty("sampleDecorationState"),
                "SF-IMP-0062 B result omitted sampleDecorationState");
        if ("none".equals(packedText) || "none".equals(stateText)) {
            throw new IllegalStateException("SF-IMP-0062 B result contains no persistent decoration sample");
        }
        try {
            return new ExpectedSample(BlockPos.of(Long.parseLong(packedText)), stateText);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(
                    "invalid SF-IMP-0062 sampleDecorationPos in " + path + ": " + packedText,
                    exception);
        }
    }

    private record ExpectedSample(BlockPos position, String stateText) {
        private ExpectedSample {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(stateText, "stateText");
        }
    }

    record ClientExpectation(long position, String expectedStateText) {
        ClientExpectation {
            Objects.requireNonNull(expectedStateText, "expectedStateText");
        }
    }
}
