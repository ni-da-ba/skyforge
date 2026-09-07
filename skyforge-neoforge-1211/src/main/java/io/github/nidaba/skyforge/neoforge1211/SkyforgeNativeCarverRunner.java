package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;

/**
 * Executes final-registry biome AIR carvers against one already-admitted exact Skyforge LevelChunk.
 *
 * <p>One-shot callers and the production admitted-volume lifecycle share the same
 * {@link SkyforgeNativeCarverCursor}. This facade simply drains that cursor without yielding;
 * production code advances it in bounded service quanta.
 */
final class SkyforgeNativeCarverRunner {
    private SkyforgeNativeCarverRunner() {}

    static Result carveAir(
            ServerLevel level,
            NoiseBasedChunkGenerator generator,
            SkyforgeExactVolumeBiomeResolver biomeResolver,
            SkyIslandWorldVolumeId volumeId,
            LevelChunk targetChunk,
            BlockPos biomeSample,
            int targetMinimumY,
            int targetMaximumY) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(generator, "generator");
        Objects.requireNonNull(biomeResolver, "biomeResolver");
        Objects.requireNonNull(volumeId, "volumeId");
        Objects.requireNonNull(targetChunk, "targetChunk");
        Objects.requireNonNull(biomeSample, "biomeSample");

        var cursor = new SkyforgeNativeCarverCursor(
                level,
                generator,
                biomeResolver,
                volumeId,
                targetChunk,
                biomeSample,
                targetMinimumY,
                targetMaximumY);
        while (!cursor.complete()) {
            cursor.advance(targetChunk, Integer.MAX_VALUE);
        }
        return cursor.result();
    }

    record Result(
            ResourceKey<Biome> biomeKey,
            int configuredCarvers,
            int startChecks,
            int startChunks,
            int carveCalls,
            int successfulCalls,
            int sampledHeights,
            int minimumNativeSampleY,
            int maximumNativeSampleY,
            int minimumMappedSampleY,
            int maximumMappedSampleY,
            int mappedOutsideTarget,
            int standaloneAnchors,
            int writeAttempts,
            int acceptedWrites,
            int rejectedWrites,
            int changedBlocks,
            long transformDigest,
            long changedPositionDigest,
            List<ResourceLocation> startedCarverKeys) {
        Result {
            Objects.requireNonNull(biomeKey, "biomeKey");
            startedCarverKeys = List.copyOf(Objects.requireNonNull(startedCarverKeys, "startedCarverKeys"));
        }
    }
}
