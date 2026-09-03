package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.GenerationStep;
import org.junit.jupiter.api.Test;

final class SkyforgeUndergroundPlacementProbeTest {
    @Test
    void observationPreservesHeightRangePositionsAndRecordsExistingWriteGate() throws Exception {
        var volumeId = new SkyIslandWorldVolumeId(59L, "probe", 0, 0, 5901L);
        var operation = SkyforgePopulationOperation.create(
                volumeId,
                new ChunkPos(0, 0),
                ResourceLocation.withDefaultNamespace("ore_probe"),
                GenerationStep.Decoration.UNDERGROUND_ORES.ordinal(),
                0);
        List<BlockPos> expected = List.of(
                new BlockPos(1, 90, 1),
                new BlockPos(2, 110, 2),
                new BlockPos(3, 130, 3));

        try (var domain = SkyforgeGenerationDomainStage.openIsland(volumeId);
                var execution = SkyforgePopulationExecutionStage.openForTest(
                        operation,
                        position -> position.getY() == 110,
                        0);
                var probe = SkyforgeUndergroundPlacementProbe.open(volumeId, 100, 120)) {
            List<BlockPos> observed = SkyforgeUndergroundPlacementProbe.observeHeightRangePositions(
                            Stream.of(expected.toArray(BlockPos[]::new)))
                    .toList();
            assertEquals(expected, observed);

            assertTrue(SkyforgeWorldGenRegionDomainBridge.acceptWrite(new BlockPos(2, 110, 2)));
            assertFalse(SkyforgeWorldGenRegionDomainBridge.acceptWrite(new BlockPos(1, 90, 1)));

            var snapshot = probe.snapshot();
            assertEquals(3, snapshot.heightRangeSamples());
            assertEquals(1, snapshot.samplesBelowEnvelope());
            assertEquals(1, snapshot.samplesInsideEnvelope());
            assertEquals(1, snapshot.samplesAboveEnvelope());
            assertEquals(90, snapshot.minimumSampleY());
            assertEquals(130, snapshot.maximumSampleY());
            assertEquals(1, snapshot.acceptedWriteAttempts());
            assertEquals(1, snapshot.rejectedWriteAttempts());
            assertEquals(1, snapshot.uniqueAcceptedWritePositions());
            assertEquals(110, snapshot.minimumAcceptedWriteY());
            assertEquals(110, snapshot.maximumAcceptedWriteY());

            execution.requireActive();
            domain.requireActive();
        }
    }
}
