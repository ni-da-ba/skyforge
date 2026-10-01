package io.github.nidaba.skyforge.reference.sampling;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.kernel.field.ScalarField2;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

final class DeterministicGridSamplerTest {
    private final DeterministicGridSampler sampler = new DeterministicGridSampler();

    @Test
    void mapsInclusiveCoordinatesIntoCanonicalRows() {
        GridSpec grid = new GridSpec(-2.0, 2.0, -3.0, 3.0, 5, 7);
        ScalarField2 analytical = coordinate -> coordinate.x() - 2.0 * coordinate.z();
        ScalarGrid sampled = sampler.sample(analytical, grid, SamplingOrder.FORWARD);

        assertAll(
                () -> assertEquals(-2.0, grid.xAt(0)),
                () -> assertEquals(2.0, grid.xAt(4)),
                () -> assertEquals(-3.0, grid.zAt(0)),
                () -> assertEquals(3.0, grid.zAt(6)),
                () -> assertEquals(4.0, sampled.valueAt(0, 0)),
                () -> assertEquals(-4.0, sampled.valueAt(4, 6)),
                () -> assertEquals(0.0, sampled.valueAt(2, 3)));
    }

    @Test
    void everyTraversalProducesIdenticalRawValuesAndChecksum() {
        GridSpec grid = new GridSpec(-5.0, 7.0, -3.0, 11.0, 19, 13);
        ScalarField2 analytical = coordinate ->
                (coordinate.x() + coordinate.z()) * (coordinate.x() - 3.0 * coordinate.z());
        ScalarGrid expected = sampler.sample(analytical, grid, SamplingOrder.FORWARD);

        for (SamplingOrder order : SamplingOrder.values()) {
            ScalarGrid actual = sampler.sample(analytical, grid, order);
            assertTrue(expected.rawValuesEqual(actual), order.name());
            assertEquals(expected.sha256(), actual.sha256(), order.name());
        }
    }

    @Test
    void scalarGridIsDefensiveAndWritesAStableBinaryEnvelope() throws IOException {
        GridSpec grid = new GridSpec(0.0, 1.0, 0.0, 1.0, 2, 2);
        double[] source = {0.0, -0.0, 1.0, -2.0};
        ScalarGrid sampled = new ScalarGrid(grid, source);
        source[0] = 99.0;
        double[] copy = sampled.values();
        copy[2] = 99.0;
        ByteArrayOutputStream first = new ByteArrayOutputStream();
        ByteArrayOutputStream second = new ByteArrayOutputStream();
        sampled.writeCanonical(first);
        sampled.writeCanonical(second);

        assertAll(
                () -> assertEquals(0.0, sampled.valueAt(0, 0)),
                () -> assertEquals(1.0, sampled.valueAt(0, 1)),
                () -> assertEquals(first.toByteArray().length, second.toByteArray().length),
                () -> assertTrue(java.util.Arrays.equals(first.toByteArray(), second.toByteArray())),
                () -> assertEquals(64, sampled.sha256().length()),
                () -> assertNotEquals(
                        Double.doubleToRawLongBits(sampled.valueAt(0, 0)),
                        Double.doubleToRawLongBits(sampled.valueAt(1, 0))));
    }

    @Test
    void rejectsInvalidGridAndSampleValues() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new GridSpec(0.0, 0.0, 0.0, 1.0, 2, 2)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new GridSpec(0.0, 1.0, 0.0, 1.0, 1, 2)),
                () -> assertThrows(
                        ArithmeticException.class,
                        () -> new GridSpec(0.0, 1.0, 0.0, 1.0, 50_000, 50_000)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new ScalarGrid(
                                new GridSpec(0.0, 1.0, 0.0, 1.0, 2, 2),
                                new double[] {0.0, 1.0, Double.NaN, 3.0})),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> sampler.sample(null, new GridSpec(0.0, 1.0, 0.0, 1.0, 2, 2), SamplingOrder.FORWARD)));
    }
}
