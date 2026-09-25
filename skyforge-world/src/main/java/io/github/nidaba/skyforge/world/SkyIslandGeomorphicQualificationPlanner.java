package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Applies an explicit hard-safety envelope to D1 diagnostics.
 *
 * <p>This planner performs no terrain mutation and has no fallback that increases excavation.
 * Rejection is a first-class upstream result.
 */
public final class SkyIslandGeomorphicQualificationPlanner {
    private static final double EPSILON = 1.0e-12;

    private SkyIslandGeomorphicQualificationPlanner() {}

    public static SkyIslandGeomorphicQualificationPlan qualify(
            SkyIslandDescriptor descriptor,
            SkyIslandGeomorphicQualificationPolicy policy) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(policy, "policy");
        return qualify(descriptor, SkyIslandGeomorphicReachDiagnosticsPlanner.measure(descriptor), policy);
    }

    static SkyIslandGeomorphicQualificationPlan qualify(
            SkyIslandDescriptor descriptor,
            List<SkyIslandGeomorphicReachDiagnostics> diagnostics,
            SkyIslandGeomorphicQualificationPolicy policy) {
        Objects.requireNonNull(descriptor, "descriptor");
        diagnostics = List.copyOf(diagnostics);
        Objects.requireNonNull(policy, "policy");

        List<SkyIslandGeomorphicReachQualification> reaches = diagnostics.stream()
                .map(d -> qualifyReach(d, policy))
                .sorted(Comparator
                        .comparingInt((SkyIslandGeomorphicReachQualification q) ->
                                q.diagnostics().hydraulicReach().geomorphicRoute()
                                        .semanticReach().startCellIndex())
                        .thenComparingInt(q ->
                                q.diagnostics().hydraulicReach().geomorphicRoute()
                                        .semanticReach().endCellIndex()))
                .toList();
        return new SkyIslandGeomorphicQualificationPlan(descriptor, policy, reaches);
    }

    static SkyIslandGeomorphicReachQualification qualifyReach(
            SkyIslandGeomorphicReachDiagnostics d,
            SkyIslandGeomorphicQualificationPolicy policy) {
        Objects.requireNonNull(d, "diagnostics");
        Objects.requireNonNull(policy, "policy");
        Set<SkyIslandGeomorphicQualificationViolation> failures =
                EnumSet.noneOf(SkyIslandGeomorphicQualificationViolation.class);

        addIfAbove(failures, d.maximumCenterlineLoweringPotential(),
                policy.maxCenterlineLoweringPotential(),
                SkyIslandGeomorphicQualificationViolation.CENTERLINE_LOWERING);
        addIfAbove(failures, d.maximumIncisionToBankfullWidthRatio(),
                policy.maxIncisionToBankfullWidthRatio(),
                SkyIslandGeomorphicQualificationViolation.INCISION_TO_WIDTH);
        addIfAbove(failures, d.maximumLateralRecoveryGrade(),
                policy.maxLateralRecoveryGrade(),
                SkyIslandGeomorphicQualificationViolation.LATERAL_RECOVERY_GRADE);
        addIfAbove(failures, d.maximumContainmentDeficitToWaterDepthRatio(),
                policy.maxContainmentDeficitToWaterDepthRatio(),
                SkyIslandGeomorphicQualificationViolation.BANK_CONTAINMENT);
        addIfAbove(failures, d.maximumDepthToBankfullWidthRatio(),
                policy.maxDepthToBankfullWidthRatio(),
                SkyIslandGeomorphicQualificationViolation.DEPTH_TO_WIDTH);
        addIfAbove(failures, d.normalizedExcavationBurden(),
                policy.maxNormalizedExcavationBurden(),
                SkyIslandGeomorphicQualificationViolation.EXCAVATION_BURDEN);
        addIfAbove(failures, d.maximumCurvatureWidthRatio(),
                policy.maxCurvatureWidthRatio(),
                SkyIslandGeomorphicQualificationViolation.CURVATURE_TO_WIDTH);
        addIfAbove(failures, d.ridgeSampleFraction(),
                policy.maxRidgeSampleFraction(),
                SkyIslandGeomorphicQualificationViolation.RIDGE_OCCUPANCY);
        addIfAbove(failures, d.maximumLongitudinalGrade(),
                policy.maxLongitudinalGrade(),
                SkyIslandGeomorphicQualificationViolation.LONGITUDINAL_GRADE);
        addIfAbove(failures, d.maximumRawTerrainUphillStepPotential(),
                policy.maxRawTerrainUphillStepPotential(),
                SkyIslandGeomorphicQualificationViolation.RAW_TERRAIN_ASCENT);

        List<SkyIslandGeomorphicQualificationViolation> ordered =
                failures.stream().sorted(Comparator.comparing(Enum::ordinal)).toList();
        return new SkyIslandGeomorphicReachQualification(d, ordered);
    }

    private static void addIfAbove(
            Set<SkyIslandGeomorphicQualificationViolation> failures,
            double measured,
            double maximum,
            SkyIslandGeomorphicQualificationViolation violation) {
        if (measured > maximum + EPSILON) {
            failures.add(violation);
        }
    }
}
