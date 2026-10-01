package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Evidence outcome for one finite F3A confluence head-compatibility problem. */
public record SkyIslandConfluenceHeadCompatibilityOutcome(
        SkyIslandHydraulicConfluenceGeometryCandidate geometry,
        SkyIslandConfluenceHeadCompatibilityStatus status,
        double nodeLowerHeadWorldUnits,
        double nodeUpperHeadWorldUnits,
        Optional<SkyIslandHydraulicQpResult> solve,
        Optional<Double> nodeHeadWorldUnits,
        List<SkyIslandConfluenceLegHeadSolution> legSolutions,
        Optional<String> diagnostic) {

    public SkyIslandConfluenceHeadCompatibilityOutcome {
        geometry = Objects.requireNonNull(geometry, "geometry");
        status = Objects.requireNonNull(status, "status");
        requireFinite(nodeLowerHeadWorldUnits, "nodeLowerHeadWorldUnits");
        requireFinite(nodeUpperHeadWorldUnits, "nodeUpperHeadWorldUnits");
        solve = Objects.requireNonNull(solve, "solve");
        nodeHeadWorldUnits = Objects.requireNonNull(nodeHeadWorldUnits, "nodeHeadWorldUnits");
        legSolutions = List.copyOf(legSolutions);
        legSolutions.forEach(value -> Objects.requireNonNull(value, "leg solution"));
        diagnostic = Objects.requireNonNull(diagnostic, "diagnostic");

        if (status == SkyIslandConfluenceHeadCompatibilityStatus.SOLVED) {
            if (solve.isEmpty()
                    || nodeHeadWorldUnits.isEmpty()
                    || legSolutions.size() != geometry.legs().size()
                    || solve.orElseThrow().status() != SkyIslandHydraulicQpStatus.SOLVED) {
                throw new IllegalArgumentException(
                        "SOLVED confluence outcome requires complete QP and leg solution");
            }
        } else if (nodeHeadWorldUnits.isPresent() || !legSolutions.isEmpty()) {
            throw new IllegalArgumentException(
                    "unsolved confluence outcome cannot expose solved heads");
        }
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
