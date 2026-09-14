package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.junit.jupiter.api.Test;

/** Deterministic, exact-owner, save/reload-style evidence for the DR-20 representative tranche. */
final class SkyforgeAuthoredVisibleHydrologyAdapterTest {
    @Test
    void realizesAllRequiredAuthoredKindsWithoutNativeSpringClassificationOrForeignOwnership() throws Exception {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.single();
        var terrain = terrain(fixture.catalog(), fixture.descriptor());
        var first = SkyforgeAuthoredVisibleHydrologyAdapter.plan(fixture.descriptor(), fixture.volume(), terrain);
        var replay = SkyforgeAuthoredVisibleHydrologyAdapter.plan(fixture.descriptor(), fixture.volume(), terrain);
        assertEquals(first, replay);
        assertEquals(
                new HashSet<>(java.util.List.of(
                        SkyforgeAuthoredVisibleHydrologyAdapter.Feature.CHANNEL,
                        SkyforgeAuthoredVisibleHydrologyAdapter.Feature.RETAINED_WATER,
                        SkyforgeAuthoredVisibleHydrologyAdapter.Feature.VERTICAL_DISCHARGE,
                        SkyforgeAuthoredVisibleHydrologyAdapter.Feature.EDGE_DISCHARGE)),
                first.stream().map(SkyforgeAuthoredVisibleHydrologyAdapter.Deployment::feature).collect(java.util.stream.Collectors.toSet()));
        for (var deployment : first) {
            assertFalse(deployment.positions().isEmpty());
            for (var position : deployment.positions()) {
                assertTrue(terrain.isSolidOwnedBy(deployment.volumeId(), position.getX(), position.getY(), position.getZ()));
                assertFalse(terrain.isSolidOwnedByOtherVolume(deployment.volumeId(), position.getX(), position.getY(), position.getZ()));
            }
        }
        assertTrue(first.stream().filter(d -> d.feature() == SkyforgeAuthoredVisibleHydrologyAdapter.Feature.VERTICAL_DISCHARGE)
                .findFirst().orElseThrow().positions().size() >= 2);
    }

    @Test
    void lifecycleRealizesOnlyAvailableChunksAndReplayRetainsAuthoredWater() throws Exception {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.single();
        var terrain = terrain(fixture.catalog(), fixture.descriptor());
        var deployments = SkyforgeAuthoredVisibleHydrologyAdapter.plan(fixture.descriptor(), fixture.volume(), terrain);
        for (var deployment : deployments) {
            var chunks = new java.util.HashMap<Long, ProtoChunk>();
            for (var position : deployment.positions()) {
                chunks.computeIfAbsent(new net.minecraft.world.level.ChunkPos(position).toLong(), key -> {
                    try { return realizedChunk(terrain, new net.minecraft.world.level.ChunkPos(key)); }
                    catch (Exception exception) { throw new RuntimeException(exception); }
                });
            }
            for (var position : deployment.positions()) {
                assertTrue(chunks.get(new net.minecraft.world.level.ChunkPos(position).toLong()).getBlockState(position).is(Blocks.WATER));
            }

            // The normal surface lifecycle realizes only the event chunk. A later exact replay
            // names the same cells and changes no persisted water.
            assertEquals(0, chunks.values().stream()
                    .mapToInt(chunk -> SkyforgeAuthoredVisibleHydrologyAdapter.applyAvailable(chunk, terrain))
                    .sum());

            // Minecraft persists the chunk block state. On reload the deterministic plan names
            // the same target cells and consequently makes no second mutation.
            var reloaded = SkyforgeAuthoredVisibleHydrologyAdapter.plan(
                    fixture.descriptor(), fixture.volume(), terrain);
            var reloadedDeployment = reloaded.stream()
                    .filter(candidate -> candidate.feature() == deployment.feature())
                    .findFirst().orElseThrow();
            assertEquals(deployment, reloadedDeployment);
            assertEquals(0, chunks.values().stream()
                    .mapToInt(chunk -> SkyforgeAuthoredVisibleHydrologyAdapter.apply(chunk, reloadedDeployment))
                    .sum());
        }
    }

    @Test
    void staticAuthoredWaterCanBePersistedWithoutAChunkLifecycleBinding() {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.single();
        var terrain = terrain(fixture.catalog(), fixture.descriptor());
        var deployment = SkyforgeAuthoredVisibleHydrologyAdapter.plan(
                fixture.descriptor(), fixture.volume(), terrain).getFirst();
        var chunks = new java.util.HashMap<Long, ProtoChunk>();
        for (var position : deployment.positions()) {
            chunks.computeIfAbsent(new net.minecraft.world.level.ChunkPos(position).toLong(),
                    key -> MinecraftTestChunkFactory.protoChunk(new net.minecraft.world.level.ChunkPos(key)));
        }

        assertTrue(chunks.values().stream()
                .mapToInt(chunk -> SkyforgeAuthoredVisibleHydrologyAdapter.apply(chunk, deployment))
                .sum() > 0);
        assertEquals(0, chunks.values().stream()
                .mapToInt(chunk -> SkyforgeAuthoredVisibleHydrologyAdapter.apply(chunk, deployment))
                .sum());
    }

    @Test
    void stackedVolumesKeepAuthoredWaterExactOwnerLocal() {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.stacked();
        var terrain = terrain(fixture.catalog(), fixture.descriptor());
        var lower = SkyforgeAuthoredVisibleHydrologyAdapter.plan(fixture.descriptor(), fixture.lower(), terrain);
        var upper = SkyforgeAuthoredVisibleHydrologyAdapter.plan(fixture.descriptor(), fixture.upper(), terrain);

        assertNotEquals(fixture.lower().id(), fixture.upper().id());
        for (var deployment : lower) {
            for (var position : deployment.positions()) {
                assertTrue(terrain.isSolidOwnedBy(fixture.lower().id(), position.getX(), position.getY(), position.getZ()));
                assertFalse(terrain.isSolidOwnedByOtherVolume(fixture.lower().id(), position.getX(), position.getY(), position.getZ()));
            }
        }
        for (var deployment : upper) {
            for (var position : deployment.positions()) {
                assertTrue(terrain.isSolidOwnedBy(fixture.upper().id(), position.getX(), position.getY(), position.getZ()));
                assertFalse(terrain.isSolidOwnedByOtherVolume(fixture.upper().id(), position.getX(), position.getY(), position.getZ()));
            }
        }
    }

    private static SkyforgeNeoForge1211ChunkAdapter terrain(
            io.github.nidaba.skyforge.world.SkyIslandWorldCatalog catalog,
            io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor descriptor) {
        var authoredDescriptors = new java.util.LinkedHashMap<
                io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId,
                io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor>();
        for (var volume : catalog.volumes()) {
            authoredDescriptors.put(volume.id(), descriptor);
        }
        return new SkyforgeNeoForge1211ChunkAdapter(
                catalog,
                io.github.nidaba.skyforge.world.SkyIslandTerrainProfile.reference(),
                new SkyforgeMinecraftBlockPalette(),
                authoredDescriptors);
    }

    private static ProtoChunk realizedChunk(SkyforgeNeoForge1211ChunkAdapter terrain, net.minecraft.world.level.ChunkPos pos) throws Exception {
        ProtoChunk chunk = MinecraftTestChunkFactory.protoChunk(pos);
        try (AutoCloseable installedSurfaceStage = SkyforgeNeoForge1211SurfaceStage.install(
                terrain, new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()))) {
            assertNotNull(installedSurfaceStage);
            SkyforgeNeoForge1211SurfaceStage.realize(chunk);
        }
        return chunk;
    }
}
