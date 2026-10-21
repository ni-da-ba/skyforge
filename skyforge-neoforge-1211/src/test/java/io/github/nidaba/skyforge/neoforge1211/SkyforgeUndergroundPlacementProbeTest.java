package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.GenerationStep;
import org.junit.jupiter.api.Test;

final class SkyforgeUndergroundPlacementProbeTest {
    @Test
    void transformedHeightEvidenceAndWriteGatesRemainIndependent() throws Exception {
        var volumeId = new SkyIslandWorldVolumeId(59L, "probe", 0, 0, 5901L);
        var operation = SkyforgePopulationOperation.create(
                volumeId,
                new ChunkPos(0, 0),
                ResourceLocation.withDefaultNamespace("ore_probe"),
                GenerationStep.Decoration.UNDERGROUND_ORES.ordinal(),
                0);

        try (var domain = SkyforgeGenerationDomainStage.openIsland(volumeId);
                var execution = SkyforgePopulationExecutionStage.openForTest(
                        operation,
                        position -> position.getY() == 110,
                        0);
                var probe = SkyforgeUndergroundPlacementProbe.open(volumeId, 100, 120)) {
            SkyforgeUndergroundPlacementProbe.observeHeightRangeTransform(
                    new BlockPos(1, 90, 1),
                    new BlockPos(1, 100, 1));
            SkyforgeUndergroundPlacementProbe.observeHeightRangeTransform(
                    new BlockPos(2, 110, 2),
                    new BlockPos(2, 110, 2));
            SkyforgeUndergroundPlacementProbe.observeHeightRangeTransform(
                    new BlockPos(3, 130, 3),
                    new BlockPos(3, 120, 3));

            assertTrue(SkyforgeWorldGenRegionDomainBridge.canWrite(new BlockPos(2, 110, 2)));
            assertFalse(SkyforgeWorldGenRegionDomainBridge.canWrite(new BlockPos(1, 90, 1)));
            assertTrue(SkyforgeWorldGenRegionDomainBridge.acceptWrite(new BlockPos(2, 110, 2)));
            assertFalse(SkyforgeWorldGenRegionDomainBridge.acceptWrite(new BlockPos(1, 90, 1)));

            var snapshot = probe.snapshot();
            assertEquals(3, snapshot.heightRangeSamples());
            assertEquals(1, snapshot.nativeSamplesBelowEnvelope());
            assertEquals(1, snapshot.nativeSamplesInsideEnvelope());
            assertEquals(1, snapshot.nativeSamplesAboveEnvelope());
            assertEquals(90, snapshot.minimumNativeSampleY());
            assertEquals(130, snapshot.maximumNativeSampleY());
            assertEquals(0, snapshot.mappedSamplesBelowEnvelope());
            assertEquals(3, snapshot.mappedSamplesInsideEnvelope());
            assertEquals(0, snapshot.mappedSamplesAboveEnvelope());
            assertEquals(100, snapshot.minimumMappedSampleY());
            assertEquals(120, snapshot.maximumMappedSampleY());
            assertEquals(2, snapshot.transformedHeightSamples());
            assertNotEquals(0L, snapshot.heightTransformDigest());
            assertEquals(2, snapshot.writePreflightChecks());
            assertEquals(1, snapshot.acceptedWritePreflights());
            assertEquals(1, snapshot.rejectedWritePreflights());
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
