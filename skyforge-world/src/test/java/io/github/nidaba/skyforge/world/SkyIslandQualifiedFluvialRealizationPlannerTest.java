package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkyIslandQualifiedFluvialRealizationPlannerTest {
    private static final long SEED = 0x534B59464F524745L;
    private static final double EPSILON = 1.0e-10;

    @Test
    void rejectedPrimaryReachContributesExactlyZeroTerrainDelta() {
        SkyIslandDescriptor descriptor = descriptor(8L, 81L, 287L);
        SkyIslandQualifiedFluvialRealizationPlan plan =
                SkyIslandQualifiedFluvialRealizationPlanner.plan(descriptor);

        assertTrue(plan.realizedQualifications().isEmpty());
        assertFalse(plan.rejectedQualifications().isEmpty());

        SkyIslandHydraulicReachGeometry rejected =
                plan.rejectedQualifications().getFirst().diagnostics().hydraulicReach();
        SkyIslandLocalPosition point =
                rejected.centerline().points().get(rejected.centerline().points().size() / 2);

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
    void confluenceTransitionsAreDeferredBeforeTerrainAuthority() {
        SkyIslandQualifiedFluvialRealizationPlan plan =
                SkyIslandQualifiedFluvialRealizationPlanner.plan(
                        descriptor(8L, 81L, 632L),
                        permissivePolicy());

        assertTrue(plan.deferredQualifications().stream()
                .anyMatch(deferral -> deferral.reasons().contains(
                        SkyIslandQualifiedFluvialDeferralReason.CONFLUENCE_TRANSITION_REQUIRED)));
        assertDeferredReachesHaveZeroTerrainAuthority(plan);
    }

    @Test
    void cascadeTransitionsAreDeferredBeforeTerrainAuthority() {
        SkyIslandQualifiedFluvialRealizationPlan plan =
                SkyIslandQualifiedFluvialRealizationPlanner.plan(
                        descriptor(6L, 61L, 512L),
                        permissivePolicy());

        assertTrue(plan.deferredQualifications().stream()
                .anyMatch(deferral -> deferral.reasons().contains(
                        SkyIslandQualifiedFluvialDeferralReason.CASCADE_TRANSITION_REQUIRED)));
        assertDeferredReachesHaveZeroTerrainAuthority(plan);
    }

    @Test
    void retainedOpenWaterTerminalIsDeferredBeforeTerrainAuthority() {
        SkyIslandQualifiedFluvialRealizationPlan plan =
                SkyIslandQualifiedFluvialRealizationPlanner.plan(
                        descriptor(6L, 61L, 83L),
                        permissivePolicy());

        assertTrue(plan.deferredQualifications().stream()
                .anyMatch(deferral -> deferral.reasons().contains(
                        SkyIslandQualifiedFluvialDeferralReason
                                .RETAINED_WATER_TRANSITION_REQUIRED)));
        assertDeferredReachesHaveZeroTerrainAuthority(plan);
    }

    @Test
    void terrainFieldPrimitiveProducesBoundedDownwardCrossSectionWithProvenance() {
        SkyIslandDescriptor descriptor = descriptor(6L, 61L, 512L);
        SkyIslandPreHydrologicTerrainField original =
                SkyIslandPreHydrologicTerrainField.create(descriptor);
        SkyIslandHydraulicChannelNetworkPlan hydraulic =
                SkyIslandHydraulicChannelNetworkPlanner.plan(descriptor);
        List<SkyIslandGeomorphicReachDiagnostics> diagnostics =
                SkyIslandGeomorphicReachDiagnosticsPlanner.measure(
                        descriptor, hydraulic, original);
        SkyIslandGeomorphicQualificationPolicy policy =
                SkyIslandGeomorphicQualificationPolicy.firstEvidenceBacked();

        SkyIslandHydraulicReachGeometry accepted =
                diagnostics.stream()
                        .filter(diagnostic ->
                                SkyIslandGeomorphicQualificationEvaluator
                                        .evaluate(diagnostic, policy)
                                        .accepted())
                        .map(SkyIslandGeomorphicReachDiagnostics::hydraulicReach)
                        .findFirst()
                        .orElseThrow();

        SkyIslandQualifiedFluvialTerrainField field =
                new SkyIslandQualifiedFluvialTerrainField(
                        original, List.of(accepted));
        SkyIslandLocalPosition point =
                accepted.centerline().points().get(accepted.centerline().points().size() / 2);
        SkyIslandQualifiedFluvialSample sample = field.sampleDetailed(point);

        assertTrue(sample.targetTerrainPotential() <= sample.originalTerrainPotential() + EPSILON);
        assertTrue(sample.terrainDeltaPotential() <= EPSILON);
        assertTrue(sample.zone() != SkyIslandQualifiedFluvialZone.UNAFFECTED);
        assertTrue(sample.provenance().isPresent());
        assertTrue(sample.waterSurfacePotential() >= sample.targetTerrainPotential() - EPSILON);
    }

    @Test
    void independentPlansProduceIdenticalClassificationAndSamples() {
        SkyIslandDescriptor descriptor = descriptor(6L, 61L, 512L);
        SkyIslandQualifiedFluvialRealizationPlan first =
                SkyIslandQualifiedFluvialRealizationPlanner.plan(descriptor);
        SkyIslandQualifiedFluvialRealizationPlan second =
                SkyIslandQualifiedFluvialRealizationPlanner.plan(descriptor);

        assertEquals(first.realizedQualifications(), second.realizedQualifications());
        assertEquals(first.deferredQualifications(), second.deferredQualifications());
        assertEquals(first.rejectedQualifications(), second.rejectedQualifications());

        List<SkyIslandLocalPosition> probes = List.of(
                new SkyIslandLocalPosition(0.0, 0.0),
                new SkyIslandLocalPosition(12.5, -7.5),
                new SkyIslandLocalPosition(-25.0, 18.0));
        for (SkyIslandLocalPosition probe : probes) {
            assertEquals(
                    first.terrainField().sampleDetailed(probe),
                    second.terrainField().sampleDetailed(probe));
        }
    }

    private static void assertDeferredReachesHaveZeroTerrainAuthority(
            SkyIslandQualifiedFluvialRealizationPlan plan) {
        assertFalse(plan.deferredQualifications().isEmpty());
        for (SkyIslandQualifiedFluvialDeferral deferral : plan.deferredQualifications()) {
            SkyIslandHydraulicReachGeometry deferred =
                    deferral.qualification().diagnostics().hydraulicReach();
            assertFalse(containsReach(plan.terrainField().acceptedReaches(), deferred));
        }
    }

    private static SkyIslandGeomorphicQualificationPolicy permissivePolicy() {
        SkyIslandGeomorphicProfileLimits limits =
                new SkyIslandGeomorphicProfileLimits(
                        1.0,
                        1.0e6,
                        1.0e6,
                        1.0e6,
                        1.0e6,
                        1.0e6,
                        1.0e6,
                        1.0,
                        1.0e6);
        return new SkyIslandGeomorphicQualificationPolicy(
                limits, limits, limits, limits);
    }

    private static boolean containsReach(
            List<SkyIslandHydraulicReachGeometry> reaches,
            SkyIslandHydraulicReachGeometry candidate) {
        SkyIslandSemanticChannelReach expected =
                candidate.geomorphicRoute().semanticReach();
        return reaches.stream().anyMatch(reach -> {
            SkyIslandSemanticChannelReach actual =
                    reach.geomorphicRoute().semanticReach();
            return actual.startCellIndex() == expected.startCellIndex()
                    && actual.endCellIndex() == expected.endCellIndex();
        });
    }

    private static SkyIslandDescriptor descriptor(long province, long cluster, long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, province, cluster, key));
    }
}
