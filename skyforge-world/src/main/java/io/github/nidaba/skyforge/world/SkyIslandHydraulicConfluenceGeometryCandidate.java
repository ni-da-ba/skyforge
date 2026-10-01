package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/**
 * Finite confluence geometry hypothesis derived only from the existing local hydraulic scale.
 *
 * <p>Each incident leg retreats exactly one node-boundary bankfull half-width along its accepted C2
 * centerline. The unit-width hypothesis is diagnostic evidence, not frozen production policy.
 */
public record SkyIslandHydraulicConfluenceGeometryCandidate(
        SkyIslandHydraulicConfluenceTransitionSite transitionSite,
        List<SkyIslandHydraulicTransitionLegGeometry> legs,
        double maximumRetreatLength) {

    public SkyIslandHydraulicConfluenceGeometryCandidate {
        transitionSite = Objects.requireNonNull(transitionSite, "transitionSite");
        legs = List.copyOf(legs);
        if (legs.size() != transitionSite.boundaries().size()) {
            throw new IllegalArgumentException("every confluence boundary must have one finite leg");
        }
        legs.forEach(leg -> Objects.requireNonNull(leg, "leg"));
        if (!Double.isFinite(maximumRetreatLength) || maximumRetreatLength <= 0.0) {
            throw new IllegalArgumentException("maximumRetreatLength must be finite and positive");
        }
        double measured = legs.stream()
                .mapToDouble(SkyIslandHydraulicTransitionLegGeometry::retreatLength)
                .max()
                .orElseThrow();
        if (Math.abs(measured - maximumRetreatLength) > 1.0e-9) {
            throw new IllegalArgumentException("maximumRetreatLength must match transition legs");
        }
    }
}
