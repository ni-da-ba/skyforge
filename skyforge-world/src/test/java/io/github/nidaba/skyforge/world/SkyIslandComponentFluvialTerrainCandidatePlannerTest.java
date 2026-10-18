package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SkyIslandComponentFluvialTerrainCandidatePlannerTest {
    private static final long SEED = 0x534B59464F524745L;
    private static final double EPSILON = 1.0e-10;

    @Test
    void ordinary77RealizesExactlyTheTwoQualifiedEdgeOutletComponents() {
        SkyIslandComponentFluvialTerrainCandidatePlan plan =
                SkyIslandComponentFluvialTerrainCandidatePlanner.plan(
                        descriptor(8L, 81L, 77L));

        assertEquals(
                List.of(559, 1842),
                plan.realizedComponents().stream()
                        .map(component ->
                                component.terminalFate().channelTerminalCellIndex())
                        .sorted()
                        .toList());
        assertTrue(plan.deferredQualifiedComponents().isEmpty());

        assertEquals(
                List.of("709->559", "1742->1842"),
                plan.terrainField().acceptedReaches().stream()
                        .map(reach -> {
                            var semantic = reach.geomorphicRoute().semanticReach();
                            return semantic.startCellIndex() + "->" + semantic.endCellIndex();
                        })
                        .toList());
        assertEquals(2, plan.postRealizationQualifications().size());
        assertTrue(plan.postRealizationQualifications().stream()
                .allMatch(SkyIslandGeomorphicReachQualification::accepted));

        for (SkyIslandHydraulicReachGeometry reach :
                plan.terrainField().acceptedReaches()) {
            SkyIslandLocalPosition point =
                    reach.centerline().points().get(
                            reach.centerline().points().size() / 2);
            SkyIslandQualifiedFluvialSample sample =
                    plan.terrainField().sampleDetailed(point);
            assertTrue(sample.terrainDeltaPotential() <= EPSILON);
            assertTrue(sample.provenance().isPresent());
            SkyIslandSemanticChannelReach semantic =
                    reach.geomorphicRoute().semanticReach();
            assertEquals(
                    semantic.startCellIndex(),
                    sample.provenance().orElseThrow().startCellIndex());
            assertEquals(
                    semantic.endCellIndex(),
                    sample.provenance().orElseThrow().endCellIndex());
        }
    }

    @Test
    void accepted77TerrainFieldSamplesTheSameCoupledCandidateThatD2Qualifies() {
        SkyIslandDescriptor descriptor = descriptor(8L, 81L, 77L);
        SkyIslandComponentFluvialTerrainCandidatePlan plan =
                SkyIslandComponentFluvialTerrainCandidatePlanner.plan(descriptor);
        SkyIslandHydraulicGeometrySkeletonPlan skeletonPlan =
                plan.assemblyPlan()
                        .ordinarySpanPlan()
                        .cascadePlan()
                        .transitionGeometry()
                        .topology()
                        .skeletonPlan();
        SkyIslandPreHydrologicTerrainField original =
                SkyIslandPreHydrologicTerrainField.create(descriptor);

        for (SkyIslandHydraulicReachGeometry realized : plan.terrainField().acceptedReaches()) {
            SkyIslandSemanticChannelReach semantic = realized.geomorphicRoute().semanticReach();
            SkyIslandHydraulicReachSkeleton skeleton = skeletonPlan.reaches().stream()
                    .filter(candidate -> candidate.geomorphicRoute().semanticReach().equals(semantic))
                    .findFirst()
                    .orElseThrow();
            SkyIslandGameScaleHydraulicModel.SourceReachSolution solution =
                    SkyIslandGameScaleHydraulicModel.solveSourceReach(descriptor, skeleton, original);
            int middle = realized.centerline().points().size() / 2;
            SkyIslandLocalPosition point = realized.centerline().points().get(middle);
            assertEquals(
                    solution.candidate().sampleTerrainMetres(point) / descriptor.reliefBudget(),
                    plan.terrainField().sample(point),
                    EPSILON);
        }
        assertTrue(plan.postRealizationQualifications().stream()
                .allMatch(SkyIslandGeomorphicReachQualification::accepted));
    }

    @Test
    void qualifiedFieldUsesTheSharedNetworkTerrainSamplerWithoutChangingItsSurface() {
        SkyIslandDescriptor descriptor = descriptor(8L, 81L, 77L);
        SkyIslandComponentFluvialTerrainCandidatePlan plan =
                SkyIslandComponentFluvialTerrainCandidatePlanner.plan(descriptor);
        SkyIslandHydraulicGeometrySkeletonPlan skeletonPlan =
                plan.assemblyPlan()
                        .ordinarySpanPlan()
                        .cascadePlan()
                        .transitionGeometry()
                        .topology()
                        .skeletonPlan();
        SkyIslandSemanticField original = SkyIslandPreHydrologicTerrainField.create(descriptor);
        Map<Long, SkyIslandSemanticField> surfaces = new LinkedHashMap<>();
        Map<Long, SkyIslandGameScaleHydraulicModel.SourceReachSolution> candidates = new LinkedHashMap<>();
        int candidateIndex = 0;
        for (SkyIslandHydraulicReachGeometry reach : plan.terrainField().acceptedReaches()) {
            SkyIslandSemanticChannelReach semantic = reach.geomorphicRoute().semanticReach();
            SkyIslandHydraulicReachSkeleton skeleton = skeletonPlan.reaches().stream()
                    .filter(value -> value.geomorphicRoute().semanticReach().equals(semantic))
                    .findFirst()
                    .orElseThrow();
            var solution = SkyIslandGameScaleHydraulicModel.solveSourceReach(
                    descriptor, skeleton, original);
            long identity = ((long) semantic.startCellIndex() << 32)
                    ^ Integer.toUnsignedLong(semantic.endCellIndex());
            if (candidateIndex++ % 2 == 0) {
                surfaces.put(identity, position ->
                        solution.candidate().sampleTerrainMetres(position) / descriptor.reliefBudget());
            } else {
                candidates.put(identity, solution);
            }
        }
        assertEquals(1, surfaces.size());
        assertEquals(1, candidates.size());
        SkyIslandQualifiedFluvialTerrainField field =
                SkyIslandQualifiedFluvialTerrainField.withCoupledTerrainSurfaces(
                        original,
                        plan.terrainField().acceptedReaches(),
                        candidates,
                        surfaces,
                        descriptor.reliefBudget());

        for (SkyIslandHydraulicReachGeometry reach : plan.terrainField().acceptedReaches()) {
            int middle = reach.centerline().points().size() / 2;
            SkyIslandLocalPosition point = reach.centerline().points().get(middle);
            SkyIslandSemanticChannelReach semantic = reach.geomorphicRoute().semanticReach();
            SkyIslandHydraulicReachSkeleton skeleton = skeletonPlan.reaches().stream()
                    .filter(value -> value.geomorphicRoute().semanticReach().equals(semantic))
                    .findFirst()
                    .orElseThrow();
            var solution = SkyIslandGameScaleHydraulicModel.solveSourceReach(
                    descriptor, skeleton, original);
            assertEquals(
                    solution.candidate().sampleTerrainMetres(point) / descriptor.reliefBudget(),
                    field.sample(point),
                    EPSILON);
        }
    }

    @Test
    void primary287ContributesExactlyZeroCandidateTerrain() {
        SkyIslandDescriptor descriptor = descriptor(8L, 81L, 287L);
        SkyIslandComponentFluvialTerrainCandidatePlan plan =
                SkyIslandComponentFluvialTerrainCandidatePlanner.plan(descriptor);

        assertTrue(plan.realizedComponents().isEmpty());
        assertTrue(plan.terrainField().acceptedReaches().isEmpty());
        assertTrue(plan.postRealizationQualifications().isEmpty());

        SkyIslandHydraulicReachSkeleton skeleton =
                SkyIslandHydraulicGeometrySkeletonPlanner.plan(descriptor)
                        .reaches()
                        .getFirst();
        SkyIslandLocalPosition point =
                skeleton.centerline().points().get(
                        skeleton.centerline().points().size() / 2);
        SkyIslandPreHydrologicTerrainField original =
                SkyIslandPreHydrologicTerrainField.create(descriptor);
        SkyIslandQualifiedFluvialSample sample =
                plan.terrainField().sampleDetailed(point);

        assertEquals(original.sample(point), sample.targetTerrainPotential(), EPSILON);
        assertEquals(0.0, sample.terrainDeltaPotential(), EPSILON);
        assertEquals(SkyIslandQualifiedFluvialZone.UNAFFECTED, sample.zone());
        assertTrue(sample.provenance().isEmpty());
    }

    @Test
    void confluence632CascadeBoundaryRemainsFailClosed() {
        SkyIslandDescriptor descriptor = descriptor(8L, 81L, 632L);
        SkyIslandHydraulicNetworkAssemblyPlan assembly =
                SkyIslandHydraulicNetworkAssemblyPlanner.plan(descriptor);

        assertTrue(assembly.terminalComponents().stream()
                .flatMap(component -> component.reaches().stream())
                .anyMatch(reach -> !reach.cascades().isEmpty()),
                "key 632 must remain a CASCADE boundary control");
        assertTrue(assembly.terminalComponents().stream()
                .noneMatch(SkyIslandComponentFluvialTerrainCandidatePlan::isCandidateEligible));

        SkyIslandComponentFluvialTerrainCandidatePlan candidate =
                SkyIslandComponentFluvialTerrainCandidatePlanner.plan(descriptor);
        assertTrue(candidate.realizedComponents().isEmpty());
        assertTrue(candidate.terrainField().acceptedReaches().isEmpty());
        assertTrue(candidate.deferredQualifiedComponents().isEmpty());
    }

    @Test
    void cascadeBearingComponentIsNeverEligibleEvenIfMarkedQualified() {
        SkyIslandHydraulicNetworkAssemblyPlan cascadeAssembly =
                SkyIslandHydraulicNetworkAssemblyPlanner.plan(descriptor(8L, 81L, 632L));
        SkyIslandHydraulicTerminalComponent cascadeComponent = cascadeAssembly.terminalComponents().stream()
                .filter(component -> component.reaches().stream()
                        .anyMatch(reach -> !reach.cascades().isEmpty()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("key 632 must contain a cascade control"));

        SkyIslandHydraulicNetworkAssemblyPlan qualifiedAssembly =
                SkyIslandHydraulicNetworkAssemblyPlanner.plan(descriptor(8L, 81L, 77L));
        SkyIslandHydraulicTerminalComponent qualifiedComponent = qualifiedAssembly.terminalComponents().stream()
                .filter(component -> component.status() == SkyIslandHydraulicAssemblyStatus.QUALIFIED)
                .findFirst()
                .orElseThrow(() -> new AssertionError("key 77 must provide a complete head solve"));
        SkyIslandHydraulicTerminalComponent adversarial = new SkyIslandHydraulicTerminalComponent(
                cascadeComponent.terminalFate(),
                cascadeComponent.reaches(),
                SkyIslandHydraulicAssemblyStatus.QUALIFIED,
                List.of(),
                qualifiedComponent.sharedHeadSolve());

        assertFalse(
                SkyIslandComponentFluvialTerrainCandidatePlan.isCandidateEligible(adversarial),
                "qualified status must not bypass F4's cascade-free boundary");
    }

    @Test
    void postRealizationD2DiagnosticReportsMeasuredAndAllowedLongitudinalGrade()
            throws Exception {
        SkyIslandComponentFluvialTerrainCandidatePlan candidate =
                SkyIslandComponentFluvialTerrainCandidatePlanner.plan(descriptor(8L, 81L, 28L));
        String diagnostic = candidate.deferredQualifiedComponents().stream()
                .filter(deferral -> deferral.reasons().contains(
                        SkyIslandComponentFluvialTerrainDeferralReason.POST_REALIZATION_D2_REJECTED))
                .map(SkyIslandComponentFluvialTerrainDeferral::diagnostic)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "key 28 must retain its post-realization D2 rejection control"));

        assertTrue(diagnostic.contains("maximum longitudinal grade="));
        assertTrue(diagnostic.contains("maximum longitudinal grade limit="));
        Path report = Path.of(
                "build", "evidence", "hydrology-join-diagnostics", "f4-network-d2-grade.txt");
        Files.createDirectories(report.getParent());
        Files.writeString(report, "key=28 seed=0x" + String.format("%016X", SEED)
                + System.lineSeparator() + diagnostic + System.lineSeparator());
    }

    @Test
    void key198CascadeFreeMultiReachUsesTheSameAssemblyForEligibilityAndRealization() {
        SkyIslandDescriptor descriptor = descriptor(8L, 81L, 198L);
        SkyIslandHydraulicNetworkAssemblyPlan assembly =
                SkyIslandHydraulicNetworkAssemblyPlanner.plan(descriptor);
        SkyIslandHydraulicTerminalComponent eligibleNetwork =
                assembly.terminalComponents().stream()
                        .filter(component -> component.reaches().size() > 1
                                && component.reaches().stream()
                                        .allMatch(reach -> reach.cascades().isEmpty())
                                && SkyIslandComponentFluvialTerrainCandidatePlan
                                        .isCandidateEligible(component))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError(
                                "key 198 must provide an eligible cascade-free multireach"));

        SkyIslandComponentFluvialTerrainCandidatePlan candidate =
                SkyIslandComponentFluvialTerrainCandidatePlanner.plan(
                        descriptor,
                        assembly,
                        SkyIslandGeomorphicQualificationPolicy.firstEvidenceBacked());

        assertTrue(candidate.assemblyPlan() == assembly,
                "candidate realization must use the exact assembly that was screened");
        boolean realized = candidate.realizedComponents().stream()
                .anyMatch(component ->
                        component.terminalFate().channelTerminalCellIndex()
                                == eligibleNetwork.terminalFate().channelTerminalCellIndex()
                                && component.reaches().size() > 1
                                && component.reaches().stream()
                                        .allMatch(reach -> reach.cascades().isEmpty())
                                && component.networkSolution().isPresent());
        assertTrue(realized,
                "key 198 should realize its eligible cascade-free network; deferrals="
                        + candidate.deferredQualifiedComponents().stream()
                                .map(deferral -> deferral.diagnostic())
                                .toList());
    }

    @Test
    void lake609ReceivesNoCandidateTerrainAuthority() {
        SkyIslandComponentFluvialTerrainCandidatePlan plan =
                SkyIslandComponentFluvialTerrainCandidatePlanner.plan(
                        descriptor(8L, 81L, 609L));

        assertTrue(plan.realizedComponents().isEmpty());
        assertTrue(plan.terrainField().acceptedReaches().isEmpty());
        assertTrue(plan.postRealizationQualifications().isEmpty());
        assertFalse(plan.assemblyPlan().terminalComponents().stream()
                .anyMatch(component ->
                        component.status() == SkyIslandHydraulicAssemblyStatus.QUALIFIED));
    }

    @Test
    void independentCandidatePlansProduceIdenticalSelectionAndSamples() {
        SkyIslandDescriptor descriptor = descriptor(8L, 81L, 77L);
        SkyIslandComponentFluvialTerrainCandidatePlan first =
                SkyIslandComponentFluvialTerrainCandidatePlanner.plan(descriptor);
        SkyIslandComponentFluvialTerrainCandidatePlan second =
                SkyIslandComponentFluvialTerrainCandidatePlanner.plan(descriptor);

        assertEquals(
                first.realizedComponents().stream()
                        .map(component ->
                                component.terminalFate().channelTerminalCellIndex())
                        .toList(),
                second.realizedComponents().stream()
                        .map(component ->
                                component.terminalFate().channelTerminalCellIndex())
                        .toList());
        assertEquals(
                first.postRealizationQualifications(),
                second.postRealizationQualifications());

        for (SkyIslandHydraulicReachGeometry reach :
                first.terrainField().acceptedReaches()) {
            SkyIslandLocalPosition probe =
                    reach.centerline().points().get(
                            reach.centerline().points().size() / 2);
            assertEquals(
                    first.terrainField().sampleDetailed(probe),
                    second.terrainField().sampleDetailed(probe));
        }
    }

    private static SkyIslandDescriptor descriptor(long province, long cluster, long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, province, cluster, key));
    }
}
