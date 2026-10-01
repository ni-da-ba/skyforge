package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.world.SkyIslandTerrainProfile;
import java.util.Map;
import net.minecraft.world.level.biome.Biomes;
import org.junit.jupiter.api.Test;

final class SkyforgeAtmosphereTerrainAuthorityTest {
    @Test
    void reconstructsTopSurfaceWithoutInstallingMutationStages() throws Exception {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.single();
        var adapter = new SkyforgeNeoForge1211ChunkAdapter(
                fixture.catalog(),
                SkyIslandTerrainProfile.reference(),
                new SkyforgeMinecraftBlockPalette(),
                Map.of(fixture.volume().id(), fixture.descriptor()));
        var expected = adapter.atmosphereTopSurface(
                        0,
                        0,
                        fixture.volume().id()::equals)
                .orElseThrow();
        SkyforgeExactVolumeBiomeResolver forest =
                (volumeId, worldX, worldY, worldZ) -> Biomes.FOREST;

        assertFalse(SkyforgeAtmosphereTerrainAuthority.active());
        assertFalse(SkyforgeNeoForge1211SurfaceStage.hasActiveBinding());
        assertFalse(SkyforgePhysicalVolumeAdmissionStage.active());
        assertFalse(SkyforgeNativeSurfacePopulationStage.hasActiveBinding());

        try (AutoCloseable binding = SkyforgeAtmosphereTerrainAuthority.install(
                fixture.catalog(),
                adapter,
                Map.of(fixture.volume().id(), forest))) {
            assertNotNull(binding);
            assertTrue(SkyforgeAtmosphereTerrainAuthority.active());
            assertFalse(SkyforgeNeoForge1211SurfaceStage.hasActiveBinding());
            assertFalse(SkyforgePhysicalVolumeAdmissionStage.active());
            assertFalse(SkyforgeNativeSurfacePopulationStage.hasActiveBinding());

            var sample = SkyforgeAtmosphereTerrainAuthority.sample(0, 0, -64, 384)
                    .orElseThrow();
            assertEquals(fixture.volume().id(), sample.volumeId());
            assertEquals(expected.firstFreeY(), sample.firstFreeHeight());
            assertEquals(Biomes.FOREST, sample.biome());
        }

        assertFalse(SkyforgeAtmosphereTerrainAuthority.active());
        assertFalse(SkyforgeNeoForge1211SurfaceStage.hasActiveBinding());
        assertFalse(SkyforgePhysicalVolumeAdmissionStage.active());
        assertFalse(SkyforgeNativeSurfacePopulationStage.hasActiveBinding());
    }

    @Test
    void declinesUnsupportedEcologySurface() throws Exception {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.single();
        var adapter = new SkyforgeNeoForge1211ChunkAdapter(
                fixture.catalog(),
                SkyIslandTerrainProfile.reference(),
                new SkyforgeMinecraftBlockPalette(),
                Map.of(fixture.volume().id(), fixture.descriptor()));
        SkyforgeExactVolumeBiomeResolver unsupported = new SkyforgeExactVolumeBiomeResolver() {
            @Override
            public net.minecraft.resources.ResourceKey<net.minecraft.world.level.biome.Biome> resolve(
                    io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId volumeId,
                    int worldX,
                    int worldY,
                    int worldZ) {
                return Biomes.PLAINS;
            }

            @Override
            public boolean supportsSurface(
                    io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId volumeId,
                    int worldX,
                    int worldY,
                    int worldZ) {
                return false;
            }
        };

        try (AutoCloseable binding = SkyforgeAtmosphereTerrainAuthority.install(
                fixture.catalog(),
                adapter,
                Map.of(fixture.volume().id(), unsupported))) {
            assertNotNull(binding);
            assertTrue(SkyforgeAtmosphereTerrainAuthority.sample(0, 0, -64, 384).isEmpty());
        }
    }
}
