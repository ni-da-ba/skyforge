package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

final class ReferenceTiledSkyIslandTerrainBackendTest {
    private static final long ROOT_SEED = 0x534b59464f524745L;
    private static final SkyIslandWorldVerticalReservation VERTICAL =
            new SkyIslandWorldVerticalReservation(180.0, 140.0);
    private static final SkyIslandTerrainProfile PROFILE = SkyIslandTerrainProfile.reference();

    private final ReferenceTiledSkyIslandTerrainBackend terrainBackend =
            new ReferenceTiledSkyIslandTerrainBackend();
    private final ReferenceTiledSkyIslandBackend occupancyBackend =
            new ReferenceTiledSkyIslandBackend();

    @Test
    void tiledSemanticTerrainMatchesMonolithicAndPreservesOccupancyExactly() {
        SkyIslandWorldCatalog catalog = catalog();
        WorldSampleGrid grid = new WorldSampleGrid(
                -2048.0, 0.0, -2048.0,
                32.0, 8.0, 32.0,
                129, 81, 129);

        WorldRegionTerrain monolithic = terrainBackend.realizeMonolithic(catalog, grid, PROFILE);
        WorldRegionTerrain tiled = terrainBackend.realizeTiled(catalog, grid, PROFILE, 8, 8);
        WorldRegionTerrain irregular = terrainBackend.realizeTiled(catalog, grid, PROFILE, 13, 11);
        WorldRegionOccupancy occupancy = occupancyBackend.realizeMonolithic(catalog, grid);

        assertArrayEquals(monolithic.semantics(), tiled.semantics());
        assertArrayEquals(monolithic.semantics(), irregular.semantics());
        assertEquals(monolithic.sha256(), tiled.sha256());
        assertEquals(monolithic.sha256(), irregular.sha256());
        assertArrayEquals(occupancy.occupancy(), monolithic.occupancyProjection().occupancy());
        assertEquals(occupancy.solidSampleCount(), monolithic.solidSampleCount());

        assertTrue(monolithic.count(SkyIslandTerrainSemantic.SURFACE_MANTLE) > 0);
        assertTrue(monolithic.count(SkyIslandTerrainSemantic.UNDERSIDE_SHELL) > 0);
        assertTrue(monolithic.count(SkyIslandTerrainSemantic.SHALLOW_INTERIOR) > 0);
        assertTrue(monolithic.count(SkyIslandTerrainSemantic.DEEP_MASS) > 0);

        int naiveEveryVolumeEveryTile = tiled.spatialQueries() * catalog.volumeCount();
        assertTrue(tiled.candidateVolumeReferences() < naiveEveryVolumeEveryTile);
    }

    @Test
    void semanticBandsRemainIdenticalAcrossTileSeamCrossingIsland() {
        SkyIslandWorldCatalog catalog = catalog();
        WorldSampleGrid grid = new WorldSampleGrid(
                -512.0, 0.0, -512.0,
                16.0, 8.0, 16.0,
                65, 81, 65);

        WorldRegionTerrain monolithic = terrainBackend.realizeMonolithic(catalog, grid, PROFILE);
        WorldRegionTerrain tiled = terrainBackend.realizeTiled(catalog, grid, PROFILE, 32, 32);
        assertArrayEquals(monolithic.semantics(), tiled.semantics());
        assertTrue(hasSolidAtX(grid, tiled, 31));
        assertTrue(hasSolidAtX(grid, tiled, 32));
    }

    private static boolean hasSolidAtX(WorldSampleGrid grid, WorldRegionTerrain terrain, int xIndex) {
        for (int y = 0; y < grid.ySamples(); y++) {
            for (int z = 0; z < grid.zSamples(); z++) {
                if (terrain.semanticAt(xIndex, y, z).isSolid()) {
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
