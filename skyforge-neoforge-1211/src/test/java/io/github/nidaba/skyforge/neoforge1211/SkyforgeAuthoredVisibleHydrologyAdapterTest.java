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
            for (var surface : deployment.surfacePositions()) {
                var state = terrain.authoredHydrologyPopulationState(fixture.volume().id(), surface)
                        .orElseThrow();
                assertTrue(SkyforgeAuthoredVisibleHydrologyAdapter.isHydrologySurfaceMaterial(state));
                assertFalse(state.is(Blocks.DIRT),
                        "authored river/lake substrate must not fall back to placeholder dirt");
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

        assertFalse(deployment.positions().isEmpty(),
                "standing water must materialize at least one authored wet cell");
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
        assertRetainedShorelineCutBounded(
                fixture.volume().id(),
                topByColumn.values(),
                waterTopY,
                terrain);

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
                BlockPos bank = new BlockPos(
                        position.getX() + direction[0],
                        waterTopY,
                        position.getZ() + direction[1]);
                boolean naturalBank = terrain.isSolidOwnedBy(
                                fixture.volume().id(),
                                bank.getX(),
                                bank.getY(),
                                bank.getZ())
                        && !terrain.isSolidOwnedByOtherVolume(
                                fixture.volume().id(),
                                bank.getX(),
                                bank.getY(),
                                bank.getZ());
                boolean conditionedBank = terrain.authoredHydrologyPopulationState(
                                fixture.volume().id(), bank)
                        .map(SkyforgeAuthoredVisibleHydrologyAdapter::isHydrologySurfaceMaterial)
                        .orElse(false);
                assertTrue(
                        naturalBank || conditionedBank,
                        "retained water boundary must be naturally banked or use bounded authored bank fill");
            }
        }
    }

    @Test
    @Tag("qualification")
    void roundedRetainedShorelineRasterPreservesCardinalCoarseConnections() {
        CorpusFixture fixture = corpus(83L);
        var watershed = io.github.nidaba.skyforge.world.SkyIslandWatershedPlanner.plan(
                fixture.descriptor());
        var footprint = io.github.nidaba.skyforge.world.SkyIslandVisibleHydrologicRealizationPlanner
                .plan(fixture.descriptor())
                .retainedWater()
                .getFirst()
                .footprint();
        var cells = footprint.cells().stream().collect(java.util.stream.Collectors.toMap(
                io.github.nidaba.skyforge.world.SkyIslandWaterbodyFootprintCell::watershedCellIndex,
                java.util.function.Function.identity()));

        io.github.nidaba.skyforge.world.SkyIslandWaterbodyFootprintCell first = null;
        io.github.nidaba.skyforge.world.SkyIslandWaterbodyFootprintCell second = null;
        for (var candidate : footprint.cells()) {
            if (!candidate.shoreline()) {
                continue;
            }
            int index = candidate.watershedCellIndex();
            int x = index % watershed.gridSize();
            int z = index / watershed.gridSize();
            int[] neighbors = {
                    x + 1 < watershed.gridSize() ? index + 1 : -1,
                    z + 1 < watershed.gridSize() ? index + watershed.gridSize() : -1
            };
            for (int neighborIndex : neighbors) {
                var neighbor = cells.get(neighborIndex);
                if (neighbor != null) {
                    first = candidate;
                    second = neighbor;
                    break;
                }
            }
            if (first != null) {
                break;
            }
        }
        assertNotNull(first, "accepted retained footprint must expose a cardinal shoreline connection");
        assertNotNull(second);

        var midpoint = new io.github.nidaba.skyforge.world.SkyIslandLocalPosition(
                0.5 * (first.position().x() + second.position().x()),
                0.5 * (first.position().z() + second.position().z()));
        double halfSpacing = watershed.spacing() * 0.5;
        assertTrue(
                SkyforgeAuthoredVisibleHydrologyAdapter.retainedShorelineContains(
                        midpoint, first, cells, watershed, halfSpacing),
                "rounded shoreline raster must bridge retained cardinal neighbors through their shared edge");
        assertTrue(
                SkyforgeAuthoredVisibleHydrologyAdapter.retainedShorelineContains(
                        midpoint, second, cells, watershed, halfSpacing),
                "cardinal shoreline bridge must be symmetric across the coarse-cell boundary");
    }

    @Test
    @Tag("qualification")
    void hydrologyReferenceKey287RetainedWaterIsFlatConnectedAndLocallyConditioned() {
        var fixture = SkyforgeHydrologyReferenceReviewFixture.create();
        var terrain = terrain(fixture.catalog(), fixture.descriptor());
        var retained = terrain.authoredHydrologyDeployments(fixture.volume().id()).stream()
                .filter(deployment ->
                        deployment.feature() == SkyforgeAuthoredVisibleHydrologyAdapter.Feature.RETAINED_WATER)
                .toList();

        assertFalse(retained.isEmpty(), "key 287 must retain its authored standing-water body");
        for (var deployment : retained) {
            var topByColumn = new java.util.LinkedHashMap<String, BlockPos>();
            var wetColumns = new java.util.LinkedHashSet<SkyforgeAuthoredVisibleHydrologyAdapter.Column>();
            for (var wet : deployment.positions()) {
                String key = wet.getX() + "," + wet.getZ();
                topByColumn.merge(
                        key,
                        wet,
                        (first, second) -> first.getY() >= second.getY() ? first : second);
                wetColumns.add(new SkyforgeAuthoredVisibleHydrologyAdapter.Column(
                        wet.getX(), wet.getZ()));
            }
            assertEquals(
                    1L,
                    topByColumn.values().stream().map(BlockPos::getY).distinct().count(),
                    "one retained waterbody must expose one flat Minecraft surface");
            assertEquals(
                    wetColumns.size(),
                    SkyforgeAuthoredVisibleHydrologyAdapter.largestConnectedFootprint(wetColumns).size(),
                    "key-287 retained water must remain one face-connected waterbody");

            int waterTopY = topByColumn.values().iterator().next().getY();
            assertRetainedShorelineCutBounded(
                    fixture.volume().id(),
                    topByColumn.values(),
                    waterTopY,
                    terrain);
            int maximumInteriorCut =
                    SkyforgeAuthoredVisibleHydrologyAdapter.maximumRetainedInteriorCutBlocks(
                            fixture.descriptor());
            for (BlockPos position : topByColumn.values()) {
                var range = terrain.integerSolidRange(
                                fixture.volume().id(),
                                position.getX(),
                                position.getZ())
                        .orElseThrow();
                assertTrue(
                        Math.max(0, range.maximumY() - waterTopY) <= maximumInteriorCut,
                        "retained-water interior conditioning must stay inside the neutral "
                                + "hydrologic-relief budget");
            }
            assertTrue(deployment.surfacePositions().stream().allMatch(position ->
                    SkyforgeAuthoredVisibleHydrologyAdapter.isHydrologySurfaceMaterial(
                            terrain.authoredHydrologyPopulationState(fixture.volume().id(), position)
                                    .orElseThrow())),
                    "key-287 retained basin substrate must use authored hydrology sediment");
        }

        var retainedColumns = retained.stream()
                .flatMap(deployment -> deployment.positions().stream())
                .map(position -> new SkyforgeAuthoredVisibleHydrologyAdapter.Column(
                        position.getX(), position.getZ()))
                .collect(java.util.stream.Collectors.toSet());

        var intent = io.github.nidaba.skyforge.world.SkyIslandVisibleHydrologicRealizationPlanner.plan(
                fixture.descriptor());
        var fluvial = io.github.nidaba.skyforge.world.SkyIslandFluvialTerrainField.create(
                fixture.descriptor(), intent.coherentHydrology());
        var channels = terrain.authoredHydrologyDeployments(fixture.volume().id()).stream()
                .filter(deployment ->
                        deployment.feature() == SkyforgeAuthoredVisibleHydrologyAdapter.Feature.CHANNEL)
                .toList();
        assertEquals(intent.channels().size(), channels.size());
        var physical = fixture.volume().compiledVolume().descriptor();
        for (int index = 0; index < channels.size(); index++) {
            var deployment = channels.get(index);
            var path = intent.channels().get(index).path();
            var reach = fluvial.reaches().stream()
                    .filter(candidate -> candidate.path().equals(path))
                    .findFirst()
                    .orElseThrow();
            var topByColumn = new java.util.LinkedHashMap<String, BlockPos>();
            for (var wet : deployment.positions()) {
                String key = wet.getX() + "," + wet.getZ();
                topByColumn.merge(
                        key,
                        wet,
                        (first, second) -> first.getY() >= second.getY() ? first : second);
            }
            int previousWaterTop = Integer.MAX_VALUE;
            for (var point : path.points()) {
                BlockPos nearest = topByColumn.values().stream()
                        .min(java.util.Comparator.comparingDouble(position -> Math.hypot(
                                position.getX() - physical.centerX() - point.x(),
                                position.getZ() - physical.centerZ() - point.z())))
                        .orElseThrow();
                double distance = Math.hypot(
                        nearest.getX() - physical.centerX() - point.x(),
                        nearest.getZ() - physical.centerZ() - point.z());
                assertTrue(
                        distance <= reach.wetHalfWidth() + 1.0,
                        "reference channel centerline must remain represented by nearby wet columns");
                var nearestColumn = new SkyforgeAuthoredVisibleHydrologyAdapter.Column(
                        nearest.getX(), nearest.getZ());
                if (retainedColumns.contains(nearestColumn)) {
                    // Retained water owns its flat basin datum. Treat that domain as a hydraulic
                    // boundary for per-reach grade evidence; the channel's non-climbing constraint
                    // resumes when it exits the lake instead of forcing a submerged trench/grade
                    // through the basin.
                    previousWaterTop = Integer.MAX_VALUE;
                    continue;
                }
                assertTrue(
                        nearest.getY() <= previousWaterTop,
                        "channel-owned free surface must not climb downstream outside retained water");
                previousWaterTop = nearest.getY();
            }
        }
    }

    @Test
    @Tag("qualification")
    void hydrologyReferenceWaterColumnsAreVerticallyFilledOntoOwnedBeds() {
        var fixture = SkyforgeHydrologyReferenceReviewFixture.create();
        var terrain = terrain(fixture.catalog(), fixture.descriptor());
        var deployments = terrain.authoredHydrologyDeployments(fixture.volume().id());

        // Deployment lists preserve authored feature provenance, so overlapping channel/lake or
        // retained-water intents may share one final physical water column. Validate the normalized
        // physical union rather than requiring every provenance list to expose its own independent
        // bed inside water owned by another deployment.
        var globalWater = deployments.stream()
                .flatMap(deployment -> deployment.positions().stream())
                .collect(java.util.stream.Collectors.toSet());
        var globalSurface = deployments.stream()
                .flatMap(deployment -> deployment.surfacePositions().stream())
                .collect(java.util.stream.Collectors.toSet());

        var byColumn = globalWater.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        position -> new SkyforgeAuthoredVisibleHydrologyAdapter.Column(
                                position.getX(), position.getZ())));
        for (var entry : byColumn.entrySet()) {
            var ys = entry.getValue().stream()
                    .map(BlockPos::getY)
                    .sorted()
                    .toList();
            assertEquals(
                    ys.getLast() - ys.getFirst() + 1,
                    ys.size(),
                    () -> {
                        var contributors = deployments.stream()
                                .filter(deployment -> deployment.positions().stream().anyMatch(position ->
                                        position.getX() == entry.getKey().x()
                                                && position.getZ() == entry.getKey().z()))
                                .map(deployment -> deployment.feature() + "="
                                        + deployment.positions().stream()
                                                .filter(position -> position.getX() == entry.getKey().x()
                                                        && position.getZ() == entry.getKey().z())
                                                .map(BlockPos::getY)
                                                .sorted()
                                                .toList())
                                .toList();
                        return "normalized authored water must fill every vertical voxel from bed to free surface: "
                                + entry.getKey()
                                + ", ys=" + ys
                                + ", contributors=" + contributors;
                    });
            BlockPos bed = new BlockPos(
                    entry.getKey().x(),
                    ys.getFirst() - 1,
                    entry.getKey().z());
            assertTrue(
                    globalSurface.contains(bed),
                    "lowest normalized authored water voxel must rest on a hydrology-owned bed: "
                            + entry.getKey());
        }
    }

    @Test
    @Tag("qualification")
    void hydrologyReferenceRetainedWaterHasNoStronglyEnclosedRasterPinhole() {
        var fixture = SkyforgeHydrologyReferenceReviewFixture.create();
        var terrain = terrain(fixture.catalog(), fixture.descriptor());
        var wet = terrain.authoredHydrologyDeployments(fixture.volume().id()).stream()
                .filter(deployment ->
                        deployment.feature() == SkyforgeAuthoredVisibleHydrologyAdapter.Feature.RETAINED_WATER)
                .flatMap(deployment -> deployment.positions().stream())
                .map(position -> new SkyforgeAuthoredVisibleHydrologyAdapter.Column(
                        position.getX(), position.getZ()))
                .collect(java.util.stream.Collectors.toSet());
        assertFalse(wet.isEmpty());

        int minimumX = wet.stream().mapToInt(SkyforgeAuthoredVisibleHydrologyAdapter.Column::x).min().orElseThrow();
        int maximumX = wet.stream().mapToInt(SkyforgeAuthoredVisibleHydrologyAdapter.Column::x).max().orElseThrow();
        int minimumZ = wet.stream().mapToInt(SkyforgeAuthoredVisibleHydrologyAdapter.Column::z).min().orElseThrow();
        int maximumZ = wet.stream().mapToInt(SkyforgeAuthoredVisibleHydrologyAdapter.Column::z).max().orElseThrow();
        for (int z = minimumZ; z <= maximumZ; z++) {
            for (int x = minimumX; x <= maximumX; x++) {
                var candidate = new SkyforgeAuthoredVisibleHydrologyAdapter.Column(x, z);
                if (wet.contains(candidate)) {
                    continue;
                }
                assertTrue(
                        SkyforgeAuthoredVisibleHydrologyAdapter.retainedRasterNeighborCount(
                                wet, candidate) < 3,
                        "retained-water raster must not leave a strongly enclosed rectangular pinhole at "
                                + candidate);
            }
        }
    }

    @Test
    @Tag("qualification")
    void hydrologyReferenceRiverLakeMouthIsMultiColumnAndFaceConnected() {
        var fixture = SkyforgeHydrologyReferenceReviewFixture.create();
        var terrain = terrain(fixture.catalog(), fixture.descriptor());
        var deployments = terrain.authoredHydrologyDeployments(fixture.volume().id());

        var retainedColumns = deployments.stream()
                .filter(deployment ->
                        deployment.feature() == SkyforgeAuthoredVisibleHydrologyAdapter.Feature.RETAINED_WATER)
                .flatMap(deployment -> deployment.positions().stream())
                .map(position -> new SkyforgeAuthoredVisibleHydrologyAdapter.Column(
                        position.getX(), position.getZ()))
                .collect(java.util.stream.Collectors.toSet());
        assertFalse(retainedColumns.isEmpty());

        int testedMouths = 0;
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (var deployment : deployments) {
            if (deployment.feature()
                    != SkyforgeAuthoredVisibleHydrologyAdapter.Feature.CHANNEL) {
                continue;
            }
            var channelColumns = deployment.positions().stream()
                    .map(position -> new SkyforgeAuthoredVisibleHydrologyAdapter.Column(
                            position.getX(), position.getZ()))
                    .collect(java.util.stream.Collectors.toSet());
            if (channelColumns.stream().noneMatch(retainedColumns::contains)) {
                continue;
            }
            var outsideColumns = channelColumns.stream()
                    .filter(column -> !retainedColumns.contains(column))
                    .collect(java.util.stream.Collectors.toSet());
            if (outsideColumns.isEmpty()) {
                // A reach fully absorbed by a retained basin has no external shoreline mouth.
                // Its overlap remains valid provenance, but mouth geometry is tested only where
                // channel water actually crosses from non-basin columns into retained water.
                continue;
            }
            var mouth = outsideColumns.stream()
                    .filter(column -> {
                        for (int[] direction : directions) {
                            if (retainedColumns.contains(
                                    new SkyforgeAuthoredVisibleHydrologyAdapter.Column(
                                            column.x() + direction[0],
                                            column.z() + direction[1]))) {
                                return true;
                            }
                        }
                        return false;
                    })
                    .collect(java.util.stream.Collectors.toSet());
            assertTrue(
                    mouth.size() >= 2,
                    "river/lake junction must expose a multi-column face-connected wet mouth; mouth="
                            + mouth);
            testedMouths++;
        }
        assertTrue(testedMouths > 0, "key-287 fixture must exercise at least one river/lake mouth");
    }

    @Test
    @Tag("qualification")
    void hydrologyReferenceConnectedRetainedRasterHasOnePhysicalDatum() {
        var fixture = SkyforgeHydrologyReferenceReviewFixture.create();
        var terrain = terrain(fixture.catalog(), fixture.descriptor());
        var retained = terrain.authoredHydrologyDeployments(fixture.volume().id()).stream()
                .filter(deployment ->
                        deployment.feature() == SkyforgeAuthoredVisibleHydrologyAdapter.Feature.RETAINED_WATER)
                .toList();

        var topByColumn =
                new java.util.LinkedHashMap<SkyforgeAuthoredVisibleHydrologyAdapter.Column, Integer>();
        for (var deployment : retained) {
            for (var wet : deployment.positions()) {
                var column = new SkyforgeAuthoredVisibleHydrologyAdapter.Column(
                        wet.getX(), wet.getZ());
                topByColumn.merge(column, wet.getY(), Math::max);
            }
        }
        assertFalse(topByColumn.isEmpty());

        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (var entry : topByColumn.entrySet()) {
            var column = entry.getKey();
            for (int[] direction : directions) {
                var neighbor = new SkyforgeAuthoredVisibleHydrologyAdapter.Column(
                        column.x() + direction[0],
                        column.z() + direction[1]);
                Integer neighborTop = topByColumn.get(neighbor);
                if (neighborTop == null) {
                    continue;
                }
                assertEquals(
                        entry.getValue(),
                        neighborTop,
                        "face-connected retained water cannot expose split standing-water datums");
            }
        }
    }

    @Test
    @Tag("qualification")
    void hydrologyReferenceChannelLakeJunctionApproachesBasinDatumWithoutSyntheticLip() {
        var fixture = SkyforgeHydrologyReferenceReviewFixture.create();
        var terrain = terrain(fixture.catalog(), fixture.descriptor());
        var deployments = terrain.authoredHydrologyDeployments(fixture.volume().id());

        var retainedTop =
                new java.util.LinkedHashMap<SkyforgeAuthoredVisibleHydrologyAdapter.Column, Integer>();
        for (var deployment : deployments) {
            if (deployment.feature()
                    != SkyforgeAuthoredVisibleHydrologyAdapter.Feature.RETAINED_WATER) {
                continue;
            }
            for (var wet : deployment.positions()) {
                var column = new SkyforgeAuthoredVisibleHydrologyAdapter.Column(
                        wet.getX(), wet.getZ());
                retainedTop.merge(column, wet.getY(), Math::max);
            }
        }
        assertFalse(retainedTop.isEmpty());

        var intent = io.github.nidaba.skyforge.world.SkyIslandVisibleHydrologicRealizationPlanner.plan(
                fixture.descriptor());
        var fluvial = io.github.nidaba.skyforge.world.SkyIslandFluvialTerrainField.create(
                fixture.descriptor(), intent.coherentHydrology());
        var physical = fixture.volume().compiledVolume().descriptor();

        var retainedCellIndices = intent.retainedWater().stream()
                .flatMap(retained -> retained.footprint().cells().stream())
                .map(io.github.nidaba.skyforge.world.SkyIslandWaterbodyFootprintCell::watershedCellIndex)
                .collect(java.util.stream.Collectors.toSet());

        int junctionColumns = 0;
        int channelIndex = 0;
        for (var deployment : deployments) {
            if (deployment.feature()
                    != SkyforgeAuthoredVisibleHydrologyAdapter.Feature.CHANNEL) {
                continue;
            }
            var channelIntent = intent.channels().get(channelIndex++);
            var reach = fluvial.reaches().stream()
                    .filter(candidate -> candidate.path().equals(channelIntent.path()))
                    .findFirst()
                    .orElseThrow();
            int sourceCell = channelIntent.path().profile().segment().sourceCellIndex();
            int downstreamCell = channelIntent.path().profile().segment().downstreamCellIndex();
            boolean semanticallyConnectedToRetained =
                    retainedCellIndices.contains(sourceCell)
                            || retainedCellIndices.contains(downstreamCell);
            var channelTop =
                    new java.util.LinkedHashMap<SkyforgeAuthoredVisibleHydrologyAdapter.Column, Integer>();
            for (var wet : deployment.positions()) {
                var column = new SkyforgeAuthoredVisibleHydrologyAdapter.Column(
                        wet.getX(), wet.getZ());
                channelTop.merge(column, wet.getY(), Math::max);
            }
            if (!semanticallyConnectedToRetained) {
                continue;
            }
            for (var entry : channelTop.entrySet()) {
                if (retainedTop.containsKey(entry.getKey())) {
                    continue;
                }
                var target = SkyforgeAuthoredVisibleHydrologyAdapter.retainedHydraulicTarget(
                        entry.getKey(), retainedTop);
                if (target.isEmpty()) {
                    continue;
                }
                junctionColumns++;
                int retainedDatum = target.orElseThrow();
                var local = new io.github.nidaba.skyforge.world.SkyIslandLocalPosition(
                        entry.getKey().x() - physical.centerX(),
                        entry.getKey().z() - physical.centerZ());
                var start = channelIntent.path().points().getFirst();
                var end = channelIntent.path().points().getLast();
                double startDistance = Math.hypot(local.x() - start.x(), local.z() - start.z());
                double endDistance = Math.hypot(local.x() - end.x(), local.z() - end.z());
                var terminalDrop = fluvial.terminalDrop(reach);
                assertTrue(
                        Math.abs(entry.getValue() - retainedDatum) <= 2,
                        "channel/lake transition must remain a bounded cascade rather than a separate terrace: "
                                + "channelIndex=" + (channelIndex - 1)
                                + ", sourceCell=" + sourceCell
                                + ", downstreamCell=" + downstreamCell
                                + ", terminalDrop=" + terminalDrop.map(drop -> drop.kind().name()).orElse("NONE")
                                + ", startDistance=" + startDistance
                                + ", endDistance=" + endDistance
                                + ", column=" + entry.getKey()
                                + ", channelTop=" + entry.getValue()
                                + ", retainedDatum=" + retainedDatum);
            }
            assertTrue(
                    deployment.forcedSurfacePositions().isEmpty(),
                    "key-287 channels must reach standing water without synthetic bank repair");
        }
        assertTrue(
                junctionColumns > 0,
                "key-287 review specimen must exercise at least one river/lake hydraulic junction");
    }

    @Test
    void retainedJunctionBlendExtendsOnlyAcrossBoundedPhysicalApproach() {
        var retained = java.util.Map.of(
                new SkyforgeAuthoredVisibleHydrologyAdapter.Column(0, 0), 100);

        var atLake = SkyforgeAuthoredVisibleHydrologyAdapter.retainedHydraulicBlendTarget(
                new SkyforgeAuthoredVisibleHydrologyAdapter.Column(0, 0), retained).orElseThrow();
        assertEquals(100, atLake.datum());
        assertEquals(0, atLake.distanceBlocks());

        var approach = SkyforgeAuthoredVisibleHydrologyAdapter.retainedHydraulicBlendTarget(
                new SkyforgeAuthoredVisibleHydrologyAdapter.Column(2, 1), retained).orElseThrow();
        assertEquals(100, approach.datum());
        assertEquals(3, approach.distanceBlocks());

        assertTrue(SkyforgeAuthoredVisibleHydrologyAdapter.retainedHydraulicBlendTarget(
                        new SkyforgeAuthoredVisibleHydrologyAdapter.Column(
                                SkyforgeAuthoredVisibleHydrologyAdapter.RETAINED_JUNCTION_BLEND_BLOCKS,
                                0),
                        retained)
                .isPresent());
        assertTrue(SkyforgeAuthoredVisibleHydrologyAdapter.retainedHydraulicBlendTarget(
                        new SkyforgeAuthoredVisibleHydrologyAdapter.Column(
                                SkyforgeAuthoredVisibleHydrologyAdapter.RETAINED_JUNCTION_BLEND_BLOCKS + 1,
                                0),
                        retained)
                .isEmpty());
    }

    @Test
    void retainedJunctionBlendRejectsEquidistantConflictingLakeDatums() {
        var retained = java.util.Map.of(
                new SkyforgeAuthoredVisibleHydrologyAdapter.Column(-1, 0), 100,
                new SkyforgeAuthoredVisibleHydrologyAdapter.Column(1, 0), 102);
        assertThrows(
                IllegalStateException.class,
                () -> SkyforgeAuthoredVisibleHydrologyAdapter.retainedHydraulicBlendTarget(
                        new SkyforgeAuthoredVisibleHydrologyAdapter.Column(0, 0), retained));
    }

    @Test
    @Tag("qualification")
    void retainedWaterOwnsChannelOverlapColumns() {
        var fixture = SkyforgeHydrologyReferenceReviewFixture.create();
        var terrain = terrain(fixture.catalog(), fixture.descriptor());
        var deployments = terrain.authoredHydrologyDeployments(fixture.volume().id());

        var retainedWater = new java.util.HashSet<BlockPos>();
        var retainedColumns =
                new java.util.HashSet<SkyforgeAuthoredVisibleHydrologyAdapter.Column>();
        for (var deployment : deployments) {
            if (deployment.feature()
                    != SkyforgeAuthoredVisibleHydrologyAdapter.Feature.RETAINED_WATER) {
                continue;
            }
            retainedWater.addAll(deployment.positions());
            for (var position : deployment.positions()) {
                retainedColumns.add(new SkyforgeAuthoredVisibleHydrologyAdapter.Column(
                        position.getX(), position.getZ()));
            }
        }
        assertFalse(retainedColumns.isEmpty());

        for (var deployment : deployments) {
            if (deployment.feature()
                    != SkyforgeAuthoredVisibleHydrologyAdapter.Feature.CHANNEL) {
                continue;
            }
            for (var wet : deployment.positions()) {
                var column = new SkyforgeAuthoredVisibleHydrologyAdapter.Column(
                        wet.getX(), wet.getZ());
                if (retainedColumns.contains(column)) {
                    assertTrue(
                            retainedWater.contains(wet),
                            "channel water inside a retained basin column must coincide with the "
                                    + "basin's own vertical water volume");
                }
            }
            assertTrue(
                    deployment.carvedPositions().stream().noneMatch(position ->
                            retainedColumns.contains(new SkyforgeAuthoredVisibleHydrologyAdapter.Column(
                                    position.getX(), position.getZ()))),
                    "retained water must suppress channel excavation through the lake bed");
            assertTrue(
                    deployment.surfacePositions().stream().noneMatch(position ->
                            retainedColumns.contains(new SkyforgeAuthoredVisibleHydrologyAdapter.Column(
                                    position.getX(), position.getZ()))),
                    "retained water must suppress channel substrate stripes through the lake bed");
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
                        fluvial.wetCorridorContains(reach, local),
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
            var forcedSurface = new java.util.HashSet<>(deployment.forcedSurfacePositions());
            assertTrue(deployment.surfacePositions().containsAll(forcedSurface));
            for (var position : deployment.surfacePositions()) {
                var actual = chunks.get(new net.minecraft.world.level.ChunkPos(position).toLong())
                        .getBlockState(position);
                assertFalse(actual.isAir(),
                        "hydrology bed/bank geometry must retain a solid substrate for native surfacing");
                assertTrue(actual.getFluidState().isEmpty(),
                        "hydrology bed/bank geometry must remain a dry solid substrate");
                assertTrue(
                        terrain.authoredHydrologyPopulationState(fixture.volume().id(), position)
                                .map(SkyforgeAuthoredVisibleHydrologyAdapter::isHydrologySurfaceMaterial)
                                .orElse(false),
                        "preserved and repaired beds must remain hydrology-owned for native dressing");
                if (forcedSurface.contains(position)) {
                    assertTrue(
                            terrain.isSolidOwnedBy(
                                    fixture.volume().id(),
                                    position.getX(),
                                    Math.min(
                                            position.getY(),
                                            terrain.integerSolidRange(
                                                            fixture.volume().id(),
                                                            position.getX(),
                                                            position.getZ())
                                                    .orElseThrow()
                                                    .maximumY()),
                                    position.getZ()),
                            "forced bank geometry must remain anchored to the exact owning carrier");
                }
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
    void productionHydrologyUsesOnlyBoundedSupportedBankGeometry() {
        var fixture = SkyforgeHydrologyReferenceReviewFixture.create();
        var terrain = terrain(fixture.catalog(), fixture.descriptor());
        var deployments = terrain.authoredHydrologyDeployments(fixture.volume().id());

        assertEquals(0, SkyforgeAuthoredVisibleHydrologyAdapter.MAX_CHANNEL_BANK_FILL_BLOCKS);
        assertTrue(SkyforgeAuthoredVisibleHydrologyAdapter.MAX_RETAINED_BANK_FILL_BLOCKS > 0);
        for (var deployment : deployments) {
            assertTrue(deployment.surfacePositions().containsAll(deployment.forcedSurfacePositions()));
            if (deployment.feature()
                    == SkyforgeAuthoredVisibleHydrologyAdapter.Feature.CHANNEL) {
                assertTrue(
                        deployment.forcedSurfacePositions().isEmpty(),
                        "flowing channels must not synthesize levees to repair carrier mismatch");
            }
            for (var position : deployment.forcedSurfacePositions()) {
                var range = terrain.integerSolidRange(
                                deployment.volumeId(), position.getX(), position.getZ())
                        .orElseThrow(() -> new AssertionError(
                                "bounded bank geometry must extend an existing physical carrier"));
                int fillDepth = position.getY() - range.maximumY();
                if (fillDepth > 0) {
                    int limit = deployment.feature()
                                    == SkyforgeAuthoredVisibleHydrologyAdapter.Feature.RETAINED_WATER
                            ? SkyforgeAuthoredVisibleHydrologyAdapter.MAX_RETAINED_BANK_FILL_BLOCKS
                            : SkyforgeAuthoredVisibleHydrologyAdapter.MAX_CHANNEL_BANK_FILL_BLOCKS;
                    assertTrue(
                            fillDepth <= limit,
                            "bank extension above compiled terrain must stay inside the bounded fill budget");
                } else {
                    assertTrue(
                            position.getY() >= range.minimumY(),
                            "bank reconstruction inside compiled terrain must stay inside the exact owner range");
                }
            }
        }
    }

    @Test
    void chunkIndexExactlyPartitionsNormalizedHydrologyAndDrivesIdempotentChunkLocalReplay() {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.single();
        var terrain = terrain(fixture.catalog(), fixture.descriptor());
        var deployments = terrain.authoredHydrologyDeployments(fixture.volume().id());
        var indexed = SkyforgeAuthoredVisibleHydrologyAdapter.indexByChunk(deployments);

        var expected = new java.util.LinkedHashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
        var expectedSurface = new java.util.LinkedHashSet<BlockPos>();
        for (var deployment : deployments) {
            expectedSurface.addAll(deployment.surfacePositions());
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
        assertEquals(
                expectedSurface.size(),
                indexed.values().stream().mapToInt(projection -> projection.surfacePositions().size()).sum());
        assertEquals(
                deployments.stream().mapToInt(deployment -> deployment.forcedSurfacePositions().size()).sum(),
                indexed.values().stream().mapToInt(projection -> projection.forcedSurfacePositions().size()).sum());
        for (var entry : indexed.entrySet()) {
            long chunkKey = entry.getKey();
            for (var state : entry.getValue().states().entrySet()) {
                assertEquals(chunkKey, new net.minecraft.world.level.ChunkPos(state.getKey()).toLong());
                assertEquals(expected.get(state.getKey()), state.getValue());
            }
            for (var surface : entry.getValue().surfacePositions()) {
                assertEquals(chunkKey, new net.minecraft.world.level.ChunkPos(surface).toLong());
                assertTrue(expectedSurface.contains(surface));
                assertTrue(entry.getValue().populationState(surface)
                        .map(SkyforgeAuthoredVisibleHydrologyAdapter::isHydrologySurfaceMaterial)
                        .orElse(false));
            }
        }

        var first = indexed.entrySet().stream()
                .filter(entry -> !entry.getValue().states().isEmpty())
                .findFirst()
                .orElseThrow();
        var chunkPos = new net.minecraft.world.level.ChunkPos(first.getKey());
        var chunk = MinecraftTestChunkFactory.protoChunk(chunkPos);

        // Reproduce the real runtime precondition for bounded bank reconciliation: the compiled
        // island terrain already exists beneath every extension column. The replay test previously
        // used an otherwise-empty ProtoChunk, which cannot exercise support-inheriting geometry.
        var forcedByColumn = first.getValue().forcedSurfacePositions().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        position -> position.getX() + "," + position.getZ()));
        for (var column : forcedByColumn.values()) {
            int minimumExtensionY = column.stream().mapToInt(BlockPos::getY).min().orElseThrow();
            BlockPos firstExtension = column.stream()
                    .filter(position -> position.getY() == minimumExtensionY)
                    .findFirst()
                    .orElseThrow();
            chunk.setBlockState(
                    firstExtension.below(),
                    Blocks.STONE.defaultBlockState(),
                    false);
        }

        for (var state : first.getValue().states().entrySet()) {
            // Seed a guaranteed mismatch for every indexed AIR/water role.
            var seed = state.getValue().isAir()
                    ? Blocks.STONE.defaultBlockState()
                    : Blocks.DIRT.defaultBlockState();
            chunk.setBlockState(state.getKey(), seed, false);
        }

        int expectedWrites = first.getValue().states().size()
                + first.getValue().forcedSurfacePositions().size();
        assertEquals(
                expectedWrites,
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
    void deploymentDefensivelyCopiesCallerCollections() {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.single();
        var volumeId = fixture.volume().id();
        var water = new java.util.ArrayList<BlockPos>();
        var carved = new java.util.ArrayList<BlockPos>();
        var surface = new java.util.ArrayList<BlockPos>();
        var forcedSurface = new java.util.ArrayList<BlockPos>();
        water.add(new BlockPos(1, 64, 1));
        carved.add(new BlockPos(2, 64, 1));
        surface.add(new BlockPos(3, 64, 1));
        forcedSurface.add(new BlockPos(3, 64, 1));

        var deployment = new SkyforgeAuthoredVisibleHydrologyAdapter.Deployment(
                volumeId,
                SkyforgeAuthoredVisibleHydrologyAdapter.Feature.CHANNEL,
                water,
                carved,
                surface,
                forcedSurface);
        water.clear();
        carved.clear();
        surface.clear();
        forcedSurface.clear();

        assertEquals(1, deployment.positions().size());
        assertEquals(1, deployment.carvedPositions().size());
        assertEquals(1, deployment.surfacePositions().size());
        assertEquals(1, deployment.forcedSurfacePositions().size());
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
                        List.of(),
                        List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyforgeAuthoredVisibleHydrologyAdapter.Deployment(
                        volumeId,
                        SkyforgeAuthoredVisibleHydrologyAdapter.Feature.CHANNEL,
                        List.of(water),
                        List.of(),
                        List.of(water),
                        List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyforgeAuthoredVisibleHydrologyAdapter.Deployment(
                        volumeId,
                        SkyforgeAuthoredVisibleHydrologyAdapter.Feature.CHANNEL,
                        List.of(water),
                        List.of(carved),
                        List.of(carved),
                        List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyforgeAuthoredVisibleHydrologyAdapter.Deployment(
                        volumeId,
                        SkyforgeAuthoredVisibleHydrologyAdapter.Feature.CHANNEL,
                        List.of(water),
                        List.of(),
                        List.of(),
                        List.of(new BlockPos(3, 64, 1))));
    }

    @Test
    void staticAuthoredWaterCanBePersistedWithoutAChunkLifecycleBinding() {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.single();
        var deployment = new SkyforgeAuthoredVisibleHydrologyAdapter.Deployment(
                fixture.volume().id(),
                SkyforgeAuthoredVisibleHydrologyAdapter.Feature.CHANNEL,
                List.of(new BlockPos(1, 64, 1)),
                List.of(),
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

    private static void assertRetainedShorelineCutBounded(
            io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId volumeId,
            java.util.Collection<BlockPos> waterSurfacePositions,
            int waterTopY,
            SkyforgeNeoForge1211ChunkAdapter terrain) {
        var wetColumns = waterSurfacePositions.stream()
                .map(position -> new SkyforgeAuthoredVisibleHydrologyAdapter.Column(
                        position.getX(), position.getZ()))
                .collect(java.util.stream.Collectors.toSet());
        for (BlockPos position : waterSurfacePositions) {
            var column = new SkyforgeAuthoredVisibleHydrologyAdapter.Column(
                    position.getX(), position.getZ());
            if (!SkyforgeAuthoredVisibleHydrologyAdapter.retainedFootprintBoundary(
                    wetColumns, column)) {
                continue;
            }
            assertTrue(
                    terrain.integerSolidRange(volumeId, position.getX(), position.getZ())
                            .map(range -> Math.max(0, range.maximumY() - waterTopY)
                                    <= SkyforgeAuthoredVisibleHydrologyAdapter.MAX_RETAINED_BASIN_CUT_BLOCKS)
                            .orElse(false),
                    "retained-water perimeter may only use bounded local conditioning; deep carrier "
                            + "reconciliation must remain submerged inside the authored basin");
        }
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
            Set<SkyforgeAuthoredVisibleHydrologyAdapter.Column> wetColumns = deployment.positions().stream()
                    .map(position -> new SkyforgeAuthoredVisibleHydrologyAdapter.Column(
                            position.getX(), position.getZ()))
                    .collect(java.util.stream.Collectors.toSet());
            for (var wet : deployment.positions()) {
                assertEquals(
                        java.util.Optional.of(deployment.volumeId()),
                        terrain.authoredVisibleHydrologyVolumeId(wet),
                        "wet cells are owned by immutable authored hydrology, even when above the "
                                + "pre-hydrology solid surface");
            }
            for (var position : deployment.carvedPositions()) {
                assertTrue(terrain.isSolidOwnedBy(
                        deployment.volumeId(), position.getX(), position.getY(), position.getZ()));
                assertFalse(terrain.isSolidOwnedByOtherVolume(
                        deployment.volumeId(), position.getX(), position.getY(), position.getZ()));
            }
            var forcedSurface = new java.util.HashSet<>(deployment.forcedSurfacePositions());
            assertTrue(deployment.surfacePositions().containsAll(forcedSurface));
            for (var position : deployment.surfacePositions()) {
                assertFalse(terrain.isSolidOwnedByOtherVolume(
                        deployment.volumeId(), position.getX(), position.getY(), position.getZ()));
                var optionalRange = terrain.integerSolidRange(
                        deployment.volumeId(), position.getX(), position.getZ());
                boolean compiledSupport = optionalRange
                        .map(range -> position.getY() >= range.minimumY()
                                && position.getY() <= range.maximumY())
                        .orElse(false);
                if (compiledSupport) {
                    continue;
                }
                assertTrue(
                        forcedSurface.contains(position),
                        "only explicit bank-repair positions may extend solid support beyond compiled geology: "
                                + "volume=" + deployment.volumeId().path()
                                + ", feature=" + deployment.feature()
                                + ", position=" + position
                                + ", range=" + optionalRange
                                + ", forced=" + forcedSurface.contains(position));
                var range = optionalRange.orElseThrow();
                int fillDepth = position.getY() - range.maximumY();
                if (deployment.feature()
                        == SkyforgeAuthoredVisibleHydrologyAdapter.Feature.RETAINED_WATER) {
                    assertTrue(
                            fillDepth >= 1
                                    && fillDepth
                                            <= SkyforgeAuthoredVisibleHydrologyAdapter.MAX_RETAINED_BANK_FILL_BLOCKS,
                            "authored retained-water bank fill must stay within the bounded shoreline budget");
                    continue;
                }

                assertEquals(
                        SkyforgeAuthoredVisibleHydrologyAdapter.Feature.CHANNEL,
                        deployment.feature(),
                        "only explicit retained-water or channel-bank conditioning may add solid support");
                assertEquals(
                        0,
                        SkyforgeAuthoredVisibleHydrologyAdapter.MAX_CHANNEL_BANK_FILL_BLOCKS,
                        "flowing channel geometry must not authorize synthetic bank fill");
                throw new AssertionError(
                        "channel deployment unexpectedly contains forced bank geometry at " + position);

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