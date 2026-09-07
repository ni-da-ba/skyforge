package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.level.biome.Biomes;
import org.junit.jupiter.api.Test;

final class SkyforgeShowcaseEcologyFixtureTest {
    @Test
    void dedicatedEcologyCatalogKeepsBroadStackedLandSurfacesInAdmissionSafeHighAir() {
        var catalog = SkyforgeNeoForge1211ShowcaseEcologyDevRuntime.catalog();

        assertEquals(2, catalog.volumes().size());
        var lower = catalog.volumes().get(0);
        var upper = catalog.volumes().get(1);
        assertNotEquals(lower.id(), upper.id());

        assertEquals(-144.0, lower.bounds().minimumX());
        assertEquals(144.0, lower.bounds().maximumX());
        assertEquals(-144.0, lower.bounds().minimumZ());
        assertEquals(144.0, lower.bounds().maximumZ());
        assertEquals(-144.0, upper.bounds().minimumX());
        assertEquals(144.0, upper.bounds().maximumX());
        assertEquals(-144.0, upper.bounds().minimumZ());
        assertEquals(144.0, upper.bounds().maximumZ());

        assertTrue(lower.bounds().minimumY() >= 176.0);
        assertTrue(lower.bounds().maximumY() < upper.bounds().maximumY());
        assertTrue(upper.bounds().maximumY() < 320.0);
        assertEquals(19 * 19, SkyforgeNeoForge1211ShowcaseEcologyDevRuntime.footprintChunkKeys().size());
    }

    @Test
    void dedicatedEcologyResolverKeepsForestAndTaigaIdentityIndependentOfStackedCoordinates() {
        var catalog = SkyforgeNeoForge1211ShowcaseEcologyDevRuntime.catalog();
        var lowerId = SkyforgeNeoForge1211ShowcaseEcologyDevRuntime.lowerVolumeId(catalog);
        var upperId = SkyforgeNeoForge1211ShowcaseEcologyDevRuntime.upperVolumeId(catalog);
        var resolver = SkyforgeNeoForge1211ShowcaseEcologyDevRuntime.biomeResolver(lowerId, upperId);

        assertEquals(Biomes.FOREST, resolver.resolve(lowerId, 0, 220, 0));
        assertEquals(Biomes.TAIGA, resolver.resolve(upperId, 0, 290, 0));
    }
}
