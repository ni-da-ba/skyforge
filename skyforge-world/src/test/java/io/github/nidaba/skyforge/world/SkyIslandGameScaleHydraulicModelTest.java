package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SkyIslandGameScaleHydraulicModelTest {
    private static final long SEED = 0x534B59464F524745L;
    private static final double EPSILON = 1.0e-8;

    @Test
    void accepted77EdgeOutletReachesCloseOnTheirCandidateBeds() {
        SkyIslandDescriptor descriptor = descriptor(77L);
        SkyIslandHydraulicNetworkAssemblyPlan assembly =
                SkyIslandHydraulicNetworkAssemblyPlanner.plan(descriptor);
        SkyIslandHydraulicGeometrySkeletonPlan skeletonPlan =
                assembly.ordinarySpanPlan()
                        .cascadePlan()
                        .transitionGeometry()
                        .topology()
                        .skeletonPlan();
        SkyIslandSemanticField terrain = SkyIslandPreHydrologicTerrainField.create(descriptor);

        assertEquals(
                Set.of("709->559", "1742->1842"),
                assembly.terminalComponents().stream()
                        .filter(component ->
                                component.status() == SkyIslandHydraulicAssemblyStatus.QUALIFIED)
                        .flatMap(component -> component.reaches().stream())
                        .map(reach -> reach.semanticReach().startCellIndex()
                                + "->"
                                + reach.semanticReach().endCellIndex())
                        .collect(java.util.stream.Collectors.toSet()));

        for (String expected : List.of("709->559", "1742->1842")) {
            String[] endpoints = expected.split("->");
            int start = Integer.parseInt(endpoints[0]);
            int end = Integer.parseInt(endpoints[1]);
            SkyIslandHydraulicReachSkeleton skeleton = skeletonPlan.reaches().stream()
                    .filter(reach -> reach.geomorphicRoute().semanticReach().startCellIndex() == start
                            && reach.geomorphicRoute().semanticReach().endCellIndex() == end)
                    .findFirst()
                    .orElseThrow();

            SkyIslandGameScaleHydraulicModel.SourceReachSolution solution =
                    SkyIslandGameScaleHydraulicModel.solveSourceReach(
                            descriptor, skeleton, terrain);
            double[] stations = solution.profile().stationsMetres();
            double[] beds = solution.profile().bedElevationsMetres();
            double[] depths = solution.profile().depthsMetres();
            double[] stages = solution.profile().waterSurfaceElevationsMetres();
            double[] discharges = solution.dischargesCubicMetresPerSecond();
            assertEquals(skeleton.samples().size(), stations.length);
            assertEquals(stations.length, discharges.length);
            assertTrue(solution.sourceNormalDepthMetres() > 0.0);
            assertTrue(solution.sourceFroudeNumber() > 0.0);
            assertEquals(
                    solution.sourceFroudeNumber() < 1.0
                            ? SkyIslandStandardStepSolver.FlowRegime.SUBCRITICAL
                            : SkyIslandStandardStepSolver.FlowRegime.SUPERCRITICAL,
                    solution.flowRegime());
            assertEquals(solution.sourceNormalDepthMetres(), depths[0], EPSILON);

            for (int i = 0; i < stations.length; i++) {
                assertTrue(Double.isFinite(stations[i]));
                assertTrue(Double.isFinite(discharges[i]) && discharges[i] > 0.0);
                assertTrue(Double.isFinite(depths[i]) && depths[i] > 0.0);
                assertEquals(beds[i] + depths[i], stages[i], EPSILON);
                assertTrue(Double.isFinite(solution.candidate().sampleTerrainMetres(
                        skeleton.centerline().points().get(i))));
            }
        }
    }

    @Test
    void componentCalibrationUsesTheTightestBankfullCapacityAcrossAllReaches() {
        var highCapacity = new SkyIslandGameScaleHydraulicModel.BankfullCapacityProfile(
                new double[] {1.0, 1.2}, new double[] {0.01, 0.01},
                2.0, 1.0, 1.0);
        var limitingCapacity = new SkyIslandGameScaleHydraulicModel.BankfullCapacityProfile(
                new double[] {1.0, 1.5}, new double[] {0.005, 0.005},
                2.0, 1.0, 1.0);

        double expected = manningCapacity(2.0, 1.0, 1.0, 0.005);
        assertEquals(
                expected,
                SkyIslandGameScaleHydraulicModel.calibrateComponentDischargeScale(
                        List.of(highCapacity, limitingCapacity)),
                1.0e-12);
    }

    @Test
    void componentCalibrationRejectsNonpositiveSlopeAndEmptyNetworks() {
        assertThrows(IllegalArgumentException.class,
                () -> new SkyIslandGameScaleHydraulicModel.BankfullCapacityProfile(
                        new double[] {1.0, 1.0}, new double[] {0.01, 0.0},
                        2.0, 1.0, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> SkyIslandGameScaleHydraulicModel.calibrateComponentDischargeScale(List.of()));
    }

    @Test
    void rejected287RemainsOutsideCandidateHydraulics() {
        SkyIslandComponentFluvialTerrainCandidatePlan plan =
                SkyIslandComponentFluvialTerrainCandidatePlanner.plan(descriptor(287L));

        assertTrue(plan.realizedComponents().isEmpty());
        assertTrue(plan.terrainField().acceptedReaches().isEmpty());
        assertTrue(plan.postRealizationQualifications().isEmpty());
    }

    private static double manningCapacity(
            double bottomWidth, double sideSlope, double depth, double slope) {
        double area = bottomWidth * depth + sideSlope * depth * depth;
        double perimeter = bottomWidth + 2.0 * depth * Math.hypot(1.0, sideSlope);
        return area * Math.pow(area / perimeter, 2.0 / 3.0) * Math.sqrt(slope)
                / SkyIslandGameScaleHydraulicModel.MANNING_ROUGHNESS;
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 8L, 81L, key));
    }
}
