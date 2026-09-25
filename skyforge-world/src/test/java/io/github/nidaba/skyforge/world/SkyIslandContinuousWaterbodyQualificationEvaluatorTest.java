package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
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

        SkyIslandContinuousWaterbodyDiagnostics baseline = closedBaseline();
        SkyIslandContinuousWaterbodyQualification result =
                SkyIslandContinuousWaterbodyQualificationEvaluator.evaluate(baseline, policy);

        assertTrue(result.accepted(), result.violations().toString());
    }

    @Test
    void structuralFailuresAreRejectedIndependentOfNumericLimits() {
        SkyIslandContinuousWaterbodyDiagnostics baseline = closedBaseline();
        SkyIslandContinuousWaterbodyQualificationLimits permissive =
                new SkyIslandContinuousWaterbodyQualificationLimits(
                        100.0, 100.0, 0.0, 1000.0, 1);
        SkyIslandContinuousWaterbodyQualificationPolicy policy =
                new SkyIslandContinuousWaterbodyQualificationPolicy(
                        permissive, permissive);

        SkyIslandContinuousWaterbodyDiagnostics escaping = copy(
                baseline, baseline.matchedTerminalReachCount(),
                baseline.maximumChannelDatumMismatchWorldUnits(), true,
                baseline.shorelineCrossingCount());
        SkyIslandContinuousWaterbodyQualification escaped =
                SkyIslandContinuousWaterbodyQualificationEvaluator.evaluate(escaping, policy);
        assertFalse(escaped.accepted());
        assertTrue(escaped.violations().contains(
                SkyIslandContinuousWaterbodyQualificationViolation.SEARCH_BOUNDARY_ESCAPE));

        SkyIslandContinuousWaterbodyDiagnostics noShoreline = copy(
                baseline, baseline.matchedTerminalReachCount(),
                baseline.maximumChannelDatumMismatchWorldUnits(), false, 0);
        SkyIslandContinuousWaterbodyQualification shoreline =
                SkyIslandContinuousWaterbodyQualificationEvaluator.evaluate(noShoreline, policy);
        assertFalse(shoreline.accepted());
        assertTrue(shoreline.violations().contains(
                SkyIslandContinuousWaterbodyQualificationViolation.MISSING_SHORELINE));
    }

    @Test
    void exactSemanticChannelJunctionEnforcesDatumCompatibility() {
        SkyIslandContinuousWaterbodyDiagnostics baseline = closedBaseline();
        SkyIslandContinuousWaterbodyQualificationLimits limits =
                new SkyIslandContinuousWaterbodyQualificationLimits(
                        100.0, 100.0, 0.0, 0.25, 1);
        SkyIslandContinuousWaterbodyQualificationPolicy policy =
                new SkyIslandContinuousWaterbodyQualificationPolicy(limits, limits);

        SkyIslandContinuousWaterbodyDiagnostics mismatched =
                copy(baseline, 1, 0.50, false, baseline.shorelineCrossingCount());
        SkyIslandContinuousWaterbodyQualification result =
                SkyIslandContinuousWaterbodyQualificationEvaluator.evaluate(mismatched, policy);

        assertFalse(result.accepted());
        assertTrue(result.violations().contains(
                SkyIslandContinuousWaterbodyQualificationViolation.CHANNEL_DATUM_MISMATCH));
    }

    @Test
    void nearbyButUnmatchedChannelDoesNotCreateSyntheticHydraulicJunction() {
        SkyIslandContinuousWaterbodyDiagnostics baseline = closedBaseline();
        SkyIslandContinuousWaterbodyQualificationLimits limits =
                new SkyIslandContinuousWaterbodyQualificationLimits(
                        100.0, 100.0, 0.0, 0.25, 1);
        SkyIslandContinuousWaterbodyQualificationPolicy policy =
                new SkyIslandContinuousWaterbodyQualificationPolicy(limits, limits);

        SkyIslandContinuousWaterbodyDiagnostics unmatched =
                copy(baseline, 0, 100.0, false, baseline.shorelineCrossingCount());
        SkyIslandContinuousWaterbodyQualification result =
                SkyIslandContinuousWaterbodyQualificationEvaluator.evaluate(unmatched, policy);

        assertTrue(result.accepted(), result.violations().toString());
    }

    private static SkyIslandContinuousWaterbodyDiagnostics closedBaseline() {
        SkyIslandDescriptor descriptor = descriptor(8L, 81L, 609L);
        for (SkyIslandContinuousWaterbodyDiagnostics diagnostics :
                SkyIslandContinuousWaterbodyDiagnosticsPlanner.measure(descriptor)) {
            if (!diagnostics.reachesSearchBoundary()
                    && diagnostics.shorelineCrossingCount() > 0) {
                return diagnostics;
            }
        }
        throw new AssertionError("fixed E1 lake-609 specimen must contain a closed retained basin");
    }

    private static SkyIslandContinuousWaterbodyDiagnostics copy(
            SkyIslandContinuousWaterbodyDiagnostics baseline,
            int matchedTerminalReachCount,
            double maximumChannelDatumMismatchWorldUnits,
            boolean reachesSearchBoundary,
            int shorelineCrossingCount) {
        return new SkyIslandContinuousWaterbodyDiagnostics(
                baseline.basin(),
                baseline.equivalentDiameter(),
                baseline.maximumDepthWorldUnits(),
                baseline.depthToEquivalentDiameterRatio(),
                baseline.maximumShorelineGrade(),
                baseline.spillHeadroomWorldUnits(),
                matchedTerminalReachCount,
                maximumChannelDatumMismatchWorldUnits,
                reachesSearchBoundary,
                shorelineCrossingCount);
    }

    private static SkyIslandDescriptor descriptor(long province, long cluster, long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, province, cluster, key));
    }
}
