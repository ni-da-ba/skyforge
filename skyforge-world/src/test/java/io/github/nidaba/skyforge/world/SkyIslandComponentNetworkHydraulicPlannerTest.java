package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SkyIslandComponentNetworkHydraulicPlannerTest {
    private static final long SEED = 0x534B59464F524745L;
    private static final double EPSILON = 1.0e-8;

    @Test
    void accepted77OrdinaryComponentsCloseWithOneCandidateCalibratedNetworkScale() {
        SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 8L, 81L, 77L));
        SkyIslandHydraulicNetworkAssemblyPlan assembly =
                SkyIslandHydraulicNetworkAssemblyPlanner.plan(descriptor);
        SkyIslandHydraulicGeometrySkeletonPlan skeletonPlan =
                assembly.ordinarySpanPlan()
                        .cascadePlan()
                        .transitionGeometry()
                        .topology()
                        .skeletonPlan();
        Map<Long, SkyIslandHydraulicReachSkeleton> skeletons = skeletonPlan.reaches().stream()
                .collect(Collectors.toUnmodifiableMap(
                        reach -> identity(reach.geomorphicRoute().semanticReach()),
                        reach -> reach));
        SkyIslandSemanticField terrain = SkyIslandPreHydrologicTerrainField.create(descriptor);

        List<SkyIslandHydraulicTerminalComponent> accepted = assembly.terminalComponents().stream()
                .filter(component -> component.status() == SkyIslandHydraulicAssemblyStatus.QUALIFIED)
                .toList();
        assertEquals(2, accepted.size());

        for (SkyIslandHydraulicTerminalComponent component : accepted) {
            var network = SkyIslandComponentNetworkHydraulicPlanner.solve(
                    descriptor, component, skeletons, terrain);
            assertEquals(component.reaches().size(), network.reachesById().size());
            assertTrue(network.dischargeScale() > 0.0);
            assertEquals(
                    component.terminalFate().channelTerminalCellIndex() + "",
                    network.flows().terminalNodeId());

            for (var assemblyReach : component.reaches()) {
                String id = assemblyReach.semanticReach().startCellIndex()
                        + "->" + assemblyReach.semanticReach().endCellIndex();
                var solved = network.reachesById().get(id);
                assertTrue(solved != null, "missing solved reach " + id);
                double[] beds = solved.profile().bedElevationsMetres();
                double[] stages = solved.profile().waterSurfaceElevationsMetres();
                double[] depths = solved.profile().depthsMetres();
                double[] stations = solved.profile().stationsMetres();
                double[] flow = solved.flows().dischargeAtStationsCubicMetresPerSecond();
                double[] lateral = solved.flows().lateralInflowByIntervalCubicMetresPerSecond();
                assertEquals(beds.length, stages.length);
                assertEquals(beds.length, depths.length);
                assertEquals(beds.length, flow.length);
                assertEquals(beds.length - 1, lateral.length);
                for (int i = 0; i < beds.length; i++) {
                    assertTrue(Double.isFinite(beds[i]));
                    assertTrue(Double.isFinite(stages[i]));
                    assertTrue(depths[i] > 0.0);
                    assertEquals(beds[i] + depths[i], stages[i], EPSILON);
                    assertTrue(flow[i] > 0.0);
                    if (i > 0) {
                        assertTrue(stations[i] > stations[i - 1]);
                        assertTrue(beds[i] < beds[i - 1]);
                    }
                }
                for (double increment : lateral) {
                    assertTrue(increment >= 0.0);
                }
            }
        }
    }

    private static long identity(SkyIslandSemanticChannelReach semantic) {
        return ((long) semantic.startCellIndex() << 32)
                ^ Integer.toUnsignedLong(semantic.endCellIndex());
    }
}
