package io.github.nidaba.skyforge.reference.evidence;

/** Measured morphology facts used by the island acceptance gates. */
public record IslandMetrics(
        int landSampleCount,
        int connectedLandComponents,
        int boundaryLandSampleCount,
        double estimatedLandArea,
        double landCentroidX,
        double landCentroidZ) {
    /** Validates a nonempty finite metric set. */
    public IslandMetrics {
        if (landSampleCount <= 0) {
            throw new IllegalArgumentException("landSampleCount must be positive");
        }
        if (connectedLandComponents <= 0) {
            throw new IllegalArgumentException("connectedLandComponents must be positive");
        }
        if (boundaryLandSampleCount < 0 || boundaryLandSampleCount > landSampleCount) {
            throw new IllegalArgumentException("boundaryLandSampleCount is invalid");
        }
        if (!Double.isFinite(estimatedLandArea) || estimatedLandArea <= 0.0) {
            throw new IllegalArgumentException("estimatedLandArea must be finite and positive");
        }
        if (!Double.isFinite(landCentroidX) || !Double.isFinite(landCentroidZ)) {
            throw new IllegalArgumentException("land centroid must be finite");
        }
    }
}
