package io.github.nidaba.skyforge.reference.evidence;

import io.github.nidaba.skyforge.reference.sampling.GridSpec;
import io.github.nidaba.skyforge.reference.sampling.ScalarGrid;
import java.util.Arrays;
import java.util.Objects;

/**
 * AUTH-0095 threshold-free upper-surface character diagnostics for the exact AUTH-0083 review grid.
 *
 * <p>The measurements describe distributions and normalized spatial scales. They deliberately do not
 * classify terrain as walkable, flat, plateau, bench, lumpy, or aesthetically acceptable.
 */
public record ProductionMorphologySurfaceCharacterDiagnostics(
        int occupiedColumns,
        int gradientSamples,
        int window3Samples,
        int window5Samples,
        int window9Samples,
        double upperReliefRangeNormalized,
        double upperStandardDeviationNormalized,
        double gradientP50,
        double gradientP75,
        double gradientP90,
        double gradientP95,
        double curvatureP50TimesRadius,
        double curvatureP75TimesRadius,
        double curvatureP90TimesRadius,
        double curvatureP95TimesRadius,
        double lag1MeanDifferenceNormalized,
        double lag2MeanDifferenceNormalized,
        double lag4MeanDifferenceNormalized,
        double lag8MeanDifferenceNormalized,
        double window3MedianRangeNormalized,
        double window3P90RangeNormalized,
        double window5MedianRangeNormalized,
        double window5P90RangeNormalized,
        double window9MedianRangeNormalized,
        double window9P90RangeNormalized) {

    /** Measures one already-generated suspended-volume specimen without adding thresholds. */
    public static ProductionMorphologySurfaceCharacterDiagnostics measure(
            SuspendedVolumeEvidence evidence) {
        Objects.requireNonNull(evidence, "evidence");
        ScalarGrid upper = evidence.upperSurface();
        ScalarGrid underside = evidence.undersideSurface();
        if (!upper.specification().equals(underside.specification())) {
            throw new IllegalArgumentException("upper and underside grids must share one domain");
        }

        GridSpec grid = upper.specification();
        int width = grid.width();
        int height = grid.height();
        double radius = evidence.compiledVolume().descriptor().nominalRadius();
        if (!(radius > 0.0)) {
            throw new IllegalArgumentException("nominal radius must be positive");
        }

        boolean[] occupied = new boolean[width * height];
        double[] occupiedUpper = new double[occupied.length];
        int occupiedCount = 0;
        double minimumUpper = Double.POSITIVE_INFINITY;
        double maximumUpper = Double.NEGATIVE_INFINITY;
        double upperSum = 0.0;
        double upperSquaredSum = 0.0;

        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                int index = z * width + x;
                double upperValue = upper.valueAt(x, z);
                double thickness = upperValue - underside.valueAt(x, z);
                if (!(thickness > 0.0)) {
                    continue;
                }
                occupied[index] = true;
                occupiedUpper[occupiedCount++] = upperValue;
                minimumUpper = Math.min(minimumUpper, upperValue);
                maximumUpper = Math.max(maximumUpper, upperValue);
                upperSum += upperValue;
                upperSquaredSum += upperValue * upperValue;
            }
        }
        if (occupiedCount == 0) {
            throw new IllegalArgumentException("surface-character evidence has no occupied columns");
        }

        Distribution gradient = gradientMagnitudes(upper, occupied, width, height);
        Distribution curvature =
                curvatureMagnitudesTimesRadius(upper, occupied, width, height, radius);

        Distribution window3 = localWindowRanges(upper, occupied, width, height, 1);
        Distribution window5 = localWindowRanges(upper, occupied, width, height, 2);
        Distribution window9 = localWindowRanges(upper, occupied, width, height, 4);

        double mean = upperSum / occupiedCount;
        double variance =
                Math.max(0.0, upperSquaredSum / occupiedCount - mean * mean);

        return new ProductionMorphologySurfaceCharacterDiagnostics(
                occupiedCount,
                gradient.count(),
                window3.count(),
                window5.count(),
                window9.count(),
                (maximumUpper - minimumUpper) / radius,
                Math.sqrt(variance) / radius,
                gradient.quantile(0.50),
                gradient.quantile(0.75),
                gradient.quantile(0.90),
                gradient.quantile(0.95),
                curvature.quantile(0.50),
                curvature.quantile(0.75),
                curvature.quantile(0.90),
                curvature.quantile(0.95),
                lagMeanDifference(upper, occupied, width, height, 1) / radius,
                lagMeanDifference(upper, occupied, width, height, 2) / radius,
                lagMeanDifference(upper, occupied, width, height, 4) / radius,
                lagMeanDifference(upper, occupied, width, height, 8) / radius,
                window3.quantile(0.50) / radius,
                window3.quantile(0.90) / radius,
                window5.quantile(0.50) / radius,
                window5.quantile(0.90) / radius,
                window9.quantile(0.50) / radius,
                window9.quantile(0.90) / radius);
    }

    private static Distribution gradientMagnitudes(
            ScalarGrid upper, boolean[] occupied, int width, int height) {
        double[] values = new double[width * height];
        int count = 0;
        double dx = upper.specification().spacingX();
        double dz = upper.specification().spacingZ();

        for (int z = 1; z + 1 < height; z++) {
            for (int x = 1; x + 1 < width; x++) {
                int center = z * width + x;
                if (!occupied[center]
                        || !occupied[center - 1]
                        || !occupied[center + 1]
                        || !occupied[center - width]
                        || !occupied[center + width]) {
                    continue;
                }
                double gx =
                        (upper.valueAt(x + 1, z) - upper.valueAt(x - 1, z))
                                / (2.0 * dx);
                double gz =
                        (upper.valueAt(x, z + 1) - upper.valueAt(x, z - 1))
                                / (2.0 * dz);
                values[count++] = Math.hypot(gx, gz);
            }
        }
        return Distribution.of(values, count);
    }

    private static Distribution curvatureMagnitudesTimesRadius(
            ScalarGrid upper,
            boolean[] occupied,
            int width,
            int height,
            double radius) {
        double[] values = new double[width * height];
        int count = 0;
        double dx = upper.specification().spacingX();
        double dz = upper.specification().spacingZ();
        double dx2 = dx * dx;
        double dz2 = dz * dz;

        for (int z = 1; z + 1 < height; z++) {
            for (int x = 1; x + 1 < width; x++) {
                int center = z * width + x;
                if (!occupied[center]
                        || !occupied[center - 1]
                        || !occupied[center + 1]
                        || !occupied[center - width]
                        || !occupied[center + width]) {
                    continue;
                }
                double value = upper.valueAt(x, z);
                double laplacian =
                        (upper.valueAt(x - 1, z) - 2.0 * value + upper.valueAt(x + 1, z))
                                        / dx2
                                + (upper.valueAt(x, z - 1)
                                                - 2.0 * value
                                                + upper.valueAt(x, z + 1))
                                        / dz2;
                values[count++] = Math.abs(laplacian) * radius;
            }
        }
        return Distribution.of(values, count);
    }

    private static double lagMeanDifference(
            ScalarGrid upper,
            boolean[] occupied,
            int width,
            int height,
            int lag) {
        long samples = 0L;
        double sum = 0.0;
        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                int index = z * width + x;
                if (!occupied[index]) {
                    continue;
                }
                if (x + lag < width) {
                    int other = index + lag;
                    if (occupied[other]) {
                        samples++;
                        sum += Math.abs(upper.valueAt(x, z) - upper.valueAt(x + lag, z));
                    }
                }
                if (z + lag < height) {
                    int other = index + lag * width;
                    if (occupied[other]) {
                        samples++;
                        sum += Math.abs(upper.valueAt(x, z) - upper.valueAt(x, z + lag));
                    }
                }
            }
        }
        return samples == 0L ? 0.0 : sum / samples;
    }

    private static Distribution localWindowRanges(
            ScalarGrid upper,
            boolean[] occupied,
            int width,
            int height,
            int halfWidth) {
        double[] values = new double[width * height];
        int count = 0;
        for (int z = halfWidth; z + halfWidth < height; z++) {
            for (int x = halfWidth; x + halfWidth < width; x++) {
                double minimum = Double.POSITIVE_INFINITY;
                double maximum = Double.NEGATIVE_INFINITY;
                boolean complete = true;
                for (int wz = z - halfWidth; wz <= z + halfWidth && complete; wz++) {
                    for (int wx = x - halfWidth; wx <= x + halfWidth; wx++) {
                        int index = wz * width + wx;
                        if (!occupied[index]) {
                            complete = false;
                            break;
                        }
                        double value = upper.valueAt(wx, wz);
                        minimum = Math.min(minimum, value);
                        maximum = Math.max(maximum, value);
                    }
                }
                if (complete) {
                    values[count++] = maximum - minimum;
                }
            }
        }
        return Distribution.of(values, count);
    }

    private record Distribution(double[] sorted) {
        static Distribution of(double[] values, int count) {
            double[] copy = Arrays.copyOf(values, count);
            Arrays.sort(copy);
            return new Distribution(copy);
        }

        int count() {
            return sorted.length;
        }

        double quantile(double fraction) {
            if (sorted.length == 0) {
                return 0.0;
            }
            int index = (int) Math.floor(fraction * (sorted.length - 1));
            return sorted[index];
        }
    }
}
