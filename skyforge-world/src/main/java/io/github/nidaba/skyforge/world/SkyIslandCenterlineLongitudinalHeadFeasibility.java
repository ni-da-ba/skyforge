package io.github.nidaba.skyforge.world;

import java.util.List;

/** Scores route-wide feasibility of D2 head intervals under ordinary longitudinal grade limits. */
@FunctionalInterface
interface SkyIslandCenterlineLongitudinalHeadFeasibility {
    /**
     * Returns a finite non-negative measure of interval-propagation conflicts for this candidate.
     *
     * <p>The evaluator is diagnostic/objective evidence only; downstream ordinary-span and
     * component planners remain the independent hard admission authority.
     */
    double evaluate(List<SkyIslandLocalPosition> centerlinePoints);
}
