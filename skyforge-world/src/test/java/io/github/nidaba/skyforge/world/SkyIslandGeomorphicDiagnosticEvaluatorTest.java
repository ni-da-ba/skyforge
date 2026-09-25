package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import org.junit.jupiter.api.Test;

class SkyIslandGeomorphicDiagnosticEvaluatorTest {
    private static final long SEED = 0x534B59464F524745L;

    @Test
    void diagnosticsAreFiniteAcrossFixedHydrologyControls() {
        for (long key : new long[] {77L, 118L, 241L, 287L, 512L, 632L, 811L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandHydraulicChannelNetworkPlan hydraulics =
                    SkyIslandHydraulicChannelNetworkPlanner.plan(descriptor);
            SkyIslandSemanticField terrain =
                    SkyIslandPreHydrologicTerrainField.create(descriptor);

            SkyIslandGeomorphicDiagnostics diagnostics =
                    SkyIslandGeomorphicDiagnosticEvaluator.evaluate(descriptor, hydraulics, terrain);

            assertTrue(Double.isFinite(diagnostics.minimumNaturalBankContainment()));
            assertTrue(diagnostics.maximumSemanticBankRecoveryGrade() >= 0.0);
            assertTrue(diagnostics.maximumCurvatureWidthRatio() >= 0.0);
            assertTrue(diagnostics.normalizedLowerBoundCutVolume() >= 0.0);
        }
    }

    @Test
    void flatTerrainProducesFiniteContainmentAndRecoveryMetrics() {
        SkyIslandDescriptor descriptor = descriptor(287L);
        SkyIslandGeomorphicChannelNetworkPlan geometry =
                SkyIslandGeomorphicChannelNetworkPlanner.plan(descriptor);
        SkyIslandSemanticField flat = ignored -> 0.70;
        SkyIslandHydraulicChannelNetworkPlan hydraulics =
                SkyIslandHydraulicChannelNetworkPlanner.plan(descriptor, geometry, flat);

        SkyIslandGeomorphicDiagnostics diagnostics =
                SkyIslandGeomorphicDiagnosticEvaluator.evaluate(descriptor, hydraulics, flat);

        assertTrue(Double.isFinite(diagnostics.minimumNaturalBankContainment()));
        assertTrue(diagnostics.maximumSemanticBankRecoveryGrade() > 0.0);
        assertTrue(diagnostics.normalizedLowerBoundCutVolume() > 0.0);
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 8L, 81L, key));
    }
}
