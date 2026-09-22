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
    void canonicalCoordinatorSurfaceSamplesPopulateOnlyExactAuthoredSurfaceAndStillDifferentiate() {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.single();
        var association = SkyIslandAuthoredRealizationAssociation.of(fixture.descriptor(), fixture.volume());
        var resolver = new SkyforgeProductionEcologyResolver(association);
        var terrain = new SkyforgeNeoForge1211ChunkAdapter(
                fixture.catalog(),
                io.github.nidaba.skyforge.world.SkyIslandTerrainProfile.reference(),
                new SkyforgeMinecraftBlockPalette(),
                java.util.Map.of(fixture.volume().id(), fixture.descriptor()));

        var carriers = new HashSet<net.minecraft.resources.ResourceKey<net.minecraft.world.level.biome.Biome>>();
        int supportedChunks = 0;
        int omittedEdgeChunks = 0;
        for (long chunkKey : requiredChunkKeys(fixture.volume().bounds())) {
            ChunkPos chunk = new ChunkPos(chunkKey);
            if (!resolver.supportsCoordinatorSurface(terrain, chunk)) {
                omittedEdgeChunks++;
                continue;
            }
            for (var probe : SkyforgeNativeSurfacePopulationCoordinator.surfaceProbeOrder()) {
                int x = chunk.getMinBlockX() + probe.localX();
                int z = chunk.getMinBlockZ() + probe.localZ();
                var range = terrain.integerSolidRange(fixture.volume().id(), x, z);
                if (range.isEmpty()) {
                    continue;
                }
                carriers.add(resolver.resolve(
                        fixture.volume().id(), x, range.orElseThrow().maximumY() + 1, z));
                supportedChunks++;
                break;
            }
        }
        assertTrue(supportedChunks > 0);
        assertTrue(omittedEdgeChunks > 0, "physical-only edge chunks must remain ecology-unclaimed");
        assertTrue(carriers.size() >= 2, "canonical authored surface must expose ecology differentiation");
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
    void dr70ReviewFreshwaterAndRiparianAnchorsOwnTheAcceptedWetPresentationCarrier() {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.dr70Review();
        var association = SkyIslandAuthoredRealizationAssociation.of(fixture.descriptor(), fixture.volume());
        var resolver = new SkyforgeProductionEcologyResolver(association);
        var profile = new SkyIslandSurfaceSiteCapabilityProfiler().profile(association);
        var rasterizer = new SkyforgeAuthoredSurfaceCellRasterizer(profile);
        var terrain = new SkyforgeNeoForge1211ChunkAdapter(
                fixture.catalog(),
                io.github.nidaba.skyforge.world.SkyIslandTerrainProfile.reference(),
                new SkyforgeMinecraftBlockPalette(),
                java.util.Map.of(fixture.volume().id(), fixture.descriptor()));

        int supportedWetAnchors = 0;
        for (var cell : profile.cells()) {
            if (!cell.physicalSurfacePresent()
                    || !rasterizer.hasAuthoredFreshwaterOrRiparianContext(cell)) {
                continue;
            }
            var projected = rasterizer.projectAnchor(cell.watershedCellIndex(), terrain);
            if (projected.isEmpty()) {
                continue;
            }
            var anchor = projected.orElseThrow();
            assertEquals(
                    Biomes.SWAMP,
                    resolver.resolveAuthoredSurface(
                                    fixture.volume().id(),
                                    anchor.worldX(),
                                    anchor.worldZ())
                            .orElseThrow(),
                    "accepted DR-70 freshwater/riparian context must not leak the native ocean biome");
            supportedWetAnchors++;
        }
        assertTrue(supportedWetAnchors > 0, "DR-70 review fixture must retain accepted wet surface anchors");
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
