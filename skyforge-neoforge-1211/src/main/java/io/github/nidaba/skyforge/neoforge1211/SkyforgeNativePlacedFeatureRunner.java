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

        try (var domain = SkyforgeGenerationDomainStage.openIsland(operation.volumeId());
                var execution = openExecution(operation, domainBiome, maximumAttachmentDepth)) {
            domain.requireActive();
            execution.requireActive();
            RandomSource random = RandomSource.create(operation.seed());
            boolean placed = domainBiome.isPresent()
                    // Biome-owned generation must preserve Minecraft's top-feature provenance so
                    // BiomeFilter can verify that this registered feature belongs to the active
                    // exact-volume biome. Plain place(...) intentionally omits that provenance and
                    // remains correct for explicit non-biome feature proofs such as SF-IMP-0053.
                    ? placedFeature.value().placeWithBiomeCheck(level, generator, random, origin)
                    : placedFeature.value().place(level, generator, random, origin);
            return new Result(placed, execution.execution().attachmentCount());
        }
    }

    private static SkyforgePopulationExecutionStage.Scope openExecution(
            SkyforgePopulationOperation operation,
            Optional<Holder<Biome>> domainBiome,
            int maximumAttachmentDepth) {
        return domainBiome.isPresent()
                ? SkyforgePopulationExecutionStage.open(
                        operation,
                        domainBiome.orElseThrow(),
                        maximumAttachmentDepth)
                : SkyforgePopulationExecutionStage.open(operation, maximumAttachmentDepth);
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

    record Result(boolean placed, int attachmentWrites) {
        Result {
            if (attachmentWrites < 0) {
                throw new IllegalArgumentException("attachmentWrites must be non-negative");
            }
        }
    }
}
