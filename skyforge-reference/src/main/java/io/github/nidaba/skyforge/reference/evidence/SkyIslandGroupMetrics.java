package io.github.nidaba.skyforge.reference.evidence;

import java.util.List;

/** Numerical metrics for one realized multi-island group union. */
public record SkyIslandGroupMetrics(
        int memberCount,
        int solidSampleCount,
        int connectedComponents,
        int overlappingSolidSamples,
        int faceContacts,
        double minimumObservedCenterSpacing,
        double minimumReservedGap,
        Bounds bounds,
        List<Integer> memberSolidSampleCounts) {

    public SkyIslandGroupMetrics {
        if (memberCount <= 0 || solidSampleCount <= 0) {
            throw new IllegalArgumentException("group metrics require members and positive occupancy");
        }
        if (connectedComponents <= 0 || overlappingSolidSamples < 0 || faceContacts < 0) {
            throw new IllegalArgumentException("group topology metrics are invalid");
        }
        if (!Double.isFinite(minimumObservedCenterSpacing)
                || !Double.isFinite(minimumReservedGap)) {
            throw new IllegalArgumentException("group spacing metrics must be finite");
        }
        if (bounds == null || memberSolidSampleCounts == null) {
            throw new NullPointerException("group metric bounds/counts must not be null");
        }
        memberSolidSampleCounts = List.copyOf(memberSolidSampleCounts);
        if (memberSolidSampleCounts.size() != memberCount) {
            throw new IllegalArgumentException("member solid-count vector length differs from member count");
        }
    }

    /** Sampled world-space bounds of the realized union. */
    public record Bounds(
            double minimumX,
            double maximumX,
            double minimumY,
            double maximumY,
            double minimumZ,
            double maximumZ) {}
}
