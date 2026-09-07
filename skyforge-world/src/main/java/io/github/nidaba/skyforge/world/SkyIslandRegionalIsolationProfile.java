package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * AUTH-0092 deterministic raw regional isolation evidence over one exact AUTH-0087 binding.
 *
 * <p>No ecological isolation class is produced. Distances remain world-horizontal planning
 * evidence attached to exact authored-realization provenance.
 */
public final class SkyIslandRegionalIsolationProfile {
    private final SkyIslandPublishedAuthoredRealizationBinding binding;
    private final List<SkyIslandRegionalIsolationEntry> islands;
    private final OptionalDouble minimumNearestCenterDistance;
    private final OptionalDouble meanNearestCenterDistance;
    private final OptionalDouble maximumNearestCenterDistance;
    private final OptionalDouble minimumNearestNominalRadialGap;
    private final OptionalDouble meanNearestNominalRadialGap;
    private final OptionalDouble maximumNearestNominalRadialGap;

    SkyIslandRegionalIsolationProfile(
            SkyIslandPublishedAuthoredRealizationBinding binding) {
        this.binding = Objects.requireNonNull(binding, "binding");
        List<SkyIslandAuthoredRealizationAssociation> associations =
                binding.associationCatalog().associations();

        ArrayList<SkyIslandRegionalIsolationEntry> entries =
                new ArrayList<>(associations.size());
        for (int index = 0; index < associations.size(); index++) {
            SkyIslandAuthoredRealizationAssociation subject = associations.get(index);
            Optional<SkyIslandRegionalIsolationNeighbor> nearest =
                    nearestNeighbor(subject, associations);
            entries.add(new SkyIslandRegionalIsolationEntry(subject, nearest));
        }
        this.islands = List.copyOf(entries);

        double[] center = entries.stream()
                .flatMap(entry -> entry.nearestNeighbor().stream())
                .mapToDouble(SkyIslandRegionalIsolationNeighbor::centerDistance)
                .toArray();
        double[] gap = entries.stream()
                .flatMap(entry -> entry.nearestNeighbor().stream())
                .mapToDouble(SkyIslandRegionalIsolationNeighbor::nominalRadialGap)
                .toArray();

        this.minimumNearestCenterDistance = minimum(center);
        this.meanNearestCenterDistance = mean(center);
        this.maximumNearestCenterDistance = maximum(center);
        this.minimumNearestNominalRadialGap = minimum(gap);
        this.meanNearestNominalRadialGap = mean(gap);
        this.maximumNearestNominalRadialGap = maximum(gap);
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
        return islands.size();
    }

    /** Canonical AUTH-0046 association order. */
    public List<SkyIslandRegionalIsolationEntry> islands() {
        return islands;
    }

    public OptionalDouble minimumNearestCenterDistance() {
        return minimumNearestCenterDistance;
    }

    public OptionalDouble meanNearestCenterDistance() {
        return meanNearestCenterDistance;
    }

    public OptionalDouble maximumNearestCenterDistance() {
        return maximumNearestCenterDistance;
    }

    public OptionalDouble minimumNearestNominalRadialGap() {
        return minimumNearestNominalRadialGap;
    }

    public OptionalDouble meanNearestNominalRadialGap() {
        return meanNearestNominalRadialGap;
    }

    public OptionalDouble maximumNearestNominalRadialGap() {
        return maximumNearestNominalRadialGap;
    }

    private static Optional<SkyIslandRegionalIsolationNeighbor> nearestNeighbor(
            SkyIslandAuthoredRealizationAssociation subject,
            List<SkyIslandAuthoredRealizationAssociation> associations) {
        SkyIslandRegionalIsolationNeighbor best = null;
        for (SkyIslandAuthoredRealizationAssociation candidate : associations) {
            if (candidate.equals(subject)) {
                continue;
            }
            double center =
                    SkyIslandRegionalIsolationEntry.centerDistance(subject, candidate);
            double gap =
                    SkyIslandRegionalIsolationEntry.nominalRadialGap(
                            subject, candidate, center);
            SkyIslandRegionalIsolationNeighbor current =
                    new SkyIslandRegionalIsolationNeighbor(candidate, center, gap);
            if (best == null || better(current, best)) {
                best = current;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Nearest means smallest nominal radial gap, then smallest center distance.
     *
     * <p>Exact remaining ties preserve the existing canonical association iteration order.
     */
    private static boolean better(
            SkyIslandRegionalIsolationNeighbor candidate,
            SkyIslandRegionalIsolationNeighbor best) {
        int gap = Double.compare(candidate.nominalRadialGap(), best.nominalRadialGap());
        if (gap != 0) {
            return gap < 0;
        }
        int center = Double.compare(candidate.centerDistance(), best.centerDistance());
        return center < 0;
    }

    private static OptionalDouble minimum(double[] values) {
        if (values.length == 0) {
            return OptionalDouble.empty();
        }
        double result = Double.POSITIVE_INFINITY;
        for (double value : values) {
            result = Math.min(result, value);
        }
        return OptionalDouble.of(result);
    }

    private static OptionalDouble maximum(double[] values) {
        if (values.length == 0) {
            return OptionalDouble.empty();
        }
        double result = Double.NEGATIVE_INFINITY;
        for (double value : values) {
            result = Math.max(result, value);
        }
        return OptionalDouble.of(result);
    }

    private static OptionalDouble mean(double[] values) {
        if (values.length == 0) {
            return OptionalDouble.empty();
        }
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return OptionalDouble.of(sum / values.length);
    }
}
