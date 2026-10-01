package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/** Hard qualification result for one continuous retained-water candidate. */
public record SkyIslandContinuousWaterbodyQualification(
        SkyIslandContinuousWaterbodyDiagnostics diagnostics,
        SkyIslandContinuousWaterbodyQualificationLimits appliedLimits,
        List<SkyIslandContinuousWaterbodyQualificationViolation> violations) {

    public SkyIslandContinuousWaterbodyQualification {
        diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
        appliedLimits = Objects.requireNonNull(appliedLimits, "appliedLimits");
        violations = List.copyOf(violations);
        violations.forEach(v -> Objects.requireNonNull(v, "violation"));
    }

    public boolean accepted() {
        return violations.isEmpty();
    }
}
