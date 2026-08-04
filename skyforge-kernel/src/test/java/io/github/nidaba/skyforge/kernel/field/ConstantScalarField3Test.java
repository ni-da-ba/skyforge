package io.github.nidaba.skyforge.kernel.field;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate3;
import org.junit.jupiter.api.Test;

final class ConstantScalarField3Test {
    private static final Coordinate3[] COORDINATES = {
        new Coordinate3(0.0, 0.0, 0.0),
        new Coordinate3(-1.0, 2.0, 1.0),
        new Coordinate3(Double.MAX_VALUE, 0.0, -Double.MAX_VALUE),
        new Coordinate3(Double.MIN_VALUE, -Double.MIN_VALUE, 0.0)
    };

    @Test
    void returnsIdenticalRawBitsAtEveryCoordinate() {
        ConstantScalarField3 field = new ConstantScalarField3(-0.0);
        long expectedBits = Double.doubleToRawLongBits(-0.0);

        for (Coordinate3 coordinate : COORDINATES) {
            assertEquals(expectedBits, Double.doubleToRawLongBits(field.sample(coordinate)));
        }
    }

    @Test
    void rejectsInvalidConstructionAndSampling() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new ConstantScalarField3(Double.NaN)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new ConstantScalarField3(Double.NEGATIVE_INFINITY)),
                () -> assertThrows(NullPointerException.class, () -> new ConstantScalarField3(1.0).sample(null)));
    }
}
