package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/** Hard qualification result for one pre-authoring candidate reach. */
public record SkyIslandGeomorphicReachQualification(
        SkyIslandGeomorphicReachDiagnostics diagnostics,
        SkyIslandGeomorphicProfileLimits appliedLimits,
        List<SkyIslandGeomorphicQualificationViolation> violations) {

    public SkyIslandGeomorphicReachQualification {
        diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
        appliedLimits = Objects.requireNonNull(appliedLimits, "appliedLimits");
        violations = List.copyOf(violations);
        violations.forEach(v -> Objects.requireNonNull(v, "violation"));
    }

    public boolean accepted() {
        return violations.isEmpty();
    }
}
