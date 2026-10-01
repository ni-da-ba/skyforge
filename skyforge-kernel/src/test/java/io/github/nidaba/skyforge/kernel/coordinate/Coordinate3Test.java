package io.github.nidaba.skyforge.kernel.coordinate;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class Coordinate3Test {
    @Test
    void retainsFiniteComponents() {
        Coordinate3 coordinate = new Coordinate3(-12.5, 7.0, 48.25);

        assertAll(
                () -> assertEquals(-12.5, coordinate.x()),
                () -> assertEquals(7.0, coordinate.y()),
                () -> assertEquals(48.25, coordinate.z()));
    }

    @Test
    void rejectsNonFiniteComponents() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new Coordinate3(Double.NaN, 0.0, 0.0)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new Coordinate3(0.0, Double.POSITIVE_INFINITY, 0.0)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new Coordinate3(0.0, 0.0, Double.NEGATIVE_INFINITY)));
    }
}
