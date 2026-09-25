package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Applies explicit retained-basin limits without modifying terrain. */
public final class SkyIslandContinuousWaterbodyQualificationEvaluator {
    private static final double EPSILON = 1.0e-12;

    private SkyIslandContinuousWaterbodyQualificationEvaluator() {}

    public static SkyIslandContinuousWaterbodyQualification evaluate(
            SkyIslandContinuousWaterbodyDiagnostics diagnostics,
            SkyIslandContinuousWaterbodyQualificationPolicy policy) {
        Objects.requireNonNull(diagnostics, "diagnostics");
        Objects.requireNonNull(policy, "policy");

        SkyIslandWaterbodyKind kind =
                diagnostics.basin().sourceCandidate().kind();
        SkyIslandContinuousWaterbodyQualificationLimits limits =
                policy.limits(kind);
        List<SkyIslandContinuousWaterbodyQualificationViolation> violations =
                new ArrayList<>();

        if (diagnostics.reachesSearchBoundary()) {
            violations.add(
                    SkyIslandContinuousWaterbodyQualificationViolation.SEARCH_BOUNDARY_ESCAPE);
        }
        if (diagnostics.shorelineCrossingCount() < limits.minimumShorelineCrossings()) {
            violations.add(
                    SkyIslandContinuousWaterbodyQualificationViolation.MISSING_SHORELINE);
        }
        if (diagnostics.depthToEquivalentDiameterRatio()
                > limits.maximumDepthToEquivalentDiameterRatio() + EPSILON) {
            violations.add(
                    SkyIslandContinuousWaterbodyQualificationViolation.EXCESSIVE_DEPTH_TO_DIAMETER);
        }
        if (diagnostics.maximumShorelineGrade()
                > limits.maximumShorelineGrade() + EPSILON) {
            violations.add(
                    SkyIslandContinuousWaterbodyQualificationViolation.EXCESSIVE_SHORELINE_GRADE);
        }
        if (diagnostics.spillHeadroomWorldUnits() + EPSILON
                < limits.minimumSpillHeadroomWorldUnits()) {
            violations.add(
                    SkyIslandContinuousWaterbodyQualificationViolation.INSUFFICIENT_SPILL_HEADROOM);
        }
        if (diagnostics.matchedTerminalReachCount() > 0
                && diagnostics.maximumChannelDatumMismatchWorldUnits()
                        > limits.maximumChannelDatumMismatchWorldUnits() + EPSILON) {
            violations.add(
                    SkyIslandContinuousWaterbodyQualificationViolation.CHANNEL_DATUM_MISMATCH);
        }

        violations.sort(Comparator.comparingInt(Enum::ordinal));
        return new SkyIslandContinuousWaterbodyQualification(
                diagnostics, limits, violations);
    }
}
