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
                .noneMatch(outcome ->
                        outcome.status() == SkyIslandOrdinarySpanStatus.NUMERICAL_FAILURE));
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
    void retained83KeepsAtLeastOneTerminalOwnedOrdinarySpanDeferred() {
        SkyIslandOrdinarySpanPlan plan =
                SkyIslandOrdinarySpanPlanner.plan(descriptor(6L, 61L, 83L));

        assertTrue(plan.outcomes().stream()
                .anyMatch(outcome ->
                        outcome.status() == SkyIslandOrdinarySpanStatus.BOUNDARY_DEFERRED
                                && (outcome.span().downstreamBoundary().diagnostic()
                                                .orElse("")
                                                .contains("RETAINED_")
                                        || outcome.span().upstreamBoundary().diagnostic()
                                                .orElse("")
                                                .contains("RETAINED_"))));
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

        assertEquals(SkyIslandGeomorphicMeasurements.from(diagnostics), direct);
        assertFalse(reach.samples().isEmpty());
    }

    private static SkyIslandDescriptor descriptor(long province, long cluster, long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, province, cluster, key));
    }
}
