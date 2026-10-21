package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

final class SkyforgePopulationSeedTest {
    private static final ResourceLocation FEATURE_KEY =
            ResourceLocation.fromNamespaceAndPath("minecraft", "trees_plains");

    @Test
    void derivationIsStableForOneExactOperation() {
        var volumeId = volume(0, 0, 101L);
        var chunk = new ChunkPos(3, -2);

        long first = SkyforgePopulationSeed.derive(volumeId, chunk, FEATURE_KEY, 9, 0);
        long second = SkyforgePopulationSeed.derive(volumeId, chunk, FEATURE_KEY, 9, 0);

        assertEquals(first, second);
    }

    @Test
    void stackedVolumesAtTheSameChunkHaveIndependentStreams() {
        var lower = volume(0, 0, 101L);
        var upper = volume(0, 1, 202L);
        var chunk = new ChunkPos(0, 0);

        assertNotEquals(
                SkyforgePopulationSeed.derive(lower, chunk, FEATURE_KEY, 9, 0),
                SkyforgePopulationSeed.derive(upper, chunk, FEATURE_KEY, 9, 0));
    }

    @Test
    void nativeDefinitionStepOccurrenceAndChunkAllParticipateInIdentity() {
        var volumeId = volume(4, 7, 303L);
        var chunk = new ChunkPos(1, 1);
        long baseline = SkyforgePopulationSeed.derive(volumeId, chunk, FEATURE_KEY, 9, 0);

        assertNotEquals(
                baseline,
                SkyforgePopulationSeed.derive(
                        volumeId,
                        chunk,
                        ResourceLocation.fromNamespaceAndPath("minecraft", "flower_plains"),
                        9,
                        0));
        assertNotEquals(baseline, SkyforgePopulationSeed.derive(volumeId, chunk, FEATURE_KEY, 10, 0));
        assertNotEquals(baseline, SkyforgePopulationSeed.derive(volumeId, chunk, FEATURE_KEY, 9, 1));
        assertNotEquals(baseline, SkyforgePopulationSeed.derive(volumeId, new ChunkPos(1, 2), FEATURE_KEY, 9, 0));
    }

    @Test
    void invalidOperationCoordinatesAreRejected() {
        var volumeId = volume(0, 0, 101L);
        var chunk = new ChunkPos(0, 0);

        assertThrows(
                IllegalArgumentException.class,
                () -> SkyforgePopulationSeed.derive(volumeId, chunk, FEATURE_KEY, -1, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> SkyforgePopulationSeed.derive(volumeId, chunk, FEATURE_KEY, 0, -1));
    }

    private static SkyIslandWorldVolumeId volume(int groupOrdinal, int memberOrdinal, long geometrySeed) {
        return new SkyIslandWorldVolumeId(
                0x534b59464f524745L,
                "population-seed-test",
                groupOrdinal,
                memberOrdinal,
                geometrySeed);
    }
}
