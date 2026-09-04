package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandTerrainProfile;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * SF-IMP-0063 stacked-volume proof for FLUID_SPRINGS coordinate mapping and persisted asynchronous
 * provenance isolation.
 */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID)
final class SkyforgeNeoForge1211FluidSpringsStackedDevRuntime {
    static final String ENABLE_PROPERTY = "skyforge.dev.fluidSpringsStacked";

    private static final ChunkPos PROOF_CHUNK = new ChunkPos(0, 0);
    private static final int PROOF_X = 8;
    private static final int PROOF_Z = 8;
    private static final int NATIVE_PROBE_Y = 30;
    private static final ResourceLocation FRAME_PROBE_KEY =
            ResourceLocation.fromNamespaceAndPath("skyforge", "sf_imp_0063_stacked_fluid_probe");
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211FluidSpringsStackedDevRuntime.class.getName());

    private static AutoCloseable persistentBinding;
    private static boolean proofStarted;
    private static boolean proofComplete;

    private SkyforgeNeoForge1211FluidSpringsStackedDevRuntime() {}

    static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    static synchronized void installFromSystemProperty() {
        if (!enabled() || persistentBinding != null) {
            return;
        }
        if (SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()) {
            throw new IllegalStateException("cannot install SF-IMP-0063 stacked proof over another Skyforge binding");
        }
        persistentBinding = SkyforgeNeoForge1211SurfaceStage.install(
                new SkyforgeNeoForge1211ChunkAdapter(
                        SkyforgeNeoForge1211BiomePopulationDevRuntime.catalog(),
                        SkyIslandTerrainProfile.reference(),
                        new SkyforgeMinecraftBlockPalette()),
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()));
        LOGGER.log(
                System.Logger.Level.INFO,
                "Skyforge SF-IMP-0063 stacked FLUID_SPRINGS provenance specimen enabled.");
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (!enabled() || proofStarted || proofComplete) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (level.dimension().equals(Level.OVERWORLD)
                    && level.getChunkSource().getChunkNow(PROOF_CHUNK.x, PROOF_CHUNK.z) != null) {
                prove(level);
            }
        }
    }

    private static synchronized void prove(ServerLevel level) {
        if (proofStarted || proofComplete) {
            return;
        }
        var volumes = SkyforgeNeoForge1211BiomePopulationDevRuntime.catalog().volumes();
        if (volumes.size() != 2) {
            throw new IllegalStateException("SF-IMP-0063 stacked proof requires exactly two fixture volumes");
        }
        SkyIslandWorldVolume lower = volumes.get(0);
        SkyIslandWorldVolume upper = volumes.get(1);
        BlockPos lowerSample = requireOwnerSample(level, lower);
        BlockPos upperSample = requireOwnerSample(level, upper);
        proofStarted = true;

        VolumeProof lowerProof = proveVolume(level, lower, upper, lowerSample, upperSample);
        VolumeProof upperProof = proveVolume(level, upper, lower, upperSample, lowerSample);
        if (lowerProof.mappedY() == upperProof.mappedY()) {
            throw new IllegalStateException("SF-IMP-0063 stacked spring frames collapsed distinct Y domains");
        }

        proofComplete = true;
        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0063 FLUID SPRINGS STACKED PASS: nativeProbe={x=" + PROOF_X
                        + ", y=" + NATIVE_PROBE_Y + ", z=" + PROOF_Z + "}, lowerMappedY="
                        + lowerProof.mappedY() + ", upperMappedY=" + upperProof.mappedY()
                        + ", ownerPropagationAccepted=true, foreignPropagationRejected=true"
                        + ", provenanceVolumeIsolation=true.");

        SkyforgeAutomatedAcceptanceHarness.completeServerCase(
                level.getServer(),
                java.util.Map.of(
                        "lowerMappedY", lowerProof.mappedY(),
                        "upperMappedY", upperProof.mappedY(),
                        "ownerPropagationAccepted", true,
                        "foreignPropagationRejected", true,
                        "provenanceVolumeIsolation", true));
    }

    private static VolumeProof proveVolume(
            ServerLevel level,
            SkyIslandWorldVolume owner,
            SkyIslandWorldVolume foreign,
            BlockPos ownerSample,
            BlockPos foreignSample) {
        SkyIslandWorldVolumeId ownerId = owner.id();
        SkyIslandWorldVolumeId foreignId = foreign.id();
        var operation = SkyforgePopulationOperation.create(
                ownerId,
                PROOF_CHUNK,
                FRAME_PROBE_KEY,
                GenerationStep.Decoration.FLUID_SPRINGS.ordinal(),
                0);

        BlockPos mapped;
        try (var domain = SkyforgeGenerationDomainStage.openIsland(ownerId);
                var execution = SkyforgePopulationExecutionStage.open(operation, 0);
                var vertical = SkyforgeVerticalPlacementFrame.open(level, operation);
                var capture = SkyforgeGeneratedFluidPropagationStage.openPopulation(level, operation)) {
            domain.requireActive();
            execution.requireActive();
            vertical.requireActive();
            capture.requireActive();

            mapped = SkyforgeVerticalPlacementFrame.mapHeightRangePosition(
                    new BlockPos(PROOF_X, NATIVE_PROBE_Y, PROOF_Z));
            if (!SkyforgeNeoForge1211SurfaceStage.isSolidOwnedBy(
                            ownerId, mapped.getX(), mapped.getY(), mapped.getZ())
                    .orElse(false)) {
                throw new IllegalStateException("SF-IMP-0063 stacked frame mapped outside owner terrain: " + mapped);
            }
            if (SkyforgeNeoForge1211SurfaceStage.isSolidOwnedBy(
                            foreignId, mapped.getX(), mapped.getY(), mapped.getZ())
                    .orElse(false)) {
                throw new IllegalStateException("SF-IMP-0063 stacked frame mapped into foreign terrain: " + mapped);
            }
            SkyforgeGeneratedFluidPropagationStage.observeScheduledTick(mapped, Fluids.WATER);
        }

        var tracked = SkyforgeGeneratedFluidPropagationStage.trackedFluids(level, ownerId);
        boolean provenancePresent = tracked.stream().anyMatch(fluid -> fluid.position() == mapped.asLong());
        if (!provenancePresent) {
            throw new IllegalStateException(
                    "SF-IMP-0063 stacked proof failed to persist owner-local generated-fluid provenance");
        }

        SkyforgeGeneratedFluidPropagationStage.beginFluidTick(
                level,
                mapped,
                Fluids.WATER.defaultFluidState());
        try {
            if (!SkyforgeGeneratedFluidPropagationStage.propagationActive()) {
                throw new IllegalStateException(
                        "SF-IMP-0063 stacked proof failed to reopen owner provenance on asynchronous tick");
            }
            if (!SkyforgeGeneratedFluidPropagationStage.isVisible(mapped)
                    || !SkyforgeGeneratedFluidPropagationStage.acceptWrite(mapped)
                    || !SkyforgeGeneratedFluidPropagationStage.isVisible(ownerSample)
                    || !SkyforgeGeneratedFluidPropagationStage.acceptWrite(ownerSample)) {
                throw new IllegalStateException("SF-IMP-0063 stacked owner propagation scope rejected owner terrain");
            }
            if (SkyforgeGeneratedFluidPropagationStage.isVisible(foreignSample)
                    || SkyforgeGeneratedFluidPropagationStage.acceptWrite(foreignSample)) {
                throw new IllegalStateException(
                        "SF-IMP-0063 stacked generated-fluid provenance admitted foreign volume terrain");
            }
        } finally {
            SkyforgeGeneratedFluidPropagationStage.endFluidTick();
        }

        return new VolumeProof(ownerId, mapped.getY());
    }

    private static BlockPos requireOwnerSample(
            ServerLevel level,
            SkyIslandWorldVolume volume) {
        int surfaceY = SkyforgeNeoForge1211SurfaceStage.queryBaseHeightClaim(
                        volume.id(),
                        PROOF_X,
                        PROOF_Z,
                        Heightmap.Types.WORLD_SURFACE_WG,
                        level.getMinBuildHeight(),
                        level.getHeight())
                .orElseThrow(() -> new IllegalStateException(
                        "SF-IMP-0063 stacked volume has no origin surface: " + volume.id().path()))
                .height();
        for (int y = surfaceY - 1; y >= Math.ceil(volume.bounds().minimumY()); y--) {
            BlockPos candidate = new BlockPos(PROOF_X, y, PROOF_Z);
            if (SkyforgeNeoForge1211SurfaceStage.isSolidOwnedBy(
                            volume.id(), candidate.getX(), candidate.getY(), candidate.getZ())
                    .orElse(false)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "SF-IMP-0063 stacked proof found no owner sample for " + volume.id().path());
    }

    private record VolumeProof(SkyIslandWorldVolumeId volumeId, int mappedY) {}
}
