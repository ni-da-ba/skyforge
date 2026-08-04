package io.github.nidaba.skyforge.reference.sampling;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class VolumeGridSpecTest {
    @Test
    void includesEveryEndpointAndDeclaresExactSpacing() {
        VolumeGridSpec grid = new VolumeGridSpec(-4.0, 4.0, 2.0, 8.0, -6.0, 6.0, 5, 4, 7);

        assertAll(
                () -> assertEquals(-4.0, grid.xAt(0)),
                () -> assertEquals(4.0, grid.xAt(4)),
                () -> assertEquals(2.0, grid.yAt(0)),
                () -> assertEquals(8.0, grid.yAt(3)),
                () -> assertEquals(-6.0, grid.zAt(0)),
                () -> assertEquals(6.0, grid.zAt(6)),
                () -> assertEquals(2.0, grid.spacingX()),
                () -> assertEquals(2.0, grid.spacingY()),
                () -> assertEquals(2.0, grid.spacingZ()),
                () -> assertEquals(140, grid.sampleCount()));
    }

    @Test
    void canonicalIndexIncrementsXThenZThenY() {
        VolumeGridSpec grid = new VolumeGridSpec(0.0, 1.0, 0.0, 1.0, 0.0, 1.0, 3, 2, 4);

        assertAll(
                () -> assertEquals(0, grid.linearIndex(0, 0, 0)),
                () -> assertEquals(1, grid.linearIndex(1, 0, 0)),
                () -> assertEquals(3, grid.linearIndex(0, 0, 1)),
                () -> assertEquals(12, grid.linearIndex(0, 1, 0)),
                () -> assertEquals(23, grid.linearIndex(2, 1, 3)));
    }

    @Test
    void rejectsInvalidBoundsDimensionsOverflowAndIndices() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new VolumeGridSpec(
                        0.0, 0.0, 0.0, 1.0, 0.0, 1.0, 2, 2, 2)),
                () -> assertThrows(IllegalArgumentException.class, () -> new VolumeGridSpec(
                        0.0, 1.0, 0.0, Double.NaN, 0.0, 1.0, 2, 2, 2)),
                () -> assertThrows(IllegalArgumentException.class, () -> new VolumeGridSpec(
                        0.0, 1.0, 0.0, 1.0, 0.0, 1.0, 1, 2, 2)),
                () -> assertThrows(ArithmeticException.class, () -> new VolumeGridSpec(
                        0.0, 1.0, 0.0, 1.0, 0.0, 1.0, 50_000, 50_000, 50_000)),
                () -> assertThrows(
                        IndexOutOfBoundsException.class,
                        () -> new VolumeGridSpec(0.0, 1.0, 0.0, 1.0, 0.0, 1.0, 2, 2, 2)
                                .linearIndex(2, 0, 0)));
    }
}
