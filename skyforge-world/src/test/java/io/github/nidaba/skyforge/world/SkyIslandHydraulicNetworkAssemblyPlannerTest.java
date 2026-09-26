package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import org.junit.jupiter.api.Test;

class SkyIslandHydraulicNetworkAssemblyPlannerTest {
    private static final long SEED = 0x534B59464F524745L;

    @Test
    void primary287TerminalComponentRemainsPhysicalRejection() {
        SkyIslandHydraulicNetworkAssemblyPlan plan =
                SkyIslandHydraulicNetworkAssemblyPlanner.plan(
                        descriptor(8L, 81L, 287L));

        SkyIslandHydraulicTerminalComponent component =
                plan.terminalComponents().stream()
                        .filter(value ->
                                value.terminalFate().channelTerminalCellIndex() == 1758)
                        .findFirst()
                        .orElseThrow();

        assertEquals(
                SkyIslandHydraulicAssemblyStatus.PHYSICAL_REJECTION,
                component.status());
        assertTrue(component.reaches().stream()
                .anyMatch(reach ->
                        reach.semanticReach().startCellIndex() == 1090
                                && reach.semanticReach().endCellIndex() == 1758
                                && reach.status()
                                        == SkyIslandHydraulicAssemblyStatus.PHYSICAL_REJECTION));
    }

    @Test
    void confluence632EdgeOutletComponentsRemainTransitionDeferredUntilBoundaryDropsSolve() {
        SkyIslandHydraulicNetworkAssemblyPlan plan =
                SkyIslandHydraulicNetworkAssemblyPlanner.plan(
                        descriptor(8L, 81L, 632L));

        assertTrue(plan.terminalComponents().stream()
                .filter(component ->
                        component.terminalFate().kind()
                                == SkyIslandChannelTerminalFateKind.EDGE_OUTLET)
                .allMatch(component ->
                        component.status()
                                == SkyIslandHydraulicAssemblyStatus.TRANSITION_DEFERRED));
        assertTrue(plan.reachAssemblies().stream()
                .filter(reach ->
                        reach.semanticReach().endCellIndex() == 710)
                .anyMatch(reach ->
                        reach.status()
                                == SkyIslandHydraulicAssemblyStatus.QUALIFIED));
    }

    @Test
    void lake609RetainedTerminalCanNeverBeQualifiedWithoutTerminalAuthority() {
        SkyIslandHydraulicNetworkAssemblyPlan plan =
                SkyIslandHydraulicNetworkAssemblyPlanner.plan(
                        descriptor(8L, 81L, 609L));

        SkyIslandHydraulicTerminalComponent retained =
                plan.terminalComponents().stream()
                        .filter(component ->
                                component.terminalFate().kind()
                                        == SkyIslandChannelTerminalFateKind.RETAINED_OPEN_WATER)
                        .findFirst()
                        .orElseThrow();

        assertFalse(retained.status() == SkyIslandHydraulicAssemblyStatus.QUALIFIED);
        assertTrue(retained.blockers().stream()
                .anyMatch(blocker -> blocker.contains("RETAINED_OPEN_WATER")));
    }

    @Test
    void repeatedAssemblyPreservesSemanticEvidence() {
        SkyIslandDescriptor descriptor = descriptor(8L, 81L, 632L);
        SkyIslandHydraulicNetworkAssemblyPlan first =
                SkyIslandHydraulicNetworkAssemblyPlanner.plan(descriptor);
        SkyIslandHydraulicNetworkAssemblyPlan second =
                SkyIslandHydraulicNetworkAssemblyPlanner.plan(descriptor);

        assertEquals(
                first.reachAssemblies().stream()
                        .map(SkyIslandHydraulicReachAssembly::status)
                        .toList(),
                second.reachAssemblies().stream()
                        .map(SkyIslandHydraulicReachAssembly::status)
                        .toList());
        assertEquals(
                first.terminalComponents().stream()
                        .map(SkyIslandHydraulicTerminalComponent::status)
                        .toList(),
                second.terminalComponents().stream()
                        .map(SkyIslandHydraulicTerminalComponent::status)
                        .toList());
        assertEquals(
                first.terminalComponents().stream()
                        .map(SkyIslandHydraulicTerminalComponent::blockers)
                        .toList(),
                second.terminalComponents().stream()
                        .map(SkyIslandHydraulicTerminalComponent::blockers)
                        .toList());
    }

    private static SkyIslandDescriptor descriptor(long province, long cluster, long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, province, cluster, key));
    }
}
