package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkyIslandGeomorphicReachDiagnosticsPlannerTest {
    private static final long SEED = 0x534B59464F524745L;

    @Test
    void diagnosticsAreDeterministicFiniteAndNonNegativeAcrossCorpus() {
        for (long key : new long[] {77L, 83L, 118L, 287L, 512L, 632L, 649L, 811L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            List<SkyIslandGeomorphicReachDiagnostics> first =
                    SkyIslandGeomorphicReachDiagnosticsPlanner.measure(descriptor);
            List<SkyIslandGeomorphicReachDiagnostics> second =
                    SkyIslandGeomorphicReachDiagnosticsPlanner.measure(descriptor);
            assertEquals(first, second);
            for (SkyIslandGeomorphicReachDiagnostics d : first) {
                assertTrue(Double.isFinite(d.maximumCenterlineLoweringWorldUnits()));
                assertTrue(Double.isFinite(d.maximumLateralRecoveryGrade()));
                assertTrue(Double.isFinite(d.maximumBankContainmentDeficitWorldUnits()));
                assertTrue(Double.isFinite(d.maximumDepthToBankfullWidthRatio()));
                assertTrue(Double.isFinite(d.maximumReliefToValleyWidthRatio()));
                assertTrue(Double.isFinite(d.normalizedExcavationBurden()));
                assertTrue(Double.isFinite(d.excavationVolumeProxyWorldUnitsCubed()));
                assertTrue(Double.isFinite(d.maximumCurvatureWidthRatio()));
                assertTrue(Double.isFinite(d.maximumLongitudinalGrade()));
                assertTrue(d.maximumCenterlineLoweringWorldUnits() >= 0.0);
                assertTrue(d.maximumLateralRecoveryGrade() >= 0.0);
                assertTrue(d.maximumBankContainmentDeficitWorldUnits() >= 0.0);
            }
        }
    }

    @Test
    void ridgeOccupancyUsesC3ArcLengthWeightedFunctionalDiagnostic() {
        SkyIslandDescriptor descriptor = descriptor(632L);
        SkyIslandSemanticField terrain = SkyIslandPreHydrologicTerrainField.create(descriptor);
        SkyIslandHydraulicChannelNetworkPlan hydraulic =
                SkyIslandHydraulicChannelNetworkPlanner.plan(descriptor);
        double planningSpacing =
                SkyIslandSemanticChannelReachPlanner.plan(descriptor).planningSpacing();

        List<SkyIslandGeomorphicReachDiagnostics> diagnostics =
                SkyIslandGeomorphicReachDiagnosticsPlanner.measure(
                        descriptor, hydraulic, terrain);
        assertEquals(hydraulic.reaches().size(), diagnostics.size());

        for (int i = 0; i < hydraulic.reaches().size(); i++) {
            SkyIslandHydraulicReachGeometry reach = hydraulic.reaches().get(i);
            double expected =
                    SkyIslandRouteFunctionalDiagnosticsPlanner.measure(
                                    reach.geomorphicRoute().route(),
                                    terrain,
                                    planningSpacing)
                            .ridgeLengthFraction();
            assertEquals(expected, diagnostics.get(i).ridgeSampleFraction(), 1.0e-12);
        }
    }

    @Test
    void flatUnmodifiedTerrainReportsContainmentDeficitWhenWaterSurfaceExceedsBankTerrain() {
        SkyIslandDescriptor descriptor = descriptor(287L);
        SkyIslandHydraulicChannelNetworkPlan hydraulic =
                SkyIslandHydraulicChannelNetworkPlanner.plan(descriptor);
        if (hydraulic.reaches().isEmpty()) {
            return;
        }
        SkyIslandHydraulicReachGeometry reach = hydraulic.reaches().getFirst();
        SkyIslandSemanticField lowFlatTerrain = ignored -> 0.0;
        SkyIslandGeomorphicReachDiagnostics diagnostics =
                SkyIslandGeomorphicReachDiagnosticsPlanner.measureReach(
                        descriptor, reach, lowFlatTerrain);
        assertTrue(diagnostics.maximumBankContainmentDeficitWorldUnits() > 0.0);
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 8L, 81L, key));
    }
}
