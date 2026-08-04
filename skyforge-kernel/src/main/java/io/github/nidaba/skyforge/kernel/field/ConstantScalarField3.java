package io.github.nidaba.skyforge.kernel.field;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate3;
import java.util.Objects;

/** An immutable three-dimensional scalar field with one finite value everywhere. */
public record ConstantScalarField3(double value) implements ScalarField3 {
    /**
     * Creates a constant field.
     *
     * @throws IllegalArgumentException if {@code value} is not finite
     */
    public ConstantScalarField3 {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("value must be finite");
        }
    }

    @Override
    public double sample(Coordinate3 coordinate) {
        Objects.requireNonNull(coordinate, "coordinate");
        return value;
    }
}
