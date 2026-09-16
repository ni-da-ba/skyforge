package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.world.SkyIslandTerrainProfile;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

final class SkyforgePhysicalVolumeAdmissionStageTest {
    @Test
    void inactiveStagePreservesHistoricalPopulationBehavior() {
        var volumeId = SkyforgeNeoForge1211PopulationDevRuntime.catalog().volumes().getFirst().id();
        assertFalse(SkyforgePhysicalVolumeAdmissionStage.active());
        assertTrue(SkyforgePhysicalVolumeAdmissionStage.allowsPopulation(volumeId));
    }

    @Test
    void deferredChunkSchedulingIsCanonicalAcrossHashIterationOrder() {
        var keys = new HashSet<Long>();
        keys.add(new ChunkPos(3, -2).toLong());
        keys.add(new ChunkPos(-4, 9).toLong());
        keys.add(new ChunkPos(3, -7).toLong());
        keys.add(new ChunkPos(-4, -1).toLong());

        assertEquals(
                List.of(
                        new ChunkPos(-4, -1).toLong(),
                        new ChunkPos(-4, 9).toLong(),
                        new ChunkPos(3, -7).toLong(),
                        new ChunkPos(3, -2).toLong()),
                List.copyOf(SkyforgePhysicalVolumeAdmissionStage.orderedChunkKeys(keys)));
    }

    @Test
    void maintainedDeferredChunkIndexActivatesAtAdmissionAndRetiresAtCompletion() throws Exception {
        var sourceCatalog = SkyforgeNeoForge1211PopulationDevRuntime.catalog();
        var volume = sourceCatalog.volumes().getFirst();
        var catalog = new io.github.nidaba.skyforge.world.SkyIslandWorldCatalog(
                sourceCatalog.rootSeed(),
                List.of(volume));
        var bounds = volume.bounds();
        int minimumChunkX = Math.floorDiv((int) Math.floor(bounds.minimumX()), 16);
        int maximumChunkX = Math.floorDiv((int) Math.floor(bounds.maximumX()), 16);
        int minimumChunkZ = Math.floorDiv((int) Math.floor(bounds.minimumZ()), 16);
        int maximumChunkZ = Math.floorDiv((int) Math.floor(bounds.maximumZ()), 16);
        assertTrue(
                minimumChunkX != maximumChunkX || minimumChunkZ != maximumChunkZ,
                "fixture volume must span multiple chunks");

        ChunkPos deferredPos = new ChunkPos(minimumChunkX, minimumChunkZ);
        ChunkPos admittingPos = minimumChunkX != maximumChunkX
                ? new ChunkPos(maximumChunkX, minimumChunkZ)
                : new ChunkPos(minimumChunkX, maximumChunkZ);
        var deferredChunk = MinecraftTestChunkFactory.protoChunk(deferredPos);
        var admittingChunk = MinecraftTestChunkFactory.protoChunk(admittingPos);
        var adapter = new SkyforgeNeoForge1211ChunkAdapter(
                catalog,
                SkyIslandTerrainProfile.reference(),
                new SkyforgeMinecraftBlockPalette());

        try (AutoCloseable terrain = SkyforgeNeoForge1211SurfaceStage.install(
                        adapter,
                        new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()));
                AutoCloseable binding = SkyforgePhysicalVolumeAdmissionStage.install(
                        catalog,
                        Map.of(volume.id(), Set.of(deferredPos.toLong(), admittingPos.toLong())))) {
            assertNotNull(terrain);
            assertNotNull(binding);

            SkyforgePhysicalVolumeAdmissionStage.observeBeforeRealization(
                    deferredChunk,
                    Optional.empty());
            assertEquals(
                    SkyforgePhysicalVolumeAdmissionState.PLANNED,
                    SkyforgePhysicalVolumeAdmissionStage.snapshot(volume.id()).state());
            assertTrue(SkyforgePhysicalVolumeAdmissionStage.eligibleCatchupChunkKeys().isEmpty());
            assertTrue(SkyforgePhysicalVolumeAdmissionStage.hasPendingCatchup(volume.id(), deferredPos));

            SkyforgePhysicalVolumeAdmissionStage.observeBeforeRealization(
                    admittingChunk,
                    Optional.empty());
            assertEquals(
                    SkyforgePhysicalVolumeAdmissionState.ADMITTED,
                    SkyforgePhysicalVolumeAdmissionStage.snapshot(volume.id()).state());
            assertEquals(
                    List.of(deferredPos.toLong()),
                    List.copyOf(SkyforgePhysicalVolumeAdmissionStage.eligibleCatchupChunkKeys()));
            assertFalse(SkyforgePhysicalVolumeAdmissionStage.hasPendingCatchup(volume.id(), admittingPos));

            var pending = SkyforgePhysicalVolumeAdmissionStage.eligibleCatchup(deferredPos).getFirst();
            SkyforgePhysicalVolumeAdmissionStage.completeCatchup(pending);
            assertFalse(SkyforgePhysicalVolumeAdmissionStage.hasPendingCatchup(volume.id(), deferredPos));
            assertTrue(SkyforgePhysicalVolumeAdmissionStage.eligibleCatchupChunkKeys().isEmpty());
        }
    }

    @Test
    void exactFootprintSkipsBoundsOnlyChunksBeforeOccupancySurvey() throws Exception {
        var sourceCatalog = SkyforgeNeoForge1211PopulationDevRuntime.catalog();
        var volume = sourceCatalog.volumes().getFirst();
        var catalog = new io.github.nidaba.skyforge.world.SkyIslandWorldCatalog(
                sourceCatalog.rootSeed(),
                List.of(volume));
        var bounds = volume.bounds();
        int minimumChunkX = Math.floorDiv((int) Math.floor(bounds.minimumX()), 16);
        int maximumChunkX = Math.floorDiv((int) Math.floor(bounds.maximumX()), 16);
        int minimumChunkZ = Math.floorDiv((int) Math.floor(bounds.minimumZ()), 16);
        int maximumChunkZ = Math.floorDiv((int) Math.floor(bounds.maximumZ()), 16);
        assertTrue(
                minimumChunkX != maximumChunkX || minimumChunkZ != maximumChunkZ,
                "fixture volume must span multiple chunks");

        ChunkPos requiredPos = new ChunkPos(minimumChunkX, minimumChunkZ);
        ChunkPos excludedPos = minimumChunkX != maximumChunkX
                ? new ChunkPos(maximumChunkX, minimumChunkZ)
                : new ChunkPos(minimumChunkX, maximumChunkZ);
        var excludedChunk = MinecraftTestChunkFactory.protoChunk(excludedPos);
        var excludedBounds = new MinecraftChunkBounds(
                excludedPos,
                excludedChunk.getMinBuildHeight(),
                excludedChunk.getHeight());
        assertTrue(
                catalog.query(excludedBounds.worldBounds()).stream()
                        .anyMatch(candidate -> candidate.id().equals(volume.id())),
                "excluded regression chunk must still intersect conservative catalog bounds");

        try (AutoCloseable binding = SkyforgePhysicalVolumeAdmissionStage.install(
                catalog,
                Map.of(volume.id(), Set.of(requiredPos.toLong())))) {
            assertNotNull(binding);
            SkyforgePhysicalVolumeAdmissionStage.observeBeforeRealization(
                    excludedChunk,
                    Optional.empty());

            var observation = SkyforgePhysicalVolumeAdmissionStage.snapshot(volume.id());
            assertEquals(0, observation.observedChunks());
            assertEquals(1, observation.requiredChunks());
            assertFalse(
                    SkyforgePhysicalVolumeAdmissionStage.hasPendingCatchup(
                            volume.id(),
                            excludedPos));
        }
    }

    @Test
    void plannedVolumeCannotPopulateBeforeWholeVolumeAdmission() throws Exception {
        var catalog = SkyforgeNeoForge1211PopulationDevRuntime.catalog();
        var volumeId = catalog.volumes().getFirst().id();

        try (AutoCloseable binding = SkyforgePhysicalVolumeAdmissionStage.install(catalog)) {
            assertNotNull(binding);
            assertTrue(SkyforgePhysicalVolumeAdmissionStage.active());
            assertFalse(SkyforgePhysicalVolumeAdmissionStage.allowsPopulation(volumeId));
        }

        assertFalse(SkyforgePhysicalVolumeAdmissionStage.active());
        assertTrue(SkyforgePhysicalVolumeAdmissionStage.allowsPopulation(volumeId));
    }
}
