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

/** Full-stop/reload verifier for persisted SF-IMP-0068 production composed cave output. */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID)
final class SkyforgeNeoForge1211ProductionComposedCaveReloadDevRuntime {
    static final String ENABLE_PROPERTY = "skyforge.dev.productionComposedCaveReload";
    static final String EXPECTED_RESULT_PROPERTY =
            "skyforge.dev.productionComposedCaveExpectedResultFile";

    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211ProductionComposedCaveReloadDevRuntime.class.getName());
    private static volatile ClientExpectation clientExpectation;
    private static boolean proofStarted;
    private static boolean proofComplete;

    private SkyforgeNeoForge1211ProductionComposedCaveReloadDevRuntime() {}

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
                || SkyforgePhysicalVolumeAdmissionStage.active()
                || SkyforgeNativeSurfacePopulationStage.hasActiveBinding()
                || SkyforgeComposedCaveStage.active()) {
            throw new IllegalStateException(
                    "SF-IMP-0068 reload must not reinstall terrain/admission/population/composed-cave mutation");
        }

        Properties expected = loadExpected();
        BlockPos nativeOnly = pos(expected, "nativeOnlyPos");
        BlockPos mouth = pos(expected, "mouthPos");
        BlockPos outward = pos(expected, "outwardPos");
        BlockPos base = pos(expected, "baseCavePos");

        BlockState nativeOnlyState = level.getBlockState(nativeOnly);
        BlockState mouthState = level.getBlockState(mouth);
        BlockState outwardState = level.getBlockState(outward);
        BlockState baseState = level.getBlockState(base);
        if (!nativeOnlyState.isAir()
                || !mouthState.isAir()
                || !outwardState.isAir()
                || !baseState.isAir()) {
            throw new IllegalStateException(
                    "SF-IMP-0068 reload lost production composed cave union: nativeOnly="
                            + nativeOnly + "=" + nativeOnlyState
                            + ", mouth=" + mouth + "=" + mouthState
                            + ", outward=" + outward + "=" + outwardState
                            + ", base=" + base + "=" + baseState);
        }

        clientExpectation = new ClientExpectation(
                nativeOnly.asLong(),
                nativeOnlyState,
                mouth.asLong(),
                mouthState,
                outward.asLong(),
                outwardState,
                base.asLong(),
                baseState);

        if (SkyforgeAutomatedAcceptanceHarness.clientMode()) {
            level.players().getFirst().teleportTo(
                    mouth.getX() + 0.5,
                    mouth.getY() + 8.0,
                    mouth.getZ() + 0.5);
        }

        proofComplete = true;
        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0068 RELOAD SERVER PASS: nativeOnly=" + nativeOnly
                        + ", mouth=" + mouth + ", outward=" + outward + ", base=" + base
                        + ". The production cave union survived full stop/reload with no mutation binding.");

        SkyforgeAutomatedAcceptanceHarness.record(
                java.util.Map.of(
                        "reloadServerPass", true,
                        "mutationBindingsAbsent", true,
                        "persistedNativeOnlyPos", Long.toString(nativeOnly.asLong()),
                        "persistedNativeOnlyState", nativeOnlyState.toString(),
                        "persistedMouthPos", Long.toString(mouth.asLong()),
                        "persistedMouthState", mouthState.toString(),
                        "persistedOutwardState", outwardState.toString(),
                        "persistedBaseState", baseState.toString()));
    }

    static ClientExpectation clientExpectation() {
        return clientExpectation;
    }

    private static BlockPos pos(Properties properties, String key) {
        return BlockPos.of(Long.parseLong(required(properties, key)));
    }

    private static Properties loadExpected() {
        String configured = System.getProperty(EXPECTED_RESULT_PROPERTY);
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException(
                    "SF-IMP-0068 reload requires " + EXPECTED_RESULT_PROPERTY);
        }
        Path path = Path.of(configured).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("SF-IMP-0068 expected result is missing: " + path);
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to read SF-IMP-0068 result " + path, exception);
        }
        return properties;
    }

    private static String required(Properties properties, String key) {
        return Objects.requireNonNull(
                properties.getProperty(key),
                () -> "SF-IMP-0068 expected result missing " + key);
    }

    record ClientExpectation(
            long nativeOnlyPosition,
            BlockState nativeOnlyState,
            long mouthPosition,
            BlockState mouthState,
            long outwardPosition,
            BlockState outwardState,
            long basePosition,
            BlockState baseState) {
        ClientExpectation {
            Objects.requireNonNull(nativeOnlyState, "nativeOnlyState");
            Objects.requireNonNull(mouthState, "mouthState");
            Objects.requireNonNull(outwardState, "outwardState");
            Objects.requireNonNull(baseState, "baseState");
        }
    }
}
