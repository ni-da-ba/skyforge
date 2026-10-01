package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Applies strict non-climbing and existing D2 longitudinal-grade limits to F4E diagnostics. */
public final class SkyIslandWorldWaterReachQualificationEvaluator {
    private static final double EPSILON = 1.0e-9;

    private SkyIslandWorldWaterReachQualificationEvaluator() {}

    public static SkyIslandWorldWaterReachQualification evaluate(
            SkyIslandWorldWaterReachDiagnostics diagnostics,
            SkyIslandGeomorphicQualificationPolicy policy) {
        Objects.requireNonNull(diagnostics, "diagnostics");
        Objects.requireNonNull(policy, "policy");

        double limit = policy.limits(
                        diagnostics.reach().geomorphicRoute().semanticReach())
                .maximumLongitudinalGrade();
        List<SkyIslandWorldWaterQualificationViolation> violations =
                new ArrayList<>();
        if (diagnostics.uphillSegments() > 0
                || diagnostics.maximumUpclimbWorld() > EPSILON) {
            violations.add(SkyIslandWorldWaterQualificationViolation.UPHILL_HEAD);
        }
        if (diagnostics.maximumAbsoluteGrade() > limit + EPSILON) {
            violations.add(
                    SkyIslandWorldWaterQualificationViolation.LONGITUDINAL_GRADE);
        }
        return new SkyIslandWorldWaterReachQualification(
                diagnostics, limit, violations);
    }
}
