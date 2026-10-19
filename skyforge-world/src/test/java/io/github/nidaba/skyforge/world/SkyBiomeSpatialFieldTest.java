package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.nidaba.skyforge.model.skyisland.SkyBiomeCatalog;
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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Deterministic coverage and identity evidence for backend-neutral SkyBiome spatial fields. */
final class SkyBiomeSpatialFieldTest {
    private static final long WORLD_SEED = 0x414c504841534b59L;
    private static final long ROOT_SEED = 0x534b59464f524745L;
    private static final WorldBounds PROVINCE =
            new WorldBounds(-4_000.0, 4_000.0, 0.0, 512.0, -4_000.0, 4_000.0);
    private static final WorldBounds REMOTE_CLOUD_SHELF =
            new WorldBounds(3_000.0, 3_200.0, 350.0, 400.0, 3_000.0, 3_200.0);

    @Test
    void fieldCoversProvinceAndResolvesShelterBeforeCloudBeforeOpenSky() {
        SkyIslandWorldCatalog islands = catalog();
        WorldBounds islandBounds = islands.volumes().getFirst().bounds();
        SkyBiomeSpatialField field = compiler().compile(
                WORLD_SEED,
                PROVINCE,
                islands,
                List.of(islandBounds, REMOTE_CLOUD_SHELF),
                128.0);

        assertEquals(SkyBiomeCatalog.ISLAND_LEE_ID, field.resolve(
                midpoint(islandBounds.minimumX(), islandBounds.maximumX()),
                midpoint(islandBounds.minimumY(), islandBounds.maximumY()),
                midpoint(islandBounds.minimumZ(), islandBounds.maximumZ())).skyBiomeId());
        assertEquals(SkyBiomeCatalog.CLOUD_SHELF_ID, field.resolve(3_100.0, 375.0, 3_100.0).skyBiomeId());
        assertEquals(SkyBiomeCatalog.OPEN_SKY_ID, field.resolve(3_500.0, 200.0, -3_500.0).skyBiomeId());
        assertThrows(IllegalArgumentException.class, () -> field.resolve(4_001.0, 200.0, 0.0));
    }

    @Test
    void zoneAssignmentsAreStableWhenInputCollectionsAreReorderedAndRecompiled() {
        SkyIslandWorldCatalog original = catalog();
        ArrayList<SkyIslandWorldVolume> reversedVolumes = new ArrayList<>(original.volumes());
        java.util.Collections.reverse(reversedVolumes);
        SkyIslandWorldCatalog reordered =
                new SkyIslandWorldCatalog(original.rootSeed(), reversedVolumes);
        List<WorldBounds> cloudShelves = List.of(original.volumes().getFirst().bounds(), REMOTE_CLOUD_SHELF);

        SkyBiomeSpatialField first = compiler().compile(
                WORLD_SEED, PROVINCE, original, cloudShelves, 128.0);
        SkyBiomeSpatialField reorderedField = compiler().compile(
                WORLD_SEED, PROVINCE, reordered, List.of(REMOTE_CLOUD_SHELF, cloudShelves.getFirst()), 128.0);
        SkyBiomeSpatialField reopened = compiler().compile(
                WORLD_SEED, PROVINCE, original, cloudShelves, 128.0);

        assertEquals(first.zones(), reorderedField.zones());
        assertEquals(first.zones(), reopened.zones());
    }

    private static SkyBiomeSpatialFieldCompiler compiler() {
        return new SkyBiomeSpatialFieldCompiler();
    }

    private static SkyIslandWorldCatalog catalog() {
        var plan = new SkyIslandArchipelagoPlanner().plan(request());
        return new SkyIslandWorldCatalogCompiler().compile(
                plan,
                SkyIslandMorphologyProviders.builtInRegistry(),
                new SkyIslandWorldVerticalReservation(180.0, 140.0));
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

    private static double midpoint(double minimum, double maximum) {
        return minimum + (maximum - minimum) / 2.0;
    }
}
