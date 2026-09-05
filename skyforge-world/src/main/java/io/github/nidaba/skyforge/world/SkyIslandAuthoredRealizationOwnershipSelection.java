package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * AUTH-0048 deterministic ownership result across explicit authored-realization associations.
 *
 * <p>Conservative candidates, exact physical occupants, and native authored owners are distinct.
 * Candidate order is canonical AUTH-0046 association identity order and therefore carries no
 * ranking meaning.
 */
public record SkyIslandAuthoredRealizationOwnershipSelection(
        Coordinate3 worldPosition,
        List<SkyIslandAuthoredRealizationOwnershipCandidate> conservativeCandidates) {

    public SkyIslandAuthoredRealizationOwnershipSelection {
        worldPosition = Objects.requireNonNull(worldPosition, "worldPosition");
        Objects.requireNonNull(conservativeCandidates, "conservativeCandidates");

        ArrayList<SkyIslandAuthoredRealizationOwnershipCandidate> ordered =
                new ArrayList<>(conservativeCandidates.size());
        Set<String> associationTokens = new HashSet<>();
        for (SkyIslandAuthoredRealizationOwnershipCandidate candidate :
                conservativeCandidates) {
            candidate = Objects.requireNonNull(candidate, "ownership candidate");
            if (!associationTokens.add(candidate.association().canonicalToken())) {
                throw new IllegalArgumentException(
                        "ownership selection cannot contain duplicate associations");
            }
            validateWorldFrame(worldPosition, candidate);
            ordered.add(candidate);
        }
        ordered.sort(
                Comparator.comparing(
                        candidate -> candidate.association().canonicalToken()));
        conservativeCandidates = List.copyOf(ordered);
    }

    /** Candidates whose conservative associated volume bounds contain the world point. */
    public int conservativeCandidateCount() {
        return conservativeCandidates.size();
    }

    /** Exact compiled physical occupants, independent of native semantic ownership. */
    public List<SkyIslandAuthoredRealizationOwnershipCandidate> exactPhysicalOccupants() {
        return conservativeCandidates.stream()
                .filter(SkyIslandAuthoredRealizationOwnershipCandidate::physicalInterior)
                .toList();
    }

    /** Exact physical occupants whose current native semantic domain owns the point. */
    public List<SkyIslandAuthoredRealizationOwnershipCandidate> authoredOwners() {
        return conservativeCandidates.stream()
                .filter(SkyIslandAuthoredRealizationOwnershipCandidate::authoredOwned)
                .toList();
    }

    public SkyIslandAuthoredRealizationOwnershipStatus status() {
        int ownerCount = authoredOwners().size();
        if (ownerCount == 0) {
            return SkyIslandAuthoredRealizationOwnershipStatus.NONE;
        }
        if (ownerCount == 1) {
            return SkyIslandAuthoredRealizationOwnershipStatus.UNIQUE;
        }
        return SkyIslandAuthoredRealizationOwnershipStatus.AMBIGUOUS;
    }

    /** Returns the single authoritative owner only when ownership is unambiguous. */
    public Optional<SkyIslandAuthoredRealizationOwnershipCandidate> uniqueOwner() {
        List<SkyIslandAuthoredRealizationOwnershipCandidate> owners = authoredOwners();
        return owners.size() == 1 ? Optional.of(owners.get(0)) : Optional.empty();
    }

    private static void validateWorldFrame(
            Coordinate3 worldPosition,
            SkyIslandAuthoredRealizationOwnershipCandidate candidate) {
        var descriptor =
                candidate.association()
                        .realizedVolume()
                        .compiledVolume()
                        .descriptor();
        double expectedLocalX = worldPosition.x() - descriptor.centerX();
        double expectedLocalZ = worldPosition.z() - descriptor.centerZ();
        SkyIslandRealizedSubsurfacePosition realized = candidate.realizedPosition();

        if (Double.doubleToLongBits(expectedLocalX)
                        != Double.doubleToLongBits(realized.localX())
                || Double.doubleToLongBits(expectedLocalZ)
                        != Double.doubleToLongBits(realized.localZ())
                || Double.doubleToLongBits(worldPosition.y())
                        != Double.doubleToLongBits(realized.physicalY())) {
            throw new IllegalArgumentException(
                    "ownership candidate must match its AUTH-0046 world/local frame");
        }
    }
}
