package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import org.junit.jupiter.api.Test;

class SkyIslandOrdinarySpanPlannerTest {
    private static final long SEED = 0x534B59464F524745L;

    @Test
    void primary287PartitionsAroundAllInternalCascadeRunsWithoutCascadeSamples() {
        SkyIslandOrdinarySpanPlan plan =
                SkyIslandOrdinarySpanPlanner.plan(descriptor(8L, 81L, 287L));

        var primary = plan.outcomes().stream()
                .filter(outcome ->
                        outcome.span().parentReachStartCellIndex() == 1090
                                && outcome.span().parentReachEndCellIndex() == 1758)
                .toList();

        assertEquals(4, primary.size());
        assertTrue(primary.stream()
                .flatMap(outcome -> outcome.span().sampleProfileKinds().stream())
                .noneMatch(kind -> kind == SkyIslandChannelProfileKind.CASCADE));
        assertTrue(primary.stream()
                .allMatch(outcome ->
                        outcome.status() == SkyIslandOrdinarySpanStatus.INFEASIBLE));

        var quantifiedEnvelopeFailures = primary.stream()
                .filter(outcome ->
                        outcome.diagnostic()
                                .orElse("")
                                .contains("infeasibilityGapWorld="))
                .toList();
        assertFalse(quantifiedEnvelopeFailures.isEmpty());
        for (var outcome : quantifiedEnvelopeFailures) {
            String diagnostic = outcome.diagnostic().orElseThrow();
            assertTrue(diagnostic.contains("lowerHeadWorld="));
            assertTrue(diagnostic.contains("upperHeadWorld="));
            String marker = "infeasibilityGapWorld=";
            String gapText = diagnostic.substring(diagnostic.indexOf(marker) + marker.length());
            gapText = gapText.substring(0, gapText.indexOf(','));
            double originalGap = Double.parseDouble(gapText);
            assertTrue(originalGap > 0.0);

            assertTrue(diagnostic.contains("localCrossSectionProbe=offsetWorld="));
            assertTrue(diagnostic.contains(",scope=local-cross-section-only"));
            String probeMarker = "localCrossSectionProbe=offsetWorld=";
            String probe = diagnostic.substring(diagnostic.indexOf(probeMarker));
            String probeGapMarker = ",gapWorld=";
            String probeGapText =
                    probe.substring(probe.indexOf(probeGapMarker) + probeGapMarker.length());
            probeGapText = probeGapText.substring(0, probeGapText.indexOf(','));
            double probeGap = Double.parseDouble(probeGapText);
            assertTrue(probeGap >= 0.0 && probeGap <= originalGap + 1.0e-10);
        }

        for (int i = 0; i + 1 < primary.size(); i++) {
            assertTrue(
                    primary.get(i).span().parentEndStationFraction()
                            < primary.get(i + 1).span().parentStartStationFraction());
        }
    }

    @Test
    void solvedConfluence632SuppliesFixedFiniteBoundaryHeadsToOrdinarySpans() {
        SkyIslandOrdinarySpanPlan plan =
                SkyIslandOrdinarySpanPlanner.plan(descriptor(8L, 81L, 632L));

        assertTrue(plan.outcomes().stream()
                .filter(outcome ->
                        outcome.span().parentReachStartCellIndex() == 759
                                && outcome.span().parentReachEndCellIndex() == 710)
                .anyMatch(outcome ->
                        outcome.span().downstreamBoundary().status()
                                == SkyIslandOrdinarySpanBoundaryStatus.FIXED_HEAD));
        assertTrue(plan.outcomes().stream()
                .filter(outcome ->
                        outcome.span().parentReachStartCellIndex() == 1088
                                && outcome.span().parentReachEndCellIndex() == 710)
                .anyMatch(outcome ->
                        outcome.span().downstreamBoundary().status()
                                == SkyIslandOrdinarySpanBoundaryStatus.FIXED_HEAD));
        assertTrue(plan.outcomes().stream()
                .noneMatch(outcome ->
                        outcome.status() == SkyIslandOrdinarySpanStatus.NUMERICAL_FAILURE));
    }

    @Test
    void lake609KeepsRetainedOpenWaterTerminalOwnedSpanDeferred() {
        SkyIslandOrdinarySpanPlan plan =
                SkyIslandOrdinarySpanPlanner.plan(descriptor(8L, 81L, 609L));

        assertTrue(plan.outcomes().stream()
                .anyMatch(outcome ->
                        outcome.status() == SkyIslandOrdinarySpanStatus.BOUNDARY_DEFERRED
                                && (outcome.span().downstreamBoundary().diagnostic()
                                                .orElse("")
                                                .contains("RETAINED_OPEN_WATER")
                                        || outcome.span().upstreamBoundary().diagnostic()
                                                .orElse("")
                                                .contains("RETAINED_OPEN_WATER"))));
    }

    @Test
    void sharedMeasurementKernelPreservesWholeReachD1Values() {
        SkyIslandDescriptor descriptor = descriptor(6L, 61L, 118L);
        SkyIslandSemanticField terrain = SkyIslandPreHydrologicTerrainField.create(descriptor);
        SkyIslandHydraulicReachGeometry reach =
                SkyIslandHydraulicChannelNetworkPlanner.plan(descriptor)
                        .reaches()
                        .getFirst();
        double planningSpacing =
                SkyIslandSemanticChannelReachPlanner.plan(descriptor).planningSpacing();

        SkyIslandGeomorphicReachDiagnostics diagnostics =
                SkyIslandGeomorphicReachDiagnosticsPlanner.measureReach(
                        descriptor, reach, terrain, planningSpacing);
        SkyIslandGeomorphicMeasurements direct =
                SkyIslandGeomorphicReachDiagnosticsPlanner.measureGeometry(
                        descriptor,
                        reach.geomorphicRoute().semanticReach(),
                        reach.centerline().points(),
                        reach.samples(),
                        terrain,
                        planningSpacing);

        SkyIslandGeomorphicMeasurements whole =
                SkyIslandGeomorphicMeasurements.from(diagnostics);
        assertEquals(
                whole.maximumCenterlineLoweringPotential(),
                direct.maximumCenterlineLoweringPotential());
        assertEquals(
                whole.maximumCenterlineLoweringWorldUnits(),
                direct.maximumCenterlineLoweringWorldUnits());
        assertEquals(
                whole.maximumLateralRecoveryGrade(),
                direct.maximumLateralRecoveryGrade());
        assertEquals(
                whole.maximumBankContainmentDeficitWorldUnits(),
                direct.maximumBankContainmentDeficitWorldUnits());
        assertEquals(
                whole.maximumDepthToBankfullWidthRatio(),
                direct.maximumDepthToBankfullWidthRatio());
        assertEquals(
                whole.maximumReliefToValleyWidthRatio(),
                direct.maximumReliefToValleyWidthRatio());
        assertEquals(
                whole.normalizedExcavationBurden(),
                direct.normalizedExcavationBurden());
        assertEquals(
                whole.excavationVolumeProxyWorldUnitsCubed(),
                direct.excavationVolumeProxyWorldUnitsCubed());
        assertEquals(
                whole.maximumCurvatureWidthRatio(),
                direct.maximumCurvatureWidthRatio());
        assertEquals(
                whole.maximumLongitudinalGrade(),
                direct.maximumLongitudinalGrade());
        assertEquals(
                SkyIslandRouteFunctionalDiagnosticsPlanner.measure(
                                reach.geomorphicRoute().route(),
                                terrain,
                                planningSpacing)
                        .ridgeLengthFraction(),
                diagnostics.ridgeSampleFraction());
        assertFalse(reach.samples().isEmpty());
    }

    private static SkyIslandDescriptor descriptor(long province, long cluster, long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, province, cluster, key));
    }
}
