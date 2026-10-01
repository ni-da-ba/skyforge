package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkyIslandConfluenceCascadeHeadCompatibilityPlannerTest {
    private static final long SEED = 0x534B59464F524745L;
    private static final double EPSILON = 1.0e-8;

    @Test
    void eligibleJointTransitionIsDeterministicAndBounded() {
        SkyIslandDescriptor descriptor = descriptor(8L, 81L, 241L);
        SkyIslandConfluenceCascadeHeadCompatibilityPlan generated =
                SkyIslandConfluenceCascadeHeadCompatibilityPlanner.plan(descriptor);
        assertTrue(generated.outcomes().stream().noneMatch(outcome ->
                outcome.status() == SkyIslandConfluenceCascadeHeadCompatibilityStatus.NUMERICAL_FAILURE));

        SkyIslandHydraulicTransitionGeometryEvidencePlan geometry =
                withFiniteBoundaryInsideAuthoredCascade(descriptor, generated.transitionGeometry());
        SkyIslandSemanticField terrain = SkyIslandPreHydrologicTerrainField.create(descriptor);
        SkyIslandGeomorphicQualificationPolicy policy =
                SkyIslandGeomorphicQualificationPolicy.firstEvidenceBacked();
        SkyIslandWatershedPlan watershed = SkyIslandWatershedPlanner.plan(descriptor);

        SkyIslandConfluenceCascadeHeadCompatibilityPlan first =
                SkyIslandConfluenceCascadeHeadCompatibilityPlanner.plan(
                        descriptor, geometry, terrain, policy, watershed);
        SkyIslandConfluenceCascadeHeadCompatibilityPlan second =
                SkyIslandConfluenceCascadeHeadCompatibilityPlanner.plan(
                        descriptor, geometry, terrain, policy, watershed);
        System.out.println("F3H controlled overlap: " + diagnostic(first));

        assertEquals(first.outcomes().size(), second.outcomes().size());
        assertTrue(first.outcomes().stream().anyMatch(outcome ->
                outcome.status() == SkyIslandConfluenceCascadeHeadCompatibilityStatus.SOLVED),
                () -> "controlled F3H overlap did not solve: " + diagnostic(first));

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
                assertTrue(a.cascadeConfluenceSideHeadWorldUnits().isEmpty());
                assertTrue(a.cascadeBoundaryHeadWorldUnits().isEmpty());
                assertTrue(a.solvedDropWorldUnits().isEmpty());
                assertTrue(a.ordinaryLegSolutions().isEmpty());
            }
        }
    }

    private static SkyIslandHydraulicTransitionGeometryEvidencePlan
            withFiniteBoundaryInsideAuthoredCascade(
                    SkyIslandDescriptor descriptor,
                    SkyIslandHydraulicTransitionGeometryEvidencePlan source) {
        SkyIslandHydraulicConfluenceGeometryCandidate confluence =
                source.confluences().stream()
                        .filter(value -> value.transitionSite().nodeCellIndex() == 671)
                        .findFirst()
                        .orElseThrow();
        SkyIslandHydraulicTransitionLegGeometry coupledLeg =
                confluence.legs().stream()
                        .filter(leg ->
                                leg.nodeBoundary().role()
                                        == SkyIslandHydraulicTransitionBoundaryRole.OUTGOING
                                        && leg.nodeBoundary().reachStartCellIndex() == 671
                                        && leg.nodeBoundary().reachEndCellIndex() == 479)
                        .findFirst()
                        .orElseThrow();
        SkyIslandHydraulicCascadeGeometryCandidate cascade =
                source.cascades().stream()
                        .filter(value ->
                                value.transitionSite().reachStartCellIndex() == 671
                                        && value.transitionSite().reachEndCellIndex() == 479
                                        && value.transitionSite().firstProfileIndex() == 1
                                        && value.transitionSite().lastProfileIndexExclusive() == 4)
                        .findFirst()
                        .orElseThrow();
        SkyIslandHydraulicReachSkeleton reach =
                source.topology().skeletonPlan().reaches().stream()
                        .filter(value -> {
                            SkyIslandSemanticChannelReach semantic =
                                    value.geomorphicRoute().semanticReach();
                            return semantic.startCellIndex() == 671
                                    && semantic.endCellIndex() == 479;
                        })
                        .findFirst()
                        .orElseThrow();

        double finiteBoundaryFraction =
                0.5 * (cascade.transitionSite().upstreamBoundary().stationFraction()
                        + cascade.transitionSite().downstreamBoundary().stationFraction());
        SkyIslandHydraulicTransitionBoundaryState finiteBoundary =
                SkyIslandHydraulicTransitionTopologyPlanner.sampleBoundaryState(
                        descriptor,
                        SkyIslandPreHydrologicTerrainField.create(descriptor),
                        reach,
                        finiteBoundaryFraction,
                        SkyIslandHydraulicTransitionBoundaryRole.OUTGOING);
        SkyIslandHydraulicTransitionLegGeometry coupledLegWithOverlap =
                new SkyIslandHydraulicTransitionLegGeometry(
                        coupledLeg.nodeBoundary(),
                        finiteBoundary,
                        finiteBoundary.arcLength() - coupledLeg.nodeBoundary().arcLength());

        List<SkyIslandHydraulicTransitionLegGeometry> legs = new ArrayList<>();
        for (SkyIslandHydraulicTransitionLegGeometry leg : confluence.legs()) {
            legs.add(leg == coupledLeg ? coupledLegWithOverlap : leg);
        }
        double maximumRetreat = legs.stream()
                .mapToDouble(SkyIslandHydraulicTransitionLegGeometry::retreatLength)
                .max()
                .orElseThrow();
        SkyIslandHydraulicConfluenceGeometryCandidate controlledConfluence =
                new SkyIslandHydraulicConfluenceGeometryCandidate(
                        confluence.transitionSite(), legs, maximumRetreat);
        List<SkyIslandHydraulicConfluenceGeometryCandidate> confluences =
                source.confluences().stream()
                        .map(value -> value == confluence ? controlledConfluence : value)
                        .toList();
        return new SkyIslandHydraulicTransitionGeometryEvidencePlan(
                descriptor,
                source.topology(),
                confluences,
                source.cascades(),
                source.openWaterInterfaces(),
                source.deferredWetlandInterfaces());
    }

    private static String diagnostic(SkyIslandConfluenceCascadeHeadCompatibilityPlan plan) {
        return plan.outcomes().stream()
                .map(value -> value.confluence().transitionSite().nodeCellIndex()
                        + ":" + value.status() + ":" + value.diagnostic())
                .toList();
    }

    private static SkyIslandDescriptor descriptor(long province, long cluster, long island) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, province, cluster, island));
    }
}
