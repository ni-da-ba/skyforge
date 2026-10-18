package io.github.nidaba.skyforge.model.skyisland;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class SkyBiomeCatalogTest {
    @Test
    void exposesDistinctStableSkyHabitatIdsInCanonicalOrder() {
        List<SkyBiomeDefinition> definitions = SkyBiomeCatalog.definitions();

        assertEquals(
                List.of(
                        SkyBiomeCatalog.CLOUD_SHELF_ID,
                        SkyBiomeCatalog.ISLAND_LEE_ID,
                        SkyBiomeCatalog.OPEN_SKY_ID),
                definitions.stream().map(SkyBiomeDefinition::id).toList());
        assertFalse(definitions.stream().anyMatch(
                definition -> definition.id().value().equals("skyforge:open_air")));
    }

    @Test
    void identifiesRegionalSkyHabitatTraitsWithoutOwningSpawnPolicy() {
        assertTrue(SkyBiomeCatalog.find(SkyBiomeCatalog.OPEN_SKY_ID)
                .orElseThrow().hasTrait(SkyBiomeTrait.OPEN_AIR));
        assertTrue(SkyBiomeCatalog.find(SkyBiomeCatalog.CLOUD_SHELF_ID)
                .orElseThrow().hasTrait(SkyBiomeTrait.CLOUD_ASSOCIATED));
        assertTrue(SkyBiomeCatalog.find(SkyBiomeCatalog.ISLAND_LEE_ID)
                .orElseThrow().hasTrait(SkyBiomeTrait.TERRAIN_SHELTERED));
        assertTrue(SkyBiomeCatalog.find(SkyBiomeId.parse("other:extension"))
                .isEmpty());
    }
}
