package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Deterministic, exact-owner, save/reload-style evidence for the DR-20 representative tranche. */
final class SkyforgeAuthoredVisibleHydrologyAdapterTest {
    private static final long WORLD_SEED = 0x534B59464F524745L;
    private static final long[] ACCEPTED_CORPUS_KEYS = {77L, 118L, 241L, 512L, 811L, 83L};

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
    void canonicalSpecimenProjectsConnectedWaterAndCutsARecessedChannelBed() {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.single();
        var terrain = terrain(fixture.catalog(), fixture.descriptor());
        var intent = io.github.nidaba.skyforge.world.SkyIslandVisibleHydrologicRealizationPlanner.plan(
                fixture.descriptor());
        var deployments = terrain.authoredHydrologyDeployments(fixture.volume().id());

        long channelDeployments = deployments.stream()
                .filter(deployment -> deployment.feature() == SkyforgeAuthoredVisibleHydrologyAdapter.Feature.CHANNEL)
                .count();
        long retainedDeployments = deployments.stream()
                .filter(deployment -> deployment.feature() == SkyforgeAuthoredVisibleHydrologyAdapter.Feature.RETAINED_WATER)
                .count();
        assertEquals(intent.channels().size(), channelDeployments);
        assertEquals(intent.retainedWater().size(), retainedDeployments);
        assertTrue(deployments.stream().allMatch(deployment ->
                deployment.feature() == SkyforgeAuthoredVisibleHydrologyAdapter.Feature.CHANNEL
                        || deployment.feature() == SkyforgeAuthoredVisibleHydrologyAdapter.Feature.RETAINED_WATER),
                "drop semantics must shape routed terrain rather than manufacture independent fluid sources");

        var channel = deployments.stream()
                .filter(deployment -> deployment.feature() == SkyforgeAuthoredVisibleHydrologyAdapter.Feature.CHANNEL)
                .findFirst()
                .orElseThrow();
        assertFalse(channel.carvedPositions().isEmpty(),
                "canonical visible channel must physically incise terrain before water placement");
        assertFalse(channel.surfacePositions().isEmpty(),
                "AUTH-0105 channel must expose deterministic bed/bank material positions");
        assertTrue(java.util.Collections.disjoint(channel.positions(), channel.carvedPositions()));
        assertTrue(java.util.Collections.disjoint(channel.positions(), channel.surfacePositions()));
        assertTrue(java.util.Collections.disjoint(channel.carvedPositions(), channel.surfacePositions()));
        assertCanonicalColumnOrder(channel.surfacePositions());
        var fluvial = io.github.nidaba.skyforge.world.SkyIslandFluvialTerrainField.create(
                fixture.descriptor(), intent.coherentHydrology());
        assertFalse(fluvial.reaches().isEmpty());
        assertTrue(fluvial.reaches().getFirst().wetHalfWidth()
                < fluvial.reaches().getFirst().bankfullHalfWidth());
        assertTrue(fluvial.reaches().getFirst().bankfullHalfWidth()
                < fluvial.reaches().getFirst().valleyHalfWidth());

        assertTrue(channel.positions().stream().allMatch(position ->
                terrain.integerSolidRange(
                                fixture.volume().id(),
                                position.getX(),
                                position.getZ())
                        .map(range -> position.getY() < range.maximumY())
                        .orElse(false)),
                "authored wet cells must remain recessed below the pre-fluvial surface");
        assertTrue(
                SkyforgeAuthoredVisibleHydrologyAdapter.physicalLoweringBlocks(
                                fixture.descriptor(),
                                io.github.nidaba.skyforge.world.SkyIslandFluvialTerrainField.MAX_FLUVIAL_LOWERING)
                        >= 1,
                "accepted neutral fluvial lowering must survive Minecraft integer discretization");
        assertTrue(channel.positions().stream().noneMatch(position ->
                terrain.integerSolidRange(
                                fixture.volume().id(),
                                position.getX(),
                                position.getZ())
                        .map(range -> position.getY() == range.maximumY())
                        .orElse(true)),
                "wet channel realization must never consume the original top-surface voxel");

        for (var deployment : deployments) {
            for (var wet : deployment.positions()) {
                var state = terrain.authoredHydrologyPopulationState(fixture.volume().id(), wet)
                        .orElseThrow();
                assertTrue(state.is(Blocks.WATER));
            }
            for (var dry : deployment.carvedPositions()) {
                var state = terrain.authoredHydrologyPopulationState(fixture.volume().id(), dry)
                        .orElseThrow();
                assertTrue(state.isAir());
            }
        }
    }

    @Test
    @Tag("qualification")
    void retainedWaterRasterizesOneConnectedLevelBasinAcrossItsAuthoredFootprint() {
        CorpusFixture fixture = corpus(83L);
        var terrain = terrain(fixture.catalog(), fixture.descriptor());
        var intent = io.github.nidaba.skyforge.world.SkyIslandVisibleHydrologicRealizationPlanner.plan(
                fixture.descriptor());
        var retainedIntent = intent.retainedWater().getFirst();
        var deployment = terrain.authoredHydrologyDeployments(fixture.volume().id()).stream()
                .filter(candidate ->
                        candidate.feature() == SkyforgeAuthoredVisibleHydrologyAdapter.Feature.RETAINED_WATER)
                .findFirst()
                .orElseThrow();

        assertFalse(deployment.carvedPositions().isEmpty(),
                "standing water must excavate a basin above its common surface");
        assertFalse(deployment.surfacePositions().isEmpty(),
                "standing water must retain a dry owned bed below its fluid volume");
        assertOwned(List.of(deployment), terrain);

        var topByColumn = new java.util.LinkedHashMap<String, BlockPos>();
        for (var wet : deployment.positions()) {
            String key = wet.getX() + "," + wet.getZ();
            topByColumn.merge(
                    key,
                    wet,
                    (first, second) -> first.getY() >= second.getY() ? first : second);
        }
        assertTrue(
                topByColumn.size() > retainedIntent.footprint().cells().size(),
                "retained water must rasterize watershed-cell area rather than one source column per coarse cell");
        assertEquals(
                1L,
                topByColumn.values().stream().map(BlockPos::getY).distinct().count(),
                "one retained waterbody must expose one level Minecraft water surface");
        int waterTopY = topByColumn.values().iterator().next().getY();
        assertTrue(topByColumn.values().stream().allMatch(position ->
                terrain.integerSolidRange(
                                fixture.volume().id(),
                                position.getX(),
                                position.getZ())
                        .map(range -> waterTopY < range.maximumY())
                        .orElse(false)),
                "retained water surface must remain recessed inside exact-volume ownership");

        var watershed = io.github.nidaba.skyforge.world.SkyIslandWatershedPlanner.plan(
                fixture.descriptor());
        var physical = fixture.volume().compiledVolume().descriptor();
        Set<Integer> representedCells = new HashSet<>();
        for (var position : topByColumn.values()) {
            double localX = position.getX() - physical.centerX();
            double localZ = position.getZ() - physical.centerZ();
            int gx = (int) Math.round(
                    (localX + fixture.descriptor().nominalRadius()) / watershed.spacing());
            int gz = (int) Math.round(
                    (localZ + fixture.descriptor().nominalRadius()) / watershed.spacing());
            gx = Math.max(0, Math.min(watershed.gridSize() - 1, gx));
            gz = Math.max(0, Math.min(watershed.gridSize() - 1, gz));
            representedCells.add(gz * watershed.gridSize() + gx);
        }
        assertTrue(representedCells.containsAll(retainedIntent.footprint().cells().stream()
                .map(io.github.nidaba.skyforge.world.SkyIslandWaterbodyFootprintCell::watershedCellIndex)
                .collect(java.util.stream.Collectors.toSet())),
                "physical basin must preserve every accepted coarse inundation cell");

        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (var position : topByColumn.values()) {
            for (int[] direction : directions) {
                String neighborKey = (position.getX() + direction[0])
                        + "," + (position.getZ() + direction[1]);
                if (topByColumn.containsKey(neighborKey)) {
                    continue;
                }
                assertTrue(
                        terrain.isSolidOwnedBy(
                                fixture.volume().id(),
                                position.getX() + direction[0],
                                waterTopY,
                                position.getZ() + direction[1])
                                && !terrain.isSolidOwnedByOtherVolume(
                                        fixture.volume().id(),
                                        position.getX() + direction[0],
                                        waterTopY,
                                        position.getZ() + direction[1]),
                        "retained water boundary must be physically banked rather than draining into void");
            }
        }
    }

    @Test
    @Tag("qualification")
    void eachChannelDeploymentKeepsWetCellsInsideItsOwnAuthoredWetCorridor() {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.dr70Review();
        var terrain = terrain(fixture.catalog(), fixture.descriptor());
        var intent = io.github.nidaba.skyforge.world.SkyIslandVisibleHydrologicRealizationPlanner.plan(
                fixture.descriptor());
        var fluvial = io.github.nidaba.skyforge.world.SkyIslandFluvialTerrainField.create(
                fixture.descriptor(), intent.coherentHydrology());
        var deployments = terrain.authoredHydrologyDeployments(fixture.volume().id());
        var channels = deployments.stream()
                .filter(deployment -> deployment.feature() == SkyforgeAuthoredVisibleHydrologyAdapter.Feature.CHANNEL)
                .toList();

        assertEquals(intent.channels().size(), channels.size());
        var physical = fixture.volume().compiledVolume().descriptor();
        for (int index = 0; index < channels.size(); index++) {
            var path = intent.channels().get(index).path();
            var reach = fluvial.reaches().stream()
                    .filter(candidate -> candidate.path().equals(path))
                    .findFirst()
                    .orElseThrow();
            for (var wet : channels.get(index).positions()) {
                var local = new io.github.nidaba.skyforge.world.SkyIslandLocalPosition(
                        wet.getX() - physical.centerX(),
                        wet.getZ() - physical.centerZ());
                assertTrue(
                        distanceToPath(local, path) <= reach.wetHalfWidth() + 1.0e-9,
                        "one channel deployment must not borrow a neighboring reach's water surface");
            }
        }
    }

    @Test
    @Tag("qualification")
    void boundedAcceptedCorpusCoversEveryRequiredImplementationKind() {
        Set<SkyforgeAuthoredVisibleHydrologyAdapter.Feature> observed = new HashSet<>();
        for (long key : ACCEPTED_CORPUS_KEYS) {
            CorpusFixture fixture = corpus(key);
            var terrain = terrain(fixture.catalog(), fixture.descriptor());
            var deployments = terrain.authoredHydrologyDeployments(fixture.volume().id());
            observed.addAll(featureKinds(deployments));
            assertOwned(deployments, terrain);
        }

        assertEquals(requiredFeatureKinds(), observed);
    }

    @Test
    @Tag("qualification")
    void lifecycleRealizesOnlyAvailableChunksAndReplayRetainsAuthoredWater() throws Exception {
        CorpusFixture fixture = corpus(77L);
        var terrain = terrain(fixture.catalog(), fixture.descriptor());
        var deployments = terrain.authoredHydrologyDeployments(fixture.volume().id());
        assertFalse(deployments.isEmpty());

        var chunkKeys = new java.util.LinkedHashSet<Long>();
        for (var deployment : deployments) {
            var allPositions = new java.util.ArrayList<BlockPos>();
            allPositions.addAll(deployment.positions());
            allPositions.addAll(deployment.carvedPositions());
            allPositions.addAll(deployment.surfacePositions());
            for (var position : allPositions) {
                chunkKeys.add(new net.minecraft.world.level.ChunkPos(position).toLong());
            }
        }

        var chunks = new java.util.HashMap<Long, ProtoChunk>();
        try (AutoCloseable installedSurfaceStage = SkyforgeNeoForge1211SurfaceStage.install(
                terrain, new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()))) {
            assertNotNull(installedSurfaceStage);
            for (long chunkKey : chunkKeys) {
                var chunkPos = new net.minecraft.world.level.ChunkPos(chunkKey);
                ProtoChunk chunk = MinecraftTestChunkFactory.protoChunk(chunkPos);
                SkyforgeNeoForge1211SurfaceStage.realize(chunk);
                chunks.put(chunkKey, chunk);
            }
        }

        for (var deployment : deployments) {
            for (var position : deployment.positions()) {
                assertTrue(chunks.get(new net.minecraft.world.level.ChunkPos(position).toLong())
                        .getBlockState(position)
                        .is(Blocks.WATER));
            }
            for (var position : deployment.carvedPositions()) {
                assertTrue(chunks.get(new net.minecraft.world.level.ChunkPos(position).toLong())
                        .getBlockState(position)
                        .isAir());
            }
            for (var position : deployment.surfacePositions()) {
                assertTrue(chunks.get(new net.minecraft.world.level.ChunkPos(position).toLong())
                        .getBlockState(position)
                        .is(Blocks.DIRT),
                        "fluvial bed/bank surface must use the accepted Skyforge surface-mantle carrier");
            }
        }

        assertEquals(0, chunks.values().stream()
                .mapToInt(chunk -> SkyforgeAuthoredVisibleHydrologyAdapter.applyAvailable(chunk, terrain))
                .sum());

        var reloaded = terrain.authoredHydrologyDeployments(fixture.volume().id());
        assertEquals(deployments, reloaded);
        for (var reloadedDeployment : reloaded) {
            assertEquals(0, chunks.values().stream()
                    .mapToInt(chunk -> SkyforgeAuthoredVisibleHydrologyAdapter.apply(chunk, reloadedDeployment))
                    .sum());
        }
    }

    @Test
    void chunkIndexExactlyPartitionsNormalizedHydrologyAndDrivesIdempotentChunkLocalReplay() {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.single();
        var terrain = terrain(fixture.catalog(), fixture.descriptor());
        var deployments = terrain.authoredHydrologyDeployments(fixture.volume().id());
        var indexed = SkyforgeAuthoredVisibleHydrologyAdapter.indexByChunk(deployments);

        var expected = new java.util.LinkedHashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
        for (var deployment : deployments) {
            for (var position : deployment.surfacePositions()) {
                assertEquals(null, expected.putIfAbsent(position, Blocks.DIRT.defaultBlockState()));
            }
            for (var position : deployment.carvedPositions()) {
                var previous = expected.putIfAbsent(position, Blocks.AIR.defaultBlockState());
                assertTrue(previous == null || previous.isAir());
            }
            for (var position : deployment.positions()) {
                var previous = expected.putIfAbsent(position, Blocks.WATER.defaultBlockState());
                assertTrue(previous == null || previous.is(Blocks.WATER));
            }
        }

        assertEquals(
                expected.size(),
                indexed.values().stream().mapToInt(projection -> projection.states().size()).sum());
        for (var entry : indexed.entrySet()) {
            long chunkKey = entry.getKey();
            for (var state : entry.getValue().states().entrySet()) {
                assertEquals(chunkKey, new net.minecraft.world.level.ChunkPos(state.getKey()).toLong());
                assertEquals(expected.get(state.getKey()), state.getValue());
            }
        }

        var first = indexed.entrySet().stream().findFirst().orElseThrow();
        var chunkPos = new net.minecraft.world.level.ChunkPos(first.getKey());
        var chunk = MinecraftTestChunkFactory.protoChunk(chunkPos);
        for (BlockPos position : first.getValue().states().keySet()) {
            chunk.setBlockState(position, Blocks.STONE.defaultBlockState(), false);
        }

        assertEquals(
                first.getValue().states().size(),
                SkyforgeAuthoredVisibleHydrologyAdapter.applyAvailable(chunk, terrain));
        assertEquals(0, SkyforgeAuthoredVisibleHydrologyAdapter.applyAvailable(chunk, terrain));
        for (var state : first.getValue().states().entrySet()) {
            var actual = chunk.getBlockState(state.getKey());
            if (state.getValue().is(Blocks.WATER)) {
                assertTrue(SkyforgeAuthoredVisibleHydrologyAdapter.isWaterBearing(actual));
            } else {
                assertEquals(state.getValue(), actual);
            }
        }
    }

    @Test
    void connectedHydrologyFootprintRequiresSharedBlockFaces() {
        var origin = new SkyforgeAuthoredVisibleHydrologyAdapter.Column(0, 0);
        var cardinal = new SkyforgeAuthoredVisibleHydrologyAdapter.Column(1, 0);
        var diagonal = new SkyforgeAuthoredVisibleHydrologyAdapter.Column(1, 1);

        assertEquals(
                2,
                SkyforgeAuthoredVisibleHydrologyAdapter.largestConnectedFootprint(
                                new java.util.LinkedHashSet<>(List.of(origin, cardinal)))
                        .size());
        assertEquals(
                1,
                SkyforgeAuthoredVisibleHydrologyAdapter.largestConnectedFootprint(
                                new java.util.LinkedHashSet<>(List.of(origin, diagonal)))
                        .size());
    }

    @Test
    void deploymentDefensivelyCopiesMutableRoleLists() {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.single();
        var volumeId = fixture.volume().id();
        var water = new java.util.ArrayList<BlockPos>();
        var carved = new java.util.ArrayList<BlockPos>();
        var surface = new java.util.ArrayList<BlockPos>();
        water.add(new BlockPos(1, 64, 1));
        carved.add(new BlockPos(2, 64, 1));
        surface.add(new BlockPos(3, 64, 1));

        var deployment = new SkyforgeAuthoredVisibleHydrologyAdapter.Deployment(
                volumeId,
                SkyforgeAuthoredVisibleHydrologyAdapter.Feature.CHANNEL,
                water,
                carved,
                surface);
        water.clear();
        carved.clear();
        surface.clear();

        assertEquals(1, deployment.positions().size());
        assertEquals(1, deployment.carvedPositions().size());
        assertEquals(1, deployment.surfacePositions().size());
    }

    @Test
    void deploymentRejectsWaterCarveAndSurfaceOverlap() {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.single();
        var volumeId = fixture.volume().id();
        BlockPos water = new BlockPos(1, 64, 1);
        BlockPos carved = new BlockPos(2, 64, 1);

        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyforgeAuthoredVisibleHydrologyAdapter.Deployment(
                        volumeId,
                        SkyforgeAuthoredVisibleHydrologyAdapter.Feature.CHANNEL,
                        List.of(water),
                        List.of(water),
                        List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyforgeAuthoredVisibleHydrologyAdapter.Deployment(
                        volumeId,
                        SkyforgeAuthoredVisibleHydrologyAdapter.Feature.CHANNEL,
                        List.of(water),
                        List.of(),
                        List.of(water)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyforgeAuthoredVisibleHydrologyAdapter.Deployment(
                        volumeId,
                        SkyforgeAuthoredVisibleHydrologyAdapter.Feature.CHANNEL,
                        List.of(water),
                        List.of(carved),
                        List.of(carved)));
    }

    @Test
    void staticAuthoredWaterCanBePersistedWithoutAChunkLifecycleBinding() {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.single();
        var deployment = new SkyforgeAuthoredVisibleHydrologyAdapter.Deployment(
                fixture.volume().id(),
                SkyforgeAuthoredVisibleHydrologyAdapter.Feature.CHANNEL,
                List.of(new BlockPos(1, 64, 1)),
                List.of(),
                List.of());
        var chunk = MinecraftTestChunkFactory.protoChunk(new net.minecraft.world.level.ChunkPos(0, 0));

        assertEquals(1, SkyforgeAuthoredVisibleHydrologyAdapter.apply(chunk, deployment));
        assertTrue(chunk.getBlockState(deployment.positions().getFirst()).is(Blocks.WATER));
        assertEquals(0, SkyforgeAuthoredVisibleHydrologyAdapter.apply(chunk, deployment));
    }

    @Test
    void settledWaterBearingVariantsRemainStableAcrossReplay() {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.single();
        BlockPos position = new BlockPos(1, 64, 1);
        var deployment = new SkyforgeAuthoredVisibleHydrologyAdapter.Deployment(
                fixture.volume().id(),
                SkyforgeAuthoredVisibleHydrologyAdapter.Feature.CHANNEL,
                List.of(position),
                List.of(),
                List.of());
        var chunk = MinecraftTestChunkFactory.protoChunk(new net.minecraft.world.level.ChunkPos(0, 0));

        assertEquals(1, SkyforgeAuthoredVisibleHydrologyAdapter.apply(chunk, deployment));
        chunk.setBlockState(position, Blocks.BUBBLE_COLUMN.defaultBlockState(), false);
        assertTrue(SkyforgeAuthoredVisibleHydrologyAdapter.isWaterBearing(chunk.getBlockState(position)));
        assertEquals(0, SkyforgeAuthoredVisibleHydrologyAdapter.apply(chunk, deployment),
                "replay must not freeze an ordinary settled water-bearing state back to literal WATER");
        assertTrue(chunk.getBlockState(position).is(Blocks.BUBBLE_COLUMN));
    }

    @Test
    @Tag("qualification")
    void stackedVolumesKeepAcceptedCorpusWaterExactOwnerLocal() {
        StackedCorpusFixture fixture = stackedCorpus(77L);
        var terrain = terrain(fixture.catalog(), fixture.descriptor());
        var lower = terrain.authoredHydrologyDeployments(fixture.lower().id());
        var upper = terrain.authoredHydrologyDeployments(fixture.upper().id());

        assertNotEquals(fixture.lower().id(), fixture.upper().id());
        assertFalse(lower.isEmpty());
        assertFalse(upper.isEmpty());
        assertOwned(lower, terrain);
        assertOwned(upper, terrain);
    }

    private static void assertCanonicalColumnOrder(List<BlockPos> positions) {
        int previousZ = Integer.MIN_VALUE;
        int previousX = Integer.MIN_VALUE;
        for (BlockPos position : positions) {
            if (position.getZ() == previousZ) {
                assertTrue(position.getX() >= previousX,
                        "hydrology columns must retain canonical x order inside each z row");
            } else {
                assertTrue(position.getZ() > previousZ,
                        "hydrology columns must retain canonical z-major order");
                previousZ = position.getZ();
                previousX = Integer.MIN_VALUE;
            }
            previousX = position.getX();
        }
    }

    private static double distanceToPath(
            io.github.nidaba.skyforge.world.SkyIslandLocalPosition position,
            io.github.nidaba.skyforge.world.SkyIslandNaturalizedChannelPath path) {
        double best = Double.POSITIVE_INFINITY;
        var points = path.points();
        for (int index = 1; index < points.size(); index++) {
            var a = points.get(index - 1);
            var b = points.get(index);
            double dx = b.x() - a.x();
            double dz = b.z() - a.z();
            double lengthSquared = dx * dx + dz * dz;
            if (lengthSquared <= 1.0e-12) {
                best = Math.min(best, Math.hypot(position.x() - a.x(), position.z() - a.z()));
                continue;
            }
            double px = position.x() - a.x();
            double pz = position.z() - a.z();
            double fraction = Math.max(0.0, Math.min(1.0, (px * dx + pz * dz) / lengthSquared));
            double nearestX = a.x() + fraction * dx;
            double nearestZ = a.z() + fraction * dz;
            best = Math.min(best, Math.hypot(position.x() - nearestX, position.z() - nearestZ));
        }
        return best;
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
        // Drop semantics are geomorphic modifiers of routed channels, not independent
        // Minecraft fluid deployments.
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
                SkyforgeAuthoredVisibleHydrologyAdapter.Feature.RETAINED_WATER);
    }

    private static void assertOwned(
            List<SkyforgeAuthoredVisibleHydrologyAdapter.Deployment> deployments,
            SkyforgeNeoForge1211ChunkAdapter terrain) {
        for (var deployment : deployments) {
            assertFalse(deployment.positions().isEmpty());
            var semanticPositions = new java.util.ArrayList<BlockPos>();
            semanticPositions.addAll(deployment.positions());
            semanticPositions.addAll(deployment.carvedPositions());
            semanticPositions.addAll(deployment.surfacePositions());
            for (var position : semanticPositions) {
                assertTrue(terrain.isSolidOwnedBy(
                        deployment.volumeId(), position.getX(), position.getY(), position.getZ()));
                assertFalse(terrain.isSolidOwnedByOtherVolume(
                        deployment.volumeId(), position.getX(), position.getY(), position.getZ()));
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
        var lower = volume(
                descriptor, 830_000L + key, "dr20-accepted-corpus-stacked/lower/" + key,
                170.0, 30.0, 38.0, rimDepth, 55.0, 45.0);
        var upper = volume(
                descriptor, 840_000L + key, "dr20-accepted-corpus-stacked/upper/" + key,
                260.0, 30.0, 38.0, rimDepth, 55.0, 45.0);
        return new StackedCorpusFixture(
                descriptor,
                lower,
                upper,
                new io.github.nidaba.skyforge.world.SkyIslandWorldCatalog(WORLD_SEED, List.of(lower, upper)));
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
        double radius = descriptor.nominalRadius();
        var physicalDescriptor = io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor.schema2(
                seed,
                0.0,
                0.0,
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
                -radius * 1.08,
                radius * 1.08,
                suspensionY - lowerBounds,
                suspensionY + upperBounds,
                -radius * 1.08,
                radius * 1.08);
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