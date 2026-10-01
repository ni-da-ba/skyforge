package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.Optional;

/**
 * AUTH-0048 state of one conservatively bounded AUTH-0046 association at one world point.
 *
 * <p>A candidate may be inside conservative bounds without lying inside the exact compiled
 * physical volume. Exact physical membership is represented by a present semantic position.
 * Native authored ownership is stricter still and follows the current authoritative semantic
 * ownership domain.
 */
public record SkyIslandAuthoredRealizationOwnershipCandidate(
        SkyIslandAuthoredRealizationAssociation association,
        SkyIslandRealizedSubsurfacePosition realizedPosition,
        SkyIslandSubsurfacePosition semanticPosition,
        boolean authoredOwned) {

    public SkyIslandAuthoredRealizationOwnershipCandidate {
        association = Objects.requireNonNull(association, "association");
        realizedPosition = Objects.requireNonNull(realizedPosition, "realizedPosition");

        if (semanticPosition == null) {
            if (authoredOwned) {
                throw new IllegalArgumentException(
                        "non-physical ownership candidate cannot be authored-owned");
            }
        } else if (!realizedPosition
                .horizontalPosition()
                .equals(semanticPosition.surfacePosition())) {
            throw new IllegalArgumentException(
                    "physical and semantic candidate positions must share island-local X/Z");
        }
    }

    /** Whether the world point lies inside the exact associated compiled physical volume. */
    public boolean physicalInterior() {
        return semanticPosition != null;
    }

    public Optional<SkyIslandSubsurfacePosition> semantic() {
        return Optional.ofNullable(semanticPosition);
    }
}
