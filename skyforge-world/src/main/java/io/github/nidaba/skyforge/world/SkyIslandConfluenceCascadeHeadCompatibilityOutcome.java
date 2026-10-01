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
        Optional<Double> cascadeConfluenceSideHeadWorldUnits,
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
        cascadeConfluenceSideHeadWorldUnits = Objects.requireNonNull(
                cascadeConfluenceSideHeadWorldUnits, "cascadeConfluenceSideHeadWorldUnits");
        cascadeBoundaryHeadWorldUnits = Objects.requireNonNull(
                cascadeBoundaryHeadWorldUnits, "cascadeBoundaryHeadWorldUnits");
        solvedDropWorldUnits = Objects.requireNonNull(solvedDropWorldUnits, "solvedDropWorldUnits");
        ordinaryLegSolutions = List.copyOf(ordinaryLegSolutions);
        ordinaryLegSolutions.forEach(value -> Objects.requireNonNull(value, "ordinary leg solution"));
        diagnostic = Objects.requireNonNull(diagnostic, "diagnostic");

        if (status == SkyIslandConfluenceCascadeHeadCompatibilityStatus.SOLVED) {
            SkyIslandHydraulicTransitionBoundaryState cascadeAtConfluence =
                    coupledLeg.nodeBoundary().role()
                                    == SkyIslandHydraulicTransitionBoundaryRole.OUTGOING
                            ? cascade.transitionSite().upstreamBoundary()
                            : cascade.transitionSite().downstreamBoundary();
            boolean touchesNode = sameLocation(cascadeAtConfluence, coupledLeg.nodeBoundary());
            boolean crossesFiniteBoundary =
                    cascade.transitionSite().upstreamBoundary().arcLength()
                                    <= coupledLeg.finiteBoundary().arcLength() + 1.0e-9
                            && cascade.transitionSite().downstreamBoundary().arcLength()
                                    >= coupledLeg.finiteBoundary().arcLength() - 1.0e-9;
            if (solve.isEmpty()
                    || solve.orElseThrow().status() != SkyIslandHydraulicQpStatus.SOLVED
                    || sharedNodeHeadWorldUnits.isEmpty()
                    || cascadeConfluenceSideHeadWorldUnits.isPresent() == touchesNode
                    || cascadeBoundaryHeadWorldUnits.isEmpty()
                    || solvedDropWorldUnits.isEmpty()
                    || !crossesFiniteBoundary) {
                throw new IllegalArgumentException(
                        "SOLVED joint outcome requires crossing geometry and complete head evidence");
            }
            if (ordinaryLegSolutions.size() != confluence.legs().size() - 1) {
                throw new IllegalArgumentException(
                        "SOLVED joint outcome requires all uncoupled ordinary-leg head evidence");
            }
            double nearHead = touchesNode
                    ? sharedNodeHeadWorldUnits.orElseThrow()
                    : cascadeConfluenceSideHeadWorldUnits.orElseThrow();
            double remoteHead = cascadeBoundaryHeadWorldUnits.orElseThrow();
            boolean cascadeUpstreamAtConfluence = coupledLeg.nodeBoundary().role()
                    == SkyIslandHydraulicTransitionBoundaryRole.OUTGOING;
            double measuredDrop = cascadeUpstreamAtConfluence
                    ? nearHead - remoteHead
                    : remoteHead - nearHead;
            if (Math.abs(measuredDrop - solvedDropWorldUnits.orElseThrow()) > 1.0e-9
                    || measuredDrop < -1.0e-9
                    || measuredDrop > authoredMaximumDropWorldUnits + 1.0e-9) {
                throw new IllegalArgumentException(
                        "joint CASCADE drop escaped its authored boundary");
            }
        } else if (sharedNodeHeadWorldUnits.isPresent()
                || cascadeConfluenceSideHeadWorldUnits.isPresent()
                || cascadeBoundaryHeadWorldUnits.isPresent()
                || solvedDropWorldUnits.isPresent()
                || !ordinaryLegSolutions.isEmpty()) {
            throw new IllegalArgumentException("unsolved joint outcome cannot expose solved heads");
        }
    }

    private static boolean sameLocation(
            SkyIslandHydraulicTransitionBoundaryState first,
            SkyIslandHydraulicTransitionBoundaryState second) {
        return Math.abs(first.stationFraction() - second.stationFraction()) <= 1.0e-9
                && Math.abs(first.arcLength() - second.arcLength()) <= 1.0e-9
                && Math.abs(first.position().x() - second.position().x()) <= 1.0e-9
                && Math.abs(first.position().z() - second.position().z()) <= 1.0e-9;
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
