package io.github.nidaba.skyforge.neoforge1211;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** Executes one registered native placed feature inside one exact Skyforge terrain domain. */
final class SkyforgeNativePlacedFeatureRunner {
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNativePlacedFeatureRunner.class.getName());

    private SkyforgeNativePlacedFeatureRunner() {}

    static Result place(
            WorldGenLevel level,
            ChunkGenerator generator,
            Holder<PlacedFeature> placedFeature,
            SkyforgePopulationOperation operation,
            BlockPos origin,
            int maximumAttachmentDepth) {
        return place(
                level,
                generator,
                placedFeature,
                Optional.empty(),
                operation,
                origin,
                maximumAttachmentDepth);
    }

    static Result place(
            WorldGenLevel level,
            ChunkGenerator generator,
            Holder<PlacedFeature> placedFeature,
            Holder<Biome> domainBiome,
            SkyforgePopulationOperation operation,
            BlockPos origin,
            int maximumAttachmentDepth) {
        return place(
                level,
                generator,
                placedFeature,
                Optional.of(Objects.requireNonNull(domainBiome, "domainBiome")),
                operation,
                origin,
                maximumAttachmentDepth);
    }

    /**
     * Development diagnostic for biome-owned tree features.
     *
     * <p>Runs no feature and performs no writes. It opens the same exact-volume terrain/biome scope
     * used by population, finds the owner surface nearest the chunk center, and evaluates the native
     * height/block/biome/sapling-survival prerequisites that the checked oak/birch/spruce tree stack
     * depends on. This lets a runtime failure distinguish placement-modifier rejection from tree
     * realization failure without weakening any native predicate.
     */
    static TreePrerequisiteProbe probeTreePrerequisites(
            WorldGenLevel level,
            Holder<Biome> domainBiome,
            SkyforgePopulationOperation operation,
            int maximumAttachmentDepth) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(domainBiome, "domainBiome");
        Objects.requireNonNull(operation, "operation");

        SurfaceProbe surface = findSurface(level, operation.originChunk(), operation);
        try (var domain = SkyforgeGenerationDomainStage.openIsland(operation.volumeId());
                var execution = SkyforgePopulationExecutionStage.open(
                        level,
                        operation,
                        domainBiome,
                        maximumAttachmentDepth)) {
            domain.requireActive();
            execution.requireActive();

            BlockPos surfacePos = new BlockPos(surface.x(), surface.firstFreeY(), surface.z());
            int oceanFloorY = level.getHeight(Heightmap.Types.OCEAN_FLOOR, surface.x(), surface.z());
            var below = level.getBlockState(surfacePos.below());
            var at = level.getBlockState(surfacePos);
            Holder<Biome> observedBiome = level.getBiome(surfacePos);
            boolean oakSurvives = Blocks.OAK_SAPLING.defaultBlockState().canSurvive(level, surfacePos);
            boolean birchSurvives = Blocks.BIRCH_SAPLING.defaultBlockState().canSurvive(level, surfacePos);
            boolean spruceSurvives = Blocks.SPRUCE_SAPLING.defaultBlockState().canSurvive(level, surfacePos);

            return new TreePrerequisiteProbe(
                    surface.x(),
                    surface.z(),
                    surface.firstFreeY(),
                    oceanFloorY,
                    below.toString(),
                    at.toString(),
                    observedBiome.equals(domainBiome),
                    oakSurvives,
                    birchSurvives,
                    spruceSurvives);
        }
    }

    private static SurfaceProbe findSurface(
            WorldGenLevel level,
            ChunkPos chunkPos,
            SkyforgePopulationOperation operation) {
        int middleX = chunkPos.getMiddleBlockX();
        int middleZ = chunkPos.getMiddleBlockZ();
        SurfaceProbe best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int x = chunkPos.getMinBlockX(); x <= chunkPos.getMaxBlockX(); x++) {
            for (int z = chunkPos.getMinBlockZ(); z <= chunkPos.getMaxBlockZ(); z++) {
                var claim = SkyforgeNeoForge1211SurfaceStage.queryBaseHeightClaim(
                        operation.volumeId(),
                        x,
                        z,
                        Heightmap.Types.OCEAN_FLOOR,
                        level.getMinBuildHeight(),
                        level.getHeight());
                if (claim.isEmpty()) {
                    continue;
                }
                int distance = Math.abs(x - middleX) + Math.abs(z - middleZ);
                if (distance < bestDistance) {
                    best = new SurfaceProbe(x, z, claim.orElseThrow().height());
                    bestDistance = distance;
                }
            }
        }
        if (best == null) {
            throw new IllegalStateException("tree prerequisite probe found no exact-volume surface in chunk "
                    + chunkPos + " for " + operation.volumeId().path());
        }
        return best;
    }

    private static Result place(
            WorldGenLevel level,
            ChunkGenerator generator,
            Holder<PlacedFeature> placedFeature,
            Optional<Holder<Biome>> domainBiome,
            SkyforgePopulationOperation operation,
            BlockPos origin,
            int maximumAttachmentDepth) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(generator, "generator");
        Objects.requireNonNull(placedFeature, "placedFeature");
        Objects.requireNonNull(domainBiome, "domainBiome");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(origin, "origin");

        ResourceLocation registryLocation = placedFeature.unwrapKey()
                .map(key -> key.location())
                .orElseGet(() -> level.registryAccess()
                        .registryOrThrow(Registries.PLACED_FEATURE)
                        .getKey(placedFeature.value()));
        if (registryLocation == null) {
            throw new IllegalArgumentException(
                    "island population requires a PlacedFeature present in the final registry");
        }
        if (!registryLocation.equals(operation.nativeDefinitionKey())) {
            throw new IllegalArgumentException("population operation key does not match PlacedFeature registry identity");
        }

        if (operation.generationStep()
                        == net.minecraft.world.level.levelgen.GenerationStep.Decoration.LAKES.ordinal()
                && !SkyforgeNativeLakeAdmissionStage.supports(placedFeature.value())) {
            // First LAKES capability admits the native bounded LakeFeature type generically by
            // configured-feature class, never by registry ID. Unknown custom LAKES feature types
            // remain fail-closed until they expose an equivalent whole-footprint contract.
            return new Result(false, 0, null, 0xcbf29ce484222325L);
        }

        try (var domain = SkyforgeGenerationDomainStage.openIsland(operation.volumeId());
                var execution = openExecution(level, operation, domainBiome, maximumAttachmentDepth);
                var hydrologyDressing = SkyforgeNativeHydrologyDressingStage.open(placedFeature.value());
                var verticalFrame = SkyforgeVerticalPlacementFrame.open(level, operation);
                var generatedFluid = SkyforgeGeneratedFluidPropagationStage.openPopulation(level, operation);
                var lakeAdmission = SkyforgeNativeLakeAdmissionStage.open(operation)) {
            domain.requireActive();
            execution.requireActive();
            hydrologyDressing.requireActive();
            verticalFrame.requireActive();
            generatedFluid.requireActive();
            lakeAdmission.requireActive();
            boolean dr40TreeTrace = Boolean.getBoolean(SkyforgeDr40ProductionEcologyEvidence.ENABLE_PROPERTY)
                    && registryLocation.equals(ResourceLocation.fromNamespaceAndPath(
                            "minecraft", "trees_birch_and_oak"))
                    && operation.originChunk().x == -1
                    && operation.originChunk().z == 0;
            long dr40TreePreStateDigest = dr40TreeTrace
                    ? populationVisibleChunkDigest(level, operation.originChunk())
                    : 0L;
            RandomSource random = RandomSource.create(operation.seed());
            boolean placed = domainBiome.isPresent()
                    // Biome-owned generation must preserve Minecraft's top-feature provenance so
                    // BiomeFilter can verify that this registered feature belongs to the active
                    // exact-volume biome. Plain place(...) intentionally omits that provenance and
                    // remains correct for explicit non-biome feature proofs such as SF-IMP-0053.
                    ? placedFeature.value().placeWithBiomeCheck(level, generator, random, origin)
                    : placedFeature.value().place(level, generator, random, origin);
            var dr40TreePreFlushAttachments = dr40TreeTrace
                    ? execution.execution().attachmentPositions()
                    : java.util.List.<BlockPos>of();
            long dr40TreePreFlushAttachmentDigest = dr40TreeTrace
                    ? execution.execution().attachmentPositionDigest()
                    : 0L;
            // Deferred population on finished chunks records the same native post-processing marks
            // as worldgen, then resolves them here before the exact-volume execution scope closes.
            // Direct worldgen never opens the bridge, so this is a no-op on the accepted path.
            SkyforgeDeferredPopulationPostProcessingBridge.flushIfActive();
            if (dr40TreeTrace) {
                LOGGER.log(
                        System.Logger.Level.INFO,
                        "SKYFORGE DR40 TREE TRACE: chunk="
                                + operation.originChunk()
                                + ", seed=" + Long.toUnsignedString(operation.seed())
                                + ", preStateDigest=" + Long.toUnsignedString(dr40TreePreStateDigest, 16)
                                + ", preFlushAttachmentDigest="
                                + Long.toUnsignedString(dr40TreePreFlushAttachmentDigest, 16)
                                + ", preFlushAttachments="
                                + dr40TreePreFlushAttachments.stream()
                                        .map(position -> Long.toString(position.asLong()))
                                        .toList()
                                + ", postFlushAttachmentDigest="
                                + Long.toUnsignedString(
                                        execution.execution().attachmentPositionDigest(), 16)
                                + ", postFlushAttachments="
                                + execution.execution().attachmentPositions().stream()
                                        .map(position -> Long.toString(position.asLong()))
                                        .toList());
            }
            if (registryLocation.equals(ResourceLocation.fromNamespaceAndPath(
                            "minecraft", "patch_grass_savanna"))
                    && operation.originChunk().x == 5
                    && operation.originChunk().z == -2) {
                LOGGER.log(
                        System.Logger.Level.INFO,
                        "SKYFORGE DR40 GRASS TRACE: chunk="
                                + operation.originChunk()
                                + ", seed=" + Long.toUnsignedString(operation.seed())
                                + ", attachments="
                                + execution.execution().attachmentPositions().stream()
                                        .map(position -> Long.toString(position.asLong()))
                                        .toList());
            }
            return new Result(
                    placed,
                    execution.execution().attachmentCount(),
                    operation.generationStep()
                                    == net.minecraft.world.level.levelgen.GenerationStep.Decoration.LAKES.ordinal()
                            ? lakeAdmission.snapshot()
                            : null,
                    execution.execution().attachmentPositionDigest());
        }
    }

    private static long populationVisibleChunkDigest(
            WorldGenLevel level,
            ChunkPos chunkPos) {
        long digest = 0xcbf29ce484222325L;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int minimumY = level.getMinBuildHeight();
        int maximumY = minimumY + level.getHeight() - 1;
        for (int x = chunkPos.getMinBlockX(); x <= chunkPos.getMaxBlockX(); x++) {
            for (int z = chunkPos.getMinBlockZ(); z <= chunkPos.getMaxBlockZ(); z++) {
                for (int y = minimumY; y <= maximumY; y++) {
                    cursor.set(x, y, z);
                    var state = level.getBlockState(cursor);
                    digest = diagnosticMix(digest, cursor.asLong());
                    digest = diagnosticMix(digest, state.toString().hashCode());
                }
            }
        }
        return digest;
    }

    private static long diagnosticMix(long digest, long value) {
        long mixed = digest;
        for (int shift = 0; shift < Long.SIZE; shift += Byte.SIZE) {
            mixed ^= (value >>> shift) & 0xffL;
            mixed *= 0x100000001b3L;
        }
        return mixed;
    }

    private static SkyforgePopulationExecutionStage.Scope openExecution(
            WorldGenLevel level,
            SkyforgePopulationOperation operation,
            Optional<Holder<Biome>> domainBiome,
            int maximumAttachmentDepth) {
        return domainBiome.isPresent()
                ? SkyforgePopulationExecutionStage.open(
                        level,
                        operation,
                        domainBiome.orElseThrow(),
                        maximumAttachmentDepth)
                : SkyforgePopulationExecutionStage.open(
                        level,
                        operation,
                        maximumAttachmentDepth);
    }

    private record SurfaceProbe(int x, int z, int firstFreeY) {}

    record TreePrerequisiteProbe(
            int x,
            int z,
            int firstFreeY,
            int oceanFloorY,
            String blockBelow,
            String blockAt,
            boolean observedExpectedBiome,
            boolean oakSurvives,
            boolean birchSurvives,
            boolean spruceSurvives) {
        TreePrerequisiteProbe {
            Objects.requireNonNull(blockBelow, "blockBelow");
            Objects.requireNonNull(blockAt, "blockAt");
        }
    }

    record Result(
            boolean placed,
            int attachmentWrites,
            SkyforgeNativeLakeAdmissionStage.Snapshot lakeAdmission,
            long attachmentPositionDigest) {
        Result(boolean placed, int attachmentWrites, SkyforgeNativeLakeAdmissionStage.Snapshot lakeAdmission) {
            this(placed, attachmentWrites, lakeAdmission, 0xcbf29ce484222325L);
        }

        Result {
            if (attachmentWrites < 0) {
                throw new IllegalArgumentException("attachmentWrites must be non-negative");
            }
        }
    }
}
