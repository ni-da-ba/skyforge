package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * Finite half-open island-local X/Z window for deterministic ecological candidate queries.
 *
 * <p>Half-open boundaries let adjacent query tiles partition space without duplicating candidates
 * that lie exactly on a shared boundary.
 */
public record SkyIslandEcologicalCandidateQueryWindow(
        double minimumX,
        double maximumX,
        double minimumZ,
        double maximumZ) {

    public SkyIslandEcologicalCandidateQueryWindow {
        requireFinite("minimumX", minimumX);
        requireFinite("maximumX", maximumX);
        requireFinite("minimumZ", minimumZ);
        requireFinite("maximumZ", maximumZ);
        if (!(maximumX > minimumX)) {
            throw new IllegalArgumentException("maximumX must be greater than minimumX");
        }
        if (!(maximumZ > minimumZ)) {
            throw new IllegalArgumentException("maximumZ must be greater than minimumZ");
        }
    }

    /** Returns whether the exact local position lies inside this half-open window. */
    public boolean contains(SkyIslandLocalPosition position) {
        Objects.requireNonNull(position, "position");
        return position.x() >= minimumX
                && position.x() < maximumX
                && position.z() >= minimumZ
                && position.z() < maximumZ;
    }

    private static void requireFinite(String name, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
