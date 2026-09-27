package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/** F4E hard qualification of one directly projected association-specific water reach. */
public record SkyIslandWorldWaterReachQualification(
        SkyIslandWorldWaterReachDiagnostics diagnostics,
        double maximumLongitudinalGrade,
        List<SkyIslandWorldWaterQualificationViolation> violations) {

    public SkyIslandWorldWaterReachQualification {
        diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
        if (!Double.isFinite(maximumLongitudinalGrade)
                || maximumLongitudinalGrade <= 0.0) {
            throw new IllegalArgumentException(
                    "maximumLongitudinalGrade must be finite and positive");
        }
        violations = List.copyOf(violations);
        violations.forEach(value -> Objects.requireNonNull(value, "violation"));
    }

    public boolean accepted() {
        return violations.isEmpty();
    }
}
