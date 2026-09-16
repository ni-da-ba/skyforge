package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * AUTH-0092 deterministic raw regional isolation evidence over one exact AUTH-0087 binding.
 *
 * <p>The publication gate remains mandatory. Distance and tie semantics are shared with AUTH-0103's
 * explicit-association catalog profile so published and runtime-fixture evidence cannot diverge.
 */
public final class SkyIslandRegionalIsolationProfile {
    private final SkyIslandPublishedAuthoredRealizationBinding binding;
    private final SkyIslandIsolationEvidenceAggregation.Result aggregate;

    SkyIslandRegionalIsolationProfile(
            SkyIslandPublishedAuthoredRealizationBinding binding) {
        this.binding = Objects.requireNonNull(binding, "binding");
        this.aggregate = SkyIslandIsolationEvidenceAggregation.aggregate(
                binding.associationCatalog().associations());
    }

    public SkyIslandPublishedAuthoredRealizationBinding binding() {
        return binding;
    }

    public SkyIslandCompiledWorldPublicationId publicationId() {
        return binding.publication().id();
    }

    public long authoredWorldSeed() {
        return binding.authoredWorldSeed();
    }

    public int islandCount() {
        return aggregate.islands().size();
    }

    /** Canonical AUTH-0046 association order. */
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
