package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import io.github.nidaba.skyforge.world.TerrainBoxObservation;
import io.github.nidaba.skyforge.world.WorldBounds;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

final class MinecraftStructurePieceUndersideSeparationProbeTest {
    private static final SkyIslandWorldVolumeId VOLUME_ID =
            new SkyIslandWorldVolumeId(11L, "group", 2, 3, 17L);
    private static final WorldBounds PIECE_BOUNDS =
            new WorldBounds(0.0, 2.0, 40.0, 42.0, 0.0, 2.0);

    @Test
    void allBelowUndersideSamplesProducePositiveEvidence() {
        TerrainBoxObservation observation = new TerrainBoxObservation(
                VOLUME_ID,
                27,
                0,
                0,
                27,
                0);

        var evidence = MinecraftStructurePieceUndersideSeparationProbe.classify(
                        PIECE_BOUNDS,
                        VOLUME_ID,
                        observation)
                .orElseThrow();

        assertEquals(VOLUME_ID, evidence.supportingVolumeId());
        assertEquals(PIECE_BOUNDS, evidence.pieceBounds());
        assertEquals(observation, evidence.observation());
    }

    @Test
    void mixedSamplesRemainNonProof() {
        TerrainBoxObservation observation = new TerrainBoxObservation(
                VOLUME_ID,
                27,
                1,
                0,
                26,
                0);

        assertTrue(MinecraftStructurePieceUndersideSeparationProbe.classify(
                        PIECE_BOUNDS,
                        VOLUME_ID,
                        observation)
                .isEmpty());
    }

    @Test
    void openBetweenSurfaceSamplesRemainNonProof() {
        TerrainBoxObservation observation = new TerrainBoxObservation(
                VOLUME_ID,
                27,
                0,
                0,
                26,
                1);

        assertTrue(MinecraftStructurePieceUndersideSeparationProbe.classify(
                        PIECE_BOUNDS,
                        VOLUME_ID,
                        observation)
                .isEmpty());
    }

    @Test
    void observationFromDifferentVolumeCannotBecomeEvidence() {
        SkyIslandWorldVolumeId otherVolume = new SkyIslandWorldVolumeId(11L, "group", 2, 4, 18L);
        TerrainBoxObservation observation = new TerrainBoxObservation(
                otherVolume,
                1,
                0,
                0,
                1,
                0);

        assertThrows(
                IllegalArgumentException.class,
                () -> MinecraftStructurePieceUndersideSeparationProbe.classify(
                        PIECE_BOUNDS,
                        VOLUME_ID,
                        observation));
    }

    @Test
    void oversizedPieceFailsOpenBeforeRuntimeObservation() {
        BoundingBox oversized = new BoundingBox(0, 0, 0, 1000, 1000, 1000);

        var evidence = MinecraftStructurePieceUndersideSeparationProbe.probe(oversized, VOLUME_ID);

        assertFalse(evidence.isPresent());
    }
}
