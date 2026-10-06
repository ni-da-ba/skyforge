package io.github.nidaba.skyforge.world;

import java.util.List;

/** Scores route-wide longitudinal grade feasibility as a bounded centerline objective. */
@FunctionalInterface
interface SkyIslandCenterlineLongitudinalHeadFeasibility {
    /**
     * Returns finite, non-negative evidence for head-envelope or candidate bed-profile grade
     * conflicts on this centerline.
     *
     * <p>The evaluator is diagnostic/objective evidence only; downstream hydraulic solvers remain
     * the independent hard admission authority.
     */
    Score evaluate(List<SkyIslandLocalPosition> centerlinePoints);

    record Score(
            double maximumLocalEnvelopeConflictWorldUnits,
            double maximumSourceEndpointEnvelopeConflictWorldUnits,
            double maximumGradePropagationConflictWorldUnits,
            double maximumConfluenceCascadeGradeConflictWorldUnits,
            double integratedSquaredConflictWorldUnits,
            double maximumConfluenceCascadeGradeConflictStation) {
        public Score(
                double maximumLocalEnvelopeConflictWorldUnits,
                double maximumSourceEndpointEnvelopeConflictWorldUnits,
                double maximumGradePropagationConflictWorldUnits,
                double maximumConfluenceCascadeGradeConflictWorldUnits,
                double integratedSquaredConflictWorldUnits) {
            this(
                    maximumLocalEnvelopeConflictWorldUnits,
                    maximumSourceEndpointEnvelopeConflictWorldUnits,
                    maximumGradePropagationConflictWorldUnits,
                    maximumConfluenceCascadeGradeConflictWorldUnits,
                    integratedSquaredConflictWorldUnits,
                    Double.NaN);
        }

        public Score {
            if (!Double.isFinite(maximumLocalEnvelopeConflictWorldUnits)
                    || maximumLocalEnvelopeConflictWorldUnits < 0.0
                    || !Double.isFinite(maximumSourceEndpointEnvelopeConflictWorldUnits)
                    || maximumSourceEndpointEnvelopeConflictWorldUnits < 0.0
                    || !Double.isFinite(maximumGradePropagationConflictWorldUnits)
                    || maximumGradePropagationConflictWorldUnits < 0.0
                    || !Double.isFinite(maximumConfluenceCascadeGradeConflictWorldUnits)
                    || maximumConfluenceCascadeGradeConflictWorldUnits < 0.0
                    || !Double.isFinite(integratedSquaredConflictWorldUnits)
                    || integratedSquaredConflictWorldUnits < 0.0
                    || (!Double.isNaN(maximumConfluenceCascadeGradeConflictStation)
                            && (!Double.isFinite(maximumConfluenceCascadeGradeConflictStation)
                                    || maximumConfluenceCascadeGradeConflictStation < 0.0
                                    || maximumConfluenceCascadeGradeConflictStation > 1.0))) {
                throw new IllegalArgumentException(
                        "longitudinal feasibility scores must be finite and non-negative");
            }
        }

        static Score zero() {
            return new Score(0.0, 0.0, 0.0, 0.0, 0.0, Double.NaN);
        }
    }
}
