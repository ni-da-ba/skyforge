package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Applies explicit hard geomorphic limits to D1 diagnostics without mutating terrain. */
public final class SkyIslandGeomorphicQualificationEvaluator {
    private static final double EPSILON = 1.0e-12;

    private SkyIslandGeomorphicQualificationEvaluator() {}

    public static SkyIslandGeomorphicReachQualification evaluate(
            SkyIslandGeomorphicReachDiagnostics diagnostics,
            SkyIslandGeomorphicQualificationPolicy policy) {
        Objects.requireNonNull(diagnostics, "diagnostics");
        Objects.requireNonNull(policy, "policy");

        SkyIslandSemanticChannelReach semantic =
                diagnostics.hydraulicReach().geomorphicRoute().semanticReach();
        SkyIslandGeomorphicProfileLimits limits = policy.limits(semantic);
        return new SkyIslandGeomorphicReachQualification(
                diagnostics,
                limits,
                violations(SkyIslandGeomorphicMeasurements.from(diagnostics), limits));
    }

    static List<SkyIslandGeomorphicQualificationViolation> violations(
            SkyIslandGeomorphicMeasurements measurements,
            SkyIslandGeomorphicProfileLimits limits) {
        Objects.requireNonNull(measurements, "measurements");
        Objects.requireNonNull(limits, "limits");
        List<SkyIslandGeomorphicQualificationViolation> violations = new ArrayList<>();

        addIfGreater(
                measurements.maximumCenterlineLoweringPotential(),
                limits.maximumCenterlineLoweringPotential(),
                SkyIslandGeomorphicQualificationViolation.CENTERLINE_LOWERING,
                violations);
        addIfGreater(
                measurements.maximumLateralRecoveryGrade(),
                limits.maximumLateralRecoveryGrade(),
                SkyIslandGeomorphicQualificationViolation.LATERAL_RECOVERY_GRADE,
                violations);
        addIfGreater(
                measurements.maximumBankContainmentDeficitWorldUnits(),
                limits.maximumBankContainmentDeficitWorldUnits(),
                SkyIslandGeomorphicQualificationViolation.BANK_CONTAINMENT,
                violations);
        addIfGreater(
                measurements.maximumDepthToBankfullWidthRatio(),
                limits.maximumDepthToBankfullWidthRatio(),
                SkyIslandGeomorphicQualificationViolation.DEPTH_TO_WIDTH,
                violations);
        addIfGreater(
                measurements.maximumReliefToValleyWidthRatio(),
                limits.maximumReliefToValleyWidthRatio(),
                SkyIslandGeomorphicQualificationViolation.RELIEF_TO_VALLEY_WIDTH,
                violations);
        addIfGreater(
                measurements.normalizedExcavationBurden(),
                limits.maximumNormalizedExcavationBurden(),
                SkyIslandGeomorphicQualificationViolation.EXCAVATION_BURDEN,
                violations);
        addIfGreater(
                measurements.maximumCurvatureWidthRatio(),
                limits.maximumCurvatureWidthRatio(),
                SkyIslandGeomorphicQualificationViolation.CURVATURE_TO_WIDTH,
                violations);
        addIfGreater(
                measurements.ridgeLengthFraction(),
                limits.maximumRidgeSampleFraction(),
                SkyIslandGeomorphicQualificationViolation.RIDGE_OCCUPANCY,
                violations);
        addIfGreater(
                measurements.maximumLongitudinalGrade(),
                limits.maximumLongitudinalGrade(),
                SkyIslandGeomorphicQualificationViolation.LONGITUDINAL_GRADE,
                violations);

        violations.sort(Comparator.comparingInt(Enum::ordinal));
        return List.copyOf(violations);
    }

    private static void addIfGreater(
            double measured,
            double limit,
            SkyIslandGeomorphicQualificationViolation violation,
            List<SkyIslandGeomorphicQualificationViolation> violations) {
        if (measured > limit + EPSILON) {
            violations.add(violation);
        }
    }
}
