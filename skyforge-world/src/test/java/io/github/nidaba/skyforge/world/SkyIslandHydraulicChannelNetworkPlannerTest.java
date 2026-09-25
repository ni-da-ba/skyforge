package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SkyIslandHydraulicChannelNetworkPlannerTest {
    private static final long SEED = 0x534B59464F524745L;
    private static final double EPSILON = 1.0e-10;

    @Test
    void hydraulicPlanIsDeterministicAndSharedNodeDatumsAgreeExactly() {
        for (long key : new long[] {77L, 118L, 241L, 287L, 512L, 632L, 811L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandHydraulicChannelNetworkPlan first =
                    SkyIslandHydraulicChannelNetworkPlanner.plan(descriptor);
            SkyIslandHydraulicChannelNetworkPlan second =
                    SkyIslandHydraulicChannelNetworkPlanner.plan(descriptor);
            assertEquals(first, second);

            Map<Integer, Double> nodeSurfaces = new HashMap<>();
            for (SkyIslandHydraulicReachGeometry reach : first.reaches()) {
                SkyIslandSemanticChannelReach semantic =
                        reach.geomorphicRoute().semanticReach();
                assertSameDatum(nodeSurfaces, semantic.startCellIndex(), reach.startWaterSurfacePotential());
                assertSameDatum(nodeSurfaces, semantic.endCellIndex(), reach.endWaterSurfacePotential());
            }
        }
    }

    @Test
    void ordinaryReachSurfacesNeverClimbDownstream() {
        for (long key : new long[] {287L, 632L, 649L, 811L}) {
            SkyIslandHydraulicChannelNetworkPlan plan =
                    SkyIslandHydraulicChannelNetworkPlanner.plan(descriptor(key));
            for (SkyIslandHydraulicReachGeometry reach : plan.reaches()) {
                for (int i = 1; i < reach.samples().size(); i++) {
                    assertTrue(
                            reach.samples().get(i).waterSurfacePotential()
                                    < reach.samples().get(i - 1).waterSurfacePotential() + EPSILON,
                            "ordinary channel free surface must not climb downstream");
                }
            }
        }
    }

    @Test
    void dischargeScaledHydraulicGeometryIsMonotone() {
        double radius = 100.0;
        double previousWidth = 0.0;
        double previousDepth = 0.0;
        for (int i = 0; i <= 100; i++) {
            double q = (double) i / 100.0;
            double width = SkyIslandHydraulicChannelNetworkPlanner.bankfullHalfWidth(radius, q);
            double depth = SkyIslandHydraulicChannelNetworkPlanner.waterDepthPotential(q);
            assertTrue(width + EPSILON >= previousWidth);
            assertTrue(depth + EPSILON >= previousDepth);
            previousWidth = width;
            previousDepth = depth;
        }
    }

    @Test
    void plannerMeasuresRequiredTerrainModificationWithoutApplyingIt() {
        SkyIslandDescriptor descriptor = descriptor(287L);
        SkyIslandGeomorphicChannelNetworkPlan geometry =
                SkyIslandGeomorphicChannelNetworkPlanner.plan(descriptor);
        SkyIslandSemanticField constantTerrain = ignored -> 0.70;

        SkyIslandHydraulicChannelNetworkPlan plan =
                SkyIslandHydraulicChannelNetworkPlanner.plan(
                        descriptor, geometry, constantTerrain);

        assertTrue(plan.maximumRequiredLowering() > 0.0);
        assertTrue(plan.meanRequiredLowering() > 0.0);
        for (SkyIslandHydraulicReachGeometry reach : plan.reaches()) {
            for (SkyIslandHydraulicGeometrySample sample : reach.samples()) {
                assertEquals(0.70, sample.terrainElevation(), EPSILON);
                assertTrue(sample.requiredCenterlineLowering() >= 0.0);
            }
        }
    }

    private static void assertSameDatum(
            Map<Integer, Double> nodeSurfaces,
            int node,
            double value) {
        Double previous = nodeSurfaces.putIfAbsent(node, value);
        if (previous != null) {
            assertEquals(previous, value, EPSILON);
        }
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 8L, 81L, key));
    }
}
