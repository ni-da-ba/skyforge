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
 * Resumable exact-volume execution of Minecraft's final-registry AIR carver neighborhood.
 *
 * <p>The cursor is deliberately the same loop as {@link SkyforgeNativeCarverRunner}, but its
 * source-chunk/carver iteration state and shared {@link CarvingMask} survive between service
 * quanta. No source chunk is ever requested. Resuming changes only scheduling; seeds, iteration
 * order, mask state, writes, and evidence aggregation remain identical to one-shot execution.
 */
final class SkyforgeNativeCarverCursor {
    private static final int VANILLA_SOURCE_RADIUS_CHUNKS = 8;
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private final ServerLevel level;
    private final NoiseBasedChunkGenerator generator;
    private final SkyforgeExactVolumeBiomeResolver biomeResolver;
    private final SkyIslandWorldVolumeId volumeId;
    private final BlockPos biomeSample;
    private final int targetMinimumY;
    private final int targetMaximumY;
    private final ChunkPos targetPos;
    private final int targetHeight;
    private final int targetMinimumBuildY;
    private final Holder<Biome> domainBiome;
    private final net.minecraft.core.Registry<ConfiguredWorldCarver<?>> configuredCarverRegistry;
    private final List<Holder<ConfiguredWorldCarver<?>>> carvers;
    private final Aquifer aquifer;
    private final CarvingMask mask;
    private final WorldgenRandom random;

    private int sourceDx = -VANILLA_SOURCE_RADIUS_CHUNKS;
    private int sourceDz = -VANILLA_SOURCE_RADIUS_CHUNKS;
    private int carverIndex;
    private boolean complete;
    private SkyforgeNativeCarverRunner.Result result;

    private int startChecks;
    private int startChunks;
    private int carveCalls;
    private int successfulCalls;
    private int sampledHeights;
    private int minimumNativeSampleY = Integer.MAX_VALUE;
    private int maximumNativeSampleY = Integer.MIN_VALUE;
    private int minimumMappedSampleY = Integer.MAX_VALUE;
    private int maximumMappedSampleY = Integer.MIN_VALUE;
    private int mappedOutsideTarget;
    private int standaloneAnchors;
    private int writeAttempts;
    private int acceptedWrites;
    private int rejectedWrites;
    private int changedBlocks;
    private long transformDigest = FNV_OFFSET_BASIS;
    private long changedPositionDigest = FNV_OFFSET_BASIS;
    private final List<ResourceLocation> startedCarverKeys = new ArrayList<>();

    SkyforgeNativeCarverCursor(
            ServerLevel level,
            NoiseBasedChunkGenerator generator,
            SkyforgeExactVolumeBiomeResolver biomeResolver,
            SkyIslandWorldVolumeId volumeId,
            LevelChunk targetChunk,
            BlockPos biomeSample,
            int targetMinimumY,
            int targetMaximumY) {
        this.level = Objects.requireNonNull(level, "level");
        this.generator = Objects.requireNonNull(generator, "generator");
        this.biomeResolver = Objects.requireNonNull(biomeResolver, "biomeResolver");
        this.volumeId = Objects.requireNonNull(volumeId, "volumeId");
        Objects.requireNonNull(targetChunk, "targetChunk");
        this.biomeSample = Objects.requireNonNull(biomeSample, "biomeSample").immutable();
        if (targetChunk.getLevel() != level) {
            throw new IllegalArgumentException("native carver target chunk belongs to another level");
        }
        if (targetMaximumY < targetMinimumY) {
            throw new IllegalArgumentException("native carver target vertical frame must be ordered");
        }
        this.targetMinimumY = targetMinimumY;
        this.targetMaximumY = targetMaximumY;
        this.targetPos = targetChunk.getPos();
        this.targetHeight = targetChunk.getHeight();
        this.targetMinimumBuildY = targetChunk.getMinBuildHeight();

        this.domainBiome = resolveBiome(level, biomeResolver, volumeId, biomeSample);
        this.configuredCarverRegistry =
                level.registryAccess().registryOrThrow(Registries.CONFIGURED_CARVER);
        List<Holder<ConfiguredWorldCarver<?>>> configured = new ArrayList<>();
        for (Holder<ConfiguredWorldCarver<?>> holder
                : domainBiome.value().getGenerationSettings().getCarvers(GenerationStep.Carving.AIR)) {
            configured.add(holder);
        }
        this.carvers = List.copyOf(configured);
        this.aquifer = Aquifer.createDisabled((x, y, z) ->
                new Aquifer.FluidStatus(Integer.MIN_VALUE, Blocks.AIR.defaultBlockState()));
        this.mask = new CarvingMask(targetHeight, targetMinimumBuildY);
        this.random = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));

        if (carvers.isEmpty()) {
            finish();
        }
    }

    Advance advance(LevelChunk targetChunk, int maximumStartChecks) {
        Objects.requireNonNull(targetChunk, "targetChunk");
        if (maximumStartChecks <= 0) {
            throw new IllegalArgumentException("native carver cursor budget must be positive");
        }
        if (targetChunk.getLevel() != level || !targetChunk.getPos().equals(targetPos)) {
            throw new IllegalArgumentException("native carver cursor resumed against another target chunk");
        }
        if (targetChunk.getHeight() != targetHeight
                || targetChunk.getMinBuildHeight() != targetMinimumBuildY) {
            throw new IllegalStateException("native carver target build frame changed while cursor was pending");
        }
        if (complete) {
            return new Advance(false, true, 0, 0);
        }

        int localStartChecks = 0;
        int localCarveCalls = 0;
        while (!complete && localStartChecks < maximumStartChecks) {
            ChunkPos sourcePos = new ChunkPos(targetPos.x + sourceDx, targetPos.z + sourceDz);
            Holder<ConfiguredWorldCarver<?>> holder = carvers.get(carverIndex);
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
            localStartChecks++;

            ConfiguredWorldCarver<?> configured = holder.value();
            if (configured.isStartChunk(random)) {
                startChunks++;
                startedCarverKeys.add(key);
                executeCarver(targetChunk, configured, sourcePos);
                localCarveCalls++;
            }
            advanceLoopPosition();
        }

        return new Advance(localStartChecks > 0, complete, localStartChecks, localCarveCalls);
    }

    boolean complete() {
        return complete;
    }

    SkyforgeNativeCarverRunner.Result result() {
        if (!complete || result == null) {
            throw new IllegalStateException("native carver cursor is not complete");
        }
        return result;
    }

    private void executeCarver(
            LevelChunk targetChunk,
            ConfiguredWorldCarver<?> configured,
            ChunkPos sourcePos) {
        CarvingContext context = new CarvingContext(
                generator,
                level.registryAccess(),
                targetChunk.getHeightAccessorForGeneration(),
                null,
                level.getChunkSource().randomState(),
                generator.generatorSettings().value().surfaceRule());

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
    }

    private void advanceLoopPosition() {
        carverIndex++;
        if (carverIndex < carvers.size()) {
            return;
        }
        carverIndex = 0;
        sourceDz++;
        if (sourceDz <= VANILLA_SOURCE_RADIUS_CHUNKS) {
            return;
        }
        sourceDz = -VANILLA_SOURCE_RADIUS_CHUNKS;
        sourceDx++;
        if (sourceDx > VANILLA_SOURCE_RADIUS_CHUNKS) {
            finish();
        }
    }

    private void finish() {
        complete = true;
        result = new SkyforgeNativeCarverRunner.Result(
                domainBiome.unwrapKey().orElseThrow(),
                carvers.size(),
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

    record Advance(
            boolean worked,
            boolean complete,
            int startChecks,
            int carveCalls) {}
}
