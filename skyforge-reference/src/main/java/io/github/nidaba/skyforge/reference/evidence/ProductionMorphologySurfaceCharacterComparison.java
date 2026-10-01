package io.github.nidaba.skyforge.reference.evidence;

import java.util.Objects;

/**
 * AUTH-0101 signed threshold-free comparison of two accepted AUTH-0095 surface-character profiles.
 *
 * <p>Every delta is {@code candidate - baseline}. The record intentionally exposes no aggregate
 * score, threshold, classification, or aesthetic/traversal verdict.
 */
public record ProductionMorphologySurfaceCharacterComparison(
        String baselineMember,
        String candidateMember,
        double upperReliefRangeNormalizedDelta,
        double upperStandardDeviationNormalizedDelta,
        double gradientP50Delta,
        double gradientP75Delta,
        double gradientP90Delta,
        double gradientP95Delta,
        double curvatureP50TimesRadiusDelta,
        double curvatureP75TimesRadiusDelta,
        double curvatureP90TimesRadiusDelta,
        double curvatureP95TimesRadiusDelta,
        double lag1MeanDifferenceNormalizedDelta,
        double lag2MeanDifferenceNormalizedDelta,
        double lag4MeanDifferenceNormalizedDelta,
        double lag8MeanDifferenceNormalizedDelta,
        double window3MedianRangeNormalizedDelta,
        double window3P90RangeNormalizedDelta,
        double window5MedianRangeNormalizedDelta,
        double window5P90RangeNormalizedDelta,
        double window9MedianRangeNormalizedDelta,
        double window9P90RangeNormalizedDelta) {

    public ProductionMorphologySurfaceCharacterComparison {
        baselineMember = requireMemberId(baselineMember, "baselineMember");
        candidateMember = requireMemberId(candidateMember, "candidateMember");
        if (baselineMember.equals(candidateMember)) {
            throw new IllegalArgumentException("comparison requires two distinct member identities");
        }
        for (double value : new double[] {
            upperReliefRangeNormalizedDelta,
            upperStandardDeviationNormalizedDelta,
            gradientP50Delta,
            gradientP75Delta,
            gradientP90Delta,
            gradientP95Delta,
            curvatureP50TimesRadiusDelta,
            curvatureP75TimesRadiusDelta,
            curvatureP90TimesRadiusDelta,
            curvatureP95TimesRadiusDelta,
            lag1MeanDifferenceNormalizedDelta,
            lag2MeanDifferenceNormalizedDelta,
            lag4MeanDifferenceNormalizedDelta,
            lag8MeanDifferenceNormalizedDelta,
            window3MedianRangeNormalizedDelta,
            window3P90RangeNormalizedDelta,
            window5MedianRangeNormalizedDelta,
            window5P90RangeNormalizedDelta,
            window9MedianRangeNormalizedDelta,
            window9P90RangeNormalizedDelta
        }) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("surface-character comparison deltas must be finite");
            }
        }
    }

    public static ProductionMorphologySurfaceCharacterComparison compare(
            String baselineMember,
            ProductionMorphologySurfaceCharacterDiagnostics baseline,
            String candidateMember,
            ProductionMorphologySurfaceCharacterDiagnostics candidate) {
        Objects.requireNonNull(baseline, "baseline");
        Objects.requireNonNull(candidate, "candidate");
        return new ProductionMorphologySurfaceCharacterComparison(
                baselineMember,
                candidateMember,
                candidate.upperReliefRangeNormalized() - baseline.upperReliefRangeNormalized(),
                candidate.upperStandardDeviationNormalized()
                        - baseline.upperStandardDeviationNormalized(),
                candidate.gradientP50() - baseline.gradientP50(),
                candidate.gradientP75() - baseline.gradientP75(),
                candidate.gradientP90() - baseline.gradientP90(),
                candidate.gradientP95() - baseline.gradientP95(),
                candidate.curvatureP50TimesRadius() - baseline.curvatureP50TimesRadius(),
                candidate.curvatureP75TimesRadius() - baseline.curvatureP75TimesRadius(),
                candidate.curvatureP90TimesRadius() - baseline.curvatureP90TimesRadius(),
                candidate.curvatureP95TimesRadius() - baseline.curvatureP95TimesRadius(),
                candidate.lag1MeanDifferenceNormalized()
                        - baseline.lag1MeanDifferenceNormalized(),
                candidate.lag2MeanDifferenceNormalized()
                        - baseline.lag2MeanDifferenceNormalized(),
                candidate.lag4MeanDifferenceNormalized()
                        - baseline.lag4MeanDifferenceNormalized(),
                candidate.lag8MeanDifferenceNormalized()
                        - baseline.lag8MeanDifferenceNormalized(),
                candidate.window3MedianRangeNormalized()
                        - baseline.window3MedianRangeNormalized(),
                candidate.window3P90RangeNormalized()
                        - baseline.window3P90RangeNormalized(),
                candidate.window5MedianRangeNormalized()
                        - baseline.window5MedianRangeNormalized(),
                candidate.window5P90RangeNormalized()
                        - baseline.window5P90RangeNormalized(),
                candidate.window9MedianRangeNormalized()
                        - baseline.window9MedianRangeNormalized(),
                candidate.window9P90RangeNormalized()
                        - baseline.window9P90RangeNormalized());
    }

    private static String requireMemberId(String value, String name) {
        Objects.requireNonNull(value, name);
        if (!value.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw new IllegalArgumentException(name + " must be lowercase hyphenated ASCII");
        }
        return value;
    }
}
