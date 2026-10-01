package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandTerrainProfile;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Final SF-IMP-0059 proof that stacked exact volumes remain independent underground domains. */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID)
final class SkyforgeNeoForge1211UndergroundStackedDevRuntime {
    static final String ENABLE_PROPERTY = "skyforge.dev.undergroundPlacementStacked";
    private static final ChunkPos PROOF_CHUNK = new ChunkPos(0, 0);
    private static final int PROOF_X = 8;
    private static final int PROOF_Z = 8;
    private static final ResourceLocation FOREIGN_VETO_KEY =
            ResourceLocation.fromNamespaceAndPath("skyforge", "sf_imp_0059_foreign_veto");
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211UndergroundStackedDevRuntime.class.getName());

    private static AutoCloseable persistentBinding;
    private static boolean proofStarted;
    private static boolean proofComplete;

    private SkyforgeNeoForge1211UndergroundStackedDevRuntime() {}

    static synchronized void installFromSystemProperty() {
        if (!enabled() || persistentBinding != null) {
            return;
        }
        if (SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()) {
            throw new IllegalStateException("cannot install SF-IMP-0059 stacked proof over another Skyforge binding");
        }
        persistentBinding = SkyforgeNeoForge1211SurfaceStage.install(
                new SkyforgeNeoForge1211ChunkAdapter(
                        SkyforgeNeoForge1211BiomePopulationDevRuntime.catalog(),
                        SkyIslandTerrainProfile.reference(),
                        new SkyforgeMinecraftBlockPalette()),
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()));
        LOGGER.log(
                System.Logger.Level.INFO,
                "Skyforge SF-IMP-0059 stacked underground specimen enabled. Create a NEW disposable Skyforge "
                        + "Development world. The accepted SF-IMP-0054 forest/taiga tablelands at the same X/Z are "
                        + "used only as geometry/biome fixtures; UNDERGROUND_ORES is replayed independently for both.");
    }

    private static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (!enabled() || proofStarted || proofComplete) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (!level.dimension().equals(Level.OVERWORLD)) {
                continue;
            }
            observeLoaded(level);
        }
    }

    private static synchronized void observeLoaded(ServerLevel level) {
        if (proofStarted || proofComplete) {
            return;
        }
        var chunk = level.getChunkSource().getChunkNow(PROOF_CHUNK.x, PROOF_CHUNK.z);
        if (chunk == null) {
            return;
        }
        var volumes = SkyforgeNeoForge1211BiomePopulationDevRuntime.catalog().volumes();
        if (volumes.size() != 2) {
            throw new IllegalStateException("SF-IMP-0059 stacked proof requires exactly two accepted 0054 volumes");
        }
        SkyIslandWorldVolume lowerVolume = volumes.get(0);
        SkyIslandWorldVolume upperVolume = volumes.get(1);
        int lowerSurfaceY = surfaceY(level, lowerVolume.id());
        int upperSurfaceY = surfaceY(level, upperVolume.id());
        if (lowerSurfaceY == upperSurfaceY) {
            throw new IllegalStateException("SF-IMP-0059 stacked fixture did not resolve distinct vertical surfaces");
        }

        BlockPos lowerOwnerSample = requireOwnerSample(level, lowerVolume, lowerSurfaceY);
        BlockPos upperOwnerSample = requireOwnerSample(level, upperVolume, upperSurfaceY);
        proofStarted = true;

        requireForeignVeto(level, lowerVolume.id(), lowerOwnerSample, upperOwnerSample);
        requireForeignVeto(level, upperVolume.id(), upperOwnerSample, lowerOwnerSample);

        VolumeProof lower = populateAndProtectForeign(
                level,
                lowerVolume,
                upperVolume,
                lowerSurfaceY,
                Biomes.FOREST);
        VolumeProof upper = populateAndProtectForeign(
                level,
                upperVolume,
                lowerVolume,
                upperSurfaceY,
                Biomes.TAIGA);

        proofComplete = true;
        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0059 UNDERGROUND STACKED PASS: lower={volume=" + lower.volumeId().path()
                        + ", frameY=[" + lower.minimumEnvelopeY() + "," + lower.maximumEnvelopeY() + "]"
                        + ", successfulFeatures=" + lower.successfulFeatures()
                        + ", acceptedWritePreflights=" + lower.acceptedWritePreflights()
                        + ", mappedOutsideVolume=" + lower.mappedOutsideVolume()
                        + ", digest=" + Long.toUnsignedString(lower.transformDigest(), 16)
                        + ", foreignPreserved=true, foreignVeto=true}, upper={volume=" + upper.volumeId().path()
                        + ", frameY=[" + upper.minimumEnvelopeY() + "," + upper.maximumEnvelopeY() + "]"
                        + ", successfulFeatures=" + upper.successfulFeatures()
                        + ", acceptedWritePreflights=" + upper.acceptedWritePreflights()
                        + ", mappedOutsideVolume=" + upper.mappedOutsideVolume()
                        + ", digest=" + Long.toUnsignedString(upper.transformDigest(), 16)
                        + ", foreignPreserved=true, foreignVeto=true}. Both registry-native underground streams were "
                        + "mapped into their own exact Y frames; the other stacked island remained byte-for-byte "
                        + "unchanged during each replay and its solid coordinates were rejected by the generic "
                        + "population write preflight.");
    }

    private static VolumeProof populateAndProtectForeign(
            ServerLevel level,
            SkyIslandWorldVolume ownerVolume,
            SkyIslandWorldVolume foreignVolume,
            int ownerSurfaceY,
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.biome.Biome> biomeKey) {
        SkyIslandWorldVolumeId ownerId = ownerVolume.id();
        List<OwnedBlockState> foreignBefore = captureOwnedStates(level, foreignVolume);
        int minimumEnvelopeY = (int) Math.ceil(ownerVolume.bounds().minimumY());
        int maximumEnvelopeY = (int) Math.floor(ownerVolume.bounds().maximumY());
        var resolver = (SkyforgeExactVolumeBiomeResolver) (volumeId, x, y, z) -> {
            if (!volumeId.equals(ownerId)) {
                throw new IllegalArgumentException("stacked underground proof resolved unexpected volume " + volumeId.path());
            }
            return biomeKey;
        };

        SkyforgeNativeBiomePopulationRunner.Result result;
        SkyforgeUndergroundPlacementProbe.Snapshot snapshot;
        var postProcessing = SkyforgeDeferredPopulationPostProcessingBridge.open(level);
        try {
            try (var probe = SkyforgeUndergroundPlacementProbe.open(
                    ownerId,
                    minimumEnvelopeY,
                    maximumEnvelopeY)) {
                result = SkyforgeNativeBiomePopulationRunner.populateStep(
                        level,
                        level.getChunkSource().getGenerator(),
                        resolver,
                        ownerId,
                        PROOF_CHUNK,
                        new BlockPos(PROOF_X, ownerSurfaceY - 1, PROOF_Z),
                        GenerationStep.Decoration.UNDERGROUND_ORES,
                        0);
                snapshot = probe.snapshot();
            }
        } finally {
            postProcessing.close();
        }

        int mappedOutside = Math.addExact(
                snapshot.mappedSamplesBelowEnvelope(),
                snapshot.mappedSamplesAboveEnvelope());
        if (snapshot.heightRangeSamples() <= 0
                || snapshot.transformedHeightSamples() <= 0
                || mappedOutside != 0
                || snapshot.mappedSamplesInsideEnvelope() != snapshot.heightRangeSamples()) {
            throw new IllegalStateException("stacked underground placement escaped or failed to exercise owner frame "
                    + ownerId.path());
        }
        if (result.successfulFeatures() <= 0
                || snapshot.acceptedWritePreflights() <= 0
                || snapshot.uniqueAcceptedPreflightPositions() <= 0) {
            throw new IllegalStateException("stacked underground placement produced no successful exact-owner writes for "
                    + ownerId.path());
        }

        List<OwnedBlockState> foreignAfter = captureOwnedStates(level, foreignVolume);
        if (!foreignAfter.equals(foreignBefore)) {
            throw new IllegalStateException("underground replay for " + ownerId.path()
                    + " mutated vertically stacked foreign volume " + foreignVolume.id().path());
        }

        return new VolumeProof(
                ownerId,
                minimumEnvelopeY,
                maximumEnvelopeY,
                result.successfulFeatures(),
                snapshot.acceptedWritePreflights(),
                mappedOutside,
                snapshot.heightTransformDigest());
    }

    private static void requireForeignVeto(
            ServerLevel level,
            SkyIslandWorldVolumeId ownerId,
            BlockPos ownerSample,
            BlockPos foreignSample) {
        var operation = SkyforgePopulationOperation.create(
                ownerId,
                PROOF_CHUNK,
                FOREIGN_VETO_KEY,
                GenerationStep.Decoration.UNDERGROUND_ORES.ordinal(),
                0);
        try (var domain = SkyforgeGenerationDomainStage.openIsland(ownerId);
                var execution = SkyforgePopulationExecutionStage.open(operation, 0);
                var verticalFrame = SkyforgeVerticalPlacementFrame.open(level, operation)) {
            domain.requireActive();
            execution.requireActive();
            verticalFrame.requireActive();
            if (!SkyforgeWorldGenRegionDomainBridge.canWrite(ownerSample)) {
                throw new IllegalStateException("exact owner preflight unexpectedly rejected owner sample " + ownerSample);
            }
            if (SkyforgeWorldGenRegionDomainBridge.canWrite(foreignSample)) {
                throw new IllegalStateException("exact owner preflight admitted foreign stacked sample " + foreignSample);
            }
        }
    }

    private static int surfaceY(ServerLevel level, SkyIslandWorldVolumeId volumeId) {
        return SkyforgeNeoForge1211SurfaceStage.queryBaseHeightClaim(
                        volumeId,
                        PROOF_X,
                        PROOF_Z,
                        Heightmap.Types.WORLD_SURFACE_WG,
                        level.getMinBuildHeight(),
                        level.getHeight())
                .orElseThrow(() -> new IllegalStateException(
                        "stacked underground proof volume has no origin surface: " + volumeId.path()))
                .height();
    }

    private static BlockPos requireOwnerSample(
            ServerLevel level,
            SkyIslandWorldVolume volume,
            int surfaceY) {
        for (int y = surfaceY - 1; y >= Math.ceil(volume.bounds().minimumY()); y--) {
            BlockPos candidate = new BlockPos(PROOF_X, y, PROOF_Z);
            if (SkyforgeNeoForge1211SurfaceStage.isSolidOwnedBy(
                            volume.id(), candidate.getX(), candidate.getY(), candidate.getZ())
                    .orElse(false)) {
                return candidate;
            }
        }
        throw new IllegalStateException("stacked underground proof found no owner-solid sample for "
                + volume.id().path());
    }

    private static List<OwnedBlockState> captureOwnedStates(
            ServerLevel level,
            SkyIslandWorldVolume volume) {
        int minimumY = Math.max(level.getMinBuildHeight(), (int) Math.ceil(volume.bounds().minimumY()));
        int maximumY = Math.min(
                level.getMinBuildHeight() + level.getHeight() - 1,
                (int) Math.floor(volume.bounds().maximumY()));
        List<OwnedBlockState> states = new ArrayList<>();
        for (int x = PROOF_CHUNK.getMinBlockX(); x <= PROOF_CHUNK.getMaxBlockX(); x++) {
            for (int z = PROOF_CHUNK.getMinBlockZ(); z <= PROOF_CHUNK.getMaxBlockZ(); z++) {
                for (int y = minimumY; y <= maximumY; y++) {
                    if (!SkyforgeNeoForge1211SurfaceStage.isSolidOwnedBy(volume.id(), x, y, z).orElse(false)) {
                        continue;
                    }
                    BlockPos position = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(position);
                    states.add(new OwnedBlockState(position.asLong(), state));
                }
            }
        }
        if (states.isEmpty()) {
            throw new IllegalStateException("stacked underground proof captured no owner terrain for "
                    + volume.id().path());
        }
        return List.copyOf(states);
    }

    private record OwnedBlockState(long position, BlockState state) {}

    private record VolumeProof(
            SkyIslandWorldVolumeId volumeId,
            int minimumEnvelopeY,
            int maximumEnvelopeY,
            int successfulFeatures,
            int acceptedWritePreflights,
            int mappedOutsideVolume,
            long transformDigest) {}
}
