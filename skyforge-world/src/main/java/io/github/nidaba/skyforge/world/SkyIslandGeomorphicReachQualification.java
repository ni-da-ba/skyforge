package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/** One D1 diagnostic record classified against an explicit D2 hard-safety policy. */
public record SkyIslandGeomorphicReachQualification(
        SkyIslandGeomorphicReachDiagnostics diagnostics,
        List<SkyIslandGeomorphicQualificationViolation> violations) {

    public SkyIslandGeomorphicReachQualification {
        diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
        violations = List.copyOf(violations);
        violations.forEach(v -> Objects.requireNonNull(v, "violation"));
    }

    public boolean accepted() {
        return violations.isEmpty();
    }
}
