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
        List<SkyIslandGeomorphicQualificationViolation> violations = new ArrayList<>();

        addIfGreater(
                diagnostics.maximumCenterlineLoweringPotential(),
                limits.maximumCenterlineLoweringPotential(),
                SkyIslandGeomorphicQualificationViolation.CENTERLINE_LOWERING,
                violations);
        addIfGreater(
                diagnostics.maximumLateralRecoveryGrade(),
                limits.maximumLateralRecoveryGrade(),
                SkyIslandGeomorphicQualificationViolation.LATERAL_RECOVERY_GRADE,
                violations);
        addIfGreater(
                diagnostics.maximumBankContainmentDeficitWorldUnits(),
                limits.maximumBankContainmentDeficitWorldUnits(),
                SkyIslandGeomorphicQualificationViolation.BANK_CONTAINMENT,
                violations);
        addIfGreater(
                diagnostics.maximumDepthToBankfullWidthRatio(),
                limits.maximumDepthToBankfullWidthRatio(),
                SkyIslandGeomorphicQualificationViolation.DEPTH_TO_WIDTH,
                violations);
        addIfGreater(
                diagnostics.maximumReliefToValleyWidthRatio(),
                limits.maximumReliefToValleyWidthRatio(),
                SkyIslandGeomorphicQualificationViolation.RELIEF_TO_VALLEY_WIDTH,
                violations);
        addIfGreater(
                diagnostics.normalizedExcavationBurden(),
                limits.maximumNormalizedExcavationBurden(),
                SkyIslandGeomorphicQualificationViolation.EXCAVATION_BURDEN,
                violations);
        addIfGreater(
                diagnostics.excavationVolumeProxyWorldUnitsCubed(),
                limits.maximumExcavationVolumeWorldUnitsCubed(),
                SkyIslandGeomorphicQualificationViolation.EXCAVATION_VOLUME,
                violations);
        addIfGreater(
                diagnostics.maximumCurvatureWidthRatio(),
                limits.maximumCurvatureWidthRatio(),
                SkyIslandGeomorphicQualificationViolation.CURVATURE_TO_WIDTH,
                violations);
        addIfGreater(
                diagnostics.ridgeSampleFraction(),
                limits.maximumRidgeSampleFraction(),
                SkyIslandGeomorphicQualificationViolation.RIDGE_OCCUPANCY,
                violations);
        addIfGreater(
                diagnostics.maximumLongitudinalGrade(),
                limits.maximumLongitudinalGrade(),
                SkyIslandGeomorphicQualificationViolation.LONGITUDINAL_GRADE,
                violations);

        violations.sort(Comparator.comparingInt(Enum::ordinal));
        return new SkyIslandGeomorphicReachQualification(diagnostics, limits, violations);
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
