package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.Optional;

/** One canonical AUTH-0092 island entry with optional raw nearest-neighbor isolation evidence. */
public record SkyIslandRegionalIsolationEntry(
        SkyIslandAuthoredRealizationAssociation association,
        Optional<SkyIslandRegionalIsolationNeighbor> nearestNeighbor) {

    public SkyIslandRegionalIsolationEntry {
        association = Objects.requireNonNull(association, "association");
        nearestNeighbor = Objects.requireNonNull(nearestNeighbor, "nearestNeighbor");
        nearestNeighbor.ifPresent(neighbor -> {
            if (neighbor.association().equals(association)) {
                throw new IllegalArgumentException(
                        "regional isolation entry cannot use itself as nearest neighbor");
            }
            double expectedCenter = centerDistance(association, neighbor.association());
            double expectedGap = nominalRadialGap(
                    association, neighbor.association(), expectedCenter);
            if (Double.doubleToLongBits(expectedCenter)
                            != Double.doubleToLongBits(neighbor.centerDistance())
                    || Double.doubleToLongBits(expectedGap)
                            != Double.doubleToLongBits(neighbor.nominalRadialGap())) {
                throw new IllegalArgumentException(
                        "regional isolation neighbor distances must match exact association geometry");
            }
        });
    }

    public boolean hasNeighbor() {
        return nearestNeighbor.isPresent();
    }

    static double centerDistance(
            SkyIslandAuthoredRealizationAssociation first,
            SkyIslandAuthoredRealizationAssociation second) {
        var a = first.realizedVolume().compiledVolume().descriptor();
        var b = second.realizedVolume().compiledVolume().descriptor();
        return Math.hypot(b.centerX() - a.centerX(), b.centerZ() - a.centerZ());
    }

    static double nominalRadialGap(
            SkyIslandAuthoredRealizationAssociation first,
            SkyIslandAuthoredRealizationAssociation second,
            double centerDistance) {
        double radiusSum =
                first.authoredDescriptor().nominalRadius()
                        + second.authoredDescriptor().nominalRadius();
        return Math.max(0.0, centerDistance - radiusSum);
    }
}
