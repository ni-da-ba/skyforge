package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SkyIslandManningHydraulicsTest {
    private static final double EPSILON = 1.0e-10;

    @Test
    void rectangularNormalDepthSatisfiesManningEquation() {
        double expectedDepthMeters = 1.0;
        double widthMeters = 10.0;
        double roughness = 0.03;
        double slope = 0.001;
        double area = widthMeters * expectedDepthMeters;
        double hydraulicRadius = area / (widthMeters + 2.0 * expectedDepthMeters);
        double discharge = (1.0 / roughness) * area
                * Math.pow(hydraulicRadius, 2.0 / 3.0) * Math.sqrt(slope);

        double solved = SkyIslandManningHydraulics.normalDepthMeters(
                discharge, roughness, slope, widthMeters, 0.0);

        assertEquals(expectedDepthMeters, solved, EPSILON);
    }

    @Test
    void trapezoidalNormalDepthSatisfiesManningEquation() {
        double expectedDepthMeters = 1.75;
        double bottomWidthMeters = 4.0;
        double sideSlope = 1.5;
        double roughness = 0.035;
        double slope = 0.004;
        double area = expectedDepthMeters
                * (bottomWidthMeters + sideSlope * expectedDepthMeters);
        double wettedPerimeter = bottomWidthMeters
                + 2.0 * expectedDepthMeters * Math.hypot(1.0, sideSlope);
        double hydraulicRadius = area / wettedPerimeter;
        double discharge = (1.0 / roughness) * area
                * Math.pow(hydraulicRadius, 2.0 / 3.0) * Math.sqrt(slope);

        double solved = SkyIslandManningHydraulics.normalDepthMeters(
                discharge, roughness, slope, bottomWidthMeters, sideSlope);

        assertEquals(expectedDepthMeters, solved, EPSILON);
    }

    @Test
    void normalDepthIncreasesWithDischargeAndRoughnessAndDecreasesWithSlope() {
        double base = SkyIslandManningHydraulics.normalDepthMeters(4.0, 0.03, 0.01, 3.0, 1.5);
        double higherDischarge =
                SkyIslandManningHydraulics.normalDepthMeters(8.0, 0.03, 0.01, 3.0, 1.5);
        double rougher =
                SkyIslandManningHydraulics.normalDepthMeters(4.0, 0.06, 0.01, 3.0, 1.5);
        double steeper =
                SkyIslandManningHydraulics.normalDepthMeters(4.0, 0.03, 0.04, 3.0, 1.5);

        assertTrue(higherDischarge > base);
        assertTrue(rougher > base);
        assertTrue(steeper < base);
    }

    @Test
    void rejectsMissingOrNonphysicalInputsInsteadOfInventingDefaults() {
        assertThrows(IllegalArgumentException.class,
                () -> SkyIslandManningHydraulics.normalDepthMeters(0.0, 0.03, 0.01, 3.0, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> SkyIslandManningHydraulics.normalDepthMeters(1.0, 0.0, 0.01, 3.0, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> SkyIslandManningHydraulics.normalDepthMeters(1.0, 0.03, 0.0, 3.0, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> SkyIslandManningHydraulics.normalDepthMeters(1.0, 0.03, 0.01, 3.0, -1.0));
    }
}
