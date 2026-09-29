package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SkyIslandContinuousWaterbodyContourMetricsTest {
    @Test
    void circleContourConvergesAndEllipseHasLowerCompactness() {
        SkyIslandContinuousWaterbodyContourMetrics.Result coarse = measure(
                0.5, 4.0, (x, z) -> Math.hypot(x, z) - 4.0);
        SkyIslandContinuousWaterbodyContourMetrics.Result fine = measure(
                0.25, 4.0, (x, z) -> Math.hypot(x, z) - 4.0);
        SkyIslandContinuousWaterbodyContourMetrics.Result ellipse = measure(
                0.125, 5.0, (x, z) -> (x * x) / 25.0 + (z * z) / 4.0 - 1.0);
        SkyIslandContinuousWaterbodyContourMetrics.Result rough = measure(
                0.125,
                4.5,
                (x, z) -> Math.hypot(x, z)
                        - 4.0 * (1.0 + 0.12 * Math.cos(5.0 * Math.atan2(z, x))));

        double coarseRatio = isoperimetricRatio(
                coarse, 0.5, 4.0, (x, z) -> Math.hypot(x, z) <= 4.0);
        double fineRatio = isoperimetricRatio(
                fine, 0.25, 4.0, (x, z) -> Math.hypot(x, z) <= 4.0);
        double roughRatio = isoperimetricRatio(
                rough,
                0.125,
                4.5,
                (x, z) -> Math.hypot(x, z)
                        <= 4.0 * (1.0 + 0.12 * Math.cos(5.0 * Math.atan2(z, x))));
        double ellipseRatio = isoperimetricRatio(
                ellipse, 0.125, 5.0, (x, z) -> (x * x) / 25.0 + (z * z) / 4.0 <= 1.0);

        assertTrue(coarse.closedLoopCount() == 1);
        assertTrue(fine.closedLoopCount() == 1);
        assertTrue(coarse.nonDegreeTwoVertexCount() == 0);
        assertTrue(fine.nonDegreeTwoVertexCount() == 0);
        assertTrue(Math.abs(fineRatio - 1.0) < Math.abs(coarseRatio - 1.0) + 0.03);
        assertTrue(
                Math.abs(fineRatio - 1.0) < 0.12,
                "refined circle should approach the isoperimetric optimum");
        assertTrue(Math.abs(fine.perimeterWorldUnits() - 8.0 * Math.PI)
                / (8.0 * Math.PI) < 0.08);
        assertTrue(ellipse.perimeterWorldUnits() > 0.0);
        assertTrue(ellipse.closedLoopCount() == 1);
        assertTrue(ellipse.nonDegreeTwoVertexCount() == 0);
        assertTrue(ellipseRatio > fineRatio + 0.12);
        assertTrue(rough.closedLoopCount() == 1);
        assertTrue(roughRatio > fineRatio + 0.08);
    }

    @Test
    void contourTouchingSearchBoundaryIsNotReportedAsClosed() {
        double spacing = 0.25;
        int width = 41;
        double[] signed = new double[width * width];
        boolean[] connected = new boolean[signed.length];
        for (int z = 0; z < width; z++) {
            for (int x = 0; x < width; x++) {
                double dx = x * spacing - 2.0;
                double dz = z * spacing - 5.0;
                int index = z * width + x;
                signed[index] = Math.hypot(dx, dz) - 3.0;
                connected[index] = signed[index] <= 0.0;
            }
        }

        SkyIslandContinuousWaterbodyContourMetrics.Result result =
                SkyIslandContinuousWaterbodyContourMetrics.measure(
                        signed, connected, width, width, spacing);

        assertTrue(result.nonDegreeTwoVertexCount() > 0);
        assertTrue(result.closedLoopCount() == 0);
    }

    private static SkyIslandContinuousWaterbodyContourMetrics.Result measure(
            double spacing,
            double extent,
            SignedField field) {
        int half = (int) Math.ceil(extent / spacing) + 2;
        int width = 2 * half + 1;
        double[] signed = new double[width * width];
        boolean[] connected = new boolean[signed.length];
        for (int z = 0; z < width; z++) {
            for (int x = 0; x < width; x++) {
                double localX = (x - half) * spacing;
                double localZ = (z - half) * spacing;
                int index = z * width + x;
                signed[index] = field.sample(localX, localZ);
                connected[index] = signed[index] <= 0.0;
            }
        }
        return SkyIslandContinuousWaterbodyContourMetrics.measure(
                signed, connected, width, width, spacing);
    }

    private static double isoperimetricRatio(
            SkyIslandContinuousWaterbodyContourMetrics.Result contour,
            double spacing,
            double extent,
            InsideField inside) {
        int half = (int) Math.ceil(extent / spacing) + 2;
        int width = 2 * half + 1;
        int count = 0;
        for (int z = 0; z < width; z++) {
            for (int x = 0; x < width; x++) {
                if (inside.contains((x - half) * spacing, (z - half) * spacing)) {
                    count++;
                }
            }
        }
        double area = count * spacing * spacing;
        return contour.perimeterWorldUnits() * contour.perimeterWorldUnits()
                / (4.0 * Math.PI * area);
    }

    @FunctionalInterface
    private interface SignedField {
        double sample(double x, double z);
    }

    @FunctionalInterface
    private interface InsideField {
        boolean contains(double x, double z);
    }
}
