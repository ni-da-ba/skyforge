package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyBiomeCatalog;
import org.junit.jupiter.api.Test;

final class SkyforgeAlphaClusterCatalogTest {
    @Test
    void compilesARepeatableHierarchicalMassifBasinClusterAtDistinctElevations() {
        var first = SkyforgeAlphaClusterCatalog.compile(0x534b59464f524745L);
        var repeated = SkyforgeAlphaClusterCatalog.compile(0x534b59464f524745L);

        assertEquals(2, first.volumeCount());
        assertEquals(
                first.volumes().stream().map(volume -> volume.id().path()).toList(),
                repeated.volumes().stream().map(volume -> volume.id().path()).toList());
        assertEquals("sf-imp-1526-alpha-cluster", first.volumes().get(0).id().groupIdentifier());
        assertEquals(0, first.volumes().get(0).id().groupOrdinal());
        assertEquals(0, first.volumes().get(0).id().memberOrdinal());
        assertEquals(1, first.volumes().get(1).id().memberOrdinal());
        var firstMember = first.volumes().get(0).compiledVolume().descriptor();
        var secondMember = first.volumes().get(1).compiledVolume().descriptor();
        assertNotEquals(firstMember.centerX(), secondMember.centerX());
        assertNotEquals(firstMember.centerZ(), secondMember.centerZ());
        assertNotEquals(firstMember.suspensionElevation(), secondMember.suspensionElevation());
        double dx = firstMember.centerX() - secondMember.centerX();
        double dz = firstMember.centerZ() - secondMember.centerZ();
        assertTrue(Math.hypot(dx, dz) >= 392.0);
    }

    @Test
    void changesGeometryWhenTheRootSeedChangesButPreservesClusterIdentity() {
        var first = SkyforgeAlphaClusterCatalog.compile(0x534b59464f524745L);
        var changed = SkyforgeAlphaClusterCatalog.compile(0x534b59464f524746L);

        assertEquals(2, changed.volumeCount());
        assertEquals("sf-imp-1526-alpha-cluster", changed.volumes().get(0).id().groupIdentifier());
        assertNotEquals(
                first.volumes().stream().map(volume -> volume.id().path()).toList(),
                changed.volumes().stream().map(volume -> volume.id().path()).toList());
    }

    @Test
    void compilesSkyBiomeShelterRegionsFromTheSameClusterCatalog() {
        var catalog = SkyforgeAlphaClusterCatalog.compile(0x534b59464f524745L);
        var field = new io.github.nidaba.skyforge.world.SkyBiomeSpatialFieldCompiler().compile(
                991L,
                new io.github.nidaba.skyforge.world.WorldBounds(-640.0, 640.0, 0.0, 512.0, -640.0, 640.0),
                catalog,
                java.util.List.of(),
                48.0);

        assertEquals(2, field.zones().stream()
                .filter(zone -> zone.assignment().skyBiomeId().equals(SkyBiomeCatalog.ISLAND_LEE_ID))
                .count());
        for (var volume : catalog.volumes()) {
            var descriptor = volume.compiledVolume().descriptor();
            assertEquals(
                    SkyBiomeCatalog.ISLAND_LEE_ID,
                    field.resolve(
                                    descriptor.centerX(),
                                    descriptor.suspensionElevation(),
                                    descriptor.centerZ())
                            .skyBiomeId());
        }
    }
}
