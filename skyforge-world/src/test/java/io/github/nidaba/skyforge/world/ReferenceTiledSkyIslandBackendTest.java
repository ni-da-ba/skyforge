package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyFamily;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviders;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoLayout;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoPlanner;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoRequest;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandGroupRole;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandGroupTemplate;
import io.github.nidaba.skyforge.recipes.skyisland.group.ProviderMorphologySpec;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupLayout;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Exact tiled-versus-monolithic proof for the first backend realization seam. */
final class ReferenceTiledSkyIslandBackendTest {
    private static final long ROOT_SEED = 0x534b59464f524745L;
    private static final SkyIslandWorldVerticalReservation VERTICAL =
            new SkyIslandWorldVerticalReservation(180.0, 140.0);

    private final ReferenceTiledSkyIslandBackend backend = new ReferenceTiledSkyIslandBackend();

    @Test
    void tiledRealizationMatchesMonolithicAcrossAnIslandCrossingTileSeam() {
        SkyIslandWorldCatalog catalog = catalog();
        WorldSampleGrid grid = new WorldSampleGrid(
                -512.0, 0.0, -512.0,
                16.0, 8.0, 16.0,
                65, 81, 65);

        WorldRegionOccupancy monolithic = backend.realizeMonolithic(catalog, grid);
        WorldRegionOccupancy tiled = backend.realizeTiled(catalog, grid, 32, 32);

        assertTrue(monolithic.solidSampleCount() > 0);
        assertArrayEquals(monolithic.occupancy(), tiled.occupancy());
        assertEquals(monolithic.sha256(), tiled.sha256());
        // Tile 0 ends at x=-16 and tile 1 begins at x=0. The centered anchor crosses that seam.
        assertTrue(hasSolidAtX(grid, tiled.occupancy(), 31));
        assertTrue(hasSolidAtX(grid, tiled.occupancy(), 32));
    }

    @Test
    void irregularTileShapesAndPartialEdgeTilesRemainExactlyEquivalent() {
        SkyIslandWorldCatalog catalog = catalog();
        WorldSampleGrid grid = new WorldSampleGrid(
                -2048.0, 0.0, -2048.0,
                32.0, 8.0, 32.0,
                129, 81, 129);

        WorldRegionOccupancy monolithic = backend.realizeMonolithic(catalog, grid);
        WorldRegionOccupancy regular = backend.realizeTiled(catalog, grid, 8, 8);
        WorldRegionOccupancy irregular = backend.realizeTiled(catalog, grid, 13, 11);
        WorldRegionOccupancy repeated = backend.realizeTiled(catalog, grid, 13, 11);

        assertEquals(catalog.volumeCount(), monolithic.candidateVolumeReferences());
        assertArrayEquals(monolithic.occupancy(), regular.occupancy());
        assertArrayEquals(monolithic.occupancy(), irregular.occupancy());
        assertEquals(monolithic.sha256(), regular.sha256());
        assertEquals(monolithic.sha256(), irregular.sha256());
        assertEquals(irregular.sha256(), repeated.sha256());
        assertEquals(irregular.spatialQueries(), repeated.spatialQueries());
        assertEquals(irregular.candidateVolumeReferences(), repeated.candidateVolumeReferences());

        int naiveEveryVolumeEveryTile = regular.spatialQueries() * catalog.volumeCount();
        assertTrue(regular.candidateVolumeReferences() < naiveEveryVolumeEveryTile);
        assertTrue(regular.candidateVolumeReferences() > 0);
    }

    @Test
    void invalidTileAndGridParametersFailEarly() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorldSampleGrid(0.0, 0.0, 0.0, 0.0, 1.0, 1.0, 1, 1, 1));
        SkyIslandWorldCatalog catalog = catalog();
        WorldSampleGrid grid = new WorldSampleGrid(0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 1, 1, 1);
        assertThrows(IllegalArgumentException.class, () -> backend.realizeTiled(catalog, grid, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> backend.realizeTiled(catalog, grid, 1, -1));
    }

    private static boolean hasSolidAtX(WorldSampleGrid grid, byte[] occupancy, int xIndex) {
        for (int y = 0; y < grid.ySamples(); y++) {
            for (int z = 0; z < grid.zSamples(); z++) {
                if (occupancy[grid.linearIndex(xIndex, y, z)] != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static SkyIslandWorldCatalog catalog() {
        var plan = new SkyIslandArchipelagoPlanner().plan(request());
        return new SkyIslandWorldCatalogCompiler().compile(
                plan, SkyIslandMorphologyProviders.builtInRegistry(), VERTICAL);
    }

    private static SkyIslandArchipelagoRequest request() {
        SkyIslandVolumeDescriptor descriptor = new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                0L,
                0.0,
                0.0,
                320.0,
                192.0,
                76.0,
                100.0,
                48.0,
                Math.PI / 6.0,
                0.65,
                0.60,
                0.25,
                0.0,
                28.0);
        var massif = ProviderMorphologySpec.full(
                SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF));
        var spine = ProviderMorphologySpec.full(
                SkyIslandMorphologyProviders.builtInId(MorphologyFamily.SPINE));
        SkyIslandGroupTemplate anchor = new SkyIslandGroupTemplate(
                "anchor",
                SkyIslandGroupRole.ANCHOR,
                descriptor,
                256.0,
                96.0,
                0.0,
                List.of(massif),
                new SkyIslandGroupLayout.Cluster(600.0, 0.0, 0.0, 0.0),
                320.0);
        SkyIslandGroupTemplate outlier = new SkyIslandGroupTemplate(
                "outlier",
                SkyIslandGroupRole.OUTLIER,
                descriptor,
                256.0,
                96.0,
                0.0,
                List.of(spine),
                new SkyIslandGroupLayout.Cluster(600.0, 0.0, 0.0, 0.0),
                320.0);
        return new SkyIslandArchipelagoRequest(
                ROOT_SEED,
                0.0,
                0.0,
                320.0,
                400.0,
                List.of(anchor, outlier),
                new SkyIslandArchipelagoLayout.Hub(1_200.0, 0.0, 0.0, 0.0, 0.0));
    }
}
