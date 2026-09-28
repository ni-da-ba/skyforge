package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/**
 * Immutable deterministic result of one ecological candidate-window query.
 *
 * <p>Results are canonically ordered by lattice cell X then Z and retain exact lattice/window
 * provenance.
 */
public record SkyIslandEcologicalCandidateWindowResult(
        SkyIslandEcologicalCandidateLatticeProfile latticeProfile,
        SkyIslandEcologicalCandidateQueryWindow window,
        List<SkyIslandEcologicalPlacementCandidate> candidates) {

    public SkyIslandEcologicalCandidateWindowResult {
        latticeProfile = Objects.requireNonNull(latticeProfile, "latticeProfile");
        window = Objects.requireNonNull(window, "window");
        Objects.requireNonNull(candidates, "candidates");
        candidates = List.copyOf(candidates);

        long previousX = 0L;
        long previousZ = 0L;
        boolean first = true;
        for (SkyIslandEcologicalPlacementCandidate candidate : candidates) {
            candidate = Objects.requireNonNull(candidate, "candidate");
            if (!latticeProfile.equals(candidate.latticeProfile())) {
                throw new IllegalArgumentException(
                        "candidate lattice provenance must match query lattice profile");
            }
            if (!window.contains(candidate.position())) {
                throw new IllegalArgumentException(
                        "candidate position must lie inside the exact half-open query window");
            }
            if (!latticeProfile.candidate(candidate.cellX(), candidate.cellZ()).equals(candidate)) {
                throw new IllegalArgumentException(
                        "candidate must equal the canonical lattice candidate for its cell");
            }
            if (!first
                    && (candidate.cellX() < previousX
                            || candidate.cellX() == previousX
                                    && candidate.cellZ() <= previousZ)) {
                throw new IllegalArgumentException(
                        "candidates must be strictly ordered by cellX then cellZ");
            }
            previousX = candidate.cellX();
            previousZ = candidate.cellZ();
            first = false;
        }
    }
}
