package io.github.nidaba.skyforge.neoforge1211;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Full-stop/reload verifier for persisted SF-IMP-0065 authored cave AIR. */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID)
final class SkyforgeNeoForge1211AuthoredCaveReloadDevRuntime {
    static final String ENABLE_PROPERTY = "skyforge.dev.authoredCaveReload";
    static final String EXPECTED_RESULT_PROPERTY = "skyforge.dev.authoredCaveExpectedResultFile";

    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211AuthoredCaveReloadDevRuntime.class.getName());
    private static volatile ClientExpectation clientExpectation;
    private static boolean proofStarted;
    private static boolean proofComplete;

    private SkyforgeNeoForge1211AuthoredCaveReloadDevRuntime() {}

    static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (!enabled() || proofStarted || proofComplete) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (!level.dimension().equals(Level.OVERWORLD) || level.players().isEmpty()) {
                continue;
            }
            verify(level);
        }
    }

    private static synchronized void verify(ServerLevel level) {
        if (proofStarted || proofComplete) {
            return;
        }
        proofStarted = true;

        if (SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()
                || SkyforgePhysicalVolumeAdmissionStage.active()) {
            throw new IllegalStateException(
                    "SF-IMP-0065 reload proof must not reinstall terrain/admission/authored-cave mutation");
        }

        Properties expected = loadExpected();
        BlockPos cavePosition = BlockPos.of(Long.parseLong(required(expected, "sampleCavePos")));
        BlockPos solidPosition = BlockPos.of(Long.parseLong(required(expected, "solidControlPos")));
        String expectedSolidState = required(expected, "solidControlState");

        BlockState caveState = level.getBlockState(cavePosition);
        BlockState solidState = level.getBlockState(solidPosition);
        if (!caveState.isAir()) {
            throw new IllegalStateException(
                    "SF-IMP-0065 reload lost persisted authored cave AIR at "
                            + cavePosition + ": " + caveState);
        }
        if (!solidState.toString().equals(expectedSolidState)) {
            throw new IllegalStateException(
                    "SF-IMP-0065 reload changed outside-cave owner-solid control at "
                            + solidPosition + ": expected=" + expectedSolidState + ", actual=" + solidState);
        }

        clientExpectation = new ClientExpectation(
                cavePosition.asLong(),
                caveState,
                solidPosition.asLong(),
                solidState);
        if (SkyforgeAutomatedAcceptanceHarness.clientMode()) {
            level.players().getFirst().teleportTo(
                    cavePosition.getX() + 0.5,
                    cavePosition.getY() + 12.0,
                    cavePosition.getZ() + 0.5);
        }
        proofComplete = true;

        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0065 RELOAD SERVER PASS: cave=" + cavePosition
                        + ", caveState=" + caveState
                        + ", solidControl=" + solidPosition
                        + ", solidState=" + solidState
                        + ". Persisted authored cave survived full stop/reload with no Skyforge mutation binding.");

        SkyforgeAutomatedAcceptanceHarness.record(
                java.util.Map.of(
                        "reloadServerPass", true,
                        "persistedCavePos", Long.toString(cavePosition.asLong()),
                        "persistedCaveState", caveState.toString(),
                        "persistedSolidControlPos", Long.toString(solidPosition.asLong()),
                        "persistedSolidControlState", solidState.toString()));
    }

    static ClientExpectation clientExpectation() {
        return clientExpectation;
    }

    private static Properties loadExpected() {
        String configured = System.getProperty(EXPECTED_RESULT_PROPERTY);
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException(
                    "SF-IMP-0065 reload requires " + EXPECTED_RESULT_PROPERTY);
        }
        Path path = Path.of(configured).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("SF-IMP-0065 expected acceptance file is missing: " + path);
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to read SF-IMP-0065 expected result " + path, exception);
        }
        return properties;
    }

    private static String required(Properties properties, String key) {
        return Objects.requireNonNull(
                properties.getProperty(key),
                () -> "SF-IMP-0065 expected result missing " + key);
    }

    record ClientExpectation(
            long cavePosition,
            BlockState caveState,
            long solidPosition,
            BlockState solidState) {
        ClientExpectation {
            Objects.requireNonNull(caveState, "caveState");
            Objects.requireNonNull(solidState, "solidState");
        }
    }
}
