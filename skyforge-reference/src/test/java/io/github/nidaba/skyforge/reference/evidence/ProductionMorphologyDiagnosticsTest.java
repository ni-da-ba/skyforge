package io.github.nidaba.skyforge.reference.evidence;

import static org.junit.jupiter.api.Assertions.*;

import io.github.nidaba.skyforge.reference.sampling.SamplingOrder;
import io.github.nidaba.skyforge.reference.sampling.VolumeGridSpec;
import io.github.nidaba.skyforge.reference.volume.ProductionMorphologyVisualReviewCorpus;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ProductionMorphologyDiagnosticsTest {

    @Test
    void diagnosticsAreFiniteForBuiltInHybridAndExternalProviderSamples() {
        List<ProductionMorphologyVisualReviewCorpus.Member> samples =
                List.of(
                        find(ProductionMorphologyVisualReviewCorpus.Kind.BUILT_IN),
                        find(ProductionMorphologyVisualReviewCorpus.Kind.BUILT_IN_HYBRID),
                        find(ProductionMorphologyVisualReviewCorpus.Kind.EXTERNAL_PROVIDER));

        for (ProductionMorphologyVisualReviewCorpus.Member member : samples) {
            var fullGrid = ProductionMorphologyVisualReviewCorpus.reviewGrid(member);
            var lightweightGrid =
                    new VolumeGridSpec(
                            fullGrid.minimumX(),
                            fullGrid.maximumX(),
                            fullGrid.minimumY(),
                            fullGrid.maximumY(),
                            fullGrid.minimumZ(),
                            fullGrid.maximumZ(),
                            49,
                            33,
                            49);
            var evidence =
                    new SuspendedVolumeEvidenceGenerator()
                            .generate(
                                    ProductionMorphologyVisualReviewCorpus.compile(member),
                                    lightweightGrid,
                                    SamplingOrder.FORWARD);
            ProductionMorphologyDiagnostics diagnostics =
                    ProductionMorphologyDiagnostics.measure(evidence);

            assertTrue(diagnostics.occupiedColumns() > 0, member.id());
            assertTrue(diagnostics.minimumThicknessNormalized() > 0.0, member.id());
            assertTrue(
                    diagnostics.maximumThicknessNormalized()
                            >= diagnostics.meanThicknessNormalized(),
                    member.id());
            assertTrue(
                    diagnostics.meanThicknessNormalized()
                            >= diagnostics.minimumThicknessNormalized(),
                    member.id());
            assertTrue(
                    diagnostics.halfTurnOccupancyMismatchFraction() >= 0.0
                            && diagnostics.halfTurnOccupancyMismatchFraction() <= 1.0,
                    member.id());
            assertTrue(
                    diagnostics.upperUndersidePearsonCorrelation() >= -1.0
                            && diagnostics.upperUndersidePearsonCorrelation() <= 1.0,
                    member.id());

            for (double value :
                    List.of(
                            diagnostics.minimumThicknessNormalized(),
                            diagnostics.fifthPercentileThicknessNormalized(),
                            diagnostics.meanThicknessNormalized(),
                            diagnostics.maximumThicknessNormalized(),
                            diagnostics.maximumNeighborThicknessJumpNormalized(),
                            diagnostics.meanUpperNeighborDifferenceNormalized(),
                            diagnostics.meanUndersideNeighborDifferenceNormalized(),
                            diagnostics.meanUpperSecondDifferenceNormalized(),
                            diagnostics.meanUndersideSecondDifferenceNormalized(),
                            diagnostics.halfTurnOccupancyMismatchFraction(),
                            diagnostics.meanHalfTurnThicknessDifferenceNormalized(),
                            diagnostics.upperUndersidePearsonCorrelation())) {
                assertTrue(Double.isFinite(value), member.id());
            }
        }
    }

    private static ProductionMorphologyVisualReviewCorpus.Member find(
            ProductionMorphologyVisualReviewCorpus.Kind kind) {
        return ProductionMorphologyVisualReviewCorpus.members().stream()
                .filter(member -> member.kind() == kind)
                .findFirst()
                .orElseThrow();
    }
}
