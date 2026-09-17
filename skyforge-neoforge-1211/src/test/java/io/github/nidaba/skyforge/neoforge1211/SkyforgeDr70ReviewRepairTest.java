package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.world.SkyIslandVisibleHydrologicRealizationPlanner;
import java.util.List;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/** Bounded machine evidence for the post-DR-60 canopy/hydrology repair tranche. */
final class SkyforgeDr70ReviewRepairTest {
    private static final int PRODUCTION_ATTACHMENT_DEPTH = 24;

    @Test
    void canonicalUpperSurfaceRejectsTreePlacementWithoutEnoughAttachmentHeadroom() {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.single();
        int firstFreeAtUpperBound = (int) Math.floor(fixture.volume().bounds().maximumY()) + 1;

        assertFalse(SkyforgeSurfaceVegetationHeadroomPolicy.admits(
                firstFreeAtUpperBound, 320, PRODUCTION_ATTACHMENT_DEPTH));
        assertTrue(SkyforgeSurfaceVegetationHeadroomPolicy.admits(
                296, 320, PRODUCTION_ATTACHMENT_DEPTH));
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
        assertTrue(deployment.positions().size() > 1,
                "Minecraft realization must preserve a multi-position authored channel");
        assertConnected(deployment.positions());
        for (BlockPos position : deployment.positions()) {
            assertTrue(terrain.isAuthoredVisibleHydrologyPosition(position));
            assertTrue(terrain.isSolidOwnedBy(
                    fixture.volume().id(), position.getX(), position.getY(), position.getZ()));
        }
        assertFalse(terrain.isAuthoredVisibleHydrologyPosition(new BlockPos(0, 0, 0)));
    }

    private static void assertConnected(List<BlockPos> positions) {
        for (int index = 1; index < positions.size(); index++) {
            BlockPos previous = positions.get(index - 1);
            BlockPos current = positions.get(index);
            int dx = Math.abs(current.getX() - previous.getX());
            int dz = Math.abs(current.getZ() - previous.getZ());
            assertTrue(dx <= 1 && dz <= 1 && dx + dz > 0,
                    () -> "authored channel raster disconnected between " + previous + " and " + current);
        }
    }

    private static SkyforgeNeoForge1211ChunkAdapter terrain(
            SkyforgeNeoForge1211ProductionComposedCaveFixture.Single fixture) {
        return new SkyforgeNeoForge1211ChunkAdapter(
                fixture.catalog(),
                io.github.nidaba.skyforge.world.SkyIslandTerrainProfile.reference(),
                new SkyforgeMinecraftBlockPalette(),
                java.util.Map.of(fixture.volume().id(), fixture.descriptor()));
    }
}
