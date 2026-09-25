package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkyIslandGeomorphicQualificationEvaluatorTest {
    private static final long SEED = 0x534B59464F524745L;

    @Test
    void explicitPermissiveEnvelopeAcceptsMeasuredReachWithoutMutation() {
        SkyIslandDescriptor descriptor = descriptor(287L);
        List<SkyIslandGeomorphicReachDiagnostics> diagnostics =
                SkyIslandGeomorphicReachDiagnosticsPlanner.measure(descriptor);
        assertFalse(diagnostics.isEmpty());

        SkyIslandGeomorphicProfileLimits permissive =
                new SkyIslandGeomorphicProfileLimits(
                        1.0,
                        100.0,
                        1000.0,
                        100.0,
                        100.0,
                        1.0,
                        1.0e12,
                        100.0,
                        1.0,
                        100.0);
        SkyIslandGeomorphicQualificationPolicy policy =
                new SkyIslandGeomorphicQualificationPolicy(
                        permissive, permissive, permissive, permissive);

        for (SkyIslandGeomorphicReachDiagnostics diagnostic : diagnostics) {
            SkyIslandGeomorphicReachQualification result =
                    SkyIslandGeomorphicQualificationEvaluator.evaluate(diagnostic, policy);
            assertTrue(result.accepted(), result.violations().toString());
        }
    }

    @Test
    void explicitLoweringEnvelopeRejectsObjectiveQuarryPressure() {
        SkyIslandDescriptor descriptor = descriptor(287L);
        SkyIslandGeomorphicReachDiagnostics diagnostic =
                SkyIslandGeomorphicReachDiagnosticsPlanner.measure(descriptor).stream()
                        .filter(d -> d.maximumCenterlineLoweringPotential() > 0.10)
                        .findFirst()
                        .orElseThrow();

        SkyIslandGeomorphicProfileLimits tightLowering =
                new SkyIslandGeomorphicProfileLimits(
                        0.10,
                        100.0,
                        1000.0,
                        100.0,
                        100.0,
                        1.0,
                        1.0e12,
                        100.0,
                        1.0,
                        100.0);
        SkyIslandGeomorphicQualificationPolicy policy =
                new SkyIslandGeomorphicQualificationPolicy(
                        tightLowering, tightLowering, tightLowering, tightLowering);

        SkyIslandGeomorphicReachQualification result =
                SkyIslandGeomorphicQualificationEvaluator.evaluate(diagnostic, policy);
        assertFalse(result.accepted());
        assertTrue(result.violations().contains(
                SkyIslandGeomorphicQualificationViolation.CENTERLINE_LOWERING));
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 8L, 81L, key));
    }
}
