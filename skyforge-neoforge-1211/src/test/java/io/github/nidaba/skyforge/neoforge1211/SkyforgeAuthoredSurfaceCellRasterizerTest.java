package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationAssociation;
import io.github.nidaba.skyforge.world.SkyIslandSurfaceSiteCapabilityProfiler;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class SkyforgeAuthoredSurfaceCellRasterizerTest {
    @Test
    void canonicalAuth0096AnchorsProjectToExactOwnedMinecraftColumns() {
        Context context = canonical();
        var rasterizer = context.rasterizer();
        int projected = 0;
        int wetProjected = 0;

        for (var cell : context.profile().cells()) {
            var projection = rasterizer.projectAnchor(
                    cell.watershedCellIndex(), context.terrain());
            if (!cell.physicalSurfacePresent()) {
                assertTrue(projection.isEmpty());
                continue;
            }
            var anchor = projection.orElseThrow();
            projected++;
            assertEquals(cell, anchor.sourceCell());
            assertTrue(context.terrain().isSolidOwnedBy(
                    context.association().realizedVolumeId(),
                    anchor.worldX(),
                    anchor.maximumSolidY(),
                    anchor.worldZ()));
            assertFalse(context.terrain().isSolidOwnedByOtherVolume(
                    context.association().realizedVolumeId(),
                    anchor.worldX(),
                    anchor.maximumSolidY(),
                    anchor.worldZ()));

            var roundTrip = rasterizer.cellForWorldColumn(
                    context.association().realizedVolumeId(),
                    anchor.worldX(),
                    anchor.worldZ());
            assertEquals(cell, roundTrip.orElseThrow());
            if (rasterizer.hasAuthoredFreshwaterOrRiparianContext(cell)) {
                wetProjected++;
            }
        }

        assertTrue(projected > 0);
        assertTrue(wetProjected > 0,
                "canonical DR-40 specimen should retain accepted freshwater/riparian anchors");
    }

    @Test
    void arbitraryRuntimeColumnsUseOnlyTheExistingWatershedLattice() {
        Context context = canonical();
        var rasterizer = context.rasterizer();
        var realized = context.association().realizedVolume()
                .compiledVolume().descriptor();

        int centerX = Math.toIntExact(Math.round(realized.centerX()));
        int centerZ = Math.toIntExact(Math.round(realized.centerZ()));
        var center = rasterizer.cellForWorldColumn(
                context.association().realizedVolumeId(), centerX, centerZ);
        assertTrue(center.isPresent());
        assertEquals(24 + 24 * 49, center.orElseThrow().watershedCellIndex());

        int outsideX = Math.toIntExact(Math.round(
                realized.centerX() + context.association().authoredDescriptor().nominalRadius() + 8.0));
        assertTrue(rasterizer.cellForWorldColumn(
                context.association().realizedVolumeId(), outsideX, centerZ).isEmpty());
    }

    @Test
    void foreignExactVolumeFailsClosed() {
        Context context = canonical();
        var foreign = new io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId(
                1L, "foreign", 0, 0, 2L);
        assertThrows(IllegalArgumentException.class, () ->
                context.rasterizer().cellForWorldColumn(foreign, 0, 0));
    }

    private static Context canonical() {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.single();
        var association = SkyIslandAuthoredRealizationAssociation.of(
                fixture.descriptor(), fixture.volume());
        var profile = new SkyIslandSurfaceSiteCapabilityProfiler().profile(association);
        var terrain = new SkyforgeNeoForge1211ChunkAdapter(
                fixture.catalog(),
                io.github.nidaba.skyforge.world.SkyIslandTerrainProfile.reference(),
                new SkyforgeMinecraftBlockPalette(),
                Map.of(fixture.volume().id(), fixture.descriptor()));
        return new Context(
                association,
                profile,
                terrain,
                new SkyforgeAuthoredSurfaceCellRasterizer(profile));
    }

    private record Context(
            SkyIslandAuthoredRealizationAssociation association,
            io.github.nidaba.skyforge.world.SkyIslandSurfaceSiteCapabilityProfile profile,
            SkyforgeNeoForge1211ChunkAdapter terrain,
            SkyforgeAuthoredSurfaceCellRasterizer rasterizer) {}
}
