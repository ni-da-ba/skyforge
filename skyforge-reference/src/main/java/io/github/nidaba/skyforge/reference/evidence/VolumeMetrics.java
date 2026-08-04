package io.github.nidaba.skyforge.reference.evidence;

/** Deterministic measurements derived from a positive-density occupancy grid. */
public record VolumeMetrics(
        int solidSampleCount,
        int connectedSolidComponents,
        double estimatedSolidVolume,
        double solidCentroidX,
        double solidCentroidY,
        double solidCentroidZ,
        Bounds bounds,
        FaceContacts faceContacts,
        AirClearance airClearance) {
    /** Inclusive world-coordinate bounds of positive-density samples. */
    public record Bounds(
            double minimumX,
            double maximumX,
            double minimumY,
            double maximumY,
            double minimumZ,
            double maximumZ) {}

    /** Positive-density sample counts on each declared domain face. */
    public record FaceContacts(
            int minimumX,
            int maximumX,
            int minimumY,
            int maximumY,
            int minimumZ,
            int maximumZ) {
        /** Returns the sum of all face contacts; edge and corner samples count per face. */
        public int total() {
            return minimumX + maximumX + minimumY + maximumY + minimumZ + maximumZ;
        }
    }

    /** World-space distance from the solid sample bounds to each domain face. */
    public record AirClearance(
            double minimumX,
            double maximumX,
            double minimumY,
            double maximumY,
            double minimumZ,
            double maximumZ) {
        /** Returns the least declared face clearance. */
        public double minimum() {
            return Math.min(
                    Math.min(Math.min(minimumX, maximumX), Math.min(minimumY, maximumY)),
                    Math.min(minimumZ, maximumZ));
        }
    }
}
