package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
        SkyIslandDescriptor descriptor = descriptor(6L, 61L, 512L);
        SkyIslandConfluenceCascadeHeadCompatibilityPlan generated =
                SkyIslandConfluenceCascadeHeadCompatibilityPlanner.plan(descriptor);
        assertTrue(generated.outcomes().stream().noneMatch(outcome ->
                outcome.status() == SkyIslandConfluenceCascadeHeadCompatibilityStatus.NUMERICAL_FAILURE));
        assertTrue(generated.outcomes().stream().noneMatch(outcome ->
                outcome.status() == SkyIslandConfluenceCascadeHeadCompatibilityStatus.SOLVED),
                () -> "generated key-512 geometry unexpectedly solved an unsupported overlap: "
                        + diagnostic(generated));

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

    @Test
    void controlledJointOverlapFeedsSpanAndNetworkEvidenceWithoutRelaxingD2() {
        SkyIslandDescriptor descriptor = descriptor(6L, 61L, 512L);
        SkyIslandHydraulicTransitionGeometryEvidencePlan geometry =
                withFiniteBoundaryInsideAuthoredCascade(
                        descriptor,
                        SkyIslandHydraulicTransitionGeometryEvidencePlanner.plan(descriptor));
        SkyIslandSemanticField terrain = SkyIslandPreHydrologicTerrainField.create(descriptor);
        SkyIslandGeomorphicQualificationPolicy policy =
                SkyIslandGeomorphicQualificationPolicy.firstEvidenceBacked();
        SkyIslandWatershedPlan watershed = SkyIslandWatershedPlanner.plan(descriptor);
        SkyIslandConfluenceHeadCompatibilityPlan confluence =
                SkyIslandConfluenceHeadCompatibilityPlanner.plan(
                        descriptor, geometry, terrain, policy);
        SkyIslandCascadeHeadCompatibilityPlan cascade =
                SkyIslandCascadeHeadCompatibilityPlanner.plan(
                        descriptor, geometry, terrain, policy, watershed);
        SkyIslandConfluenceCascadeHeadCompatibilityPlan joint =
                SkyIslandConfluenceCascadeHeadCompatibilityPlanner.plan(
                        descriptor, geometry, terrain, policy, watershed);
        SkyIslandJointTransitionAdmissions admissions =
                SkyIslandJointTransitionAdmissions.from(confluence, cascade, joint);
        SkyIslandConfluenceCascadeHeadCompatibilityOutcome admitted =
                admissions.forNode(1729).orElseThrow();
        assertEquals(
                SkyIslandConfluenceCascadeHeadCompatibilityStatus.SOLVED,
                admitted.status());
        assertTrue(admissions.forCascade(admitted.cascade().transitionSite()).isPresent());

        SkyIslandOrdinarySpanPlan spans = SkyIslandOrdinarySpanPlanner.plan(
                descriptor, confluence, cascade, joint, terrain, policy);
        assertEquals(joint, spans.jointPlan());
        List<SkyIslandOrdinarySpanOutcome> coupledSpans = spans.outcomes().stream()
                .filter(value -> value.span().parentReachStartCellIndex() == 1729
                        && value.span().parentReachEndCellIndex() == 1969)
                .toList();
        assertTrue(!coupledSpans.isEmpty());
        assertTrue(coupledSpans.stream().noneMatch(value ->
                value.status() == SkyIslandOrdinarySpanStatus.BOUNDARY_DEFERRED),
                () -> "joint heads did not close ordinary boundaries: "
                        + coupledSpans.stream().map(SkyIslandOrdinarySpanOutcome::status).toList());

        SkyIslandHydraulicNetworkAssemblyPlan assembly =
                SkyIslandHydraulicNetworkAssemblyPlanner.plan(descriptor, spans);
        SkyIslandHydraulicReachAssembly coupledReach = assembly.reachAssemblies().stream()
                .filter(value -> value.semanticReach().startCellIndex() == 1729
                        && value.semanticReach().endCellIndex() == 1969)
                .findFirst()
                .orElseThrow();
        assertTrue(coupledReach.blockers().stream()
                .noneMatch(value -> value.contains("CASCADE profiles 1..2")
                        || value.contains("confluence 1729 CASCADE_COUPLED")),
                () -> "admitted overlap still deferred: " + coupledReach.blockers());
        assertTrue(assembly.terminalComponents().stream()
                .allMatch(value -> value.status() != SkyIslandHydraulicAssemblyStatus.QUALIFIED
                        || value.blockers().isEmpty()));

        SkyIslandConfluenceCascadeHeadCompatibilityPlan ambiguous =
                new SkyIslandConfluenceCascadeHeadCompatibilityPlan(
                        descriptor, geometry, List.of(admitted, admitted));
        assertTrue(SkyIslandJointTransitionAdmissions.from(
                confluence, cascade, ambiguous).forNode(1729).isEmpty());
    }

    private static SkyIslandHydraulicTransitionGeometryEvidencePlan
            withFiniteBoundaryInsideAuthoredCascade(
                    SkyIslandDescriptor descriptor,
                    SkyIslandHydraulicTransitionGeometryEvidencePlan source) {
        SkyIslandHydraulicConfluenceGeometryCandidate confluence =
                source.confluences().stream()
                        .filter(value -> value.transitionSite().nodeCellIndex() == 1729)
                        .findFirst()
                        .orElseThrow();
        SkyIslandHydraulicTransitionLegGeometry coupledLeg =
                confluence.legs().stream()
                        .filter(leg ->
                                leg.nodeBoundary().role()
                                        == SkyIslandHydraulicTransitionBoundaryRole.OUTGOING
                                        && leg.nodeBoundary().reachStartCellIndex() == 1729
                                        && leg.nodeBoundary().reachEndCellIndex() == 1969)
                        .findFirst()
                        .orElseThrow();
        SkyIslandHydraulicCascadeGeometryCandidate cascade =
                source.cascades().stream()
                        .filter(value ->
                                value.transitionSite().reachStartCellIndex() == 1729
                                        && value.transitionSite().reachEndCellIndex() == 1969
                                        && value.transitionSite().firstProfileIndex() == 1
                                        && value.transitionSite().lastProfileIndexExclusive() == 5)
                        .findFirst()
                        .orElseThrow();
        SkyIslandHydraulicReachSkeleton originalReach =
                source.topology().skeletonPlan().reaches().stream()
                        .filter(value -> {
                            SkyIslandSemanticChannelReach semantic =
                                    value.geomorphicRoute().semanticReach();
                            return semantic.startCellIndex() == 1729
                                    && semantic.endCellIndex() == 1969;
                        })
                        .findFirst()
                        .orElseThrow();

        SkyIslandSemanticChannelReach originalSemantic =
                originalReach.geomorphicRoute().semanticReach();
        int cascadeProfileIndex = cascade.transitionSite().firstProfileIndex();
        int ordinaryRemoteProfileIndex = cascadeProfileIndex + 1;
        assertEquals(
                SkyIslandChannelProfileKind.CASCADE,
                originalSemantic.profiles().get(cascadeProfileIndex).kind());
        assertEquals(
                SkyIslandChannelProfileKind.CASCADE,
                originalSemantic.profiles().get(ordinaryRemoteProfileIndex).kind());

        List<SkyIslandChannelProfile> controlledProfiles =
                new ArrayList<>(originalSemantic.profiles());
        SkyIslandChannelProfile remoteProfile =
                controlledProfiles.get(ordinaryRemoteProfileIndex);
        controlledProfiles.set(
                ordinaryRemoteProfileIndex,
                new SkyIslandChannelProfile(
                        remoteProfile.segment(),
                        SkyIslandChannelProfileKind.ALLUVIAL,
                        remoteProfile.gradientPotential(),
                        remoteProfile.streamPowerPotential(),
                        remoteProfile.bankfullWidthPotential(),
                        remoteProfile.depthPotential(),
                        remoteProfile.incisionPotential()));
        SkyIslandSemanticChannelReach controlledSemantic =
                new SkyIslandSemanticChannelReach(
                        originalSemantic.startCellIndex(),
                        originalSemantic.endCellIndex(),
                        controlledProfiles);
        SkyIslandGeomorphicReachRoute controlledRoute =
                new SkyIslandGeomorphicReachRoute(
                        controlledSemantic, originalReach.geomorphicRoute().route());
        SkyIslandHydraulicReachSkeleton controlledReach =
                new SkyIslandHydraulicReachSkeleton(
                        controlledRoute,
                        originalReach.centerline(),
                        originalReach.samples(),
                        originalReach.pathLength(),
                        originalReach.maximumBankfullHalfWidth(),
                        originalReach.maximumWaterDepthPotential());
        List<SkyIslandHydraulicReachSkeleton> reaches =
                source.topology().skeletonPlan().reaches().stream()
                        .map(value -> value == originalReach ? controlledReach : value)
                        .toList();
        SkyIslandHydraulicGeometrySkeletonPlan skeletonPlan =
                new SkyIslandHydraulicGeometrySkeletonPlan(
                        descriptor,
                        source.topology().skeletonPlan().geomorphicNetwork(),
                        reaches);

        double cascadeStartFraction =
                cascade.transitionSite().upstreamBoundary().stationFraction();
        double cascadeEndFraction =
                cascadeStartFraction
                        + (cascade.transitionSite().downstreamBoundary().stationFraction()
                                        - cascadeStartFraction)
                                / cascade.transitionSite().profileCount();
        double overlapFraction = 0.5 * (cascadeStartFraction + cascadeEndFraction);
        SkyIslandSemanticField terrain = SkyIslandPreHydrologicTerrainField.create(descriptor);
        SkyIslandHydraulicTransitionBoundaryState cascadeUpstream =
                SkyIslandHydraulicTransitionTopologyPlanner.sampleBoundaryState(
                        descriptor,
                        terrain,
                        controlledReach,
                        cascadeStartFraction,
                        SkyIslandHydraulicTransitionBoundaryRole.INCOMING);
        SkyIslandHydraulicTransitionBoundaryState cascadeDownstream =
                SkyIslandHydraulicTransitionTopologyPlanner.sampleBoundaryState(
                        descriptor,
                        terrain,
                        controlledReach,
                        cascadeEndFraction,
                        SkyIslandHydraulicTransitionBoundaryRole.OUTGOING);
        SkyIslandChannelSegment cascadeSegment =
                controlledSemantic.profiles().get(cascadeProfileIndex).segment();
        SkyIslandHydraulicCascadeTransitionSite controlledCascadeSite =
                new SkyIslandHydraulicCascadeTransitionSite(
                        controlledSemantic.startCellIndex(),
                        controlledSemantic.endCellIndex(),
                        cascadeProfileIndex,
                        cascadeProfileIndex + 1,
                        cascadeSegment.sourceCellIndex(),
                        cascadeSegment.downstreamCellIndex(),
                        cascadeUpstream,
                        cascadeDownstream);
        SkyIslandHydraulicCascadeGeometryCandidate controlledCascade =
                new SkyIslandHydraulicCascadeGeometryCandidate(
                        controlledCascadeSite,
                        List.of(cascadeUpstream.position(), cascadeDownstream.position()),
                        cascadeDownstream.arcLength() - cascadeUpstream.arcLength());

        SkyIslandHydraulicTransitionBoundaryState finiteBoundary =
                SkyIslandHydraulicTransitionTopologyPlanner.sampleBoundaryState(
                        descriptor,
                        terrain,
                        controlledReach,
                        overlapFraction,
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
        List<SkyIslandHydraulicCascadeGeometryCandidate> cascades =
                source.cascades().stream()
                        .map(value -> value == cascade ? controlledCascade : value)
                        .toList();
        List<SkyIslandHydraulicCascadeTransitionSite> cascadeSites =
                source.topology().cascades().stream()
                        .map(value -> value.equals(cascade.transitionSite())
                                ? controlledCascadeSite
                                : value)
                        .toList();
        SkyIslandHydraulicTransitionTopologyPlan topology =
                new SkyIslandHydraulicTransitionTopologyPlan(
                        descriptor,
                        skeletonPlan,
                        source.topology().confluences(),
                        cascadeSites,
                        source.topology().basins(),
                        source.topology().unresolvedTerminals());
        return new SkyIslandHydraulicTransitionGeometryEvidencePlan(
                descriptor,
                topology,
                confluences,
                cascades,
                source.openWaterInterfaces(),
                source.deferredWetlandInterfaces());
    }

    private static String diagnostic(SkyIslandConfluenceCascadeHeadCompatibilityPlan plan) {
        return plan.outcomes().stream()
                .map(value -> value.confluence().transitionSite().nodeCellIndex()
                        + ":" + value.status() + ":" + value.diagnostic())
                .toList()
                .toString();
    }

    private static SkyIslandDescriptor descriptor(long province, long cluster, long island) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, province, cluster, island));
    }
}
