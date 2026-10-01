package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SkyIslandCaveGeometryPlannerTest {
    private static final long SEED = 0x534B59464F524745L;

    @Test
    void geometryIsDeterministicAndPreservesTopologyCardinality() {
        SkyIslandDescriptor descriptor = descriptor(653L);
        SkyIslandCaveGeometryPlan first = SkyIslandCaveGeometryPlanner.plan(descriptor);
        SkyIslandCaveGeometryPlan second = SkyIslandCaveGeometryPlanner.plan(descriptor);

        assertEquals(first, second);
        assertFalse(first.systems().isEmpty());
        assertEquals(first.topology().nodeCount(), first.chamberCount());
        assertEquals(first.topology().linkCount(), first.passageCount());
        assertEquals(first.topology().systems().size(), first.systems().size());
    }

    @Test
    void caveFreeControlsRemainGeometryFree() {
        for (long key : new long[] {2332L, 2211L}) {
            SkyIslandCaveGeometryPlan plan = SkyIslandCaveGeometryPlanner.plan(descriptor(key));
            assertTrue(plan.topology().systems().isEmpty());
            assertTrue(plan.systems().isEmpty());
            assertEquals(0, plan.chamberCount());
            assertEquals(0, plan.passageCount());
        }
    }

    @Test
    void chambersRemainBoundedInSemanticDepthAndMapToNodes() {
        for (long key : new long[] {653L, 1051L, 1439L, 3670L}) {
            SkyIslandCaveGeometryPlan plan = SkyIslandCaveGeometryPlanner.plan(descriptor(key));
            Map<Integer, SkyIslandCaveNode> nodes = new HashMap<>();
            for (SkyIslandCaveSystem system : plan.topology().systems()) {
                for (SkyIslandCaveNode node : system.nodes()) {
                    nodes.put(node.nodeId(), node);
                }
            }

            for (SkyIslandCaveSystemGeometry system : plan.systems()) {
                for (SkyIslandCaveChamberGeometry chamber : system.chambers()) {
                    SkyIslandCaveNode node = nodes.get(chamber.nodeId());
                    assertTrue(node != null);
                    assertEquals(node.position(), chamber.center());
                    assertTrue(chamber.horizontalRadius() > 0.0);
                    assertTrue(chamber.depthRadius() > 0.0);
                    assertTrue(chamber.center().depthFraction() - chamber.depthRadius() >= -1.0e-12);
                    assertTrue(chamber.center().depthFraction() + chamber.depthRadius() <= 1.0 + 1.0e-12);
                }
            }
        }
    }

    @Test
    void passageSamplesStayInsideNaturalizedOwnershipAndSemanticDepth() {
        for (long key : new long[] {653L, 1051L, 3670L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandSemanticFieldSet semantic = SkyIslandSemanticFieldSet.create(descriptor);
            SkyIslandCaveGeometryPlan plan = SkyIslandCaveGeometryPlanner.plan(descriptor);

            for (SkyIslandCaveSystemGeometry system : plan.systems()) {
                for (SkyIslandCavePassageGeometry passage : system.passages()) {
                    assertEquals(13, passage.points().size());
                    for (SkyIslandCavePassagePoint point : passage.points()) {
                        assertTrue(point.position().depthFraction() > 0.0);
                        assertTrue(point.position().depthFraction() < 1.0);
                        assertTrue(semantic.interiority().sample(point.position().surfacePosition()) > 0.0);
                        assertTrue(point.horizontalRadius() > 0.0);
                        assertTrue(point.depthRadius() > 0.0);
                        assertTrue(point.position().depthFraction() - point.depthRadius() >= -1.0e-12);
                        assertTrue(point.position().depthFraction() + point.depthRadius() <= 1.0 + 1.0e-12);
                    }
                }
            }
        }
    }

    @Test
    void passageEndpointsRemainAnchoredToTopologyNodes() {
        SkyIslandCaveGeometryPlan plan = SkyIslandCaveGeometryPlanner.plan(descriptor(653L));
        Map<Integer, SkyIslandCaveNode> nodes = new HashMap<>();
        for (SkyIslandCaveSystem system : plan.topology().systems()) {
            for (SkyIslandCaveNode node : system.nodes()) {
                nodes.put(node.nodeId(), node);
            }
        }
        Map<Integer, SkyIslandCaveLink> links = new HashMap<>();
        for (SkyIslandCaveSystem system : plan.topology().systems()) {
            for (SkyIslandCaveLink link : system.links()) {
                links.put(link.linkId(), link);
            }
        }

        for (SkyIslandCaveSystemGeometry system : plan.systems()) {
            for (SkyIslandCavePassageGeometry passage : system.passages()) {
                SkyIslandCaveLink link = links.get(passage.linkId());
                SkyIslandSubsurfacePosition first = passage.points().getFirst().position();
                SkyIslandSubsurfacePosition last = passage.points().getLast().position();
                assertEquals(nodes.get(link.firstNodeId()).position(), first);
                assertEquals(nodes.get(link.secondNodeId()).position(), last);
            }
        }
    }

    @Test
    void positiveCorpusContainsAtLeastOneMeaningfullyCurvedPassage() {
        boolean foundCurved = false;
        for (long key : new long[] {653L, 1051L, 3670L}) {
            SkyIslandCaveGeometryPlan plan = SkyIslandCaveGeometryPlanner.plan(descriptor(key));
            for (SkyIslandCaveSystemGeometry system : plan.systems()) {
                for (SkyIslandCavePassageGeometry passage : system.passages()) {
                    SkyIslandSubsurfacePosition first = passage.points().getFirst().position();
                    SkyIslandSubsurfacePosition middle =
                            passage.points().get(passage.points().size() / 2).position();
                    SkyIslandSubsurfacePosition last = passage.points().getLast().position();
                    double linearX = 0.5 * (first.x() + last.x());
                    double linearZ = 0.5 * (first.z() + last.z());
                    double linearDepth = 0.5 * (first.depthFraction() + last.depthFraction());
                    double normalizedDeviation = Math.sqrt(
                            Math.pow((middle.x() - linearX) / plan.descriptor().nominalRadius(), 2.0)
                                    + Math.pow((middle.z() - linearZ) / plan.descriptor().nominalRadius(), 2.0)
                                    + Math.pow(middle.depthFraction() - linearDepth, 2.0));
                    foundCurved |= normalizedDeviation > 0.005;
                }
            }
        }
        assertTrue(foundCurved);
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 8L, 81L, key));
    }
}
