package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * AUTH-0099 exact cave-node evidence paired with the nearest active AUTH-0033 host-planning cell.
 *
 * <p>The nearest host is coarse surrounding-host evidence. It is not a claim that the cave center
 * itself is solid material or that a concrete mine/resource deposit exists there.
 */
public record SkyIslandCaveSiteNodeEvidence(
        SkyIslandCaveNode sourceNode,
        SkyIslandGeologySample geology,
        SkyIslandMaterialFamilyCell nearestHostCell,
        double normalizedHostDistance) {

    public SkyIslandCaveSiteNodeEvidence {
        sourceNode = Objects.requireNonNull(sourceNode, "sourceNode");
        geology = Objects.requireNonNull(geology, "geology");
        nearestHostCell = Objects.requireNonNull(nearestHostCell, "nearestHostCell");
        if (!geology.owned()) {
            throw new IllegalArgumentException(
                    "cave-node site evidence requires accepted owned geology");
        }
        if (!Double.isFinite(normalizedHostDistance) || normalizedHostDistance < 0.0) {
            throw new IllegalArgumentException(
                    "normalizedHostDistance must be finite and non-negative");
        }
    }
}
