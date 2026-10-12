package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * Solves steady uniform-flow normal depth from Manning's equation in SI units.
 *
 * <p>This is a hydraulic primitive, not a terrain or channel-routing planner. Callers supply a
 * cross-section and the energy slope; the solver does not invent roughness, discharge, or boundary
 * conditions. The cross-section must describe a continuous open section whose conveyance increases
 * with depth over the interval containing the solution.
 */
public final class SkyIslandManningNormalDepthSolver {
    private static final int MAXIMUM_BRACKET_EXPANSIONS = 128;
    private static final int MAXIMUM_BISECTION_ITERATIONS = 128;
    private static final double RELATIVE_DEPTH_TOLERANCE = 1.0e-12;
    private static final double MINIMUM_DEPTH_TOLERANCE = 1.0e-12;

    private SkyIslandManningNormalDepthSolver() {}

    /**
     * Open-channel section properties as functions of water depth, in metres.
     */
    public interface CrossSection {
        /** Wetted cross-sectional area, in square metres. */
        double area(double depthMetres);

        /** Wetted perimeter, in metres. */
        double wettedPerimeter(double depthMetres);
    }

    /**
     * Returns the positive normal depth that satisfies Manning's equation:
     *
     * <pre>
     * Q = (1 / n) A (A / P)^(2 / 3) sqrt(S)
     * </pre>
     *
     * <p>All inputs and section properties use SI units: discharge in m³/s, depth and perimeter in
     * metres, area in m², Manning roughness {@code n} in s/m^(1/3), and positive energy slope as a
     * dimensionless ratio. This method is for steady uniform flow only; it does not solve gradually
     * varied flow, transitions, junctions, or unsteady routing.
     *
     * @param dischargeCubicMetresPerSecond positive discharge
     * @param manningRoughness positive SI Manning roughness
     * @param energySlope positive energy slope
     * @param section cross-section geometry
     * @return normal depth in metres
     * @throws IllegalArgumentException if inputs or evaluated section properties are not finite and
     *     physically positive
     */
    public static double solve(
            double dischargeCubicMetresPerSecond,
            double manningRoughness,
            double energySlope,
            CrossSection section) {
        requirePositiveFinite(dischargeCubicMetresPerSecond, "discharge");
        requirePositiveFinite(manningRoughness, "Manning roughness");
        requirePositiveFinite(energySlope, "energy slope");
        Objects.requireNonNull(section, "section");

        double lower = 0.0;
        double upper = 1.0;
        double upperDischarge = dischargeAtDepth(upper, manningRoughness, energySlope, section);
        int expansions = 0;
        while (upperDischarge < dischargeCubicMetresPerSecond
                && expansions < MAXIMUM_BRACKET_EXPANSIONS) {
            lower = upper;
            upper *= 2.0;
            upperDischarge = dischargeAtDepth(upper, manningRoughness, energySlope, section);
            expansions++;
        }
        if (upperDischarge < dischargeCubicMetresPerSecond) {
            throw new IllegalArgumentException("cross-section could not bracket the requested discharge");
        }

        for (int iteration = 0; iteration < MAXIMUM_BISECTION_ITERATIONS; iteration++) {
            double middle = lower + (upper - lower) * 0.5;
            if (middle == lower || middle == upper) {
                return middle;
            }
            double middleDischarge =
                    dischargeAtDepth(middle, manningRoughness, energySlope, section);
            if (middleDischarge < dischargeCubicMetresPerSecond) {
                lower = middle;
            } else {
                upper = middle;
            }

            double tolerance = Math.max(
                    MINIMUM_DEPTH_TOLERANCE,
                    RELATIVE_DEPTH_TOLERANCE * upper);
            if (upper - lower <= tolerance) {
                return lower + (upper - lower) * 0.5;
            }
        }
        return lower + (upper - lower) * 0.5;
    }

    private static double dischargeAtDepth(
            double depthMetres,
            double manningRoughness,
            double energySlope,
            CrossSection section) {
        double area = section.area(depthMetres);
        double perimeter = section.wettedPerimeter(depthMetres);
        if (!(area > 0.0) || !Double.isFinite(area)) {
            throw new IllegalArgumentException("cross-sectional area must be finite and positive");
        }
        if (!(perimeter > 0.0) || !Double.isFinite(perimeter)) {
            throw new IllegalArgumentException("wetted perimeter must be finite and positive");
        }

        double hydraulicRadius = area / perimeter;
        double discharge = area
                * Math.pow(hydraulicRadius, 2.0 / 3.0)
                * Math.sqrt(energySlope)
                / manningRoughness;
        if (!(discharge >= 0.0) || !Double.isFinite(discharge)) {
            throw new IllegalArgumentException("Manning conveyance must be finite and nonnegative");
        }
        return discharge;
    }

    private static void requirePositiveFinite(double value, String name) {
        if (!(value > 0.0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }
}
