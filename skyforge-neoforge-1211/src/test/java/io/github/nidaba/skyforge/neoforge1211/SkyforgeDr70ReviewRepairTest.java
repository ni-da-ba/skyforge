package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Tag("qualification")
    @Test
    void selectedReviewSpecimenIsBoundedAndExercisesHydrologyAndCaves() {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.dr70Review();
        assertEquals(2885L, fixture.islandKey());
        assertTrue(fixture.descriptor().nominalRadius() < 120.0,
                "DR-70 review specimen must remain near the accepted bounded runtime workload");
        assertTrue(fixture.field().exposureGeometry().connectionCount() > 0,
                "DR-70 review specimen must preserve authored cave connectivity");

        var visible = SkyIslandVisibleHydrologicRealizationPlanner.plan(fixture.descriptor());
        assertTrue(visible.channels().size() >= 20,
                "review specimen must exercise a substantial connected channel system");
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

    @Tag("qualification")
    @Test
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
        long distinctColumns = deployment.positions().stream()
                .map(position -> new Column(position.getX(), position.getZ()))
                .distinct()
                .count();
        assertTrue(distinctColumns > semantic.size(),
                "physical channel must read wider than sparse semantic sample points");
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
