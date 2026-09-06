package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandTerrainProfile;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
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
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Full-stop/reload verifier for persisted SF-IMP-0069 native interior fluid output. */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID)
final class SkyforgeNeoForge1211ProductionInteriorPopulationReloadDevRuntime {
    static final String ENABLE_PROPERTY = "skyforge.dev.productionInteriorPopulationReload";
    static final String EXPECTED_RESULT_PROPERTY =
            "skyforge.dev.productionInteriorPopulationExpectedResultFile";

    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211ProductionInteriorPopulationReloadDevRuntime.class.getName());
    private static final int TIMEOUT_TICKS = 400;

    private static volatile ClientExpectation clientExpectation;
    private static AutoCloseable persistentTerrainBinding;
    private static ExpectedSample expectedSample;
    private static long firstObservedGameTick = Long.MIN_VALUE;
    private static boolean scheduledFreshTick;
    private static boolean proofComplete;

    private SkyforgeNeoForge1211ProductionInteriorPopulationReloadDevRuntime() {}

    static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    static synchronized void installFromSystemProperty() {
        if (!enabled() || persistentTerrainBinding != null) {
            return;
        }
        if (SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()
                || SkyforgePhysicalVolumeAdmissionStage.active()
                || SkyforgeNativeSurfacePopulationStage.hasActiveBinding()
                || SkyforgeComposedCaveStage.active()
                || SkyforgeNativeInteriorPopulationStage.active()) {
            throw new IllegalStateException(
                    "SF-IMP-0069 reload must begin without any Skyforge mutation lifecycle binding");
        }

        expectedSample = readExpectedSample();
        persistentTerrainBinding = SkyforgeNeoForge1211SurfaceStage.install(
                new SkyforgeNeoForge1211ChunkAdapter(
                        SkyforgeNeoForge1211ProductionComposedCaveFixture.single().catalog(),
                        SkyIslandTerrainProfile.reference(),
                        new SkyforgeMinecraftBlockPalette()),
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()));

        LOGGER.log(
                System.Logger.Level.INFO,
                "Skyforge SF-IMP-0069 reload verifier enabled. Only deterministic compiled terrain ownership "
                        + "is restored; admission, surface population, composed caves, and interior population "
                        + "remain absent. Persisted generated-fluid provenance must rehydrate for "
                        + expectedSample.position() + ".");
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (!enabled() || proofComplete || expectedSample == null) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (!level.dimension().equals(Level.OVERWORLD) || level.players().isEmpty()) {
                continue;
            }
            if (firstObservedGameTick == Long.MIN_VALUE) {
                firstObservedGameTick = level.getGameTime();
            }
            if (level.getGameTime() - firstObservedGameTick > TIMEOUT_TICKS) {
                SkyforgeAutomatedAcceptanceHarness.fail(
                        event.getServer(),
                        "SF-IMP-0069 reload did not observe persisted provenance propagation within "
                                + TIMEOUT_TICKS + " ticks");
                return;
            }

            BlockPos position = expectedSample.position();
            int chunkX = SectionPos.blockToSectionCoord(position.getX());
            int chunkZ = SectionPos.blockToSectionCoord(position.getZ());
            if (level.getChunkSource().getChunkNow(chunkX, chunkZ) == null) {
                level.players().getFirst().teleportTo(
                        position.getX() + 0.5,
                        Math.max(position.getY() + 16.0, 260.0),
                        position.getZ() + 0.5);
                continue;
            }
            verify(level, event);
        }
    }

    private static synchronized void verify(ServerLevel level, ServerTickEvent.Post event) {
        if (proofComplete || expectedSample == null) {
            return;
        }
        if (SkyforgePhysicalVolumeAdmissionStage.active()
                || SkyforgeNativeSurfacePopulationStage.hasActiveBinding()
                || SkyforgeComposedCaveStage.active()
                || SkyforgeNativeInteriorPopulationStage.active()) {
            SkyforgeAutomatedAcceptanceHarness.fail(
                    event.getServer(),
                    "SF-IMP-0069 reload unexpectedly reinstalled a mutation lifecycle binding");
            return;
        }

        BlockPos position = expectedSample.position();
        String initialState = level.getBlockState(position).toString();
        if (!initialState.equals(expectedSample.stateText())) {
            SkyforgeAutomatedAcceptanceHarness.fail(
                    event.getServer(),
                    "SF-IMP-0069 persisted fluid mismatch before reload propagation at " + position
                            + ": expected=" + expectedSample.stateText() + ", actual=" + initialState);
            return;
        }

        SkyIslandWorldVolumeId volumeId = expectedSample.volumeId();
        var before = SkyforgeGeneratedFluidPropagationStage.snapshot(level, volumeId);
        boolean sampleTracked = SkyforgeGeneratedFluidPropagationStage.trackedFluids(level, volumeId).stream()
                .anyMatch(fluid -> fluid.position() == position.asLong());
        if (!sampleTracked || before.trackedPositions() <= 0) {
            SkyforgeAutomatedAcceptanceHarness.fail(
                    event.getServer(),
                    "SF-IMP-0069 saved B world lost generated-fluid provenance for " + position
                            + ": trackedPositions=" + before.trackedPositions());
            return;
        }

        if (!scheduledFreshTick) {
            var fluidState = level.getFluidState(position);
            if (fluidState.isEmpty()) {
                SkyforgeAutomatedAcceptanceHarness.fail(
                        event.getServer(),
                        "SF-IMP-0069 persisted provenance sample contains no fluid after reload: " + position);
                return;
            }
            Fluid fluid = fluidState.getType();
            level.scheduleTick(position, fluid, 1);
            scheduledFreshTick = true;
            return;
        }

        var after = SkyforgeGeneratedFluidPropagationStage.snapshot(level, volumeId);
        if (after.propagationTicks() <= 0) {
            return;
        }
        if (after.scheduledOutsideOwner() != 0 || after.rejectedBoundaryWrites() != 0) {
            SkyforgeAutomatedAcceptanceHarness.fail(
                    event.getServer(),
                    "SF-IMP-0069 post-reload generated fluid escaped its exact-volume contract: " + after);
            return;
        }

        String finalState = level.getBlockState(position).toString();
        if (level.getFluidState(position).isEmpty()) {
            SkyforgeAutomatedAcceptanceHarness.fail(
                    event.getServer(),
                    "SF-IMP-0069 reload propagation removed the persisted sample fluid at " + position);
            return;
        }

        clientExpectation = new ClientExpectation(position.asLong(), finalState);
        proofComplete = true;
        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0069 RELOAD SERVER PASS: position=" + position
                        + ", initialState=" + initialState
                        + ", finalState=" + finalState
                        + ", trackedPositions=" + after.trackedPositions()
                        + ", freshPropagationTicks=" + after.propagationTicks()
                        + ", provenanceDigest=" + Long.toUnsignedString(after.digest(), 16)
                        + ". No mutation lifecycle was reinstalled.");

        SkyforgeAutomatedAcceptanceHarness.record(
                java.util.Map.ofEntries(
                        java.util.Map.entry("reloadServerPass", true),
                        java.util.Map.entry("mutationBindingsAbsent", true),
                        java.util.Map.entry("persistedFluidPos", Long.toString(position.asLong())),
                        java.util.Map.entry("persistedInitialFluidState", initialState),
                        java.util.Map.entry("persistedFluidState", finalState),
                        java.util.Map.entry("persistedTrackedPositions", after.trackedPositions()),
                        java.util.Map.entry("reloadPropagationTicks", after.propagationTicks()),
                        java.util.Map.entry("scheduledOutsideOwner", after.scheduledOutsideOwner()),
                        java.util.Map.entry("rejectedBoundaryWrites", after.rejectedBoundaryWrites()),
                        java.util.Map.entry("reloadProvenanceDigest", Long.toUnsignedString(after.digest(), 16))));
    }

    static ClientExpectation clientExpectation() {
        return clientExpectation;
    }

    private static ExpectedSample readExpectedSample() {
        String configured = System.getProperty(EXPECTED_RESULT_PROPERTY);
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("SF-IMP-0069 reload requires " + EXPECTED_RESULT_PROPERTY);
        }
        Path path = Path.of(configured).toAbsolutePath().normalize();
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to read SF-IMP-0069 expected result " + path, exception);
        }
        if (!"PASS".equals(properties.getProperty("status"))) {
            throw new IllegalStateException("SF-IMP-0069 reload source result did not report PASS: " + path);
        }

        String packedText = Objects.requireNonNull(
                properties.getProperty("sampleFluidPos"),
                "SF-IMP-0069 B result omitted sampleFluidPos");
        String stateText = Objects.requireNonNull(
                properties.getProperty("sampleFluidState"),
                "SF-IMP-0069 B result omitted sampleFluidState");
        var volume = SkyforgeNeoForge1211ProductionComposedCaveFixture.single().volume();
        try {
            return new ExpectedSample(
                    BlockPos.of(Long.parseLong(packedText)),
                    stateText,
                    volume.id());
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(
                    "invalid SF-IMP-0069 sampleFluidPos in " + path + ": " + packedText,
                    exception);
        }
    }

    private record ExpectedSample(
            BlockPos position,
            String stateText,
            SkyIslandWorldVolumeId volumeId) {
        private ExpectedSample {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(stateText, "stateText");
            Objects.requireNonNull(volumeId, "volumeId");
        }
    }

    record ClientExpectation(long position, String expectedStateText) {
        ClientExpectation {
            Objects.requireNonNull(expectedStateText, "expectedStateText");
        }
    }
}
