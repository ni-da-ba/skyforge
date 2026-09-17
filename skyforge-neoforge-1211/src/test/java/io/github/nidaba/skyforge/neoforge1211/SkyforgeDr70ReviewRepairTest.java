package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.world.SkyIslandVisibleHydrologicRealizationPlanner;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/** Bounded machine evidence for the post-DR-60 canopy/hydrology repair tranche. */
final class SkyforgeDr70ReviewRepairTest {
    private static final int PRODUCTION_ATTACHMENT_DEPTH = 24;

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
    void canonicalAuth0104ChannelRasterIsMultiCellConnectedAndOwnerLocal() {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.single();
        var terrain = terrain(fixture);
        var semantic = SkyIslandVisibleHydrologicRealizationPlanner.plan(fixture.descriptor())
                .channels().getFirst().path().points();
        var deployment = SkyforgeAuthoredVisibleHydrologyAdapter
                .plan(fixture.descriptor(), fixture.volume(), terrain).stream()
                .filter(candidate -> candidate.feature() == SkyforgeAuthoredVisibleHydrologyAdapter.Feature.CHANNEL)
                .findFirst()
                .orElseThrow();

        assertTrue(semantic.size() > 1, "AUTH-0104 canonical channel must remain multi-position");
        assertTrue(SkyforgeAuthoredVisibleHydrologyAdapter.channelRadius(
                        SkyIslandVisibleHydrologicRealizationPlanner.plan(fixture.descriptor())
                                .channels().getFirst().path()) >= 1,
                "physical channel footprint must have authored-route-local breadth");
        long distinctColumns = deployment.positions().stream()
                .map(position -> new Column(position.getX(), position.getZ()))
                .distinct()
                .count();
        assertTrue(distinctColumns > semantic.size(),
                "physical channel must read wider than sparse semantic sample points");
        assertConnectedFootprint(deployment.positions());
        for (BlockPos position : deployment.positions()) {
            assertTrue(terrain.isAuthoredVisibleHydrologyPosition(position));
            assertTrue(terrain.isSolidOwnedBy(
                    fixture.volume().id(), position.getX(), position.getY(), position.getZ()));
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
