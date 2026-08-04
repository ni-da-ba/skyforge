package io.github.nidaba.skyforge.kernel.field;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate2;
import org.junit.jupiter.api.Test;

final class ConstantScalarField2Test {
    private static final Coordinate2[] COORDINATES = {
        new Coordinate2(0.0, 0.0),
        new Coordinate2(-1.0, 1.0),
        new Coordinate2(Double.MAX_VALUE, -Double.MAX_VALUE),
        new Coordinate2(Double.MIN_VALUE, -Double.MIN_VALUE)
    };

    @Test
    void returnsIdenticalRawBitsAtEveryCoordinate() {
        ConstantScalarField2 field = new ConstantScalarField2(-0.0);
        long expectedBits = Double.doubleToRawLongBits(-0.0);

        for (Coordinate2 coordinate : COORDINATES) {
            assertEquals(expectedBits, Double.doubleToRawLongBits(field.sample(coordinate)));
        }
    }

    @Test
    void rejectsInvalidConstructionAndSampling() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new ConstantScalarField2(Double.NaN)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new ConstantScalarField2(Double.POSITIVE_INFINITY)),
                () -> assertThrows(NullPointerException.class, () -> new ConstantScalarField2(1.0).sample(null)));
    }
}
