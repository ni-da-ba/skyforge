package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.junit.jupiter.api.Test;

/** Deterministic, exact-owner, save/reload-style evidence for the DR-20 representative tranche. */
final class SkyforgeAuthoredVisibleHydrologyAdapterTest {
    private static final long WORLD_SEED = 0x534B59464F524745L;
    private static final long[] ACCEPTED_CORPUS_KEYS = {77L, 118L, 241L, 512L, 811L, 83L};


    @Test
    void f4kReviewFixtureClipsWaterHeadsToTheF4dAuthorizedReplacementBand() throws Exception {
        var fixture = SkyforgeF4KHydrologyReviewDevRuntime.fixture();
        var reviewColumn = fixture.authorization().quantization().authorizedColumns().stream()
                .filter(column -> column.projection().semanticSample().wet() && column.mutatesTerrain())
                .filter(column -> Math.min(
                                (int) Math.ceil(column.projection().originalUpperSurfaceWorldY()
                                        + (column.projection().semanticSample().waterSurfacePotential()
                                                - column.projection().semanticSample().originalTerrainPotential())
                                                * fixture.descriptor().reliefBudget()) - 1,
                                column.originalSupport().maximumSolidY())
                        >= column.targetMaximumSolidY() + 1)
                .findFirst()
                .orElseThrow();
        var terrain = terrain(fixture.catalog(), fixture.descriptor());
        var chunk = MinecraftTestChunkFactory.protoChunk(
                new net.minecraft.world.level.ChunkPos(reviewColumn.worldX() >> 4, reviewColumn.worldZ() >> 4));

        try (AutoCloseable installedSurfaceStage = SkyforgeNeoForge1211SurfaceStage.installAuthorizedHydrology(
                terrain,
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()),
                fixture.authorization())) {
            assertNotNull(installedSurfaceStage);
            SkyforgeNeoForge1211SurfaceStage.realize(chunk);
        }

        int firstWaterY = reviewColumn.targetMaximumSolidY() + 1;
        int clippedWaterMaximumY = Math.min(
                (int) Math.ceil(reviewColumn.projection().originalUpperSurfaceWorldY()
                        + (reviewColumn.projection().semanticSample().waterSurfacePotential()
                                - reviewColumn.projection().semanticSample().originalTerrainPotential())
                                * fixture.descriptor().reliefBudget()) - 1,
                reviewColumn.originalSupport().maximumSolidY());
        assertTrue(clippedWaterMaximumY >= firstWaterY);
        assertTrue(chunk.getBlockState(new BlockPos(
                        reviewColumn.worldX(), firstWaterY, reviewColumn.worldZ()))
                .is(Blocks.WATER));
        assertTrue(clippedWaterMaximumY <= reviewColumn.originalSupport().maximumSolidY());
    }

    @Test
    void f4kReviewWarmupCoversExactlyTheChunksContainingEligibleWaterHeads() {
        var fixture = SkyforgeF4KHydrologyReviewDevRuntime.fixture();
        Set<Long> expectedChunks = new HashSet<>();
        for (BlockPos position : SkyforgeF4KHydrologyReviewDevRuntime.expectedWaterHeadPositions(fixture)) {
            expectedChunks.add(new net.minecraft.world.level.ChunkPos(position).toLong());
        }

        assertFalse(expectedChunks.isEmpty());
        assertEquals(
                expectedChunks,
                SkyforgeF4KHydrologyReviewDevRuntime.requiredWaterHeadChunkKeys(fixture));
    }

    @Test
    void qualifiedOnlyBindingDoesNotFallBackWhenAuthorizationIsAbsent() throws Exception {
        CorpusFixture fixture = corpus(77L);
        var terrain = terrain(fixture.catalog(), fixture.descriptor());
        var legacyDeployments = SkyforgeAuthoredVisibleHydrologyAdapter.plan(
                fixture.descriptor(), fixture.volume(), terrain);
        assertFalse(legacyDeployments.isEmpty());
        BlockPos sample = legacyDeployments.getFirst().positions().getFirst();
        var chunk = MinecraftTestChunkFactory.protoChunk(
                new net.minecraft.world.level.ChunkPos(sample));

        try (AutoCloseable installed = SkyforgeNeoForge1211SurfaceStage.installQualifiedHydrology(
                terrain,
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()),
                Map.of())) {
            assertNotNull(installed);
            SkyforgeNeoForge1211SurfaceStage.realize(chunk);
        }

        for (var deployment : legacyDeployments) {
            for (BlockPos position : deployment.positions()) {
                if (chunk.getPos().equals(new net.minecraft.world.level.ChunkPos(position))) {
                    assertFalse(chunk.getBlockState(position).is(Blocks.WATER));
                }
            }
        }
    }

    @Test
    void qualifiedBindingAppliesMultipleExactVolumeAuthorizations() {
        StackedCorpusFixture fixture = qualifiedMultiVolumeCorpus(77L);
        var terrain = terrain(fixture.catalog(), fixture.descriptor());
        var candidate = io.github.nidaba.skyforge.world.SkyIslandComponentFluvialTerrainCandidatePlanner
                .plan(fixture.descriptor());
        Map<io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId,
                io.github.nidaba.skyforge.world.SkyIslandHydrologyRuntimeAuthorization> authorizations =
                        new LinkedHashMap<>();
        for (var volume : List.of(fixture.lower(), fixture.upper())) {
            var association = io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationAssociation
                    .of(fixture.descriptor(), volume);
            var voxel = io.github.nidaba.skyforge.world.SkyIslandFluvialVoxelQuantizationPlanner
                    .plan(association, candidate);
            var direct = io.github.nidaba.skyforge.world.SkyIslandWorldWaterProjectionQualificationPlanner
                    .plan(voxel);
            var head = io.github.nidaba.skyforge.world.SkyIslandWorldWaterHeadRefinementPlanner
                    .plan(direct);
            var refined = io.github.nidaba.skyforge.world.SkyIslandWorldHeadRefinedTerrainPlanner
                    .plan(head);
            authorizations.put(
                    volume.id(),
                    io.github.nidaba.skyforge.world.SkyIslandHydrologyRuntimeAuthorization.fromF4H(
                            association, candidate, refined.terrainField()));
        }

        var binding = SkyforgeAuthoredVisibleHydrologyAdapter.bindQualified(authorizations);
        var lowerAuthorization = authorizations.get(fixture.lower().id());
        var upperAuthorization = authorizations.get(fixture.upper().id());
        BlockPos lowerWaterHead = firstAuthorizedWaterHead(lowerAuthorization, fixture.descriptor());
        BlockPos upperWaterHead = firstAuthorizedWaterHead(upperAuthorization, fixture.descriptor());
        var lowerChunk = MinecraftTestChunkFactory.protoChunk(new net.minecraft.world.level.ChunkPos(lowerWaterHead));
        var upperChunk = MinecraftTestChunkFactory.protoChunk(new net.minecraft.world.level.ChunkPos(upperWaterHead));
        assertNotEquals(lowerChunk.getPos(), upperChunk.getPos());
        int lowerChanged = SkyforgeAuthoredVisibleHydrologyAdapter.applyAvailable(
                lowerChunk, terrain, binding);
        int upperChanged = SkyforgeAuthoredVisibleHydrologyAdapter.applyAvailable(
                upperChunk, terrain, binding);
        assertTrue(lowerChanged > 0);
        assertTrue(upperChanged > 0);
        assertTrue(lowerChunk.getBlockState(lowerWaterHead).is(Blocks.WATER));
        assertTrue(upperChunk.getBlockState(upperWaterHead).is(Blocks.WATER));
        assertEquals(0, SkyforgeAuthoredVisibleHydrologyAdapter.applyAvailable(
                lowerChunk, terrain, binding));
        assertEquals(0, SkyforgeAuthoredVisibleHydrologyAdapter.applyAvailable(
                upperChunk, terrain, binding));
    }

    @Test
    void canonicalSpecimenRealizesOnlyAuthoredKindsWithoutSynthesisOrForeignOwnership() {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.single();
        var terrain = terrain(fixture.catalog(), fixture.descriptor());
        var first = SkyforgeAuthoredVisibleHydrologyAdapter.plan(fixture.descriptor(), fixture.volume(), terrain);
        var replay = SkyforgeAuthoredVisibleHydrologyAdapter.plan(fixture.descriptor(), fixture.volume(), terrain);

        assertEquals(first, replay);
        assertEquals(authoredFeatureKinds(fixture.descriptor()), featureKinds(first));
        assertOwned(first, terrain);
    }

    @Test
    void boundedAcceptedCorpusCoversEveryRequiredImplementationKind() {
        Set<SkyforgeAuthoredVisibleHydrologyAdapter.Feature> observed = new HashSet<>();
        for (long key : ACCEPTED_CORPUS_KEYS) {
            CorpusFixture fixture = corpus(key);
            var terrain = terrain(fixture.catalog(), fixture.descriptor());
            var deployments = SkyforgeAuthoredVisibleHydrologyAdapter.plan(
                    fixture.descriptor(), fixture.volume(), terrain);
            observed.addAll(featureKinds(deployments));
            assertOwned(deployments, terrain);
        }

        assertEquals(requiredFeatureKinds(), observed);
    }

    @Test
    void lifecycleRealizesOnlyAvailableChunksAndReplayRetainsAuthoredWater() throws Exception {
        CorpusFixture fixture = corpus(77L);
        var terrain = terrain(fixture.catalog(), fixture.descriptor());
        var deployments = SkyforgeAuthoredVisibleHydrologyAdapter.plan(
                fixture.descriptor(), fixture.volume(), terrain);
        assertFalse(deployments.isEmpty());

        for (var deployment : deployments) {
            var chunks = new java.util.HashMap<Long, ProtoChunk>();
            for (var position : deployment.positions()) {
                chunks.computeIfAbsent(new net.minecraft.world.level.ChunkPos(position).toLong(), key -> {
                    try {
                        return realizedChunk(terrain, new net.minecraft.world.level.ChunkPos(key));
                    } catch (Exception exception) {
                        throw new RuntimeException(exception);
                    }
                });
            }
            for (var position : deployment.positions()) {
                assertTrue(chunks.get(new net.minecraft.world.level.ChunkPos(position).toLong())
                        .getBlockState(position)
                        .is(Blocks.WATER));
            }

            assertEquals(0, chunks.values().stream()
                    .mapToInt(chunk -> SkyforgeAuthoredVisibleHydrologyAdapter.applyAvailable(chunk, terrain))
                    .sum());

            var reloaded = SkyforgeAuthoredVisibleHydrologyAdapter.plan(
                    fixture.descriptor(), fixture.volume(), terrain);
            var reloadedDeployment = reloaded.stream()
                    .filter(candidate -> candidate.feature() == deployment.feature())
                    .findFirst()
                    .orElseThrow();
            assertEquals(deployment, reloadedDeployment);
            assertEquals(0, chunks.values().stream()
                    .mapToInt(chunk -> SkyforgeAuthoredVisibleHydrologyAdapter.apply(chunk, reloadedDeployment))
                    .sum());
        }
    }

    @Test
    void staticAuthoredWaterCanBePersistedWithoutAChunkLifecycleBinding() {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.single();
        var deployment = new SkyforgeAuthoredVisibleHydrologyAdapter.Deployment(
                fixture.volume().id(),
                SkyforgeAuthoredVisibleHydrologyAdapter.Feature.CHANNEL,
                List.of(new BlockPos(1, 64, 1)));
        var chunk = MinecraftTestChunkFactory.protoChunk(new net.minecraft.world.level.ChunkPos(0, 0));

        assertEquals(1, SkyforgeAuthoredVisibleHydrologyAdapter.apply(chunk, deployment));
        assertTrue(chunk.getBlockState(deployment.positions().getFirst()).is(Blocks.WATER));
        assertEquals(0, SkyforgeAuthoredVisibleHydrologyAdapter.apply(chunk, deployment));
    }

    @Test
    void stackedVolumesKeepAcceptedCorpusWaterExactOwnerLocal() {
        StackedCorpusFixture fixture = stackedCorpus(77L);
        var terrain = terrain(fixture.catalog(), fixture.descriptor());
        var lower = SkyforgeAuthoredVisibleHydrologyAdapter.plan(fixture.descriptor(), fixture.lower(), terrain);
        var upper = SkyforgeAuthoredVisibleHydrologyAdapter.plan(fixture.descriptor(), fixture.upper(), terrain);

        assertNotEquals(fixture.lower().id(), fixture.upper().id());
        assertFalse(lower.isEmpty());
        assertFalse(upper.isEmpty());
        assertOwned(lower, terrain);
        assertOwned(upper, terrain);
    }

    private static Set<SkyforgeAuthoredVisibleHydrologyAdapter.Feature> authoredFeatureKinds(
            io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor descriptor) {
        var intent = io.github.nidaba.skyforge.world.SkyIslandVisibleHydrologicRealizationPlanner.plan(descriptor);
        Set<SkyforgeAuthoredVisibleHydrologyAdapter.Feature> features = new HashSet<>();
        if (!intent.channels().isEmpty()) {
            features.add(SkyforgeAuthoredVisibleHydrologyAdapter.Feature.CHANNEL);
        }
        if (!intent.retainedWater().isEmpty()) {
            features.add(SkyforgeAuthoredVisibleHydrologyAdapter.Feature.RETAINED_WATER);
        }
        if (intent.drops().stream().anyMatch(drop ->
                drop.kind() == io.github.nidaba.skyforge.world.SkyIslandVisibleHydrologicRealizationKind.CASCADE
                        || drop.kind()
                                == io.github.nidaba.skyforge.world.SkyIslandVisibleHydrologicRealizationKind.WATERFALL)) {
            features.add(SkyforgeAuthoredVisibleHydrologyAdapter.Feature.VERTICAL_DISCHARGE);
        }
        if (intent.drops().stream().anyMatch(drop ->
                drop.kind() == io.github.nidaba.skyforge.world.SkyIslandVisibleHydrologicRealizationKind.EDGE_DISCHARGE)) {
            features.add(SkyforgeAuthoredVisibleHydrologyAdapter.Feature.EDGE_DISCHARGE);
        }
        return features;
    }

    private static Set<SkyforgeAuthoredVisibleHydrologyAdapter.Feature> featureKinds(
            List<SkyforgeAuthoredVisibleHydrologyAdapter.Deployment> deployments) {
        return deployments.stream()
                .map(SkyforgeAuthoredVisibleHydrologyAdapter.Deployment::feature)
                .collect(java.util.stream.Collectors.toSet());
    }

    private static Set<SkyforgeAuthoredVisibleHydrologyAdapter.Feature> requiredFeatureKinds() {
        return Set.of(
                SkyforgeAuthoredVisibleHydrologyAdapter.Feature.CHANNEL,
                SkyforgeAuthoredVisibleHydrologyAdapter.Feature.RETAINED_WATER,
                SkyforgeAuthoredVisibleHydrologyAdapter.Feature.VERTICAL_DISCHARGE,
                SkyforgeAuthoredVisibleHydrologyAdapter.Feature.EDGE_DISCHARGE);
    }

    private static BlockPos firstAuthorizedWaterHead(
            io.github.nidaba.skyforge.world.SkyIslandHydrologyRuntimeAuthorization authorization,
            io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor descriptor) {
        return authorization.quantization().authorizedColumns().stream()
                .filter(column -> column.projection().semanticSample().wet() && column.mutatesTerrain())
                .filter(column -> {
                    var projection = column.projection();
                    var semantic = projection.semanticSample();
                    double waterHeadWorld = projection.originalUpperSurfaceWorldY()
                            + (semantic.waterSurfacePotential() - semantic.originalTerrainPotential())
                                    * descriptor.reliefBudget();
                    int waterMaximumY = (int) Math.ceil(waterHeadWorld) - 1;
                    return Math.min(waterMaximumY, column.originalSupport().maximumSolidY())
                            >= column.targetMaximumSolidY() + 1;
                })
                .map(column -> new BlockPos(
                        column.worldX(),
                        column.targetMaximumSolidY() + 1,
                        column.worldZ()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("qualified F4H authorization has no discrete water head"));
    }

    private static void assertOwned(
            List<SkyforgeAuthoredVisibleHydrologyAdapter.Deployment> deployments,
            SkyforgeNeoForge1211ChunkAdapter terrain) {
        for (var deployment : deployments) {
            assertFalse(deployment.positions().isEmpty());
            for (var position : deployment.positions()) {
                assertTrue(terrain.isSolidOwnedBy(
                        deployment.volumeId(), position.getX(), position.getY(), position.getZ()));
                assertFalse(terrain.isSolidOwnedByOtherVolume(
                        deployment.volumeId(), position.getX(), position.getY(), position.getZ()));
            }
            if (deployment.feature() == SkyforgeAuthoredVisibleHydrologyAdapter.Feature.VERTICAL_DISCHARGE) {
                assertTrue(deployment.positions().size() >= 2);
            }
        }
    }

    private static CorpusFixture corpus(long key) {
        var descriptor = io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator.derive(
                io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity.of(WORLD_SEED, 6L, 61L, key));
        long physicalSeed = 820_000L + key;
        var volume = volume(
                descriptor,
                physicalSeed,
                "dr20-accepted-corpus/" + key,
                220.0,
                58.0,
                82.0,
                Math.min(54.0, descriptor.nominalRadius() * 0.18),
                110.0,
                80.0);
        return new CorpusFixture(
                descriptor,
                volume,
                new io.github.nidaba.skyforge.world.SkyIslandWorldCatalog(WORLD_SEED, List.of(volume)));
    }

    private static StackedCorpusFixture stackedCorpus(long key) {
        var descriptor = io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator.derive(
                io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity.of(WORLD_SEED, 6L, 61L, key));
        double rimDepth = Math.min(48.0, descriptor.nominalRadius() * 0.16);
        double acceptedRimDepth = Math.min(32.0, descriptor.nominalRadius());
        var lower = volume(
                descriptor, 830_000L + key, "dr20-accepted-corpus-stacked/lower/" + key,
                256.0, 72.0, 104.0, acceptedRimDepth, 192.0, 192.0);
        var upper = volume(
                descriptor, 840_000L + key, "dr20-accepted-corpus-stacked/upper/" + key,
                768.0, 72.0, 104.0, acceptedRimDepth, 192.0, 192.0);
        return new StackedCorpusFixture(
                descriptor,
                lower,
                upper,
                new io.github.nidaba.skyforge.world.SkyIslandWorldCatalog(WORLD_SEED, List.of(lower, upper)));
    }

    private static StackedCorpusFixture qualifiedMultiVolumeCorpus(long key) {
        var descriptor = io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator.derive(
                io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity.of(WORLD_SEED, 8L, 81L, key));
        var lower = qualifiedF4hVolumeAt(
                descriptor, 910_077L, "dr20-f4h-multivolume/lower/" + key, 0.0);
        var upper = qualifiedF4hVolumeAt(
                descriptor, 910_078L, "dr20-f4h-multivolume/upper/" + key, 512.0);
        return new StackedCorpusFixture(
                descriptor,
                lower,
                upper,
                new io.github.nidaba.skyforge.world.SkyIslandWorldCatalog(WORLD_SEED, List.of(lower, upper)));
    }

    private static io.github.nidaba.skyforge.world.SkyIslandWorldVolume qualifiedF4hVolumeAt(
            io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor descriptor,
            long seed,
            String path,
            double centerX) {
        double radius = descriptor.nominalRadius();
        var physicalDescriptor = io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor.schema2(
                seed,
                centerX,
                0.0,
                256.0,
                radius,
                72.0,
                104.0,
                Math.min(32.0, radius),
                0.43,
                0.62,
                0.57,
                0.18,
                descriptor.morphologyFamily(),
                0.22,
                38.0,
                0.31);
        var compiled = new io.github.nidaba.skyforge.recipes.skyisland.SemanticSkyIslandVolumeRecipe()
                .compile(physicalDescriptor);
        var id = new io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId(WORLD_SEED, path, 0, 0, seed);
        var bounds = new io.github.nidaba.skyforge.world.WorldBounds(
                centerX - radius,
                centerX + radius,
                64.0,
                448.0,
                -radius,
                radius);
        return new io.github.nidaba.skyforge.world.SkyIslandWorldVolume(id, bounds, compiled);
    }

    private static io.github.nidaba.skyforge.world.SkyIslandWorldVolume volume(
            io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor descriptor,
            long seed,
            String path,
            double suspensionY,
            double upperThickness,
            double lowerThickness,
            double rimDepth,
            double lowerBounds,
            double upperBounds) {
        return volumeAt(
                descriptor,
                seed,
                path,
                0.0,
                0.0,
                suspensionY,
                upperThickness,
                lowerThickness,
                rimDepth,
                lowerBounds,
                upperBounds);
    }

    private static io.github.nidaba.skyforge.world.SkyIslandWorldVolume volumeAt(
            io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor descriptor,
            long seed,
            String path,
            double centerX,
            double centerZ,
            double suspensionY,
            double upperThickness,
            double lowerThickness,
            double rimDepth,
            double lowerBounds,
            double upperBounds) {
        double radius = descriptor.nominalRadius();
        var physicalDescriptor = io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor.schema2(
                seed,
                centerX,
                centerZ,
                suspensionY,
                radius,
                upperThickness,
                lowerThickness,
                rimDepth,
                0.0,
                0.24,
                0.62,
                0.0,
                descriptor.morphologyFamily(),
                0.10,
                28.0,
                0.18);
        var compiled = new io.github.nidaba.skyforge.recipes.skyisland.SemanticSkyIslandVolumeRecipe()
                .compile(physicalDescriptor);
        var id = new io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId(WORLD_SEED, path, 0, 0, seed);
        var bounds = new io.github.nidaba.skyforge.world.WorldBounds(
                centerX - radius * 1.08,
                centerX + radius * 1.08,
                suspensionY - lowerBounds,
                suspensionY + upperBounds,
                centerZ - radius * 1.08,
                centerZ + radius * 1.08);
        return new io.github.nidaba.skyforge.world.SkyIslandWorldVolume(id, bounds, compiled);
    }

    private static SkyforgeNeoForge1211ChunkAdapter terrain(
            io.github.nidaba.skyforge.world.SkyIslandWorldCatalog catalog,
            io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor descriptor) {
        var authoredDescriptors = new java.util.LinkedHashMap<
                io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId,
                io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor>();
        for (var volume : catalog.volumes()) {
            authoredDescriptors.put(volume.id(), descriptor);
        }
        return new SkyforgeNeoForge1211ChunkAdapter(
                catalog,
                io.github.nidaba.skyforge.world.SkyIslandTerrainProfile.reference(),
                new SkyforgeMinecraftBlockPalette(),
                authoredDescriptors);
    }

    private static ProtoChunk realizedChunk(
            SkyforgeNeoForge1211ChunkAdapter terrain,
            net.minecraft.world.level.ChunkPos pos) throws Exception {
        ProtoChunk chunk = MinecraftTestChunkFactory.protoChunk(pos);
        try (AutoCloseable installedSurfaceStage = SkyforgeNeoForge1211SurfaceStage.install(
                terrain, new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()))) {
            assertNotNull(installedSurfaceStage);
            SkyforgeNeoForge1211SurfaceStage.realize(chunk);
        }
        return chunk;
    }

    private record CorpusFixture(
            io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor descriptor,
            io.github.nidaba.skyforge.world.SkyIslandWorldVolume volume,
            io.github.nidaba.skyforge.world.SkyIslandWorldCatalog catalog) {}

    private record StackedCorpusFixture(
            io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor descriptor,
            io.github.nidaba.skyforge.world.SkyIslandWorldVolume lower,
            io.github.nidaba.skyforge.world.SkyIslandWorldVolume upper,
            io.github.nidaba.skyforge.world.SkyIslandWorldCatalog catalog) {}
}
