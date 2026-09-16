package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationAssociation;
import io.github.nidaba.skyforge.world.SkyIslandSurfaceSiteCapabilityProfiler;
import java.util.HashSet;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biomes;
import org.junit.jupiter.api.Test;

final class SkyforgeProductionEcologyResolverTest {
    @Test
    void canonicalCoordinatorSurfaceSamplesResolveMultipleAuthoredCarriers() {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.single();
        var association = SkyIslandAuthoredRealizationAssociation.of(fixture.descriptor(), fixture.volume());
        var resolver = new SkyforgeProductionEcologyResolver(association);
        var terrain = new SkyforgeNeoForge1211ChunkAdapter(
                fixture.catalog(),
                io.github.nidaba.skyforge.world.SkyIslandTerrainProfile.reference(),
                new SkyforgeMinecraftBlockPalette(),
                java.util.Map.of(fixture.volume().id(), fixture.descriptor()));

        var carriers = new HashSet<net.minecraft.resources.ResourceKey<net.minecraft.world.level.biome.Biome>>();
        int sampledChunks = 0;
        for (long chunkKey : requiredChunkKeys(fixture.volume().bounds())) {
            ChunkPos chunk = new ChunkPos(chunkKey);
            for (var probe : SkyforgeNativeSurfacePopulationCoordinator.surfaceProbeOrder()) {
                int x = chunk.getMinBlockX() + probe.localX();
                int z = chunk.getMinBlockZ() + probe.localZ();
                var range = terrain.integerSolidRange(fixture.volume().id(), x, z);
                if (range.isEmpty()) {
                    continue;
                }
                carriers.add(resolver.resolve(
                        fixture.volume().id(), x, range.orElseThrow().maximumY() + 1, z));
                sampledChunks++;
                break;
            }
        }
        assertTrue(sampledChunks > 0);
        assertTrue(carriers.size() >= 2, "canonical production surface must expose authored ecology differentiation");
    }

    @Test
    void acceptedFreshwaterOrRiparianAnchorsUseWetCarrierWithoutNewThreshold() {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.single();
        var association = SkyIslandAuthoredRealizationAssociation.of(fixture.descriptor(), fixture.volume());
        var resolver = new SkyforgeProductionEcologyResolver(association);
        var profile = new SkyIslandSurfaceSiteCapabilityProfiler().profile(association);
        var rasterizer = new SkyforgeAuthoredSurfaceCellRasterizer(profile);
        var wet = profile.cells().stream()
                .filter(cell -> cell.physicalSurfacePresent()
                        && rasterizer.hasAuthoredFreshwaterOrRiparianContext(cell))
                .findFirst()
                .orElseThrow();
        var projected = rasterizer.projectAnchor(
                wet.watershedCellIndex(),
                new SkyforgeNeoForge1211ChunkAdapter(
                        fixture.catalog(),
                        io.github.nidaba.skyforge.world.SkyIslandTerrainProfile.reference(),
                        new SkyforgeMinecraftBlockPalette(),
                        java.util.Map.of(fixture.volume().id(), fixture.descriptor())))
                .orElseThrow();
        assertEquals(
                Biomes.SWAMP,
                resolver.resolve(
                        fixture.volume().id(),
                        projected.worldX(),
                        projected.maximumSolidY() + 1,
                        projected.worldZ()));
    }

    @Test
    void foreignVolumeFailsClosed() {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.single();
        var resolver = new SkyforgeProductionEcologyResolver(
                SkyIslandAuthoredRealizationAssociation.of(fixture.descriptor(), fixture.volume()));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(
                SkyforgeNeoForge1211ProductionComposedCaveFixture.stacked().upper().id(), 0, 220, 0));
    }

    private static java.util.Set<Long> requiredChunkKeys(io.github.nidaba.skyforge.world.WorldBounds bounds) {
        int minX = Math.floorDiv((int) Math.floor(bounds.minimumX()), 16);
        int maxX = Math.floorDiv((int) Math.floor(bounds.maximumX()), 16);
        int minZ = Math.floorDiv((int) Math.floor(bounds.minimumZ()), 16);
        int maxZ = Math.floorDiv((int) Math.floor(bounds.maximumZ()), 16);
        var result = new java.util.LinkedHashSet<Long>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                result.add(ChunkPos.asLong(x, z));
            }
        }
        return java.util.Set.copyOf(result);
    }
}
