package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

/** Minecraft-side regression proof for SF-IMP-0047 piece-derived support geometry. */
final class SkyforgeStructurePieceFootprintTest {

    @Test
    void extractsDistinctFloorContactBoxesWithoutHigherSuperstructure() {
        BoundingBox first = new BoundingBox(0, 180, 0, 4, 186, 4);
        BoundingBox duplicateFirst = new BoundingBox(0, 180, 0, 4, 190, 4);
        BoundingBox second = new BoundingBox(10, 180, 0, 14, 188, 4);
        BoundingBox upper = new BoundingBox(2, 187, 2, 12, 194, 6);
        BoundingBox fallback = new BoundingBox(0, 180, 0, 14, 194, 6);

        List<BoundingBox> support = MinecraftStructureSupportGeometry.floorContactBoxes(
                List.of(first, duplicateFirst, second, upper),
                180,
                fallback);

        assertEquals(2, support.size());
        assertBox(0, 180, 0, 4, 180, 4, support.get(0));
        assertBox(10, 180, 0, 14, 180, 4, support.get(1));
    }

    @Test
    void fallsBackToStartEnvelopeWhenNoPieceTouchesResolvedFloor() {
        BoundingBox upper = new BoundingBox(2, 187, 2, 12, 194, 6);
        BoundingBox fallback = new BoundingBox(-4, 180, -5, 16, 194, 9);

        List<BoundingBox> support = MinecraftStructureSupportGeometry.floorContactBoxes(
                List.of(upper),
                180,
                fallback);

        assertEquals(1, support.size());
        assertBox(-4, 180, -5, 16, 180, 9, support.getFirst());
    }

    @Test
    void serializedFoundationLeavesEnvelopeGapUntouched() {
        List<BoundingBox> support = List.of(
                new BoundingBox(0, 180, 0, 2, 180, 2),
                new BoundingBox(8, 180, 0, 10, 180, 2));
        SkyIslandWorldVolumeId volumeId = new SkyIslandWorldVolumeId(91L, "piece-footprint", 2, 3, 92L);
        SkyforgeFoundationPiece piece = new SkyforgeFoundationPiece(support, 180, volumeId, 8);

        assertEquals(2, piece.footprintBoxCount());
        assertTrue(piece.supportsColumn(1, 1));
        assertFalse(piece.supportsColumn(5, 1));
        assertTrue(piece.supportsColumn(9, 1));
        assertEquals(0, piece.getBoundingBox().minX());
        assertEquals(10, piece.getBoundingBox().maxX());
        assertEquals(179, piece.getBoundingBox().maxY());
        assertEquals(172, piece.getBoundingBox().minY());

        CompoundTag tag = new CompoundTag();
        piece.addAdditionalSaveData(null, tag);
        assertArrayEquals(
                new int[] {0, 2, 0, 2, 8, 10, 0, 2},
                tag.getIntArray("SkyforgeFootprint"));
    }

    @Test
    void policyPreservesPieceUnionInsteadOfEnvelope() {
        List<BoundingBox> support = List.of(
                new BoundingBox(0, 180, 0, 2, 180, 2),
                new BoundingBox(8, 180, 0, 10, 180, 2));

        var requirements = MinecraftStructureSupportPolicy.requirements(support);

        assertEquals(2, requirements.footprint().rectangles().size());
        assertTrue(requirements.footprint().contains(1.0, 1.0));
        assertFalse(requirements.footprint().contains(5.0, 1.0));
        assertTrue(requirements.footprint().contains(9.0, 1.0));
    }

    private static void assertBox(
            int minimumX,
            int minimumY,
            int minimumZ,
            int maximumX,
            int maximumY,
            int maximumZ,
            BoundingBox actual) {
        assertEquals(minimumX, actual.minX());
        assertEquals(minimumY, actual.minY());
        assertEquals(minimumZ, actual.minZ());
        assertEquals(maximumX, actual.maxX());
        assertEquals(maximumY, actual.maxY());
        assertEquals(maximumZ, actual.maxZ());
    }
}
