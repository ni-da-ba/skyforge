package io.github.nidaba.skyforge.reference.evidence;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.island.IslandDescriptor;
import io.github.nidaba.skyforge.recipes.island.CompiledIsland;
import io.github.nidaba.skyforge.recipes.island.SignalFreeIslandRecipe;
import io.github.nidaba.skyforge.reference.sampling.GridSpec;
import io.github.nidaba.skyforge.reference.sampling.SamplingOrder;
import org.junit.jupiter.api.Test;

final class IslandEvidenceGeneratorTest {
    private static final double TOLERANCE = 1.0e-10;

    private final IslandEvidenceGenerator generator = new IslandEvidenceGenerator();
    private final SignalFreeIslandRecipe recipe = new SignalFreeIslandRecipe();

    @Test
    void producesClosedFiniteSignalFreeMorphologyEvidence() {
        IslandDescriptor descriptor = descriptor();
        GridSpec grid = centeredGrid(descriptor, 65);
        IslandEvidence evidence = generator.generate(
                recipe.compile(descriptor), grid, SamplingOrder.FORWARD);
        int center = grid.width() / 2;

        assertAll(
                () -> assertEquals(descriptor.maximumElevation(), evidence.height().valueAt(center, center), TOLERANCE),
                () -> assertTrue(evidence.heightStatistics().minimum() < 0.0),
                () -> assertEquals(descriptor.maximumElevation(), evidence.heightStatistics().maximum(), TOLERANCE),
                () -> assertTrue(evidence.slopeStatistics().minimum() >= 0.0),
                () -> assertTrue(evidence.slopeStatistics().maximum() > 0.0),
                () -> assertEquals(1, evidence.metrics().connectedLandComponents()),
                () -> assertEquals(0, evidence.metrics().boundaryLandSampleCount()),
                () -> assertEquals(descriptor.centerX(), evidence.metrics().landCentroidX(), grid.spacingX()),
                () -> assertEquals(descriptor.centerZ(), evidence.metrics().landCentroidZ(), grid.spacingZ()),
                () -> assertEquals(descriptor.centerZ(), evidence.eastWest().fixedCoordinate()),
                () -> assertEquals(descriptor.centerX(), evidence.northSouth().fixedCoordinate()),
                () -> assertEquals(
                        descriptor.maximumElevation(), evidence.eastWest().heightAt(center), TOLERANCE),
                () -> assertEquals(
                        descriptor.maximumElevation(), evidence.northSouth().heightAt(center), TOLERANCE));
    }

    @Test
    void numericalEvidenceIsIndependentOfEvaluationSchedule() {
        IslandDescriptor descriptor = descriptor();
        CompiledIsland compiled = recipe.compile(descriptor);
        GridSpec grid = centeredGrid(descriptor, 33);
        IslandEvidence expected = generator.generate(compiled, grid, SamplingOrder.FORWARD);

        for (SamplingOrder order : SamplingOrder.values()) {
            IslandEvidence actual = generator.generate(compiled, grid, order);
            assertAll(
                    order.name(),
                    () -> assertEquals(expected.height().sha256(), actual.height().sha256()),
                    () -> assertEquals(expected.landMask().sha256(), actual.landMask().sha256()),
                    () -> assertEquals(expected.slope().sha256(), actual.slope().sha256()),
                    () -> assertEquals(expected.heightStatistics(), actual.heightStatistics()),
                    () -> assertEquals(expected.slopeStatistics(), actual.slopeStatistics()),
                    () -> assertEquals(expected.metrics(), actual.metrics()),
                    () -> assertEquals(expected.eastWest().canonicalCsv(), actual.eastWest().canonicalCsv()),
                    () -> assertEquals(expected.northSouth().canonicalCsv(), actual.northSouth().canonicalCsv()));
        }
    }

    @Test
    void standardGridUsesMandatedBoundsAndResolution() {
        IslandDescriptor descriptor = descriptor();
        GridSpec grid = generator.standardGrid(descriptor);
        double halfWidth = 1.5 * descriptor.nominalRadius();

        assertAll(
                () -> assertEquals(descriptor.centerX() - halfWidth, grid.minimumX()),
                () -> assertEquals(descriptor.centerX() + halfWidth, grid.maximumX()),
                () -> assertEquals(descriptor.centerZ() - halfWidth, grid.minimumZ()),
                () -> assertEquals(descriptor.centerZ() + halfWidth, grid.maximumZ()),
                () -> assertEquals(1024, grid.width()),
                () -> assertEquals(1024, grid.height()));
    }

    private static GridSpec centeredGrid(IslandDescriptor descriptor, int resolution) {
        double halfWidth = 1.5 * descriptor.nominalRadius();
        return new GridSpec(
                descriptor.centerX() - halfWidth,
                descriptor.centerX() + halfWidth,
                descriptor.centerZ() - halfWidth,
                descriptor.centerZ() + halfWidth,
                resolution,
                resolution);
    }

    private static IslandDescriptor descriptor() {
        return new IslandDescriptor(
                IslandDescriptor.SCHEMA_VERSION,
                17L,
                10.0,
                -5.0,
                80.0,
                20.0,
                20.0,
                0.25 * Math.PI,
                0.5,
                0.0,
                20.0);
    }
}
