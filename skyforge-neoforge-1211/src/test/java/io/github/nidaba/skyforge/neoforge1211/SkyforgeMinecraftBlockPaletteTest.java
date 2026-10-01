package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.world.SkyIslandTerrainSampleContext;
import io.github.nidaba.skyforge.world.SkyIslandTerrainSemantic;
import org.junit.jupiter.api.Test;

final class SkyforgeMinecraftBlockPaletteTest {
    @Test
    void everySkyforgeSemanticMapsToAConcreteVanillaKeyWithoutChangingOccupancy() {
        SkyforgeMinecraftBlockPalette palette = new SkyforgeMinecraftBlockPalette();

        for (SkyIslandTerrainSemantic semantic : SkyIslandTerrainSemantic.values()) {
            var context = new SkyIslandTerrainSampleContext(1.0, 2.0, 3.0, semantic);
            var key = palette.blockKey(context);
            assertEquals("minecraft", key.getNamespace());
            assertTrue(palette.preservesOccupancy(semantic, key));
        }

        assertEquals(
                SkyforgeMinecraftBlockPalette.AIR,
                palette.blockKey(new SkyIslandTerrainSampleContext(
                        0.0, 0.0, 0.0, SkyIslandTerrainSemantic.AIR)));
        assertEquals(
                SkyforgeMinecraftBlockPalette.DIRT,
                palette.blockKey(new SkyIslandTerrainSampleContext(
                        0.0, 0.0, 0.0, SkyIslandTerrainSemantic.SURFACE_MANTLE)));
        assertEquals(
                SkyforgeMinecraftBlockPalette.DEEPSLATE,
                palette.blockKey(new SkyIslandTerrainSampleContext(
                        0.0, 0.0, 0.0, SkyIslandTerrainSemantic.DEEP_MASS)));
    }
}
