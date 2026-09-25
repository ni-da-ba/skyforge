package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkyIslandContinuousWaterbodyQualificationEvaluatorTest {
    private static final long SEED = 0x534B59464F524745L;

    @Test
    void explicitPermissivePolicyAcceptsClosedMeasuredBasins() {
        SkyIslandContinuousWaterbodyQualificationLimits permissive =
                new SkyIslandContinuousWaterbodyQualificationLimits(
                        100.0, 100.0, 0.0, 1000.0, 1);
        SkyIslandContinuousWaterbodyQualificationPolicy policy =
                new SkyIslandContinuousWaterbodyQualificationPolicy(
                        permissive, permissive);

        for (long key : new long[] {83L, 287L, 512L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            for (SkyIslandContinuousWaterbodyDiagnostics d :
                    SkyIslandContinuousWaterbodyDiagnosticsPlanner.measure(descriptor)) {
                SkyIslandContinuousWaterbodyQualification result =
                        SkyIslandContinuousWaterbodyQualificationEvaluator.evaluate(d, policy);
                if (!d.reachesSearchBoundary() && d.shorelineCrossingCount() > 0) {
                    assertTrue(result.accepted(), result.violations().toString());
                }
            }
        }
    }

    @Test
    void boundaryEscapeIsAlwaysRejectedIndependentOfNumericLimits() {
        SkyIslandDescriptor descriptor = descriptor(83L);
        List<SkyIslandContinuousWaterbodyDiagnostics> diagnostics =
                SkyIslandContinuousWaterbodyDiagnosticsPlanner.measure(descriptor);

        SkyIslandContinuousWaterbodyDiagnostics escaping =
                diagnostics.stream()
                        .filter(SkyIslandContinuousWaterbodyDiagnostics::reachesSearchBoundary)
                        .findFirst()
                        .orElse(null);

        if (escaping == null) {
            return;
        }

        SkyIslandContinuousWaterbodyQualificationLimits permissive =
                new SkyIslandContinuousWaterbodyQualificationLimits(
                        100.0, 100.0, 0.0, 1);
        SkyIslandContinuousWaterbodyQualificationPolicy policy =
                new SkyIslandContinuousWaterbodyQualificationPolicy(
                        permissive, permissive);

        SkyIslandContinuousWaterbodyQualification result =
                SkyIslandContinuousWaterbodyQualificationEvaluator.evaluate(
                        escaping, policy);
        assertFalse(result.accepted());
        assertTrue(result.violations().contains(
                SkyIslandContinuousWaterbodyQualificationViolation.SEARCH_BOUNDARY_ESCAPE));
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 6L, 61L, key));
    }
}
