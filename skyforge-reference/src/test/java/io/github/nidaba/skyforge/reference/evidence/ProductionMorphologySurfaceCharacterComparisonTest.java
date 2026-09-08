package io.github.nidaba.skyforge.reference.evidence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.reference.sampling.SamplingOrder;
import io.github.nidaba.skyforge.reference.sampling.VolumeGridSpec;
import io.github.nidaba.skyforge.reference.volume.ProductionMorphologyVisualReviewCorpus;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

final class ProductionMorphologySurfaceCharacterComparisonTest {

    @Test
    void comparisonIsCandidateMinusBaselineWithoutAggregateClassification() {
        var massif = diagnostics("builtin-massif-medium-seed-skyforge");
        var tableland = diagnostics("builtin-tableland-medium-seed-skyforge");

        var comparison = ProductionMorphologySurfaceCharacterComparison.compare(
                "builtin-massif-medium-seed-skyforge",
                massif,
                "builtin-tableland-medium-seed-skyforge",
                tableland);

        assertEquals(
                tableland.gradientP90() - massif.gradientP90(),
                comparison.gradientP90Delta(),
                0.0);
        assertEquals(
                tableland.lag1MeanDifferenceNormalized()
                        - massif.lag1MeanDifferenceNormalized(),
                comparison.lag1MeanDifferenceNormalizedDelta(),
                0.0);
        assertEquals(
                tableland.window9P90RangeNormalized()
                        - massif.window9P90RangeNormalized(),
                comparison.window9P90RangeNormalizedDelta(),
                0.0);

        assertTrue(
                comparison.upperReliefRangeNormalizedDelta() != 0.0
                        || comparison.upperStandardDeviationNormalizedDelta() != 0.0
                        || comparison.gradientP90Delta() != 0.0
                        || comparison.window9P90RangeNormalizedDelta() != 0.0,
                "distinct family specimens should expose descriptive differences without classifying them");

        List<String> forbidden = List.of(
                "score", "walk", "plateau", "bench", "lumpy", "aesthetic", "pass", "fail", "better", "worse");
        for (Method method :
                ProductionMorphologySurfaceCharacterComparison.class.getDeclaredMethods()) {
            String name = method.getName().toLowerCase(Locale.ROOT);
            assertTrue(forbidden.stream().noneMatch(name::contains), method.getName());
        }
    }

    @Test
    void normalizedMassifScaleComparisonCollapsesToNumericalTolerance() {
        var medium = diagnostics("builtin-massif-medium-seed-skyforge");
        var large = diagnostics("builtin-massif-large-seed-skyforge");

        var comparison = ProductionMorphologySurfaceCharacterComparison.compare(
                "builtin-massif-medium-seed-skyforge",
                medium,
                "builtin-massif-large-seed-skyforge",
                large);

        for (double delta : deltas(comparison)) {
            assertEquals(0.0, delta, 1.0e-10);
        }
    }

    @Test
    void rejectsSameIdentityAndMalformedIds() {
        var massif = diagnostics("builtin-massif-medium-seed-skyforge");
        assertThrows(
                IllegalArgumentException.class,
                () -> ProductionMorphologySurfaceCharacterComparison.compare(
                        "builtin-massif-medium-seed-skyforge",
                        massif,
                        "builtin-massif-medium-seed-skyforge",
                        massif));
        assertThrows(
                IllegalArgumentException.class,
                () -> ProductionMorphologySurfaceCharacterComparison.compare(
                        "Bad Id",
                        massif,
                        "builtin-tableland-medium-seed-skyforge",
                        diagnostics("builtin-tableland-medium-seed-skyforge")));
    }

    private static double[] deltas(ProductionMorphologySurfaceCharacterComparison c) {
        return new double[] {
            c.upperReliefRangeNormalizedDelta(),
            c.upperStandardDeviationNormalizedDelta(),
            c.gradientP50Delta(),
            c.gradientP75Delta(),
            c.gradientP90Delta(),
            c.gradientP95Delta(),
            c.curvatureP50TimesRadiusDelta(),
            c.curvatureP75TimesRadiusDelta(),
            c.curvatureP90TimesRadiusDelta(),
            c.curvatureP95TimesRadiusDelta(),
            c.lag1MeanDifferenceNormalizedDelta(),
            c.lag2MeanDifferenceNormalizedDelta(),
            c.lag4MeanDifferenceNormalizedDelta(),
            c.lag8MeanDifferenceNormalizedDelta(),
            c.window3MedianRangeNormalizedDelta(),
            c.window3P90RangeNormalizedDelta(),
            c.window5MedianRangeNormalizedDelta(),
            c.window5P90RangeNormalizedDelta(),
            c.window9MedianRangeNormalizedDelta(),
            c.window9P90RangeNormalizedDelta()
        };
    }

    private static ProductionMorphologySurfaceCharacterDiagnostics diagnostics(String id) {
        ProductionMorphologyVisualReviewCorpus.Member member =
                ProductionMorphologyVisualReviewCorpus.members().stream()
                        .filter(candidate -> candidate.id().equals(id))
                        .findFirst()
                        .orElseThrow();
        var full = ProductionMorphologyVisualReviewCorpus.reviewGrid(member);
        var lightweight =
                new VolumeGridSpec(
                        full.minimumX(),
                        full.maximumX(),
                        full.minimumY(),
                        full.maximumY(),
                        full.minimumZ(),
                        full.maximumZ(),
                        49,
                        33,
                        49);
        SuspendedVolumeEvidence evidence =
                new SuspendedVolumeEvidenceGenerator()
                        .generate(
                                ProductionMorphologyVisualReviewCorpus.compile(member),
                                lightweight,
                                SamplingOrder.FORWARD);
        return ProductionMorphologySurfaceCharacterDiagnostics.measure(evidence);
    }
}
