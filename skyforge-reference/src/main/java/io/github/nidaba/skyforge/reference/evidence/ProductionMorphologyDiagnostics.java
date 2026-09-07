package io.github.nidaba.skyforge.reference.evidence;

import io.github.nidaba.skyforge.reference.sampling.ScalarGrid;
import java.util.Arrays;
import java.util.Objects;

/**
 * AUTH-0083 scale-normalized measurements for production morphology visual review.
 *
 * <p>These values deliberately carry no pass/fail thresholds. They exist to expose evidence for
 * issue #214 and to support threshold selection only after reference and Minecraft atlases are
 * reviewed together.
 */
public record ProductionMorphologyDiagnostics(
        int occupiedColumns,
        double minimumThicknessNormalized,
        double fifthPercentileThicknessNormalized,
        double meanThicknessNormalized,
        double maximumThicknessNormalized,
        double maximumNeighborThicknessJumpNormalized,
        double meanUpperNeighborDifferenceNormalized,
        double meanUndersideNeighborDifferenceNormalized,
        double meanUpperSecondDifferenceNormalized,
        double meanUndersideSecondDifferenceNormalized,
        double halfTurnOccupancyMismatchFraction,
        double meanHalfTurnThicknessDifferenceNormalized,
        double upperUndersidePearsonCorrelation) {

    /** Measures one already-generated suspended-volume evidence specimen. */
    public static ProductionMorphologyDiagnostics measure(SuspendedVolumeEvidence evidence) {
        Objects.requireNonNull(evidence, "evidence");
        ScalarGrid upper = evidence.upperSurface();
        ScalarGrid underside = evidence.undersideSurface();
        if (!upper.specification().equals(underside.specification())) {
            throw new IllegalArgumentException("upper and underside grids must share one domain");
        }

        int width = upper.specification().width();
        int height = upper.specification().height();
        double radius = evidence.compiledVolume().descriptor().nominalRadius();
        double suspension = evidence.compiledVolume().descriptor().suspensionElevation();

        double[] thickness = new double[width * height];
        boolean[] occupied = new boolean[thickness.length];
        double[] occupiedThickness = new double[thickness.length];
        int occupiedCount = 0;

        double minimumThickness = Double.POSITIVE_INFINITY;
        double maximumThickness = Double.NEGATIVE_INFINITY;
        double thicknessSum = 0.0;

        double upperSum = 0.0;
        double undersideDepthSum = 0.0;
        double upperSquaredSum = 0.0;
        double undersideDepthSquaredSum = 0.0;
        double crossSum = 0.0;

        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                int index = z * width + x;
                double upperValue = upper.valueAt(x, z);
                double undersideValue = underside.valueAt(x, z);
                double localThickness = upperValue - undersideValue;
                thickness[index] = localThickness;
                if (!(localThickness > 0.0)) {
                    continue;
                }

                occupied[index] = true;
                occupiedThickness[occupiedCount++] = localThickness;
                minimumThickness = Math.min(minimumThickness, localThickness);
                maximumThickness = Math.max(maximumThickness, localThickness);
                thicknessSum += localThickness;

                double upperOffset = upperValue - suspension;
                double undersideDepth = suspension - undersideValue;
                upperSum += upperOffset;
                undersideDepthSum += undersideDepth;
                upperSquaredSum += upperOffset * upperOffset;
                undersideDepthSquaredSum += undersideDepth * undersideDepth;
                crossSum += upperOffset * undersideDepth;
            }
        }

        if (occupiedCount == 0) {
            throw new IllegalArgumentException("production morphology evidence has no occupied columns");
        }

        double[] sorted = Arrays.copyOf(occupiedThickness, occupiedCount);
        Arrays.sort(sorted);
        double fifthPercentile = sorted[(int) Math.floor(0.05 * (sorted.length - 1))];

        NeighborStats neighborStats =
                neighbors(upper, underside, thickness, occupied, width, height);
        SecondDifferenceStats secondDifferenceStats =
                secondDifferences(upper, underside, occupied, width, height);
        HalfTurnStats halfTurnStats =
                halfTurn(thickness, occupied, width, height);

        return new ProductionMorphologyDiagnostics(
                occupiedCount,
                minimumThickness / radius,
                fifthPercentile / radius,
                (thicknessSum / occupiedCount) / radius,
                maximumThickness / radius,
                neighborStats.maximumThicknessJump() / radius,
                normalizedMean(
                        neighborStats.upperDifferenceSum(),
                        neighborStats.samples(),
                        radius),
                normalizedMean(
                        neighborStats.undersideDifferenceSum(),
                        neighborStats.samples(),
                        radius),
                normalizedMean(
                        secondDifferenceStats.upperSum(),
                        secondDifferenceStats.samples(),
                        radius),
                normalizedMean(
                        secondDifferenceStats.undersideSum(),
                        secondDifferenceStats.samples(),
                        radius),
                halfTurnStats.mismatchFraction(),
                normalizedMean(
                        halfTurnStats.thicknessDifferenceSum(),
                        halfTurnStats.comparablePairs(),
                        radius),
                pearson(
                        occupiedCount,
                        upperSum,
                        undersideDepthSum,
                        upperSquaredSum,
                        undersideDepthSquaredSum,
                        crossSum));
    }

    private static NeighborStats neighbors(
            ScalarGrid upper,
            ScalarGrid underside,
            double[] thickness,
            boolean[] occupied,
            int width,
            int height) {
        long samples = 0L;
        double maximumThicknessJump = 0.0;
        double upperDifferenceSum = 0.0;
        double undersideDifferenceSum = 0.0;

        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                int index = z * width + x;
                if (!occupied[index]) {
                    continue;
                }
                if (x + 1 < width) {
                    int neighbor = index + 1;
                    if (occupied[neighbor]) {
                        samples++;
                        maximumThicknessJump =
                                Math.max(
                                        maximumThicknessJump,
                                        Math.abs(thickness[index] - thickness[neighbor]));
                        upperDifferenceSum +=
                                Math.abs(upper.valueAt(x, z) - upper.valueAt(x + 1, z));
                        undersideDifferenceSum +=
                                Math.abs(
                                        underside.valueAt(x, z)
                                                - underside.valueAt(x + 1, z));
                    }
                }
                if (z + 1 < height) {
                    int neighbor = index + width;
                    if (occupied[neighbor]) {
                        samples++;
                        maximumThicknessJump =
                                Math.max(
                                        maximumThicknessJump,
                                        Math.abs(thickness[index] - thickness[neighbor]));
                        upperDifferenceSum +=
                                Math.abs(upper.valueAt(x, z) - upper.valueAt(x, z + 1));
                        undersideDifferenceSum +=
                                Math.abs(
                                        underside.valueAt(x, z)
                                                - underside.valueAt(x, z + 1));
                    }
                }
            }
        }
        return new NeighborStats(
                samples,
                maximumThicknessJump,
                upperDifferenceSum,
                undersideDifferenceSum);
    }

    private static SecondDifferenceStats secondDifferences(
            ScalarGrid upper,
            ScalarGrid underside,
            boolean[] occupied,
            int width,
            int height) {
        long samples = 0L;
        double upperSum = 0.0;
        double undersideSum = 0.0;

        for (int z = 0; z < height; z++) {
            for (int x = 1; x + 1 < width; x++) {
                int center = z * width + x;
                if (occupied[center - 1] && occupied[center] && occupied[center + 1]) {
                    samples++;
                    upperSum +=
                            Math.abs(
                                    upper.valueAt(x - 1, z)
                                            - 2.0 * upper.valueAt(x, z)
                                            + upper.valueAt(x + 1, z));
                    undersideSum +=
                            Math.abs(
                                    underside.valueAt(x - 1, z)
                                            - 2.0 * underside.valueAt(x, z)
                                            + underside.valueAt(x + 1, z));
                }
            }
        }
        for (int z = 1; z + 1 < height; z++) {
            for (int x = 0; x < width; x++) {
                int center = z * width + x;
                if (occupied[center - width]
                        && occupied[center]
                        && occupied[center + width]) {
                    samples++;
                    upperSum +=
                            Math.abs(
                                    upper.valueAt(x, z - 1)
                                            - 2.0 * upper.valueAt(x, z)
                                            + upper.valueAt(x, z + 1));
                    undersideSum +=
                            Math.abs(
                                    underside.valueAt(x, z - 1)
                                            - 2.0 * underside.valueAt(x, z)
                                            + underside.valueAt(x, z + 1));
                }
            }
        }
        return new SecondDifferenceStats(samples, upperSum, undersideSum);
    }

    private static HalfTurnStats halfTurn(
            double[] thickness,
            boolean[] occupied,
            int width,
            int height) {
        long pairs = 0L;
        long mismatches = 0L;
        long comparablePairs = 0L;
        double thicknessDifferenceSum = 0.0;

        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                int index = z * width + x;
                int mirrorX = width - 1 - x;
                int mirrorZ = height - 1 - z;
                int mirror = mirrorZ * width + mirrorX;
                if (index >= mirror) {
                    continue;
                }
                pairs++;
                if (occupied[index] != occupied[mirror]) {
                    mismatches++;
                } else if (occupied[index]) {
                    comparablePairs++;
                    thicknessDifferenceSum +=
                            Math.abs(thickness[index] - thickness[mirror]);
                }
            }
        }

        return new HalfTurnStats(
                pairs == 0L ? 0.0 : (double) mismatches / pairs,
                comparablePairs,
                thicknessDifferenceSum);
    }

    private static double normalizedMean(double sum, long samples, double radius) {
        return samples == 0L ? 0.0 : (sum / samples) / radius;
    }

    private static double pearson(
            int count,
            double firstSum,
            double secondSum,
            double firstSquaredSum,
            double secondSquaredSum,
            double crossSum) {
        double covariance = count * crossSum - firstSum * secondSum;
        double firstVariance = count * firstSquaredSum - firstSum * firstSum;
        double secondVariance = count * secondSquaredSum - secondSum * secondSum;
        double denominator =
                Math.sqrt(Math.max(0.0, firstVariance) * Math.max(0.0, secondVariance));
        if (!(denominator > 0.0)) {
            return 0.0;
        }
        return covariance / denominator;
    }

    private record NeighborStats(
            long samples,
            double maximumThicknessJump,
            double upperDifferenceSum,
            double undersideDifferenceSum) {}

    private record SecondDifferenceStats(
            long samples,
            double upperSum,
            double undersideSum) {}

    private record HalfTurnStats(
            double mismatchFraction,
            long comparablePairs,
            double thicknessDifferenceSum) {}
}
