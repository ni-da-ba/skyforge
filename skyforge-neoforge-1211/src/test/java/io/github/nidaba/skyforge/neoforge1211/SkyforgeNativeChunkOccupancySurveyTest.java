package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.world.SkyIslandTerrainProfile;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.junit.jupiter.api.Test;

final class SkyforgeNativeChunkOccupancySurveyTest {
    @Test
    void boundedSurveyMatchesHistoricalFullHeightEvidenceExactly() throws Exception {
        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.single();
        var volume = fixture.volume();
        var bounds = volume.bounds();
        int centerX = (int) Math.floor((bounds.minimumX() + bounds.maximumX()) * 0.5);
        int centerZ = (int) Math.floor((bounds.minimumZ() + bounds.maximumZ()) * 0.5);
        ChunkPos chunkPos = new ChunkPos(Math.floorDiv(centerX, 16), Math.floorDiv(centerZ, 16));
        ProtoChunk chunk = MinecraftTestChunkFactory.protoChunk(chunkPos);

        var adapter = new SkyforgeNeoForge1211ChunkAdapter(
                fixture.catalog(),
                SkyIslandTerrainProfile.reference(),
                new SkyforgeMinecraftBlockPalette());
        BlockPos owned = firstOwnedPosition(adapter, volume.id(), chunk);
        chunk.setBlockState(owned, Blocks.CHEST.defaultBlockState(), false);

        SkyforgeNativeChunkOccupancySurvey.Result bounded;
        SkyforgeNativeChunkOccupancySurvey.Result fullHeight;
        SkyforgeNativeChunkOccupancySurvey.VerticalRange range;
        try (AutoCloseable terrain = SkyforgeNeoForge1211SurfaceStage.install(
                adapter,
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()))) {
            assertNotNull(terrain);
            range = SkyforgeNativeChunkOccupancySurvey.boundedVerticalRange(chunk, bounds);
            bounded = SkyforgeNativeChunkOccupancySurvey.survey(volume.id(), chunk);
            fullHeight = SkyforgeNativeChunkOccupancySurvey.surveyRange(
                    volume.id(),
                    chunk,
                    chunk.getMinBuildHeight(),
                    chunk.getMaxBuildHeight());
        }

        assertTrue(range.height() > 0);
        assertTrue(range.height() < chunk.getHeight(), "fixture should prove that the bounded scan is smaller");
        assertEquals(fullHeight, bounded, "Y clamping must preserve exact admission evidence");
        assertTrue(bounded.conflicts());
        assertEquals(owned, bounded.firstConflict().orElseThrow().position());
    }

    @Test
    void disjointVolumeBoundsProduceEmptyChunkLocalRange() {
        ProtoChunk chunk = MinecraftTestChunkFactory.protoChunk(new ChunkPos(0, 0));
        var aboveBuildHeight = new io.github.nidaba.skyforge.world.WorldBounds(
                -16.0,
                16.0,
                500.0,
                600.0,
                -16.0,
                16.0);

        var range = SkyforgeNativeChunkOccupancySurvey.boundedVerticalRange(chunk, aboveBuildHeight);

        assertEquals(chunk.getMaxBuildHeight(), range.minimumY());
        assertEquals(chunk.getMaxBuildHeight(), range.maximumYExclusive());
        assertEquals(0, range.height());
    }

    private static BlockPos firstOwnedPosition(
            SkyforgeNeoForge1211ChunkAdapter adapter,
            io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId volumeId,
            ProtoChunk chunk) {
        int minimumX = chunk.getPos().getMinBlockX();
        int minimumZ = chunk.getPos().getMinBlockZ();
        for (int y = chunk.getMinBuildHeight(); y < chunk.getMaxBuildHeight(); y++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                for (int localX = 0; localX < 16; localX++) {
                    int x = minimumX + localX;
                    int z = minimumZ + localZ;
                    if (adapter.isSolidOwnedBy(volumeId, x, y, z)) {
                        return new BlockPos(x, y, z);
                    }
                }
            }
        }
        throw new AssertionError("fixture center chunk contains no exact-volume solid");
    }
}
