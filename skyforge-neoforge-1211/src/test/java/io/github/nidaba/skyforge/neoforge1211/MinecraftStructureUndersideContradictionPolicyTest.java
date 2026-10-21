package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

final class MinecraftStructureUndersideContradictionPolicyTest {
    private static final int SURFACE_FLOOR_Y = 223;
    private static final Predicate<BoundingBox> BELOW_PROOF = box -> box.maxY() <= 170;

    @Test
    void detachedWhollyBelowComponentIsPositiveContradiction() {
        BoundingBox surface = box(0, 223, 0, 10, 230, 10);
        BoundingBox detachedBelow = box(2, 150, 2, 4, 160, 4);

        var contradiction = MinecraftStructureUndersideContradictionPolicy.findContradictoryComponent(
                List.of(surface, detachedBelow), SURFACE_FLOOR_Y, BELOW_PROOF);

        assertTrue(contradiction.isPresent());
        assertEquals(1, contradiction.orElseThrow().size());
        assertEquals(150, contradiction.orElseThrow().getFirst().minY());
    }

    @Test
    void connectedVerticalChainPreservesNativeStructure() {
        BoundingBox surface = box(0, 223, 0, 4, 230, 4);
        BoundingBox shaft = box(0, 171, 0, 4, 222, 4);
        BoundingBox deepRoom = box(0, 160, 0, 4, 170, 4);

        var contradiction = MinecraftStructureUndersideContradictionPolicy.findContradictoryComponent(
                List.of(surface, shaft, deepRoom), SURFACE_FLOOR_Y, BELOW_PROOF);

        assertTrue(contradiction.isEmpty());
    }

    @Test
    void disconnectedComponentWithAnyUnprovedPieceRemainsUncertain() {
        BoundingBox surface = box(0, 223, 0, 4, 230, 4);
        BoundingBox below = box(20, 150, 20, 24, 160, 24);
        BoundingBox ambiguous = box(20, 161, 20, 24, 180, 24);

        var contradiction = MinecraftStructureUndersideContradictionPolicy.findContradictoryComponent(
                List.of(surface, below, ambiguous), SURFACE_FLOOR_Y, BELOW_PROOF);

        assertTrue(contradiction.isEmpty());
    }

    @Test
    void separatedSurfaceBuildingsAreNotContradiction() {
        BoundingBox left = box(0, 223, 0, 4, 230, 4);
        BoundingBox right = box(40, 223, 40, 44, 230, 44);

        var contradiction = MinecraftStructureUndersideContradictionPolicy.findContradictoryComponent(
                List.of(left, right), SURFACE_FLOOR_Y, ignored -> false);

        assertTrue(contradiction.isEmpty());
    }

    @Test
    void oneBlockGapCountsAsConnectedToBiasTowardPreservation() {
        BoundingBox surface = box(0, 223, 0, 4, 230, 4);
        BoundingBox adjacent = box(0, 215, 0, 4, 222, 4);
        BoundingBox deep = box(0, 200, 0, 4, 214, 4);

        var components = MinecraftStructureUndersideContradictionPolicy.connectedComponents(
                List.of(surface, adjacent, deep));

        assertEquals(1, components.size());
    }

    @Test
    void absenceOfSurfaceRootFailsOpen() {
        BoundingBox detachedBelow = box(0, 150, 0, 2, 160, 2);

        var contradiction = MinecraftStructureUndersideContradictionPolicy.findContradictoryComponent(
                List.of(detachedBelow), SURFACE_FLOOR_Y, ignored -> true);

        assertTrue(contradiction.isEmpty());
    }

    @Test
    void extremeCoordinatesDoNotOverflowAdjacency() {
        BoundingBox minimum = box(Integer.MIN_VALUE, 223, 0, Integer.MIN_VALUE, 223, 0);
        BoundingBox maximum = box(Integer.MAX_VALUE, 223, 0, Integer.MAX_VALUE, 223, 0);

        var components = MinecraftStructureUndersideContradictionPolicy.connectedComponents(List.of(minimum, maximum));

        assertEquals(2, components.size());
        assertFalse(components.isEmpty());
    }

    private static BoundingBox box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
