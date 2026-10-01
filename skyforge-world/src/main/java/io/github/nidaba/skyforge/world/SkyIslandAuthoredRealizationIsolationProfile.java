package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * AUTH-0103 raw association-catalog isolation evidence using exact AUTH-0092 distance semantics.
 *
 * <p>No ecological-isolation class, species suitability, carrying capacity, or population pressure
 * is derived. A singleton catalog legitimately has no nearest-neighbor evidence.
 */
public final class SkyIslandAuthoredRealizationIsolationProfile {
    private final SkyIslandAuthoredRealizationCatalog catalog;
    private final SkyIslandIsolationEvidenceAggregation.Result aggregate;

    SkyIslandAuthoredRealizationIsolationProfile(
            SkyIslandAuthoredRealizationCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.aggregate = SkyIslandIsolationEvidenceAggregation.aggregate(catalog.associations());
    }

    public SkyIslandAuthoredRealizationCatalog catalog() {
        return catalog;
    }

    public long authoredWorldSeed() {
        return catalog.authoredWorldSeed();
    }

    public int islandCount() {
        return aggregate.islands().size();
    }

    public List<SkyIslandRegionalIsolationEntry> islands() {
        return aggregate.islands();
    }

    public OptionalDouble minimumNearestCenterDistance() {
        return aggregate.minimumNearestCenterDistance();
    }

    public OptionalDouble meanNearestCenterDistance() {
        return aggregate.meanNearestCenterDistance();
    }

    public OptionalDouble maximumNearestCenterDistance() {
        return aggregate.maximumNearestCenterDistance();
    }

    public OptionalDouble minimumNearestNominalRadialGap() {
        return aggregate.minimumNearestNominalRadialGap();
    }

    public OptionalDouble meanNearestNominalRadialGap() {
        return aggregate.meanNearestNominalRadialGap();
    }

    public OptionalDouble maximumNearestNominalRadialGap() {
        return aggregate.maximumNearestNominalRadialGap();
    }
}
