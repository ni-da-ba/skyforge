package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.GravityProcessor;
import org.junit.jupiter.api.Test;

final class SkyforgeTerrainProjectionSeamTest {
    @Test
    void bootstrapReplacesOnlyTheSharedVanillaTerrainMatchingGravityProcessor() {
        SkyforgeTerrainProjectionBootstrap.install();

        List<?> processors = StructureTemplatePool.Projection.TERRAIN_MATCHING.processors;
        assertEquals(1, processors.stream().filter(SkyforgeTerrainScopedGravityProcessor.class::isInstance).count());
        assertEquals(0, processors.stream().filter(processor -> processor.getClass() == GravityProcessor.class).count());
        assertTrue(SkyforgeTerrainProjectionBootstrap.installed());

        SkyforgeTerrainProjectionBootstrap.install();
        assertEquals(1, StructureTemplatePool.Projection.TERRAIN_MATCHING.processors.stream()
                .filter(SkyforgeTerrainScopedGravityProcessor.class::isInstance)
                .count());
    }

    @Test
    void projectionScopeCarriesOnlyItsIndependentBaseWorldSnapshot() {
        int[] heights = new int[256];
        Arrays.fill(heights, 71);
        var snapshot = new MinecraftBaseTerrainSurfaceSnapshot(new ChunkPos(0, 0), heights);

        assertFalse(SkyforgeTerrainProjectionStage.active());
        try (SkyforgeTerrainProjectionStage.Scope scope = SkyforgeTerrainProjectionStage.open(snapshot)) {
            scope.requireActive();
            assertTrue(SkyforgeTerrainProjectionStage.active());
            assertEquals(71, SkyforgeTerrainProjectionStage.baseWorldFirstFreeHeight(8, 8).orElseThrow());
            assertTrue(SkyforgeTerrainProjectionStage.baseWorldFirstFreeHeight(24, 8).isEmpty());
            assertThrows(IllegalStateException.class, () -> SkyforgeTerrainProjectionStage.open(snapshot));
        }
        assertFalse(SkyforgeTerrainProjectionStage.active());
    }

    @Test
    void developmentFixtureResolvesBaseWorldAndIslandAsSeparateDomains() throws Exception {
        var catalog = SkyforgeNeoForge1211TerrainProjectionDevRuntime.catalog();
        var adapter = SkyforgeNeoForge1211TerrainProjectionDevRuntime.adapter();
        var volumeId = catalog.volumes().getFirst().id();

        try (AutoCloseable binding = SkyforgeNeoForge1211SurfaceStage.installNativeSurfaceAdapted(
                adapter,
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()))) {
            assertNotNull(binding);
            assertEquals(List.of(volumeId),
                    SkyforgeNeoForge1211SurfaceStage.claimingVolumeIds(560, 223, 8).orElseThrow());
            assertTrue(SkyforgeNeoForge1211SurfaceStage.claimingVolumeIds(520, 70, 8).orElseThrow().isEmpty());

            var baseDomain = SkyforgeNeoForge1211SurfaceStage.resolveTerrainDomain(520, 70, 8).orElseThrow();
            assertInstanceOf(MinecraftTerrainDomain.BaseWorld.class, baseDomain);

            var islandDomain = SkyforgeNeoForge1211SurfaceStage.resolveTerrainDomain(560, 223, 8).orElseThrow();
            var island = assertInstanceOf(MinecraftTerrainDomain.SkyforgeVolume.class, islandDomain);
            assertEquals(volumeId, island.volumeId());
            assertEquals(224, SkyforgeNeoForge1211SurfaceStage.skyforgeFirstFreeHeight(volumeId, 560, 8).orElseThrow());
        }
    }
}
