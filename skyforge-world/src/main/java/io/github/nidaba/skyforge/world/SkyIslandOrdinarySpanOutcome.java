package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Solved or explicitly deferred evidence for one finite F3D ordinary span. */
public record SkyIslandOrdinarySpanOutcome(
        SkyIslandOrdinaryHydraulicSpan span,
        SkyIslandOrdinarySpanStatus status,
        Optional<SkyIslandHydraulicQpResult> solve,
        List<SkyIslandHydraulicGeometrySample> solvedSamples,
        Optional<SkyIslandGeomorphicMeasurements> measurements,
        List<SkyIslandGeomorphicQualificationViolation> violations,
        Optional<String> diagnostic) {

    public SkyIslandOrdinarySpanOutcome {
        span = Objects.requireNonNull(span, "span");
        status = Objects.requireNonNull(status, "status");
        solve = Objects.requireNonNull(solve, "solve");
        solvedSamples = List.copyOf(solvedSamples);
        measurements = Objects.requireNonNull(measurements, "measurements");
        violations = List.copyOf(violations);
        diagnostic = Objects.requireNonNull(diagnostic, "diagnostic");
        solvedSamples.forEach(value -> Objects.requireNonNull(value, "solved sample"));
        violations.forEach(value -> Objects.requireNonNull(value, "violation"));

        boolean solvedStatus =
                status == SkyIslandOrdinarySpanStatus.SOLVED_QUALIFIED
                        || status == SkyIslandOrdinarySpanStatus.SOLVED_REJECTED;
        if (solvedStatus) {
            if (solve.isEmpty()
                    || solve.orElseThrow().status() != SkyIslandHydraulicQpStatus.SOLVED
                    || solvedSamples.size() != span.samples().size()
                    || measurements.isEmpty()
                    || diagnostic.isPresent()) {
                throw new IllegalArgumentException(
                        "solved ordinary span requires complete QP/geometry/measurement evidence");
            }
            if ((status == SkyIslandOrdinarySpanStatus.SOLVED_QUALIFIED) != violations.isEmpty()) {
                throw new IllegalArgumentException(
                        "ordinary-span solved status must agree with D2 violations");
            }
        } else if (!solvedSamples.isEmpty()
                || measurements.isPresent()
                || !violations.isEmpty()) {
            throw new IllegalArgumentException(
                    "unsolved ordinary span cannot expose solved geometry or D2 measurements");
        }
        if (status == SkyIslandOrdinarySpanStatus.BOUNDARY_DEFERRED
                && !span.boundaryDeferred()) {
            throw new IllegalArgumentException(
                    "BOUNDARY_DEFERRED outcome requires a deferred span boundary");
        }
    }
}
