package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/** Shared exact AUTH-0092 nearest-neighbor isolation evidence semantics. */
final class SkyIslandIsolationEvidenceAggregation {
    private SkyIslandIsolationEvidenceAggregation() {}

    static Result aggregate(List<SkyIslandAuthoredRealizationAssociation> associations) {
        ArrayList<SkyIslandRegionalIsolationEntry> entries =
                new ArrayList<>(associations.size());
        for (SkyIslandAuthoredRealizationAssociation subject : associations) {
            entries.add(new SkyIslandRegionalIsolationEntry(
                    subject,
                    nearestNeighbor(subject, associations)));
        }
        List<SkyIslandRegionalIsolationEntry> frozen = List.copyOf(entries);
        double[] center = frozen.stream()
                .flatMap(entry -> entry.nearestNeighbor().stream())
                .mapToDouble(SkyIslandRegionalIsolationNeighbor::centerDistance)
                .toArray();
        double[] gap = frozen.stream()
                .flatMap(entry -> entry.nearestNeighbor().stream())
                .mapToDouble(SkyIslandRegionalIsolationNeighbor::nominalRadialGap)
                .toArray();
        return new Result(
                frozen,
                minimum(center),
                mean(center),
                maximum(center),
                minimum(gap),
                mean(gap),
                maximum(gap));
    }

    private static Optional<SkyIslandRegionalIsolationNeighbor> nearestNeighbor(
            SkyIslandAuthoredRealizationAssociation subject,
            List<SkyIslandAuthoredRealizationAssociation> associations) {
        SkyIslandRegionalIsolationNeighbor best = null;
        for (SkyIslandAuthoredRealizationAssociation candidate : associations) {
            if (candidate.equals(subject)) {
                continue;
            }
            double center = SkyIslandRegionalIsolationEntry.centerDistance(subject, candidate);
            double gap = SkyIslandRegionalIsolationEntry.nominalRadialGap(subject, candidate, center);
            SkyIslandRegionalIsolationNeighbor current =
                    new SkyIslandRegionalIsolationNeighbor(candidate, center, gap);
            if (best == null || better(current, best)) {
                best = current;
            }
        }
        return Optional.ofNullable(best);
    }

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

    record Result(
            List<SkyIslandRegionalIsolationEntry> islands,
            OptionalDouble minimumNearestCenterDistance,
            OptionalDouble meanNearestCenterDistance,
            OptionalDouble maximumNearestCenterDistance,
            OptionalDouble minimumNearestNominalRadialGap,
            OptionalDouble meanNearestNominalRadialGap,
            OptionalDouble maximumNearestNominalRadialGap) {}
}
