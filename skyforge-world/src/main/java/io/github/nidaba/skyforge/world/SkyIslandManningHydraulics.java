package io.github.nidaba.skyforge.world;

/**
 * SI-unit open-channel hydraulics primitives.
 *
 * <p>This class deliberately has no dependency on Skyforge's normalized terrain fields. Manning's
 * equation requires a discharge with physical dimensions, a roughness coefficient, channel
 * geometry, and a positive energy/friction slope. Skyforge's current semantic graph supplies only
 * relative discharge and its hydraulic calibration supplies a width/depth potential; those values
 * cannot be silently treated as calibrated SI inputs.
 *
 * <p>The normal-depth calculation is a uniform-flow reference state, not a gradually-varied-flow
 * solver. It is useful for calibrating/validating the future profile integration and must not be
 * used as a backwater, confluence, reservoir, or cascade solution.
 */
public final class SkyIslandManningHydraulics {
    private static final int MAX_BRACKET_STEPS = 128;
    private static final int BISECTION_STEPS = 128;
    private static final double RELATIVE_DEPTH_TOLERANCE = 1.0e-12;

    private SkyIslandManningHydraulics() {}

    /**
     * Returns the uniform-flow friction/energy slope required to carry the supplied discharge
     * at a specified depth in a trapezoidal section.
     *
     * <p>This is the inverse of the conveyance form of Manning's equation:
     * {@code S_f = (Q n / (A R^(2/3)))^2}. It is a local uniform-flow reference state, not a
     * gradually-varied-flow substitute.
     */
    public static double uniformFlowEnergySlope(
            double dischargeCubicMetersPerSecond,
            double manningRoughness,
            double depthMeters,
            double bottomWidthMeters,
            double sideSlopeHorizontalPerVertical) {
        requireFinitePositive(dischargeCubicMetersPerSecond, "discharge");
        requireFinitePositive(manningRoughness, "Manning roughness");
        requireFinitePositive(depthMeters, "depth");
        requireFinitePositive(bottomWidthMeters, "bottom width");
        if (!Double.isFinite(sideSlopeHorizontalPerVertical)
                || sideSlopeHorizontalPerVertical < 0.0) {
            throw new IllegalArgumentException("side slope must be finite and non-negative");
        }
        double area = depthMeters
                * (bottomWidthMeters + sideSlopeHorizontalPerVertical * depthMeters);
        double wettedPerimeter = bottomWidthMeters
                + 2.0 * depthMeters
                        * Math.hypot(1.0, sideSlopeHorizontalPerVertical);
        double hydraulicRadius = area / wettedPerimeter;
        double conveyanceWithoutRoughness =
                area * Math.pow(hydraulicRadius, 2.0 / 3.0);
        double scaledDischarge = dischargeCubicMetersPerSecond * manningRoughness
                / conveyanceWithoutRoughness;
        double slope = scaledDischarge * scaledDischarge;
        if (!Double.isFinite(slope) || slope <= 0.0) {
            throw new ArithmeticException("uniform-flow energy slope is not finite and positive");
        }
        return slope;
    }

    /**
     * Solves Manning's equation for trapezoidal-channel normal depth.
     *
     * @param dischargeCubicMetersPerSecond positive SI discharge Q
     * @param manningRoughness SI Manning coefficient n (s/m^(1/3))
     * @param energySlope positive dimensionless friction/energy slope S
     * @param bottomWidthMeters positive channel bottom width b
     * @param sideSlopeHorizontalPerVertical nonnegative trapezoid side slope z (H:V)
     * @return positive normal depth in meters
     */
    public static double normalDepthMeters(
            double dischargeCubicMetersPerSecond,
            double manningRoughness,
            double energySlope,
            double bottomWidthMeters,
            double sideSlopeHorizontalPerVertical) {
        requireFinitePositive(dischargeCubicMetersPerSecond, "discharge");
        requireFinitePositive(manningRoughness, "Manning roughness");
        requireFinitePositive(energySlope, "energy slope");
        requireFinitePositive(bottomWidthMeters, "bottom width");
        if (!Double.isFinite(sideSlopeHorizontalPerVertical)
                || sideSlopeHorizontalPerVertical < 0.0) {
            throw new IllegalArgumentException("side slope must be finite and non-negative");
        }

        double targetConveyance = dischargeCubicMetersPerSecond * manningRoughness
                / Math.sqrt(energySlope);
        double lower = 0.0;
        double upper = Math.max(1.0, bottomWidthMeters);
        int bracketSteps = 0;
        while (conveyance(upper, bottomWidthMeters, sideSlopeHorizontalPerVertical)
                < targetConveyance) {
            if (++bracketSteps > MAX_BRACKET_STEPS || !Double.isFinite(upper * 2.0)) {
                throw new ArithmeticException("could not bracket finite Manning normal depth");
            }
            upper *= 2.0;
        }

        for (int iteration = 0; iteration < BISECTION_STEPS; iteration++) {
            double middle = lower + 0.5 * (upper - lower);
            if (conveyance(middle, bottomWidthMeters, sideSlopeHorizontalPerVertical)
                    < targetConveyance) {
                lower = middle;
            } else {
                upper = middle;
            }
            if (upper - lower
                    <= RELATIVE_DEPTH_TOLERANCE * Math.max(1.0, upper)) {
                break;
            }
        }
        return lower + 0.5 * (upper - lower);
    }

    private static double conveyance(
            double depthMeters,
            double bottomWidthMeters,
            double sideSlopeHorizontalPerVertical) {
        double area = depthMeters
                * (bottomWidthMeters + sideSlopeHorizontalPerVertical * depthMeters);
        double wettedPerimeter = bottomWidthMeters
                + 2.0 * depthMeters
                        * Math.hypot(1.0, sideSlopeHorizontalPerVertical);
        double hydraulicRadius = area / wettedPerimeter;
        return area * Math.pow(hydraulicRadius, 2.0 / 3.0);
    }

    private static void requireFinitePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }
}
