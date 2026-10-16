package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SkyIslandGeomorphicChannelNetworkPlannerTest {
    private static final long SEED = 0x534B59464F524745L;
    private static final double EPSILON = 1.0e-10;

    @Test
    void priorityFloodGuidedRoutesStayBoundToSemanticReachesOnAcceptedAndRejectedControls() {
        for (long key : new long[] {77L, 287L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandSemanticChannelReachPlan semantics =
                    SkyIslandSemanticChannelReachPlanner.plan(descriptor);
            SkyIslandSemanticField terrain =
                    SkyIslandPreHydrologicTerrainField.create(descriptor);
            SkyIslandSemanticField interiority =
                    SkyIslandSemanticFieldSet.create(descriptor).interiority();
            SkyIslandGeomorphicChannelNetworkPlan plan =
                    SkyIslandGeomorphicChannelNetworkPlanner.plan(descriptor);

            for (SkyIslandGeomorphicReachRoute routed : plan.routes()) {
                SkyIslandSemanticChannelReach reach = routed.semanticReach();
                double corridorHalfWidth =
                        semantics.planningSpacing()
                                * SkyIslandGeomorphicChannelNetworkPlanner
                                        .ROUTE_CORRIDOR_SPACING_FRACTION;
                SkyIslandGeomorphicRouteAnchor startAnchor =
                        new SkyIslandGeomorphicRouteAnchor(
                                plan.requireNode(reach.startCellIndex()).physicalPosition(), 0.0);
                SkyIslandGeomorphicRouteAnchor endAnchor =
                        new SkyIslandGeomorphicRouteAnchor(
                                plan.requireNode(reach.endCellIndex()).physicalPosition(), 0.0);
                SkyIslandGeomorphicCandidateRoute expected =
                        SkyIslandTerrainAwareRouteSolver.solveAtResolution(
                                terrain,
                                interiority,
                                reach.guidancePoints(),
                                semantics.planningSpacing(),
                                corridorHalfWidth,
                                startAnchor,
                                endAnchor,
                                SkyIslandTerrainAwareRouteSolver
                                        .FINE_DIVISIONS_PER_PLANNING_CELL);
                boolean transitionSensitive =
                        plan.requireNode(reach.startCellIndex()).kind()
                                        == SkyIslandGeomorphicNetworkNodeKind.CONFLUENCE
                                || plan.requireNode(reach.endCellIndex()).kind()
                                        == SkyIslandGeomorphicNetworkNodeKind.CONFLUENCE
                                || reach.profiles().stream().anyMatch(
                                        profile -> profile.kind()
                                                == SkyIslandChannelProfileKind.CASCADE);
                if (!transitionSensitive) {
                    SkyIslandGeomorphicCandidateRoute drainageGuide =
                            SkyIslandTerrainAwareRouteSolver.solvePriorityFloodAtResolution(
                                    terrain,
                                    interiority,
                                    reach.guidancePoints(),
                                    semantics.planningSpacing(),
                                    corridorHalfWidth,
                                    startAnchor,
                                    endAnchor,
                                    SkyIslandTerrainAwareRouteSolver
                                            .FINE_DIVISIONS_PER_PLANNING_CELL);
                    SkyIslandGeomorphicCandidateRoute guided =
                            SkyIslandTerrainAwareRouteSolver.solveAtResolution(
                                    terrain,
                                    interiority,
                                    drainageGuide.points(),
                                    semantics.planningSpacing(),
                                    corridorHalfWidth,
                                    startAnchor,
                                    endAnchor,
                                    SkyIslandTerrainAwareRouteSolver
                                            .FINE_DIVISIONS_PER_PLANNING_CELL);
                    if (SkyIslandGeomorphicChannelNetworkPlanner
                            .priorityFloodCandidateIsNoWorse(guided, expected)) {
                        expected = guided;
                    }
                }
                assertEquals(expected, routed.route());
                assertEquals(reach, routed.semanticReach());
            }
        }
    }

    @Test
    void networkGeometryIsDeterministicAndEveryReachUsesSharedPhysicalAnchors() {
        for (long key : new long[] {77L, 118L, 241L, 287L, 512L, 632L, 649L, 811L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandGeomorphicChannelNetworkPlan first =
                    SkyIslandGeomorphicChannelNetworkPlanner.plan(descriptor);
            SkyIslandGeomorphicChannelNetworkPlan second =
                    SkyIslandGeomorphicChannelNetworkPlanner.plan(descriptor);

            assertEquals(first, second);
            for (SkyIslandGeomorphicReachRoute routed : first.routes()) {
                SkyIslandSemanticChannelReach reach = routed.semanticReach();
                assertEquals(
                        first.requireNode(reach.startCellIndex()).physicalPosition(),
                        routed.route().points().getFirst());
                assertEquals(
                        first.requireNode(reach.endCellIndex()).physicalPosition(),
                        routed.route().points().getLast());
            }
        }
    }

    @Test
    void physicalNetworkNodesStayInsideBoundedSemanticRegions() {
        for (long key : new long[] {287L, 632L, 649L}) {
            SkyIslandGeomorphicChannelNetworkPlan plan =
                    SkyIslandGeomorphicChannelNetworkPlanner.plan(descriptor(key));
            double step =
                    plan.planningSpacing() / SkyIslandTerrainAwareRouteSolver.FINE_DIVISIONS_PER_PLANNING_CELL;

            for (SkyIslandGeomorphicNetworkNode node : plan.nodes()) {
                assertTrue(node.displacement() <= node.searchRadius() + EPSILON);
                assertTrue(Math.abs(node.physicalPosition().x() / step
                                - Math.rint(node.physicalPosition().x() / step))
                        <= EPSILON);
                assertTrue(Math.abs(node.physicalPosition().z() / step
                                - Math.rint(node.physicalPosition().z() / step))
                        <= EPSILON);
            }
        }
    }

    @Test
    void confluenceUsesExactlyOnePhysicalPointAcrossAllIncidentReaches() {
        boolean foundConfluence = false;
        for (long key : new long[] {287L, 632L, 649L, 811L}) {
            SkyIslandGeomorphicChannelNetworkPlan plan =
                    SkyIslandGeomorphicChannelNetworkPlanner.plan(descriptor(key));
            for (SkyIslandGeomorphicNetworkNode node : plan.nodes()) {
                if (node.kind() != SkyIslandGeomorphicNetworkNodeKind.CONFLUENCE) {
                    continue;
                }
                foundConfluence = true;
                int incident = 0;
                for (SkyIslandGeomorphicReachRoute route : plan.routes()) {
                    SkyIslandSemanticChannelReach reach = route.semanticReach();
                    if (reach.startCellIndex() == node.cellIndex()) {
                        incident++;
                        assertEquals(node.physicalPosition(), route.route().points().getFirst());
                    }
                    if (reach.endCellIndex() == node.cellIndex()) {
                        incident++;
                        assertEquals(node.physicalPosition(), route.route().points().getLast());
                    }
                }
                assertTrue(incident >= 3, "a confluence must have at least two incoming and one outgoing reach");
            }
        }
        assertTrue(foundConfluence, "test corpus must exercise at least one semantic confluence");
    }

    @Test
    void everySemanticNodeAppearsOnceInPhysicalNetwork() {
        SkyIslandDescriptor descriptor = descriptor(287L);
        SkyIslandSemanticChannelReachPlan semantics =
                SkyIslandSemanticChannelReachPlanner.plan(descriptor);
        SkyIslandGeomorphicChannelNetworkPlan geometry =
                SkyIslandGeomorphicChannelNetworkPlanner.plan(descriptor);

        Map<Integer, Integer> expected = new HashMap<>();
        for (SkyIslandSemanticChannelReach reach : semantics.reaches()) {
            expected.put(reach.startCellIndex(), 1);
            expected.put(reach.endCellIndex(), 1);
        }
        assertEquals(expected.size(), geometry.nodes().size());
        for (int cellIndex : expected.keySet()) {
            assertEquals(1L, geometry.nodes().stream()
                    .filter(node -> node.cellIndex() == cellIndex)
                    .count());
        }
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 8L, 81L, key));
    }
}
