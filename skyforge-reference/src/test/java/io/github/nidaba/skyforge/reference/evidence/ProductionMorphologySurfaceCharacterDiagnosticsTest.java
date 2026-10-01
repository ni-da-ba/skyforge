package io.github.nidaba.skyforge.reference.evidence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.reference.sampling.SamplingOrder;
import io.github.nidaba.skyforge.reference.sampling.VolumeGridSpec;
import io.github.nidaba.skyforge.reference.volume.ProductionMorphologyVisualReviewCorpus;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ProductionMorphologySurfaceCharacterDiagnosticsTest {

    @Test
    void distributionsAreFiniteOrderedAndThresholdFreeForBuiltInSamples() {
        for (ProductionMorphologyVisualReviewCorpus.Member member :
                representativeBuiltIns()) {
            var evidence = evidence(member);
            var d = ProductionMorphologySurfaceCharacterDiagnostics.measure(evidence);

            assertTrue(d.occupiedColumns() > 0, member.id());
            assertTrue(d.gradientSamples() > 0, member.id());
            assertTrue(d.window3Samples() > 0, member.id());
            assertTrue(d.window5Samples() > 0, member.id());
            assertTrue(d.window9Samples() > 0, member.id());

            assertOrdered(d.gradientP50(), d.gradientP75(), d.gradientP90(), d.gradientP95(), member.id());
            assertOrdered(
                    d.curvatureP50TimesRadius(),
                    d.curvatureP75TimesRadius(),
                    d.curvatureP90TimesRadius(),
                    d.curvatureP95TimesRadius(),
                    member.id());

            for (double value :
                    List.of(
                            d.upperReliefRangeNormalized(),
                            d.upperStandardDeviationNormalized(),
                            d.gradientP50(),
                            d.gradientP75(),
                            d.gradientP90(),
                            d.gradientP95(),
                            d.curvatureP50TimesRadius(),
                            d.curvatureP75TimesRadius(),
                            d.curvatureP90TimesRadius(),
                            d.curvatureP95TimesRadius(),
                            d.lag1MeanDifferenceNormalized(),
                            d.lag2MeanDifferenceNormalized(),
                            d.lag4MeanDifferenceNormalized(),
                            d.lag8MeanDifferenceNormalized(),
                            d.window3MedianRangeNormalized(),
                            d.window3P90RangeNormalized(),
                            d.window5MedianRangeNormalized(),
                            d.window5P90RangeNormalized(),
                            d.window9MedianRangeNormalized(),
                            d.window9P90RangeNormalized())) {
                assertTrue(Double.isFinite(value), member.id());
                assertTrue(value >= 0.0, member.id() + " -> " + value);
            }
        }
    }

    @Test
    void normalizedSurfaceCharacterIsScaleCovariantForOneExactMorphology() {
        var small = find("builtin-massif-small-seed-skyforge");
        var medium = find("builtin-massif-medium-seed-skyforge");
        var large = find("builtin-massif-large-seed-skyforge");

        var a = ProductionMorphologySurfaceCharacterDiagnostics.measure(evidence(small));
        var b = ProductionMorphologySurfaceCharacterDiagnostics.measure(evidence(medium));
        var c = ProductionMorphologySurfaceCharacterDiagnostics.measure(evidence(large));

        assertClose(a.upperReliefRangeNormalized(), b.upperReliefRangeNormalized());
        assertClose(b.upperReliefRangeNormalized(), c.upperReliefRangeNormalized());
        assertClose(a.gradientP90(), b.gradientP90());
        assertClose(b.gradientP90(), c.gradientP90());
        assertClose(a.curvatureP90TimesRadius(), b.curvatureP90TimesRadius());
        assertClose(b.curvatureP90TimesRadius(), c.curvatureP90TimesRadius());
        assertClose(a.lag8MeanDifferenceNormalized(), b.lag8MeanDifferenceNormalized());
        assertClose(b.lag8MeanDifferenceNormalized(), c.lag8MeanDifferenceNormalized());
        assertClose(a.window9MedianRangeNormalized(), b.window9MedianRangeNormalized());
        assertClose(b.window9MedianRangeNormalized(), c.window9MedianRangeNormalized());
    }

    @Test
    void auth0083CanonicalGridKeepsHorizontalSpacingAtOneTwentyFourthRadius() {
        for (ProductionMorphologyVisualReviewCorpus.Member member :
                ProductionMorphologyVisualReviewCorpus.members().stream()
                        .filter(m -> m.kind() == ProductionMorphologyVisualReviewCorpus.Kind.BUILT_IN)
                        .toList()) {
            var descriptor = ProductionMorphologyVisualReviewCorpus.descriptor(member);
            var grid = ProductionMorphologyVisualReviewCorpus.reviewGrid(member);
            assertEquals(
                    1.0 / 24.0,
                    grid.spacingX() / descriptor.nominalRadius(),
                    1.0e-15,
                    member.id());
            assertEquals(
                    1.0 / 24.0,
                    grid.spacingZ() / descriptor.nominalRadius(),
                    1.0e-15,
                    member.id());
        }
    }

    private static List<ProductionMorphologyVisualReviewCorpus.Member> representativeBuiltIns() {
        return List.of(
                find("builtin-massif-medium-seed-skyforge"),
                find("builtin-tableland-medium-seed-skyforge"),
                find("builtin-spine-medium-seed-skyforge"),
                find("builtin-basin-medium-seed-skyforge"),
                find("builtin-lobed-medium-seed-skyforge"));
    }

    private static ProductionMorphologyVisualReviewCorpus.Member find(String id) {
        return ProductionMorphologyVisualReviewCorpus.members().stream()
                .filter(member -> member.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private static SuspendedVolumeEvidence evidence(
            ProductionMorphologyVisualReviewCorpus.Member member) {
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
        return new SuspendedVolumeEvidenceGenerator()
                .generate(
                        ProductionMorphologyVisualReviewCorpus.compile(member),
                        lightweight,
                        SamplingOrder.FORWARD);
    }

    private static void assertOrdered(
            double p50, double p75, double p90, double p95, String member) {
        assertTrue(p50 <= p75, member);
        assertTrue(p75 <= p90, member);
        assertTrue(p90 <= p95, member);
    }

    private static void assertClose(double first, double second) {
        assertEquals(first, second, 1.0e-10);
    }
}
