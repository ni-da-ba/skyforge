package io.github.nidaba.skyforge.kernel.coordinate;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class Coordinate2Test {
    @Test
    void retainsFiniteComponents() {
        Coordinate2 coordinate = new Coordinate2(-12.5, 48.25);

        assertAll(
                () -> assertEquals(-12.5, coordinate.x()),
                () -> assertEquals(48.25, coordinate.z()));
    }

    @Test
    void rejectsNonFiniteComponents() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new Coordinate2(Double.NaN, 0.0)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new Coordinate2(Double.POSITIVE_INFINITY, 0.0)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new Coordinate2(0.0, Double.NEGATIVE_INFINITY)));
    }
}
