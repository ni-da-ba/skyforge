package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;

/** Development-only acceptance observer for SF-IMP-0058 persistent biome presentation. */
final class SkyforgeNeoForge1211BiomePresentationDevRuntime {
    private static final int PROOF_X = 8;
    private static final int PROOF_Z = 8;
    private static final int SAME_COLUMN_GAP_Y = 150;
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211BiomePresentationDevRuntime.class.getName());

    private static boolean proofComplete;

    private SkyforgeNeoForge1211BiomePresentationDevRuntime() {}

    static synchronized void observeLoaded(ServerLevel level) {
        if (!Boolean.getBoolean(SkyforgePersistentBiomePresentationStage.PROOF_PROPERTY) || proofComplete) {
            return;
        }
        var volumes = SkyforgeNeoForge1211PhysicalAdmissionDevRuntime.catalog().volumes();
        SkyIslandWorldVolumeId lowerId = volumes.get(0).id();
        SkyIslandWorldVolumeId upperId = volumes.get(1).id();
        var lower = SkyforgePhysicalVolumeAdmissionStage.snapshot(lowerId);
        var upper = SkyforgePhysicalVolumeAdmissionStage.snapshot(upperId);
        if (lower.state() != SkyforgePhysicalVolumeAdmissionState.REJECTED
                || upper.state() != SkyforgePhysicalVolumeAdmissionState.ADMITTED
                || !SkyforgePhysicalVolumeAdmissionStage.pendingCatchupChunks(upperId).isEmpty()
                || !SkyforgePhysicalVolumeAdmissionStage.pendingBiomePresentationChunks(upperId).isEmpty()) {
            return;
        }
        if (!SkyforgePhysicalVolumeAdmissionStage.pendingBiomePresentationChunks(lowerId).isEmpty()) {
            throw new IllegalStateException("SF-IMP-0058 rejected lower volume retained biome-presentation work");
        }

        var originChunk = level.getChunkSource().getChunkNow(0, 0);
        if (originChunk == null) {
            return;
        }
        int upperSurfaceY = SkyforgeNeoForge1211SurfaceStage.queryBaseHeightClaim(
                        upperId,
                        PROOF_X,
                        PROOF_Z,
                        Heightmap.Types.WORLD_SURFACE_WG,
                        level.getMinBuildHeight(),
                        level.getHeight())
                .orElseThrow(() -> new IllegalStateException("SF-IMP-0058 upper proof volume has no origin surface"))
                .height();
        BlockPos upperSurfaceBlock = new BlockPos(PROOF_X, upperSurfaceY - 1, PROOF_Z);
        BlockPos standingPosition = new BlockPos(PROOF_X, upperSurfaceY, PROOF_Z);
        ResourceKey<Biome> storedSurfaceBiome = storedBiomeKey(originChunk, upperSurfaceBlock);
        ResourceKey<Biome> storedStandingBiome = storedBiomeKey(originChunk, standingPosition);
        if (!storedSurfaceBiome.equals(Biomes.TAIGA) || !storedStandingBiome.equals(Biomes.TAIGA)) {
            throw new IllegalStateException("SF-IMP-0058 upper island biome was not persisted through its surface/HUD cell: "
                    + "surfacePosition=" + upperSurfaceBlock
                    + ", surfaceBiome=" + storedSurfaceBiome.location()
                    + ", standingPosition=" + standingPosition
                    + ", standingBiome=" + storedStandingBiome.location());
        }

        BlockPos gap = new BlockPos(PROOF_X, SAME_COLUMN_GAP_Y, PROOF_Z);
        boolean upperOwnsGap = SkyforgeNeoForge1211SurfaceStage.isSolidOwnedBy(
                        upperId,
                        gap.getX(),
                        gap.getY(),
                        gap.getZ())
                .orElseThrow();
        boolean lowerOwnsGap = SkyforgeNeoForge1211SurfaceStage.isSolidOwnedBy(
                        lowerId,
                        gap.getX(),
                        gap.getY(),
                        gap.getZ())
                .orElseThrow();
        if (upperOwnsGap || lowerOwnsGap) {
            throw new IllegalStateException("SF-IMP-0058 same-column preservation sample is owned by a Skyforge volume");
        }

        int quartX = QuartPos.fromBlock(gap.getX());
        int quartY = QuartPos.fromBlock(gap.getY());
        int quartZ = QuartPos.fromBlock(gap.getZ());
        ResourceKey<Biome> storedGapBiome = storedBiomeKey(originChunk, gap);
        ResourceKey<Biome> expectedGapBiome = level.getChunkSource()
                .getGenerator()
                .getBiomeSource()
                .getNoiseBiome(
                        quartX,
                        quartY,
                        quartZ,
                        level.getChunkSource().randomState().sampler())
                .unwrapKey()
                .orElseThrow(() -> new IllegalStateException("native generator returned an unregistered biome holder"));
        if (!storedGapBiome.equals(expectedGapBiome)) {
            throw new IllegalStateException("SF-IMP-0058 changed an unowned same-column biome cell: position="
                    + gap + ", expected=" + expectedGapBiome.location() + ", actual=" + storedGapBiome.location());
        }

        proofComplete = true;
        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0058 BIOME PRESENTATION PASS: upper={volume=" + upperId.path()
                        + ", surfaceY=" + upperSurfaceY
                        + ", surfaceBiome=" + storedSurfaceBiome.location()
                        + ", standingBiome=" + storedStandingBiome.location()
                        + ", pendingPresentation=0}, sameColumnGap={y=" + SAME_COLUMN_GAP_Y
                        + ", storedBiome=" + storedGapBiome.location()
                        + ", nativeExpectedBiome=" + expectedGapBiome.location()
                        + ", preserved=true}, lower={volume=" + lowerId.path()
                        + ", state=" + lower.state()
                        + ", pendingPresentation=0}. Exact-volume biome identity persisted through the player's surface-air "
                        + "cell without converting vertically unrelated base-world cells.");
    }

    private static ResourceKey<Biome> storedBiomeKey(
            net.minecraft.world.level.chunk.LevelChunk chunk,
            BlockPos position) {
        return chunk.getNoiseBiome(
                        QuartPos.fromBlock(position.getX()),
                        QuartPos.fromBlock(position.getY()),
                        QuartPos.fromBlock(position.getZ()))
                .unwrapKey()
                .orElseThrow(() -> new IllegalStateException("chunk biome storage returned an unregistered biome holder"));
    }
}
