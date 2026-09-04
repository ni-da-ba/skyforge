package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SkyIslandCaveSystemPlannerTest {
    private static final long SEED = 0x534B59464F524745L;

    @Test
    void topologyIsDeterministicAndEachSystemIsAConnectedTree() {
        SkyIslandDescriptor descriptor = descriptor(653L);
        SkyIslandCaveSystemPlan first = SkyIslandCaveSystemPlanner.plan(descriptor);
        SkyIslandCaveSystemPlan second = SkyIslandCaveSystemPlanner.plan(descriptor);

        assertEquals(first, second);
        assertFalse(first.systems().isEmpty());

        for (SkyIslandCaveSystem system : first.systems()) {
            assertEquals(system.nodes().size() - 1, system.links().size());
            assertConnected(system);
        }
    }

    @Test
    void everyCaveNodeIsGroundedInItsAuth0023VoidRegion() {
        for (long key : new long[] {653L, 1051L, 1439L, 3670L}) {
            SkyIslandCaveSystemPlan plan = SkyIslandCaveSystemPlanner.plan(descriptor(key));
            Map<Integer, SkyIslandGeologicRegion> voidRegions = new HashMap<>();
            for (SkyIslandGeologicRegion region : plan.geology().regions()) {
                if (region.kind() == SkyIslandGeologicRegionKind.VOID_PRONE_DOMAIN) {
                    voidRegions.put(region.regionId(), region);
                }
            }

            for (SkyIslandCaveSystem system : plan.systems()) {
                for (SkyIslandCaveNode node : system.nodes()) {
                    SkyIslandGeologicRegion source = voidRegions.get(node.sourceVoidRegionId());
                    assertTrue(source != null);
                    assertTrue(source.cells().stream()
                            .anyMatch(cell -> cell.position().equals(node.position())));
                }
            }
        }
    }

    @Test
    void chamberSelectionRemainsSparseRelativeToVoidRegions() {
        for (long key : new long[] {653L, 1051L, 1439L, 3670L}) {
            SkyIslandCaveSystemPlan plan = SkyIslandCaveSystemPlanner.plan(descriptor(key));
            Map<Integer, Integer> anchorsByRegion = new HashMap<>();
            for (SkyIslandCaveSystem system : plan.systems()) {
                for (SkyIslandCaveNode node : system.nodes()) {
                    anchorsByRegion.merge(node.sourceVoidRegionId(), 1, Integer::sum);
                }
            }
            for (int count : anchorsByRegion.values()) {
                assertTrue(count >= 1 && count <= 3);
            }
        }
    }

    @Test
    void islandsWithoutVoidDomainsProduceNoInventedCaves() {
        for (long key : new long[] {2332L, 2211L}) {
            SkyIslandCaveSystemPlan plan = SkyIslandCaveSystemPlanner.plan(descriptor(key));
            assertEquals(0, plan.geology().regionCount(SkyIslandGeologicRegionKind.VOID_PRONE_DOMAIN));
            assertTrue(plan.systems().isEmpty());
            assertEquals(0, plan.nodeCount());
            assertEquals(0, plan.linkCount());
        }
    }

    @Test
    void interRegionLinksCarryExpressedGeologicalSupport() {
        SkyIslandCaveSystemPlan plan = SkyIslandCaveSystemPlanner.plan(descriptor(1051L));
        for (SkyIslandCaveSystem system : plan.systems()) {
            for (SkyIslandCaveLink link : system.links()) {
                if (link.kind() == SkyIslandCaveConnectionKind.VOID_CONTINUITY) {
                    continue;
                }
                assertTrue(Math.max(link.fractureSupport(), link.aquiferSupport()) >= 0.34 - 1.0e-12);
                switch (link.kind()) {
                    case FRACTURE_BRIDGE -> assertTrue(link.fractureSupport() >= 0.34 - 1.0e-12);
                    case AQUIFER_BRIDGE -> assertTrue(link.aquiferSupport() >= 0.34 - 1.0e-12);
                    case MIXED_GEOLOGIC_BRIDGE -> {
                        assertTrue(link.fractureSupport() >= 0.34 - 1.0e-12);
                        assertTrue(link.aquiferSupport() >= 0.34 - 1.0e-12);
                    }
                    case VOID_CONTINUITY -> throw new AssertionError("handled above");
                }
            }
        }
    }

    private static void assertConnected(SkyIslandCaveSystem system) {
        Map<Integer, SkyIslandCaveNode> nodes = new HashMap<>();
        for (SkyIslandCaveNode node : system.nodes()) {
            nodes.put(node.nodeId(), node);
        }

        Set<Integer> visited = new HashSet<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(system.nodes().getFirst().nodeId());

        while (!queue.isEmpty()) {
            int nodeId = queue.removeFirst();
            if (!visited.add(nodeId)) {
                continue;
            }
            for (SkyIslandCaveLink link : system.links()) {
                if (link.touches(nodeId)) {
                    int other = link.other(nodeId);
                    if (!visited.contains(other)) {
                        queue.addLast(other);
                    }
                }
            }
        }
        assertEquals(nodes.keySet(), visited);
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 8L, 81L, key));
    }
}
