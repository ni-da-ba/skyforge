package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import org.junit.jupiter.api.Test;

class SkyIslandConfluenceCascadeHeadCompatibilityPlannerTest {
    private static final long SEED = 0x534B59464F524745L;
    private static final double EPSILON = 1.0e-8;

    @Test
    void confluence632JointTransitionIsDeterministicAndFailClosed() {
        SkyIslandDescriptor descriptor = descriptor(8L, 81L, 632L);
        SkyIslandConfluenceCascadeHeadCompatibilityPlan first =
                SkyIslandConfluenceCascadeHeadCompatibilityPlanner.plan(descriptor);
        SkyIslandConfluenceCascadeHeadCompatibilityPlan second =
                SkyIslandConfluenceCascadeHeadCompatibilityPlanner.plan(descriptor);

        System.out.println("F3H fixture diagnostic: first=" + diagnostic(first)
                + "; second=" + diagnostic(second));

        assertEquals(first.outcomes().size(), second.outcomes().size());
        var confluence710 = first.outcomes().stream()
                .filter(outcome -> outcome.confluence().transitionSite().nodeCellIndex() == 710)
                .toList();
        assertTrue(
                !confluence710.isEmpty(),
                () -> "expected a confluence-710 joint outcome; first="
                        + diagnostic(first) + "; second=" + diagnostic(second));
        assertTrue(
                confluence710.stream().anyMatch(outcome ->
                        outcome.status() == SkyIslandConfluenceCascadeHeadCompatibilityStatus.SOLVED),
                () -> "the eligible confluence-710/CASCADE overlap should solve jointly; observed "
                        + confluence710);
        assertTrue(first.outcomes().stream().noneMatch(outcome ->
                outcome.status() == SkyIslandConfluenceCascadeHeadCompatibilityStatus.NUMERICAL_FAILURE));

        for (int i = 0; i < first.outcomes().size(); i++) {
            SkyIslandConfluenceCascadeHeadCompatibilityOutcome a = first.outcomes().get(i);
            SkyIslandConfluenceCascadeHeadCompatibilityOutcome b = second.outcomes().get(i);
            assertEquals(a.status(), b.status());
            assertEquals(a.sharedNodeHeadWorldUnits(), b.sharedNodeHeadWorldUnits());
            assertEquals(
                    a.cascadeConfluenceSideHeadWorldUnits(),
                    b.cascadeConfluenceSideHeadWorldUnits());
            assertEquals(a.cascadeBoundaryHeadWorldUnits(), b.cascadeBoundaryHeadWorldUnits());
            assertEquals(a.solvedDropWorldUnits(), b.solvedDropWorldUnits());
            assertEquals(a.ordinaryLegSolutions(), b.ordinaryLegSolutions());
            assertEquals(
                    a.solve().map(SkyIslandHydraulicQpResult::primalResidual),
                    b.solve().map(SkyIslandHydraulicQpResult::primalResidual));

            if (a.status() == SkyIslandConfluenceCascadeHeadCompatibilityStatus.SOLVED) {
                double node = a.sharedNodeHeadWorldUnits().orElseThrow();
                double drop = a.solvedDropWorldUnits().orElseThrow();
                assertTrue(node + EPSILON >= a.nodeLowerHeadWorldUnits());
                assertTrue(node <= a.nodeUpperHeadWorldUnits() + EPSILON);
                assertTrue(drop >= -EPSILON);
                assertTrue(drop <= a.authoredMaximumDropWorldUnits() + EPSILON);
                assertTrue(a.solve().orElseThrow().primalResidual() <= 1.0e-7);
                assertEquals(a.confluence().legs().size() - 1, a.ordinaryLegSolutions().size());
            } else {
                assertTrue(a.sharedNodeHeadWorldUnits().isEmpty());
                assertTrue(a.cascadeBoundaryHeadWorldUnits().isEmpty());
                assertTrue(a.solvedDropWorldUnits().isEmpty());
                assertTrue(a.ordinaryLegSolutions().isEmpty());
            }
        }
    }

    private static String diagnostic(SkyIslandConfluenceCascadeHeadCompatibilityPlan plan) {
        var geometry = plan.transitionGeometry();
        var reaches = geometry.topology().skeletonPlan().reaches().stream()
                .map(reach -> reach.geomorphicRoute().semanticReach())
                .filter(reach -> reach.startCellIndex() == 710 || reach.endCellIndex() == 710)
                .map(reach -> reach.startCellIndex() + "->" + reach.endCellIndex()
                        + "/profiles=" + reach.profiles().size())
                .toList();
        var confluenceLegs = geometry.confluences().stream()
                .filter(value -> value.transitionSite().nodeCellIndex() == 710)
                .flatMap(value -> value.legs().stream())
                .map(leg -> leg.nodeBoundary().reachStartCellIndex() + "->"
                        + leg.nodeBoundary().reachEndCellIndex() + "/"
                        + leg.nodeBoundary().role())
                .toList();
        var cascades = geometry.cascades().stream()
                .filter(value -> reaches.stream().anyMatch(reach -> {
                    var site = value.transitionSite();
                    return reach.startsWith(site.reachStartCellIndex() + "->"
                                    + site.reachEndCellIndex() + "/");
                }))
                .map(value -> value.transitionSite().reachStartCellIndex() + "->"
                        + value.transitionSite().reachEndCellIndex() + "/profiles="
                        + value.transitionSite().firstProfileIndex() + ".."
                        + value.transitionSite().lastProfileIndexExclusive())
                .toList();
        var nodeKinds = geometry.topology().skeletonPlan().geomorphicNetwork().nodes().stream()
                .filter(node -> node.cellIndex() == 710)
                .map(SkyIslandGeomorphicNetworkNode::kind)
                .toList();
        return "outcomes=" + plan.outcomes().stream()
                .map(value -> value.confluence().transitionSite().nodeCellIndex()
                        + ":" + value.status() + ":" + value.diagnostic())
                .toList()
                + ", nodeKinds=" + nodeKinds
                + ", incidentReaches=" + reaches
                + ", confluenceLegs=" + confluenceLegs
                + ", cascades=" + cascades;
    }

    private static SkyIslandDescriptor descriptor(long province, long cluster, long island) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, province, cluster, island));
    }
}
