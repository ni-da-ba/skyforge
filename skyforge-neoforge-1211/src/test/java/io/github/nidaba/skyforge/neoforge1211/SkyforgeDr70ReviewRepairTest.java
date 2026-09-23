package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.world.SkyIslandVisibleHydrologicRealizationPlanner;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Bounded machine evidence for the post-DR-60 canopy/hydrology repair tranche. */
final class SkyforgeDr70ReviewRepairTest {
    private static final int PRODUCTION_ATTACHMENT_DEPTH = 24;

    @Test
    @Tag("qualification")
    void selectedReviewSpecimenIsBoundedAndExercisesHydrologyAndCaves() {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.dr70Review();
        assertEquals(2885L, fixture.islandKey());
        assertTrue(fixture.descriptor().nominalRadius() < 120.0,
                "DR-70 review specimen must remain near the accepted bounded runtime workload");
        assertTrue(fixture.field().exposureGeometry().connectionCount() > 0,
                "DR-70 review specimen must preserve authored cave connectivity");

        var visible = SkyIslandVisibleHydrologicRealizationPlanner.plan(fixture.descriptor());
        assertFalse(visible.channels().isEmpty(),
                "review specimen must retain a routed visible channel system");
        assertTrue(visible.drops().stream().filter(drop ->
                drop.kind() != io.github.nidaba.skyforge.world.SkyIslandVisibleHydrologicRealizationKind.EDGE_DISCHARGE)
                .count() >= 1,
                "review specimen must exercise at least one interior drop");

        var terrain = new SkyforgeNeoForge1211ChunkAdapter(
                fixture.catalog(),
                io.github.nidaba.skyforge.world.SkyIslandTerrainProfile.reference(),
                new SkyforgeMinecraftBlockPalette(),
                java.util.Map.of(fixture.volume().id(), fixture.descriptor()));
        var deployments = terrain.authoredHydrologyDeployments(fixture.volume().id());
        assertTrue(deployments.stream().anyMatch(deployment ->
                deployment.feature() == SkyforgeAuthoredVisibleHydrologyAdapter.Feature.CHANNEL
                        && !deployment.positions().isEmpty()
                        && !deployment.carvedPositions().isEmpty()),
                "review specimen must project AUTH-0105 into both wet and dry Minecraft terrain");
    }

    @Test
    @Tag("qualification")
    void atlasKey2754FailsClosedWhenAuthoredChannelHasNoPhysicalCarrier() {
        var fixture = SkyforgeDr70HumanReviewAtlasFixture.runtimeFixture(44);
        assertEquals(2754L, fixture.member().islandKey());

        var terrain = new SkyforgeNeoForge1211ChunkAdapter(
                fixture.catalog(),
                io.github.nidaba.skyforge.world.SkyIslandTerrainProfile.reference(),
                new SkyforgeMinecraftBlockPalette(),
                fixture.descriptorsByVolumeId());

        var failure = assertThrows(
                IllegalStateException.class,
                () -> terrain.authoredHydrologyDeployments(fixture.volume().id()));
        assertTrue(
                failure.getMessage().contains("accepted channel cannot project")
                        && failure.getMessage().contains("sourceCell=565")
                        && failure.getMessage().contains("downstreamCell=516")
                        && failure.getMessage().contains("wetCorridorCandidates=24")
                        && failure.getMessage().contains("solidCarrierCandidates=4"),
                "a historical atlas channel whose semantic corridor is mostly physical void "
                        + "must fail closed rather than inventing terrain, rerouting water, or "
                        + "materializing an orphan drop source");
    }

    @Test
    void canonicalUpperSurfaceRejectsTreePlacementWithoutEnoughAttachmentHeadroom() {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.single();
        int maximumBuildHeightExclusive = 320;
        int firstFreeAtCanonicalUpperSurface =
                (int) Math.floor(fixture.volume().bounds().maximumY()) + 1;
        int treeAttachmentDepth =
                SkyforgeNativeBiomePopulationRunner.treeAttachmentDepth(PRODUCTION_ATTACHMENT_DEPTH);

        assertTrue(treeAttachmentDepth > PRODUCTION_ATTACHMENT_DEPTH,
                "tall trees need a larger bounded envelope than ordinary surface attachments");
        assertFalse(SkyforgeSurfaceVegetationHeadroomPolicy.admits(
                firstFreeAtCanonicalUpperSurface,
                maximumBuildHeightExclusive,
                treeAttachmentDepth));
        assertTrue(SkyforgeSurfaceVegetationHeadroomPolicy.admits(
                maximumBuildHeightExclusive - treeAttachmentDepth,
                maximumBuildHeightExclusive,
                treeAttachmentDepth));
        assertTrue(SkyforgeNativeBiomePopulationRunner.treeAttachmentDepth(40) == 40,
                "an already-larger caller envelope must never be reduced");
    }

    @Test
    @Tag("qualification")
    void canonicalAuth0105ChannelProjectsDryLandformAndContainedWater() {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.single();
        var terrain = terrain(fixture);
        var semantic = SkyIslandVisibleHydrologicRealizationPlanner.plan(fixture.descriptor())
                .channels().getFirst().path().points();
        var deployment = terrain.authoredHydrologyDeployments(fixture.volume().id()).stream()
                .filter(candidate -> candidate.feature() == SkyforgeAuthoredVisibleHydrologyAdapter.Feature.CHANNEL)
                .findFirst()
                .orElseThrow();

        assertTrue(semantic.size() > 1, "accepted canonical channel must remain multi-position");
        var fluvial = io.github.nidaba.skyforge.world.SkyIslandFluvialTerrainField.create(
                fixture.descriptor(),
                SkyIslandVisibleHydrologicRealizationPlanner.plan(fixture.descriptor())
                        .coherentHydrology());
        var reach = fluvial.reaches().getFirst();
        assertTrue(reach.wetHalfWidth() < reach.bankfullHalfWidth());
        assertTrue(reach.bankfullHalfWidth() < reach.valleyHalfWidth(),
                "AUTH-0105 must expose dry valley terrain beyond the wet corridor");
        var physical = fixture.volume().compiledVolume().descriptor();
        double maximumBankDistance = deployment.surfacePositions().stream()
                .mapToDouble(position -> distanceToPath(
                        new io.github.nidaba.skyforge.world.SkyIslandLocalPosition(
                                position.getX() - physical.centerX(),
                                position.getZ() - physical.centerZ()),
                        reach.path()))
                .max()
                .orElse(0.0);
        assertTrue(
                maximumBankDistance >= Math.min(1.0, reach.bankfullHalfWidth() * 0.35),
                "physical bank/bed footprint must extend laterally beyond the authored centerline");
        assertFalse(deployment.carvedPositions().isEmpty(),
                "physical channel must cut a dry recessed bed rather than replace the hilltop with water");
        assertTrue(java.util.Collections.disjoint(
                deployment.positions(), deployment.carvedPositions()));
        assertTrue(deployment.positions().stream().allMatch(position ->
                terrain.integerSolidRange(
                                fixture.volume().id(),
                                position.getX(),
                                position.getZ())
                        .map(range -> position.getY() < range.maximumY())
                        .orElse(false)),
                "AUTH-0105 water must be physically recessed below the pre-fluvial surface");
        assertConnectedFootprint(deployment.positions());
        for (BlockPos position : deployment.positions()) {
            assertTrue(terrain.isAuthoredVisibleHydrologyPosition(position));
            assertTrue(terrain.isSolidOwnedBy(
                    fixture.volume().id(), position.getX(), position.getY(), position.getZ()));
        }
        for (BlockPos position : deployment.carvedPositions()) {
            assertTrue(terrain.isSolidOwnedBy(
                    fixture.volume().id(), position.getX(), position.getY(), position.getZ()));
            assertFalse(terrain.isAuthoredVisibleHydrologyPosition(position),
                    "dry bank/bed-clearance cells must not become fluid-domain authority");
        }
        assertFalse(terrain.isAuthoredVisibleHydrologyPosition(new BlockPos(0, 0, 0)));
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

    private static void assertConnectedFootprint(List<BlockPos> positions) {
        Set<Column> columns = new HashSet<>();
        for (BlockPos position : positions) {
            columns.add(new Column(position.getX(), position.getZ()));
        }
        assertFalse(columns.isEmpty());

        Set<Column> visited = new HashSet<>();
        ArrayDeque<Column> queue = new ArrayDeque<>();
        Column first = columns.iterator().next();
        visited.add(first);
        queue.add(first);
        while (!queue.isEmpty()) {
            Column current = queue.removeFirst();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if ((dx == 0 && dz == 0) || Math.abs(dx) + Math.abs(dz) > 1) {
                        continue;
                    }
                    Column neighbor = new Column(current.x() + dx, current.z() + dz);
                    if (columns.contains(neighbor) && visited.add(neighbor)) {
                        queue.addLast(neighbor);
                    }
                }
            }
        }
        assertTrue(visited.size() == columns.size(),
                () -> "authored channel footprint is disconnected: visited="
                        + visited.size() + ", total=" + columns.size());
    }

    private record Column(int x, int z) {}

    private static SkyforgeNeoForge1211ChunkAdapter terrain(
            SkyforgeNeoForge1211ProductionComposedCaveFixture.Single fixture) {
        return new SkyforgeNeoForge1211ChunkAdapter(
                fixture.catalog(),
                io.github.nidaba.skyforge.world.SkyIslandTerrainProfile.reference(),
                new SkyforgeMinecraftBlockPalette(),
                java.util.Map.of(fixture.volume().id(), fixture.descriptor()));
    }
}