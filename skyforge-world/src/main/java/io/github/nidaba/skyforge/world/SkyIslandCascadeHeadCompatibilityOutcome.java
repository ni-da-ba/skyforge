package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.Optional;

/** Evidence outcome for one finite F3A CASCADE interval. */
public record SkyIslandCascadeHeadCompatibilityOutcome(
        SkyIslandHydraulicCascadeGeometryCandidate geometry,
        SkyIslandCascadeHeadCompatibilityStatus status,
        double authoredMaximumDropWorldUnits,
        Optional<SkyIslandHydraulicQpResult> solve,
        Optional<Double> upstreamHeadWorldUnits,
        Optional<Double> downstreamHeadWorldUnits,
        Optional<Double> solvedDropWorldUnits,
        Optional<String> diagnostic) {

    public SkyIslandCascadeHeadCompatibilityOutcome {
        geometry = Objects.requireNonNull(geometry, "geometry");
        status = Objects.requireNonNull(status, "status");
        if (!Double.isFinite(authoredMaximumDropWorldUnits)
                || authoredMaximumDropWorldUnits < 0.0) {
            throw new IllegalArgumentException(
                    "authoredMaximumDropWorldUnits must be finite and non-negative");
        }
        solve = Objects.requireNonNull(solve, "solve");
        upstreamHeadWorldUnits = Objects.requireNonNull(upstreamHeadWorldUnits, "upstreamHeadWorldUnits");
        downstreamHeadWorldUnits = Objects.requireNonNull(downstreamHeadWorldUnits, "downstreamHeadWorldUnits");
        solvedDropWorldUnits = Objects.requireNonNull(solvedDropWorldUnits, "solvedDropWorldUnits");
        diagnostic = Objects.requireNonNull(diagnostic, "diagnostic");

        if (status == SkyIslandCascadeHeadCompatibilityStatus.SOLVED) {
            if (solve.isEmpty()
                    || solve.orElseThrow().status() != SkyIslandHydraulicQpStatus.SOLVED
                    || upstreamHeadWorldUnits.isEmpty()
                    || downstreamHeadWorldUnits.isEmpty()
                    || solvedDropWorldUnits.isEmpty()) {
                throw new IllegalArgumentException(
                        "SOLVED cascade outcome requires complete QP/head evidence");
            }
            double measured =
                    upstreamHeadWorldUnits.orElseThrow()
                            - downstreamHeadWorldUnits.orElseThrow();
            if (Math.abs(measured - solvedDropWorldUnits.orElseThrow()) > 1.0e-9) {
                throw new IllegalArgumentException(
                        "solved cascade drop must match boundary-head difference");
            }
            if (measured < -1.0e-9
                    || measured > authoredMaximumDropWorldUnits + 1.0e-9) {
                throw new IllegalArgumentException(
                        "solved cascade drop escaped authored discontinuity bound");
            }
        } else if (upstreamHeadWorldUnits.isPresent()
                || downstreamHeadWorldUnits.isPresent()
                || solvedDropWorldUnits.isPresent()) {
            throw new IllegalArgumentException(
                    "unsolved cascade outcome cannot expose solved heads");
        }
    }
}
