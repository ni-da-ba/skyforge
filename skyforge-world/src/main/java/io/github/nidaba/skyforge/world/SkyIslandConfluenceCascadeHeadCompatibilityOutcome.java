package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Evidence outcome for one confluence leg coupled to an authored CASCADE endpoint. */
public record SkyIslandConfluenceCascadeHeadCompatibilityOutcome(
        SkyIslandHydraulicConfluenceGeometryCandidate confluence,
        SkyIslandHydraulicCascadeGeometryCandidate cascade,
        SkyIslandHydraulicTransitionLegGeometry coupledLeg,
        SkyIslandConfluenceCascadeHeadCompatibilityStatus status,
        double nodeLowerHeadWorldUnits,
        double nodeUpperHeadWorldUnits,
        double authoredMaximumDropWorldUnits,
        Optional<SkyIslandHydraulicQpResult> solve,
        Optional<Double> sharedNodeHeadWorldUnits,
        Optional<Double> cascadeBoundaryHeadWorldUnits,
        Optional<Double> solvedDropWorldUnits,
        List<SkyIslandConfluenceLegHeadSolution> ordinaryLegSolutions,
        Optional<String> diagnostic) {

    public SkyIslandConfluenceCascadeHeadCompatibilityOutcome {
        confluence = Objects.requireNonNull(confluence, "confluence");
        cascade = Objects.requireNonNull(cascade, "cascade");
        coupledLeg = Objects.requireNonNull(coupledLeg, "coupledLeg");
        status = Objects.requireNonNull(status, "status");
        requireFinite(nodeLowerHeadWorldUnits, "nodeLowerHeadWorldUnits");
        requireFinite(nodeUpperHeadWorldUnits, "nodeUpperHeadWorldUnits");
        if (!Double.isFinite(authoredMaximumDropWorldUnits)
                || authoredMaximumDropWorldUnits < 0.0) {
            throw new IllegalArgumentException(
                    "authoredMaximumDropWorldUnits must be finite and non-negative");
        }
        solve = Objects.requireNonNull(solve, "solve");
        sharedNodeHeadWorldUnits = Objects.requireNonNull(
                sharedNodeHeadWorldUnits, "sharedNodeHeadWorldUnits");
        cascadeBoundaryHeadWorldUnits = Objects.requireNonNull(
                cascadeBoundaryHeadWorldUnits, "cascadeBoundaryHeadWorldUnits");
        solvedDropWorldUnits = Objects.requireNonNull(solvedDropWorldUnits, "solvedDropWorldUnits");
        ordinaryLegSolutions = List.copyOf(ordinaryLegSolutions);
        ordinaryLegSolutions.forEach(value -> Objects.requireNonNull(value, "ordinary leg solution"));
        diagnostic = Objects.requireNonNull(diagnostic, "diagnostic");

        if (status == SkyIslandConfluenceCascadeHeadCompatibilityStatus.SOLVED) {
            if (solve.isEmpty()
                    || solve.orElseThrow().status() != SkyIslandHydraulicQpStatus.SOLVED
                    || sharedNodeHeadWorldUnits.isEmpty()
                    || cascadeBoundaryHeadWorldUnits.isEmpty()
                    || solvedDropWorldUnits.isEmpty()
                    || ordinaryLegSolutions.size() != confluence.legs().size() - 1) {
                throw new IllegalArgumentException(
                        "SOLVED joint outcome requires complete node, cascade, and ordinary-leg evidence");
            }
            double measuredDrop = cascadeStartsAtNode(cascade, coupledLeg)
                    ? sharedNodeHeadWorldUnits.orElseThrow()
                            - cascadeBoundaryHeadWorldUnits.orElseThrow()
                    : cascadeBoundaryHeadWorldUnits.orElseThrow()
                            - sharedNodeHeadWorldUnits.orElseThrow();
            if (Math.abs(measuredDrop - solvedDropWorldUnits.orElseThrow()) > 1.0e-9
                    || measuredDrop < -1.0e-9
                    || measuredDrop > authoredMaximumDropWorldUnits + 1.0e-9) {
                throw new IllegalArgumentException(
                        "joint CASCADE drop escaped its authored boundary");
            }
        } else if (sharedNodeHeadWorldUnits.isPresent()
                || cascadeBoundaryHeadWorldUnits.isPresent()
                || solvedDropWorldUnits.isPresent()
                || !ordinaryLegSolutions.isEmpty()) {
            throw new IllegalArgumentException("unsolved joint outcome cannot expose solved heads");
        }
    }

    private static boolean cascadeStartsAtNode(
            SkyIslandHydraulicCascadeGeometryCandidate cascade,
            SkyIslandHydraulicTransitionLegGeometry leg) {
        return cascade.transitionSite().firstProfileIndex() == 0
                && leg.nodeBoundary().role() == SkyIslandHydraulicTransitionBoundaryRole.OUTGOING;
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
