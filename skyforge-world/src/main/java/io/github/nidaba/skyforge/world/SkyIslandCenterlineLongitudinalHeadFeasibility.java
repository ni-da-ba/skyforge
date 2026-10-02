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
    Score evaluate(List<SkyIslandLocalPosition> centerlinePoints);

    record Score(
            double maximumConflictWorldUnits,
            double integratedSquaredConflictWorldUnits) {
        Score {
            if (!Double.isFinite(maximumConflictWorldUnits)
                    || maximumConflictWorldUnits < 0.0
                    || !Double.isFinite(integratedSquaredConflictWorldUnits)
                    || integratedSquaredConflictWorldUnits < 0.0) {
                throw new IllegalArgumentException(
                        "longitudinal feasibility scores must be finite and non-negative");
            }
        }

        static Score zero() {
            return new Score(0.0, 0.0);
        }
    }
}
