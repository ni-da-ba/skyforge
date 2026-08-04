package io.github.nidaba.skyforge.kernel.field;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate2;

/** A deterministic scalar-valued field over finite horizontal coordinates. */
@FunctionalInterface
public interface ScalarField2 {
    /**
     * Evaluates the field at {@code coordinate}.
     *
     * @param coordinate a finite coordinate
     * @return the field value
     * @throws NullPointerException if {@code coordinate} is {@code null}
     */
    double sample(Coordinate2 coordinate);
}
