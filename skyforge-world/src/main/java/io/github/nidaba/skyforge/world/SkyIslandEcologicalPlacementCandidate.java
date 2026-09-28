package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * One deterministic backend-neutral ecological placement candidate.
 *
 * <p>The admission value is deterministic thinning provenance, not ecological support or random
 * runtime state.
 */
public record SkyIslandEcologicalPlacementCandidate(
        SkyIslandEcologicalCandidateLatticeProfile latticeProfile,
        long cellX,
        long cellZ,
        SkyIslandLocalPosition position,
        double admissionValue) {

    public SkyIslandEcologicalPlacementCandidate {
        latticeProfile = Objects.requireNonNull(latticeProfile, "latticeProfile");
        position = Objects.requireNonNull(position, "position");
        if (!Double.isFinite(admissionValue)
                || admissionValue < 0.0
                || admissionValue >= 1.0) {
            throw new IllegalArgumentException("admissionValue must be finite and in [0, 1)");
        }
    }
}
