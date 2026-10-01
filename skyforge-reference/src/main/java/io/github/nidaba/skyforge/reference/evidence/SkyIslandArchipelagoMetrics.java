package io.github.nidaba.skyforge.reference.evidence;

import java.util.List;
import java.util.Objects;

/** Numerical summary for one realized hierarchical archipelago. */
public record SkyIslandArchipelagoMetrics(
        int groupCount,
        int islandCount,
        int solidSampleCount,
        int connectedComponents,
        int overlappingSolidSamples,
        int crossGroupOverlappingSolidSamples,
        int faceContacts,
        double minimumObservedGroupGap,
        Bounds bounds,
        List<Integer> groupSolidSampleCounts,
        List<Integer> islandSolidSampleCounts) {

    public SkyIslandArchipelagoMetrics {
        if (groupCount <= 0 || islandCount <= 0 || solidSampleCount <= 0) {
            throw new IllegalArgumentException("archipelago counts must be positive");
        }
        if (connectedComponents <= 0
                || overlappingSolidSamples < 0
                || crossGroupOverlappingSolidSamples < 0
                || faceContacts < 0) {
            throw new IllegalArgumentException("archipelago topology metrics are invalid");
        }
        if (!Double.isFinite(minimumObservedGroupGap) || minimumObservedGroupGap < 0.0) {
            throw new IllegalArgumentException("minimumObservedGroupGap must be finite and non-negative");
        }
        Objects.requireNonNull(bounds, "bounds");
        groupSolidSampleCounts = List.copyOf(groupSolidSampleCounts);
        islandSolidSampleCounts = List.copyOf(islandSolidSampleCounts);
        if (groupSolidSampleCounts.size() != groupCount) {
            throw new IllegalArgumentException("group solid-count size differs from groupCount");
        }
        if (islandSolidSampleCounts.size() != islandCount) {
            throw new IllegalArgumentException("island solid-count size differs from islandCount");
        }
    }

    /** Realized occupied world bounds on the review grid. */
    public record Bounds(
            double minimumX,
            double maximumX,
            double minimumY,
            double maximumY,
            double minimumZ,
            double maximumZ) {}
}
