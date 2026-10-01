package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class SkyIslandConfluenceCascadeHeadCompatibilityPlannerTest {
    private static final long SEED = 0x534B59464F524745L;
    private static final double EPSILON = 1.0e-8;

    @Test
    void eligibleJointTransitionInExistingConfluenceCorpusIsDeterministicAndBounded() {
        List<SkyIslandDescriptor> candidates = List.of(
                descriptor(8L, 81L, 241L),
                descriptor(8L, 81L, 632L),
                descriptor(8L, 81L, 287L),
                descriptor(8L, 81L, 649L),
                descriptor(6L, 61L, 83L),
                descriptor(6L, 61L, 77L),
                descriptor(6L, 61L, 512L));
        List<String> attempted = new ArrayList<>();
        SkyIslandDescriptor selectedDescriptor = null;
        SkyIslandConfluenceCascadeHeadCompatibilityPlan first = null;

        for (SkyIslandDescriptor candidate : candidates) {
            SkyIslandConfluenceCascadeHeadCompatibilityPlan plan =
                    SkyIslandConfluenceCascadeHeadCompatibilityPlanner.plan(candidate);
            String detail = "island=" + candidate.identity().islandKey() + ": " + diagnostic(plan);
            attempted.add(detail);
            System.out.println("F3H fixture probe " + detail);
            assertTrue(plan.outcomes().stream().noneMatch(outcome ->
                    outcome.status() == SkyIslandConfluenceCascadeHeadCompatibilityStatus.NUMERICAL_FAILURE));
            if (plan.outcomes().stream().anyMatch(outcome ->
                    outcome.status() == SkyIslandConfluenceCascadeHeadCompatibilityStatus.SOLVED)) {
                selectedDescriptor = candidate;
                first = plan;
                break;
            }
        }

        assertTrue(first != null,
                () -> "no existing confluence-corpus fixture exercises the supported F3H solve; "
                        + String.join(" | ", attempted));
        SkyIslandDescriptor descriptor = Objects.requireNonNull(selectedDescriptor);
        first = Objects.requireNonNull(first);
        SkyIslandConfluenceCascadeHeadCompatibilityPlan second =
                SkyIslandConfluenceCascadeHeadCompatibilityPlanner.plan(descriptor);
        System.out.println("F3H selected fixture island=" + descriptor.identity().islandKey()
                + ": " + diagnostic(first));

        assertEquals(first.outcomes().size(), second.outcomes().size());
        assertTrue(first.outcomes().stream().anyMatch(outcome ->
                outcome.status() == SkyIslandConfluenceCascadeHeadCompatibilityStatus.SOLVED));

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
        var confluences = geometry.confluences().stream()
                .map(value -> value.transitionSite().nodeCellIndex() + ":"
                        + value.legs().stream()
                                .map(leg -> leg.nodeBoundary().reachStartCellIndex() + "->"
                                        + leg.nodeBoundary().reachEndCellIndex() + "/"
                                        + leg.nodeBoundary().role())
                                .toList())
                .toList();
        var cascades = geometry.cascades().stream()
                .map(value -> value.transitionSite().reachStartCellIndex() + "->"
                        + value.transitionSite().reachEndCellIndex() + "/profiles="
                        + value.transitionSite().firstProfileIndex() + ".."
                        + value.transitionSite().lastProfileIndexExclusive())
                .toList();
        return "outcomes=" + plan.outcomes().stream()
                .map(value -> value.confluence().transitionSite().nodeCellIndex()
                        + ":" + value.status() + ":" + value.diagnostic())
                .toList()
                + ", confluences=" + confluences
                + ", cascades=" + cascades;
    }

    private static SkyIslandDescriptor descriptor(long province, long cluster, long island) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, province, cluster, island));
    }
}
