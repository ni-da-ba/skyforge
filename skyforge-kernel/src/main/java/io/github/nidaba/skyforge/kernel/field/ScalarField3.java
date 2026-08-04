package io.github.nidaba.skyforge.kernel.field;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate3;

/** A deterministic scalar-valued field over finite three-dimensional coordinates. */
@FunctionalInterface
public interface ScalarField3 {
    /**
     * Evaluates the field at {@code coordinate}.
     *
     * @param coordinate a finite coordinate
     * @return the field value
     * @throws NullPointerException if {@code coordinate} is {@code null}
     */
    double sample(Coordinate3 coordinate);
}
