package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
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
    void projectionScopeIsThreadLocalBoundedAndNonNested() {
        assertFalse(SkyforgeTerrainProjectionStage.active());
        try (SkyforgeTerrainProjectionStage.Scope scope = SkyforgeTerrainProjectionStage.open()) {
            scope.requireActive();
            assertTrue(SkyforgeTerrainProjectionStage.active());
            assertThrows(IllegalStateException.class, SkyforgeTerrainProjectionStage::open);
        }
        assertFalse(SkyforgeTerrainProjectionStage.active());
    }

    @Test
    void developmentFixtureKeepsVillageRootOutsideButProvidesUpperOverlapProvenance() throws Exception {
        var catalog = SkyforgeNeoForge1211TerrainProjectionDevRuntime.catalog();
        var adapter = SkyforgeNeoForge1211TerrainProjectionDevRuntime.adapter();
        var volumeId = catalog.volumes().getFirst().id();

        try (AutoCloseable binding = SkyforgeNeoForge1211SurfaceStage.installNativeSurfaceAdapted(
                adapter,
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()))) {
            assertNotNull(binding);
            assertEquals(List.of(volumeId),
                    SkyforgeNeoForge1211SurfaceStage.claimingVolumeIds(560, 223, 8).orElseThrow());
            assertTrue(SkyforgeNeoForge1211SurfaceStage.claimingVolumeIds(520, 223, 8).orElseThrow().isEmpty());
            double underside = SkyforgeNeoForge1211SurfaceStage.undersideSurfaceHeight(volumeId, 560, 8)
                    .orElseThrow();
            assertTrue(underside > 200.0);
            assertTrue(underside < 224.0);
        }
    }
}
