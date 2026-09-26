package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import org.junit.jupiter.api.Test;

class SkyIslandHydraulicTransitionGeometryEvidencePlannerTest {
    private static final long SEED = 0x534B59464F524745L;
    private static final double EPSILON = 1.0e-9;

    @Test
    void confluence632RetreatsEachIncidentLegByExactlyOneLocalHalfWidth() {
        SkyIslandHydraulicTransitionGeometryEvidencePlan plan =
                SkyIslandHydraulicTransitionGeometryEvidencePlanner.plan(
                        descriptor(8L, 81L, 632L));

        SkyIslandHydraulicConfluenceGeometryCandidate confluence =
                plan.confluences().stream()
                        .filter(candidate ->
                                candidate.transitionSite().nodeCellIndex() == 710)
                        .findFirst()
                        .orElseThrow();

        assertEquals(3, confluence.legs().size());
        for (SkyIslandHydraulicTransitionLegGeometry leg : confluence.legs()) {
            assertEquals(
                    leg.nodeBoundary().bankfullHalfWidth(),
                    leg.retreatLength(),
                    EPSILON);
            assertEquals(
                    leg.retreatLength(),
                    Math.abs(
                            leg.nodeBoundary().arcLength()
                                    - leg.finiteBoundary().arcLength()),
                    EPSILON);
            if (leg.nodeBoundary().role()
                    == SkyIslandHydraulicTransitionBoundaryRole.INCOMING) {
                assertTrue(
                        leg.finiteBoundary().stationFraction()
                                < leg.nodeBoundary().stationFraction());
            } else {
                assertTrue(
                        leg.finiteBoundary().stationFraction()
                                > leg.nodeBoundary().stationFraction());
            }
        }
    }

    @Test
    void primary287CascadeGeometryExactlyCoversAuthoredCascadeIntervals() {
        SkyIslandHydraulicTransitionGeometryEvidencePlan plan =
                SkyIslandHydraulicTransitionGeometryEvidencePlanner.plan(
                        descriptor(8L, 81L, 287L));

        int profiles =
                plan.cascades().stream()
                        .mapToInt(candidate -> candidate.transitionSite().profileCount())
                        .sum();
        assertEquals(10, profiles);
        assertFalse(plan.cascades().isEmpty());

        for (SkyIslandHydraulicCascadeGeometryCandidate cascade : plan.cascades()) {
            assertEquals(
                    cascade.transitionSite().downstreamBoundary().arcLength()
                            - cascade.transitionSite().upstreamBoundary().arcLength(),
                    cascade.pathLength(),
                    EPSILON);
            assertEquals(
                    cascade.transitionSite().upstreamBoundary().position(),
                    cascade.centerlinePoints().getFirst());
            assertEquals(
                    cascade.transitionSite().downstreamBoundary().position(),
                    cascade.centerlinePoints().getLast());
        }
    }

    @Test
    void lake609ExposesContinuousShorelineTargetAndUnresolvedDatumPressure() {
        SkyIslandDescriptor descriptor = descriptor(8L, 81L, 609L);
        SkyIslandHydraulicTransitionGeometryEvidencePlan plan =
                SkyIslandHydraulicTransitionGeometryEvidencePlanner.plan(descriptor);

        SkyIslandHydraulicBasinInterfaceGeometry geometry =
                plan.openWaterInterfaces().stream()
                        .filter(candidate ->
                                candidate.transitionSite()
                                                .terminalFate()
                                                .channelTerminalCellIndex()
                                        == 1397)
                        .findFirst()
                        .orElseThrow();

        assertEquals(SkyIslandWaterbodyKind.LAKE, geometry.basin().sourceCandidate().kind());
        assertEquals(
                geometry.transitionSite().terminalFate().watershedTerminalCellIndex(),
                geometry.basin().sourceCandidate().sinkCellIndex());
        assertTrue(geometry.shorelineGap() >= 0.0);
        assertTrue(geometry.basin().shorelineCrossings().contains(
                geometry.nearestShorelinePoint()));
        assertTrue(geometry.preferredRiverToBasinDatumMismatchWorldUnits() > 0.0);
    }

    @Test
    void geometryEvidenceIsDeterministicAndPreservesUnresolvedTerminalBlockers() {
        SkyIslandDescriptor descriptor = descriptor(6L, 61L, 512L);
        SkyIslandHydraulicTransitionGeometryEvidencePlan first =
                SkyIslandHydraulicTransitionGeometryEvidencePlanner.plan(descriptor);
        SkyIslandHydraulicTransitionGeometryEvidencePlan second =
                SkyIslandHydraulicTransitionGeometryEvidencePlanner.plan(descriptor);

        assertEquals(first, second);
        assertTrue(first.topology().unresolvedTerminals().stream()
                .anyMatch(fate -> fate.channelTerminalCellIndex() == 306));
        assertTrue(first.topology().unresolvedTerminals().stream()
                .anyMatch(fate -> fate.channelTerminalCellIndex() == 497));
    }

    private static SkyIslandDescriptor descriptor(long province, long cluster, long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, province, cluster, key));
    }
}
