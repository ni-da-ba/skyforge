package io.github.nidaba.skyforge.kernel.field;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate2;
import java.util.Objects;

/** An immutable two-dimensional scalar field with one finite value everywhere. */
public record ConstantScalarField2(double value) implements ScalarField2 {
    /**
     * Creates a constant field.
     *
     * @throws IllegalArgumentException if {@code value} is not finite
     */
    public ConstantScalarField2 {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("value must be finite");
        }
    }

    @Override
    public double sample(Coordinate2 coordinate) {
        Objects.requireNonNull(coordinate, "coordinate");
        return value;
    }
}
