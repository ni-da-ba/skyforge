package io.github.nidaba.skyforge.reference.evidence;

import io.github.nidaba.skyforge.reference.sampling.ScalarGrid;
import java.util.Objects;

/** Deterministic summary statistics evaluated in canonical row-major order. */
public record GridStatistics(double minimum, double maximum, double mean, int sampleCount) {
    /** Computes finite statistics with compensated summation. */
    public static GridStatistics from(ScalarGrid grid) {
        Objects.requireNonNull(grid, "grid");
        double[] values = grid.values();
        double minimum = values[0];
        double maximum = values[0];
        double sum = 0.0;
        double correction = 0.0;
        for (double value : values) {
            minimum = Math.min(minimum, value);
            maximum = Math.max(maximum, value);
            double adjusted = value - correction;
            double next = sum + adjusted;
            correction = (next - sum) - adjusted;
            sum = next;
        }
        double mean = sum / values.length;
        if (!Double.isFinite(mean)) {
            throw new IllegalArgumentException("grid mean is not finite");
        }
        return new GridStatistics(minimum, maximum, mean, values.length);
    }

    /** Validates a finite, nonempty statistical summary. */
    public GridStatistics {
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || !Double.isFinite(mean)) {
            throw new IllegalArgumentException("statistics must be finite");
        }
        if (minimum > maximum) {
            throw new IllegalArgumentException("minimum must not exceed maximum");
        }
        if (sampleCount <= 0) {
            throw new IllegalArgumentException("sampleCount must be positive");
        }
    }
}
