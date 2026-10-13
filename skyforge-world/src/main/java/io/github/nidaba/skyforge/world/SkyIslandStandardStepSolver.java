package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * Computes steady gradually varied profiles by the standard-step energy method for finite channel
 * cross sections.
 *
 * <p>Sections are supplied at strictly increasing stations in the upstream-to-downstream direction.
 * The solver assumes constant discharge and Manning roughness between stations, hydrostatic pressure,
 * kinetic-energy coefficient alpha=1, no lateral inflow, and no local transition/junction losses.
 * A subcritical profile is marched upstream from its downstream depth boundary; a supercritical
 * profile is marched downstream from its upstream depth boundary. Each step must have a root that
 * remains in the requested regime; otherwise the solve fails closed.
 *
 * <p>Friction loss uses HEC-RAS's average-conveyance method:
 * {@code Sf_bar = (Q / ((K_up + K_down) / 2))^2}, with
 * {@code K = A R^(2/3) / n}. Cross sections must expose area, wetted perimeter, and top width as
 * functions of depth. This solver does not model hydraulic jumps, confluences, lateral inflows,
 * unsteady flow, or rapidly varied transitions.
 */
public final class SkyIslandStandardStepSolver {
    private static final double GRAVITY_METRES_PER_SECOND_SQUARED = 9.81;
    private static final int MAXIMUM_BRACKET_EXPANSIONS = 128;
    private static final int MAXIMUM_BISECTION_ITERATIONS = 128;
    private static final double DEPTH_TOLERANCE_METRES = 1.0e-10;

    private SkyIslandStandardStepSolver() {}

    public enum FlowRegime {
        SUBCRITICAL,
        SUPERCRITICAL
    }

    /** Wetted geometry at a cross section, all in SI units. */
    public interface CrossSection {
        /** Wetted area, in square metres. */
        double area(double depthMetres);

        /** Wetted perimeter, in metres. */
        double wettedPerimeter(double depthMetres);

        /** Water-surface top width, in metres. */
        double topWidth(double depthMetres);
    }

    /**
     * Solves depths at all supplied stations.
     *
     * @param stationsMetres strictly increasing upstream-to-downstream stations
     * @param bedElevationsMetres bed elevation at each station
     * @param sections cross-section geometry at each station
     * @param dischargeCubicMetresPerSecond constant positive discharge
     * @param manningRoughness positive SI Manning roughness
     * @param boundaryDepthMetres downstream subcritical or upstream supercritical boundary depth
     * @param regime flow regime preserved at every station
     * @return water depth at each station, in metres
     * @throws IllegalArgumentException for invalid geometry/inputs or a step without a same-regime root
     */
    public static double[] solve(
            double[] stationsMetres,
            double[] bedElevationsMetres,
            CrossSection[] sections,
            double dischargeCubicMetresPerSecond,
            double manningRoughness,
            double boundaryDepthMetres,
            FlowRegime regime) {
        Objects.requireNonNull(stationsMetres, "stationsMetres");
        Objects.requireNonNull(bedElevationsMetres, "bedElevationsMetres");
        Objects.requireNonNull(sections, "sections");
        Objects.requireNonNull(regime, "regime");
        requirePositiveFinite(dischargeCubicMetresPerSecond, "discharge");
        requirePositiveFinite(manningRoughness, "Manning roughness");
        requirePositiveFinite(boundaryDepthMetres, "boundary depth");
        if (stationsMetres.length < 2
                || stationsMetres.length != bedElevationsMetres.length
                || stationsMetres.length != sections.length) {
            throw new IllegalArgumentException(
                    "station, bed, and section arrays must have the same length of at least two");
        }
        for (int i = 0; i < stationsMetres.length; i++) {
            if (!Double.isFinite(stationsMetres[i]) || !Double.isFinite(bedElevationsMetres[i])) {
                throw new IllegalArgumentException("station and bed elevations must be finite");
            }
            Objects.requireNonNull(sections[i], "section");
            if (i > 0 && !(stationsMetres[i] > stationsMetres[i - 1])) {
                throw new IllegalArgumentException("stations must be strictly increasing");
            }
        }

        double boundaryCriticalDepth = criticalDepth(
                dischargeCubicMetresPerSecond, sections[regime == FlowRegime.SUBCRITICAL
                        ? sections.length - 1
                        : 0]);
        if (regime == FlowRegime.SUBCRITICAL && !(boundaryDepthMetres > boundaryCriticalDepth)) {
            throw new IllegalArgumentException("subcritical boundary depth must exceed critical depth");
        }
        if (regime == FlowRegime.SUPERCRITICAL && !(boundaryDepthMetres < boundaryCriticalDepth)) {
            throw new IllegalArgumentException("supercritical boundary depth must be below critical depth");
        }

        double[] depths = new double[stationsMetres.length];
        if (regime == FlowRegime.SUBCRITICAL) {
            int last = depths.length - 1;
            depths[last] = boundaryDepthMetres;
            for (int i = last - 1; i >= 0; i--) {
                depths[i] = solveSubcriticalStep(
                        stationsMetres[i + 1] - stationsMetres[i],
                        bedElevationsMetres[i],
                        bedElevationsMetres[i + 1],
                        depths[i + 1],
                        sections[i],
                        sections[i + 1],
                        dischargeCubicMetresPerSecond,
                        manningRoughness);
            }
        } else {
            depths[0] = boundaryDepthMetres;
            for (int i = 0; i < depths.length - 1; i++) {
                depths[i + 1] = solveSupercriticalStep(
                        stationsMetres[i + 1] - stationsMetres[i],
                        bedElevationsMetres[i],
                        bedElevationsMetres[i + 1],
                        depths[i],
                        sections[i],
                        sections[i + 1],
                        dischargeCubicMetresPerSecond,
                        manningRoughness);
            }
        }
        return depths;
    }

    private static double solveSubcriticalStep(
            double stationLength,
            double upstreamBed,
            double downstreamBed,
            double downstreamDepth,
            CrossSection upstreamSection,
            CrossSection downstreamSection,
            double discharge,
            double roughness) {
        double downstreamEnergy =
                downstreamBed + specificEnergy(downstreamDepth, downstreamSection, discharge);
        double lower = criticalDepth(discharge, upstreamSection);
        double lowerResidual = subcriticalResidual(
                lower, stationLength, upstreamBed, downstreamEnergy, downstreamDepth,
                upstreamSection, downstreamSection, discharge, roughness);
        if (lowerResidual > 0.0) {
            throw new IllegalArgumentException("subcritical standard step has no bracketed energy root");
        }

        double upper = Math.max(Math.max(lower * 2.0, downstreamDepth), 1.0);
        double upperResidual = subcriticalResidual(
                upper, stationLength, upstreamBed, downstreamEnergy, downstreamDepth,
                upstreamSection, downstreamSection, discharge, roughness);
        int expansions = 0;
        while (upperResidual < 0.0 && expansions < MAXIMUM_BRACKET_EXPANSIONS) {
            upper *= 2.0;
            upperResidual = subcriticalResidual(
                    upper, stationLength, upstreamBed, downstreamEnergy, downstreamDepth,
                    upstreamSection, downstreamSection, discharge, roughness);
            expansions++;
        }
        if (upperResidual < 0.0) {
            throw new IllegalArgumentException("subcritical standard step could not bracket energy root");
        }
        return bisect(lower, upper, value -> subcriticalResidual(
                value, stationLength, upstreamBed, downstreamEnergy, downstreamDepth,
                upstreamSection, downstreamSection, discharge, roughness));
    }

    private static double subcriticalResidual(
            double upstreamDepth,
            double stationLength,
            double upstreamBed,
            double downstreamEnergy,
            double downstreamDepth,
            CrossSection upstreamSection,
            CrossSection downstreamSection,
            double discharge,
            double roughness) {
        double frictionLoss = frictionLoss(
                stationLength, upstreamDepth, downstreamDepth,
                upstreamSection, downstreamSection, discharge, roughness);
        return upstreamBed + specificEnergy(upstreamDepth, upstreamSection, discharge)
                - downstreamEnergy - frictionLoss;
    }

    private static double solveSupercriticalStep(
            double stationLength,
            double upstreamBed,
            double downstreamBed,
            double upstreamDepth,
            CrossSection upstreamSection,
            CrossSection downstreamSection,
            double discharge,
            double roughness) {
        double upstreamEnergy =
                upstreamBed + specificEnergy(upstreamDepth, upstreamSection, discharge);
        double upper = criticalDepth(discharge, downstreamSection);
        double upperResidual = supercriticalResidual(
                upper, stationLength, downstreamBed, upstreamEnergy, upstreamDepth,
                upstreamSection, downstreamSection, discharge, roughness);
        if (upperResidual > 0.0) {
            throw new IllegalArgumentException("supercritical standard step has no bracketed energy root");
        }

        double lower = upper * 0.5;
        double lowerResidual = supercriticalResidual(
                lower, stationLength, downstreamBed, upstreamEnergy, upstreamDepth,
                upstreamSection, downstreamSection, discharge, roughness);
        int contractions = 0;
        while (lowerResidual <= 0.0 && contractions < MAXIMUM_BRACKET_EXPANSIONS) {
            lower *= 0.1;
            lowerResidual = supercriticalResidual(
                    lower, stationLength, downstreamBed, upstreamEnergy, upstreamDepth,
                    upstreamSection, downstreamSection, discharge, roughness);
            contractions++;
        }
        if (!(lowerResidual > 0.0)) {
            throw new IllegalArgumentException("supercritical standard step could not bracket energy root");
        }
        return bisect(lower, upper, value -> supercriticalResidual(
                value, stationLength, downstreamBed, upstreamEnergy, upstreamDepth,
                upstreamSection, downstreamSection, discharge, roughness));
    }

    private static double supercriticalResidual(
            double downstreamDepth,
            double stationLength,
            double downstreamBed,
            double upstreamEnergy,
            double upstreamDepth,
            CrossSection upstreamSection,
            CrossSection downstreamSection,
            double discharge,
            double roughness) {
        double frictionLoss = frictionLoss(
                stationLength, upstreamDepth, downstreamDepth,
                upstreamSection, downstreamSection, discharge, roughness);
        return downstreamBed + specificEnergy(downstreamDepth, downstreamSection, discharge)
                + frictionLoss - upstreamEnergy;
    }

    private static double frictionLoss(
            double stationLength,
            double upstreamDepth,
            double downstreamDepth,
            CrossSection upstreamSection,
            CrossSection downstreamSection,
            double discharge,
            double roughness) {
        double averageConveyance = 0.5 * (
                conveyance(upstreamDepth, upstreamSection, roughness)
                        + conveyance(downstreamDepth, downstreamSection, roughness));
        double averageFrictionSlope = square(discharge / averageConveyance);
        return stationLength * averageFrictionSlope;
    }

    private static double specificEnergy(double depth, CrossSection section, double discharge) {
        double area = positiveFinite(section.area(depth), "cross-sectional area");
        double velocity = discharge / area;
        return depth + velocity * velocity / (2.0 * GRAVITY_METRES_PER_SECOND_SQUARED);
    }

    private static double conveyance(double depth, CrossSection section, double roughness) {
        double area = positiveFinite(section.area(depth), "cross-sectional area");
        double perimeter = positiveFinite(section.wettedPerimeter(depth), "wetted perimeter");
        double hydraulicRadius = area / perimeter;
        return positiveFinite(
                area * Math.pow(hydraulicRadius, 2.0 / 3.0) / roughness,
                "Manning conveyance");
    }

    private static double criticalDepth(double discharge, CrossSection section) {
        double upper = 1.0;
        int expansions = 0;
        while (froudeSquared(upper, section, discharge) > 1.0
                && expansions < MAXIMUM_BRACKET_EXPANSIONS) {
            upper *= 2.0;
            expansions++;
        }
        if (froudeSquared(upper, section, discharge) > 1.0) {
            throw new IllegalArgumentException("cross section could not bracket critical depth");
        }

        double lower = upper * 0.5;
        int contractions = 0;
        while (froudeSquared(lower, section, discharge) <= 1.0
                && contractions < MAXIMUM_BRACKET_EXPANSIONS) {
            upper = lower;
            lower *= 0.5;
            contractions++;
        }
        if (!(froudeSquared(lower, section, discharge) > 1.0)) {
            throw new IllegalArgumentException("cross section could not bracket critical depth");
        }
        return bisect(lower, upper, depth -> froudeSquared(depth, section, discharge) - 1.0);
    }

    private static double froudeSquared(double depth, CrossSection section, double discharge) {
        double area = positiveFinite(section.area(depth), "cross-sectional area");
        double topWidth = positiveFinite(section.topWidth(depth), "top width");
        double value = discharge * discharge * topWidth
                / (GRAVITY_METRES_PER_SECOND_SQUARED * area * area * area);
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Froude number must be finite");
        }
        return value;
    }

    private static double square(double value) {
        return value * value;
    }

    private static double bisect(double lower, double upper, Residual residual) {
        double lowerResidual = residual.at(lower);
        for (int iteration = 0; iteration < MAXIMUM_BISECTION_ITERATIONS; iteration++) {
            double middle = lower + (upper - lower) * 0.5;
            if (middle == lower || middle == upper || upper - lower <= DEPTH_TOLERANCE_METRES) {
                return middle;
            }
            double middleResidual = residual.at(middle);
            if ((middleResidual > 0.0) == (lowerResidual > 0.0)) {
                lower = middle;
                lowerResidual = middleResidual;
            } else {
                upper = middle;
            }
        }
        return lower + (upper - lower) * 0.5;
    }

    private static double positiveFinite(double value, String name) {
        if (!(value > 0.0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
        return value;
    }

    private static void requirePositiveFinite(double value, String name) {
        positiveFinite(value, name);
    }

    @FunctionalInterface
    private interface Residual {
        double at(double value);
    }
}
