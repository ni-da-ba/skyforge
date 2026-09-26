package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import org.junit.jupiter.api.Test;

class SkyIslandHydraulicTransitionTopologyPlannerTest {
    private static final long SEED = 0x534B59464F524745L;
    private static final double EPSILON = 1.0e-10;

    @Test
    void confluence632OwnsOneSharedThreeBoundaryTransition() {
        SkyIslandHydraulicTransitionTopologyPlan plan =
                SkyIslandHydraulicTransitionTopologyPlanner.plan(
                        descriptor(8L, 81L, 632L));

        SkyIslandHydraulicConfluenceTransitionSite site =
                plan.confluences().stream()
                        .filter(candidate -> candidate.nodeCellIndex() == 710)
                        .findFirst()
                        .orElseThrow();

        assertEquals(3, site.boundaries().size());
        assertEquals(
                2,
                site.boundaries().stream()
                        .filter(boundary ->
                                boundary.role()
                                        == SkyIslandHydraulicTransitionBoundaryRole.INCOMING)
                        .count());
        assertEquals(
                1,
                site.boundaries().stream()
                        .filter(boundary ->
                                boundary.role()
                                        == SkyIslandHydraulicTransitionBoundaryRole.OUTGOING)
                        .count());
        for (SkyIslandHydraulicTransitionBoundaryState boundary : site.boundaries()) {
            assertEquals(site.nodePosition().x(), boundary.position().x(), EPSILON);
            assertEquals(site.nodePosition().z(), boundary.position().z(), EPSILON);
        }
    }

    @Test
    void primary287CascadeSitesPartitionEveryAuthoredCascadeProfileExactlyOnce() {
        SkyIslandDescriptor descriptor = descriptor(8L, 81L, 287L);
        SkyIslandHydraulicTransitionTopologyPlan plan =
                SkyIslandHydraulicTransitionTopologyPlanner.plan(descriptor);

        long expectedCascadeProfiles = plan.skeletonPlan().reaches().stream()
                .flatMap(reach ->
                        reach.geomorphicRoute().semanticReach().profiles().stream())
                .filter(profile -> profile.kind() == SkyIslandChannelProfileKind.CASCADE)
                .count();
        int ownedCascadeProfiles =
                plan.cascades().stream()
                        .mapToInt(SkyIslandHydraulicCascadeTransitionSite::profileCount)
                        .sum();

        assertEquals(10, expectedCascadeProfiles);
        assertEquals(expectedCascadeProfiles, ownedCascadeProfiles);
        assertFalse(plan.cascades().isEmpty());
        for (SkyIslandHydraulicCascadeTransitionSite site : plan.cascades()) {
            assertTrue(site.upstreamBoundary().stationFraction()
                    < site.downstreamBoundary().stationFraction());
            assertTrue(site.upstreamBoundary().arcLength()
                    < site.downstreamBoundary().arcLength());
        }
    }

    @Test
    void lake609RetainedTerminalBecomesExplicitBasinTransitionSite() {
        SkyIslandHydraulicTransitionTopologyPlan plan =
                SkyIslandHydraulicTransitionTopologyPlanner.plan(
                        descriptor(8L, 81L, 609L));

        SkyIslandHydraulicBasinTransitionSite site =
                plan.basins().stream()
                        .filter(candidate ->
                                candidate.terminalFate().channelTerminalCellIndex() == 1397)
                        .findFirst()
                        .orElseThrow();

        assertEquals(
                SkyIslandChannelTerminalFateKind.RETAINED_OPEN_WATER,
                site.terminalFate().kind());
        assertEquals(
                SkyIslandWaterbodyKind.LAKE,
                site.terminalFate().waterbodyKind().orElseThrow());
        assertEquals(1397, site.riverBoundary().reachEndCellIndex());
        assertEquals(1.0, site.riverBoundary().stationFraction(), EPSILON);
        assertEquals(
                SkyIslandHydraulicTransitionBoundaryRole.INCOMING,
                site.riverBoundary().role());
    }

    @Test
    void stress512KeepsUnresolvedTerminalFatesExplicitAndSeparateFromBasins() {
        SkyIslandHydraulicTransitionTopologyPlan plan =
                SkyIslandHydraulicTransitionTopologyPlanner.plan(
                        descriptor(6L, 61L, 512L));

        assertTrue(plan.unresolvedTerminals().stream()
                .anyMatch(fate -> fate.channelTerminalCellIndex() == 306));
        assertTrue(plan.unresolvedTerminals().stream()
                .anyMatch(fate -> fate.channelTerminalCellIndex() == 497));
        assertTrue(plan.unresolvedTerminals().stream()
                .allMatch(fate -> fate.kind() == SkyIslandChannelTerminalFateKind.UNRESOLVED));
        assertTrue(plan.basins().stream()
                .noneMatch(site ->
                        site.terminalFate().channelTerminalCellIndex() == 306
                                || site.terminalFate().channelTerminalCellIndex() == 497));
    }

    @Test
    void independentPlansAreIdentical() {
        SkyIslandDescriptor descriptor = descriptor(8L, 81L, 632L);
        assertEquals(
                SkyIslandHydraulicTransitionTopologyPlanner.plan(descriptor),
                SkyIslandHydraulicTransitionTopologyPlanner.plan(descriptor));
    }

    private static SkyIslandDescriptor descriptor(long province, long cluster, long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, province, cluster, key));
    }
}
