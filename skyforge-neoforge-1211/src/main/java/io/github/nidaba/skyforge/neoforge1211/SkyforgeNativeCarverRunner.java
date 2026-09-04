package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;

/**
 * Executes final-registry biome AIR carvers against one already-admitted exact Skyforge LevelChunk.
 *
 * <p>The start-chunk/random loop mirrors vanilla's 17x17 carver neighborhood without requesting any
 * source chunk. The active exact-volume biome supplies the registry-native carver list. Carver
 * HeightProviders consume native randomness first; the return-boundary mixin then maps sampled Y
 * into the caller-supplied Skyforge interior frame. Direct LevelChunk writes are fenced separately.
 *
 * <p>SF-IMP-0061 deliberately uses Minecraft's disabled-air aquifer adapter. Aquifer/water-table
 * semantics are not silently inferred from a floating island's altitude and remain a later
 * hydrology milestone.
 */
final class SkyforgeNativeCarverRunner {
    private static final int VANILLA_SOURCE_RADIUS_CHUNKS = 8;
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

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
        if (targetChunk.getLevel() != level) {
            throw new IllegalArgumentException("native carver target chunk belongs to another level");
        }
        if (targetMaximumY < targetMinimumY) {
            throw new IllegalArgumentException("native carver target vertical frame must be ordered");
        }

        Holder<Biome> domainBiome = resolveBiome(level, biomeResolver, volumeId, biomeSample);
        var configuredCarverRegistry =
                level.registryAccess().registryOrThrow(Registries.CONFIGURED_CARVER);
        Iterable<Holder<ConfiguredWorldCarver<?>>> carvers =
                domainBiome.value().getGenerationSettings().getCarvers(GenerationStep.Carving.AIR);

        // CarvingContext.topMaterial is not needed by the interior-only acceptance fixture because
        // its Skyforge surface is raw dirt rather than grass/mycelium. The noise chunk is therefore
        // intentionally absent in this first deferred-carver seam.
        CarvingContext context = new CarvingContext(
                generator,
                level.registryAccess(),
                targetChunk.getHeightAccessorForGeneration(),
                null,
                level.getChunkSource().randomState(),
                generator.generatorSettings().value().surfaceRule());

        Aquifer aquifer = Aquifer.createDisabled((x, y, z) ->
                new Aquifer.FluidStatus(Integer.MIN_VALUE, Blocks.AIR.defaultBlockState()));
        CarvingMask mask = new CarvingMask(targetChunk.getHeight(), targetChunk.getMinBuildHeight());

        int configuredCarvers = 0;
        for (Holder<ConfiguredWorldCarver<?>> ignored : carvers) {
            configuredCarvers++;
        }
        if (configuredCarvers == 0) {
            return new Result(
                    domainBiome.unwrapKey().orElseThrow(),
                    0, 0, 0, 0, 0, 0,
                    Integer.MIN_VALUE, Integer.MIN_VALUE,
                    Integer.MIN_VALUE, Integer.MIN_VALUE,
                    0, 0,
                    0, 0, 0, 0,
                    FNV_OFFSET_BASIS,
                    FNV_OFFSET_BASIS,
                    List.of());
        }

        int startChecks = 0;
        int startChunks = 0;
        int carveCalls = 0;
        int successfulCalls = 0;
        int sampledHeights = 0;
        int minimumNativeSampleY = Integer.MAX_VALUE;
        int maximumNativeSampleY = Integer.MIN_VALUE;
        int minimumMappedSampleY = Integer.MAX_VALUE;
        int maximumMappedSampleY = Integer.MIN_VALUE;
        int mappedOutsideTarget = 0;
        int standaloneAnchors = 0;
        int writeAttempts = 0;
        int acceptedWrites = 0;
        int rejectedWrites = 0;
        int changedBlocks = 0;
        long transformDigest = FNV_OFFSET_BASIS;
        long changedPositionDigest = FNV_OFFSET_BASIS;
        List<ResourceLocation> startedCarverKeys = new ArrayList<>();

        ChunkPos targetPos = targetChunk.getPos();
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));

        for (int dx = -VANILLA_SOURCE_RADIUS_CHUNKS; dx <= VANILLA_SOURCE_RADIUS_CHUNKS; dx++) {
            for (int dz = -VANILLA_SOURCE_RADIUS_CHUNKS; dz <= VANILLA_SOURCE_RADIUS_CHUNKS; dz++) {
                ChunkPos sourcePos = new ChunkPos(targetPos.x + dx, targetPos.z + dz);
                int carverIndex = 0;
                for (Holder<ConfiguredWorldCarver<?>> holder : carvers) {
                    ResourceLocation key = holder.unwrapKey()
                            .map(ResourceKey::location)
                            .orElseGet(() -> configuredCarverRegistry.getKey(holder.value()));
                    if (key == null) {
                        throw new IllegalStateException(
                                "biome carver settings contain a configured carver absent from the final registry");
                    }

                    random.setLargeFeatureSeed(
                            volumeId.archipelagoRootSeed() + (long) carverIndex,
                            sourcePos.x,
                            sourcePos.z);
                    startChecks++;
                    ConfiguredWorldCarver<?> configured = holder.value();
                    if (!configured.isStartChunk(random)) {
                        carverIndex++;
                        continue;
                    }

                    startChunks++;
                    startedCarverKeys.add(key);
                    SkyforgeCarverVerticalFrame.Snapshot verticalSnapshot;
                    SkyforgeCarverExecutionStage.Snapshot writeSnapshot;
                    boolean carved;
                    var postProcessing = SkyforgeDeferredPopulationPostProcessingBridge.open(level);
                    try {
                        try (var domain = SkyforgeGenerationDomainStage.openIsland(volumeId);
                                var execution = SkyforgeCarverExecutionStage.open(volumeId, targetPos);
                                var vertical = SkyforgeCarverVerticalFrame.open(
                                        level, volumeId, targetMinimumY, targetMaximumY)) {
                            domain.requireActive();
                            execution.requireActive();
                            vertical.requireActive();

                            carveCalls++;
                            carved = configured.carve(
                                    context,
                                    targetChunk,
                                    position -> resolveBiome(level, biomeResolver, volumeId, position),
                                    random,
                                    aquifer,
                                    sourcePos,
                                    mask);
                            SkyforgeDeferredPopulationPostProcessingBridge.flushIfActive();
                            verticalSnapshot = vertical.snapshot();
                            writeSnapshot = execution.snapshot();
                        }
                    } finally {
                        postProcessing.close();
                    }
                    if (carved) {
                        successfulCalls++;
                    }

                    sampledHeights = Math.addExact(sampledHeights, verticalSnapshot.sampledHeights());
                    if (verticalSnapshot.sampledHeights() > 0) {
                        minimumNativeSampleY = Math.min(
                                minimumNativeSampleY, verticalSnapshot.minimumNativeSampleY());
                        maximumNativeSampleY = Math.max(
                                maximumNativeSampleY, verticalSnapshot.maximumNativeSampleY());
                        minimumMappedSampleY = Math.min(
                                minimumMappedSampleY, verticalSnapshot.minimumMappedSampleY());
                        maximumMappedSampleY = Math.max(
                                maximumMappedSampleY, verticalSnapshot.maximumMappedSampleY());
                    }
                    mappedOutsideTarget = Math.addExact(
                            mappedOutsideTarget, verticalSnapshot.mappedSamplesOutsideTarget());
                    standaloneAnchors = Math.addExact(
                            standaloneAnchors, verticalSnapshot.standaloneAnchors());
                    writeAttempts = Math.addExact(writeAttempts, writeSnapshot.writeAttempts());
                    acceptedWrites = Math.addExact(acceptedWrites, writeSnapshot.acceptedWriteAttempts());
                    rejectedWrites = Math.addExact(rejectedWrites, writeSnapshot.rejectedWriteAttempts());
                    changedBlocks = Math.addExact(changedBlocks, writeSnapshot.changedBlocks());
                    transformDigest = mix(transformDigest, verticalSnapshot.transformDigest());
                    changedPositionDigest = mix(changedPositionDigest, writeSnapshot.changedPositionDigest());
                    carverIndex++;
                }
            }
        }

        return new Result(
                domainBiome.unwrapKey().orElseThrow(),
                configuredCarvers,
                startChecks,
                startChunks,
                carveCalls,
                successfulCalls,
                sampledHeights,
                sampledHeights == 0 ? Integer.MIN_VALUE : minimumNativeSampleY,
                sampledHeights == 0 ? Integer.MIN_VALUE : maximumNativeSampleY,
                sampledHeights == 0 ? Integer.MIN_VALUE : minimumMappedSampleY,
                sampledHeights == 0 ? Integer.MIN_VALUE : maximumMappedSampleY,
                mappedOutsideTarget,
                standaloneAnchors,
                writeAttempts,
                acceptedWrites,
                rejectedWrites,
                changedBlocks,
                transformDigest,
                changedPositionDigest,
                List.copyOf(startedCarverKeys));
    }

    private static Holder<Biome> resolveBiome(
            ServerLevel level,
            SkyforgeExactVolumeBiomeResolver resolver,
            SkyIslandWorldVolumeId volumeId,
            BlockPos position) {
        ResourceKey<Biome> key = Objects.requireNonNull(
                resolver.resolve(volumeId, position.getX(), position.getY(), position.getZ()),
                "carver biome resolver returned null");
        return level.registryAccess()
                .registryOrThrow(Registries.BIOME)
                .getHolder(key)
                .orElseThrow(() -> new IllegalStateException(
                        "exact-volume carver biome is absent from final registry: " + key.location()));
    }

    private static long mix(long digest, long value) {
        long mixed = digest;
        for (int shift = 0; shift < Long.SIZE; shift += Byte.SIZE) {
            mixed ^= (value >>> shift) & 0xffL;
            mixed *= FNV_PRIME;
        }
        return mixed;
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
