package io.github.nidaba.skyforge.model.island;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class IslandDescriptorTest {
    @Test
    void preservesSemanticValuesAndAcceptsEverySeedPattern() {
        IslandDescriptor minimumSeed = descriptor(Long.MIN_VALUE, 0.25 * Math.PI);
        IslandDescriptor maximumSeed = descriptor(Long.MAX_VALUE, 0.25 * Math.PI);

        assertAll(
                () -> assertEquals(Long.MIN_VALUE, minimumSeed.seed()),
                () -> assertEquals(Long.MAX_VALUE, maximumSeed.seed()),
                () -> assertEquals(256.0, minimumSeed.nominalRadius()),
                () -> assertEquals(96.0, minimumSeed.maximumElevation()),
                () -> assertEquals(0.25 * Math.PI, minimumSeed.ridgeAzimuth()));
    }

    @Test
    void canonicalizesTheBidirectionalRidgeAxisToZeroInclusivePiExclusive() {
        assertAll(
                () -> assertEquals(0.5 * Math.PI, descriptor(1L, -0.5 * Math.PI).ridgeAzimuth()),
                () -> assertEquals(0.0, descriptor(1L, Math.PI).ridgeAzimuth()),
                () -> assertEquals(0.25 * Math.PI, descriptor(1L, 2.25 * Math.PI).ridgeAzimuth()),
                () -> assertEquals(
                        Double.doubleToRawLongBits(0.0),
                        Double.doubleToRawLongBits(descriptor(1L, -0.0).ridgeAzimuth())));
    }

    @Test
    void rejectsUnsupportedSchemaAndNonFiniteProperties() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new IslandDescriptor(
                        2, 1L, 0.0, 0.0, 256.0, 96.0, 32.0, 0.0, 0.5, 0.0, 16.0)),
                () -> assertThrows(IllegalArgumentException.class, () -> new IslandDescriptor(
                        1, 1L, Double.NaN, 0.0, 256.0, 96.0, 32.0, 0.0, 0.5, 0.0, 16.0)),
                () -> assertThrows(IllegalArgumentException.class, () -> new IslandDescriptor(
                        1, 1L, 0.0, Double.POSITIVE_INFINITY, 256.0, 96.0, 32.0, 0.0, 0.5, 0.0, 16.0)),
                () -> assertThrows(IllegalArgumentException.class, () -> new IslandDescriptor(
                        1, 1L, 0.0, 0.0, 256.0, 96.0, 32.0, Double.NaN, 0.5, 0.0, 16.0)),
                () -> assertThrows(IllegalArgumentException.class, () -> new IslandDescriptor(
                        1, 1L, 0.0, 0.0, 256.0, 96.0, 32.0, 0.0, 0.5, 0.0, Double.NaN)));
    }

    @Test
    void rejectsInvalidPositiveBoundedAndNormalizedProperties() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new IslandDescriptor(
                        1, 1L, 0.0, 0.0, 0.0, 96.0, 32.0, 0.0, 0.5, 0.0, 16.0)),
                () -> assertThrows(IllegalArgumentException.class, () -> new IslandDescriptor(
                        1, 1L, 0.0, 0.0, 256.0, -1.0, 32.0, 0.0, 0.5, 0.0, 16.0)),
                () -> assertThrows(IllegalArgumentException.class, () -> new IslandDescriptor(
                        1, 1L, 0.0, 0.0, 256.0, 96.0, 0.0, 0.0, 0.5, 0.0, 16.0)),
                () -> assertThrows(IllegalArgumentException.class, () -> new IslandDescriptor(
                        1, 1L, 0.0, 0.0, 256.0, 96.0, 257.0, 0.0, 0.5, 0.0, 16.0)),
                () -> assertThrows(IllegalArgumentException.class, () -> new IslandDescriptor(
                        1, 1L, 0.0, 0.0, 256.0, 96.0, 32.0, 0.0, -0.01, 0.0, 16.0)),
                () -> assertThrows(IllegalArgumentException.class, () -> new IslandDescriptor(
                        1, 1L, 0.0, 0.0, 256.0, 96.0, 32.0, 0.0, 0.5, 1.01, 16.0)),
                () -> assertThrows(IllegalArgumentException.class, () -> new IslandDescriptor(
                        1, 1L, 0.0, 0.0, 256.0, 96.0, 32.0, 0.0, 0.5, 0.0, -1.0)));
    }

    @Test
    void acceptsBoundaryValuesForNormalizedControlsAndFalloff() {
        assertAll(
                () -> assertDoesNotThrow(() -> new IslandDescriptor(
                        1, 1L, 0.0, 0.0, 256.0, 96.0, 256.0, 0.0, 0.0, 0.0, 16.0)),
                () -> assertDoesNotThrow(() -> new IslandDescriptor(
                        1, 1L, 0.0, 0.0, 256.0, 96.0, 256.0, 0.0, 1.0, 1.0, 16.0)));
    }

    private static IslandDescriptor descriptor(long seed, double azimuth) {
        return new IslandDescriptor(1, seed, 12.0, -8.0, 256.0, 96.0, 32.0, azimuth, 0.5, 0.0, 16.0);
    }
}
