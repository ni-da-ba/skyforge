package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

/** Focused proof for the first backend-neutral world spatial query boundary. */
final class SkyIslandWorldCatalogTest {
    private static final long ROOT_SEED = 0x534b59464f524745L;
    private static final SkyIslandWorldVerticalReservation VERTICAL_RESERVATION =
            new SkyIslandWorldVerticalReservation(180.0, 140.0);

    @Test
    void closedWorldBoundsTreatBoundaryTouchAsRelevant() {
        WorldBounds first = new WorldBounds(0.0, 16.0, 0.0, 256.0, 0.0, 16.0);
        WorldBounds adjacent = new WorldBounds(16.0, 32.0, 0.0, 256.0, 0.0, 16.0);
        WorldBounds separated = new WorldBounds(16.0001, 32.0, 0.0, 256.0, 0.0, 16.0);

        assertTrue(first.intersects(adjacent));
        assertTrue(adjacent.intersects(first));
        assertFalse(first.intersects(separated));
        assertTrue(first.contains(16.0, 256.0, 16.0));
    }

    @Test
    void invalidWorldBoundsAndVerticalReservationsFailEarly() {
        assertThrows(IllegalArgumentException.class,
                () -> new WorldBounds(1.0, 0.0, 0.0, 1.0, 0.0, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> new SkyIslandWorldVerticalReservation(-1.0, 10.0));
        assertThrows(IllegalArgumentException.class,
                () -> new SkyIslandWorldVerticalReservation(0.0, 0.0));
    }

    @Test
    void archipelagoCompilesIntoStablePlanOrderWorldVolumes() {
        var plan = new SkyIslandArchipelagoPlanner().plan(request());
        var compiler = new SkyIslandWorldCatalogCompiler();
        var registry = SkyIslandMorphologyProviders.builtInRegistry();
        SkyIslandWorldCatalog first = compiler.compile(plan, registry, VERTICAL_RESERVATION);
        SkyIslandWorldCatalog second = compiler.compile(plan, registry, VERTICAL_RESERVATION);

        assertEquals(ROOT_SEED, first.rootSeed());
        assertEquals(plan.totalMemberCount(), first.volumeCount());
        assertEquals(2, first.volumeCount());
        assertEquals("anchor", first.volumes().get(0).id().groupIdentifier());
        assertEquals("outlier", first.volumes().get(1).id().groupIdentifier());
        assertEquals(0, first.volumes().get(0).id().groupOrdinal());
        assertEquals(1, first.volumes().get(1).id().groupOrdinal());
        assertEquals(
                first.volumes().stream().map(SkyIslandWorldVolume::id).toList(),
                second.volumes().stream().map(SkyIslandWorldVolume::id).toList());
        assertEquals(
                first.volumes().stream().map(SkyIslandWorldVolume::bounds).toList(),
                second.volumes().stream().map(SkyIslandWorldVolume::bounds).toList());
    }

    @Test
    void regionQueryReturnsOnlyConservativelyRelevantVolumesInStableOrder() {
        var plan = new SkyIslandArchipelagoPlanner().plan(request());
        SkyIslandWorldCatalog catalog = new SkyIslandWorldCatalogCompiler().compile(
                plan, SkyIslandMorphologyProviders.builtInRegistry(), VERTICAL_RESERVATION);
        SkyIslandWorldVolume anchor = catalog.volumes().get(0);
        SkyIslandWorldVolume outlier = catalog.volumes().get(1);

        WorldBounds anchorQuery = anchor.bounds();
        assertEquals(List.of(anchor.id()), catalog.query(anchorQuery).stream()
                .map(SkyIslandWorldVolume::id)
                .toList());

        WorldBounds wholeRegion = enclosing(anchor.bounds(), outlier.bounds());
        assertEquals(
                catalog.volumes().stream().map(SkyIslandWorldVolume::id).toList(),
                catalog.query(wholeRegion).stream().map(SkyIslandWorldVolume::id).toList());

        WorldBounds emptySky = new WorldBounds(100_000.0, 100_016.0, 0.0, 512.0, 100_000.0, 100_016.0);
        assertTrue(catalog.query(emptySky).isEmpty());
    }

    @Test
    void queryCullsByVerticalReservationAndIncludesExactBoundaries() {
        var plan = new SkyIslandArchipelagoPlanner().plan(request());
        SkyIslandWorldCatalog catalog = new SkyIslandWorldCatalogCompiler().compile(
                plan, SkyIslandMorphologyProviders.builtInRegistry(), VERTICAL_RESERVATION);
        SkyIslandWorldVolume anchor = catalog.volumes().get(0);
        WorldBounds bounds = anchor.bounds();

        WorldBounds touchingTop = new WorldBounds(
                bounds.minimumX(), bounds.maximumX(),
                bounds.maximumY(), bounds.maximumY(),
                bounds.minimumZ(), bounds.maximumZ());
        assertEquals(1, catalog.query(touchingTop).size());

        WorldBounds above = new WorldBounds(
                bounds.minimumX(), bounds.maximumX(),
                bounds.maximumY() + 1.0, bounds.maximumY() + 2.0,
                bounds.minimumZ(), bounds.maximumZ());
        assertTrue(catalog.query(above).isEmpty());
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

    private static WorldBounds enclosing(WorldBounds first, WorldBounds second) {
        return new WorldBounds(
                Math.min(first.minimumX(), second.minimumX()),
                Math.max(first.maximumX(), second.maximumX()),
                Math.min(first.minimumY(), second.minimumY()),
                Math.max(first.maximumY(), second.maximumY()),
                Math.min(first.minimumZ(), second.minimumZ()),
                Math.max(first.maximumZ(), second.maximumZ()));
    }
}
