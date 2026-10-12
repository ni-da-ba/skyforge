package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * Computes a steady gradually varied water-surface profile with the standard-step energy method
 * for a unit-width channel.
 *
 * <p>Discharge is per unit width (m²/s), depth and station are metres, and Manning roughness is in
 * SI units. The unit-width friction slope is Sf = n² q² / h^(10/3), matching the wide-channel
 * shallow-water formulation used by the SWASHES MacDonald benchmark profiles. This is deliberately
 * narrower than the finite-section Manning normal-depth primitive: it does not model transitions,
 * junctions, lateral inflow, or unsteady flow.
 *
 * <p>Stations must be strictly increasing in the upstream-to-downstream direction. A subcritical
 * profile is marched upstream from a downstream depth boundary; a supercritical profile is marched
 * downstream from an upstream depth boundary. Every step must have a same-regime energy root.
 * Critical crossings or unbracketed steps fail closed.
 *
 * <p>Reference method: standard-step energy equation as documented in USACE HEC-RAS 1D steady-flow
 * theory. Exact profile/bed controls use the smooth subcritical and supercritical MacDonald cases
 * assembled by Delestre et al. in SWASHES, Section 3.2.
 */
public final class SkyIslandUnitWidthStandardStepSolver {
    private static final double GRAVITY_METRES_PER_SECOND_SQUARED = 9.81;
    private static final int MAXIMUM_BRACKET_EXPANSIONS = 128;
    private static final int MAXIMUM_BISECTION_ITERATIONS = 128;
    private static final double DEPTH_TOLERANCE_METRES = 1.0e-10;

    private SkyIslandUnitWidthStandardStepSolver() {}

    public enum FlowRegime {
        SUBCRITICAL,
        SUPERCRITICAL
    }

    /**
     * Solves the water depths at the supplied stations, preserving the requested flow regime.
     *
     * @param stationsMetres strictly increasing upstream-to-downstream stations
     * @param bedElevationsMetres bed elevation at each station
     * @param unitDischargeSquareMetresPerSecond positive discharge per unit channel width
     * @param manningRoughness positive SI Manning roughness
     * @param boundaryDepthMetres subcritical downstream or supercritical upstream boundary depth
     * @param regime flow regime to preserve at every step
     * @return depth at each station, in metres
     * @throws IllegalArgumentException for malformed inputs or a step without a same-regime root
     */
    public static double[] solve(
            double[] stationsMetres,
            double[] bedElevationsMetres,
            double unitDischargeSquareMetresPerSecond,
            double manningRoughness,
            double boundaryDepthMetres,
            FlowRegime regime) {
        Objects.requireNonNull(stationsMetres, "stationsMetres");
        Objects.requireNonNull(bedElevationsMetres, "bedElevationsMetres");
        Objects.requireNonNull(regime, "regime");
        requirePositiveFinite(unitDischargeSquareMetresPerSecond, "unit discharge");
        requirePositiveFinite(manningRoughness, "Manning roughness");
        requirePositiveFinite(boundaryDepthMetres, "boundary depth");
        if (stationsMetres.length < 2 || stationsMetres.length != bedElevationsMetres.length) {
            throw new IllegalArgumentException("station and bed arrays must have the same length of at least two");
        }
        for (int i = 0; i < stationsMetres.length; i++) {
            if (!Double.isFinite(stationsMetres[i]) || !Double.isFinite(bedElevationsMetres[i])) {
                throw new IllegalArgumentException("station and bed elevations must be finite");
            }
            if (i > 0 && !(stationsMetres[i] > stationsMetres[i - 1])) {
                throw new IllegalArgumentException("stations must be strictly increasing");
            }
        }

        double criticalDepth = criticalDepth(unitDischargeSquareMetresPerSecond);
        if (regime == FlowRegime.SUBCRITICAL && !(boundaryDepthMetres > criticalDepth)) {
            throw new IllegalArgumentException("subcritical boundary depth must exceed critical depth");
        }
        if (regime == FlowRegime.SUPERCRITICAL && !(boundaryDepthMetres < criticalDepth)) {
            throw new IllegalArgumentException("supercritical boundary depth must be below critical depth");
        }

        double[] depths = new double[stationsMetres.length];
        if (regime == FlowRegime.SUBCRITICAL) {
            int last = depths.length - 1;
            depths[last] = boundaryDepthMetres;
            for (int i = last - 1; i >= 0; i--) {
                double stationLength = stationsMetres[i + 1] - stationsMetres[i];
                depths[i] = solveSubcriticalStep(
                        stationLength,
                        bedElevationsMetres[i],
                        bedElevationsMetres[i + 1],
                        depths[i + 1],
                        unitDischargeSquareMetresPerSecond,
                        manningRoughness,
                        criticalDepth);
            }
        } else {
            depths[0] = boundaryDepthMetres;
            for (int i = 0; i < depths.length - 1; i++) {
                double stationLength = stationsMetres[i + 1] - stationsMetres[i];
                depths[i + 1] = solveSupercriticalStep(
                        stationLength,
                        bedElevationsMetres[i],
                        bedElevationsMetres[i + 1],
                        depths[i],
                        unitDischargeSquareMetresPerSecond,
                        manningRoughness,
                        criticalDepth);
            }
        }
        return depths;
    }

    private static double solveSubcriticalStep(
            double stationLength,
            double upstreamBed,
            double downstreamBed,
            double downstreamDepth,
            double unitDischarge,
            double roughness,
            double criticalDepth) {
        double downstreamEnergy = downstreamBed + specificEnergy(downstreamDepth, unitDischarge);
        double lower = criticalDepth;
        double lowerResidual = subcriticalResidual(
                lower, stationLength, upstreamBed, downstreamEnergy, downstreamDepth, unitDischarge, roughness);
        if (lowerResidual > 0.0) {
            throw new IllegalArgumentException("subcritical standard step has no bracketed energy root");
        }

        double upper = Math.max(Math.max(criticalDepth * 2.0, downstreamDepth), 1.0);
        double upperResidual = subcriticalResidual(
                upper, stationLength, upstreamBed, downstreamEnergy, downstreamDepth, unitDischarge, roughness);
        int expansions = 0;
        while (upperResidual < 0.0 && expansions < MAXIMUM_BRACKET_EXPANSIONS) {
            upper *= 2.0;
            upperResidual = subcriticalResidual(
                    upper, stationLength, upstreamBed, downstreamEnergy, downstreamDepth, unitDischarge, roughness);
            expansions++;
        }
        if (upperResidual < 0.0) {
            throw new IllegalArgumentException("subcritical standard step could not bracket energy root");
        }
        return bisect(lower, upper, value -> subcriticalResidual(
                value, stationLength, upstreamBed, downstreamEnergy, downstreamDepth, unitDischarge, roughness));
    }

    private static double subcriticalResidual(
            double upstreamDepth,
            double stationLength,
            double upstreamBed,
            double downstreamEnergy,
            double downstreamDepth,
            double unitDischarge,
            double roughness) {
        double frictionLoss = stationLength
                * (frictionSlope(upstreamDepth, unitDischarge, roughness)
                        + frictionSlope(downstreamDepth, unitDischarge, roughness))
                * 0.5;
        return upstreamBed + specificEnergy(upstreamDepth, unitDischarge) - downstreamEnergy - frictionLoss;
    }

    private static double solveSupercriticalStep(
            double stationLength,
            double upstreamBed,
            double downstreamBed,
            double upstreamDepth,
            double unitDischarge,
            double roughness,
            double criticalDepth) {
        double upstreamEnergy = upstreamBed + specificEnergy(upstreamDepth, unitDischarge);
        double upper = criticalDepth;
        double upperResidual = supercriticalResidual(
                upper, stationLength, downstreamBed, upstreamEnergy, upstreamDepth, unitDischarge, roughness);
        if (upperResidual > 0.0) {
            throw new IllegalArgumentException("supercritical standard step has no bracketed energy root");
        }

        double lower = criticalDepth * 0.5;
        double lowerResidual = supercriticalResidual(
                lower, stationLength, downstreamBed, upstreamEnergy, upstreamDepth, unitDischarge, roughness);
        int contractions = 0;
        while (lowerResidual <= 0.0 && contractions < MAXIMUM_BRACKET_EXPANSIONS) {
            lower *= 0.1;
            lowerResidual = supercriticalResidual(
                    lower, stationLength, downstreamBed, upstreamEnergy, upstreamDepth, unitDischarge, roughness);
            contractions++;
        }
        if (!(lowerResidual > 0.0)) {
            throw new IllegalArgumentException("supercritical standard step could not bracket energy root");
        }
        return bisect(lower, upper, value -> supercriticalResidual(
                value, stationLength, downstreamBed, upstreamEnergy, upstreamDepth, unitDischarge, roughness));
    }

    private static double supercriticalResidual(
            double downstreamDepth,
            double stationLength,
            double downstreamBed,
            double upstreamEnergy,
            double upstreamDepth,
            double unitDischarge,
            double roughness) {
        double frictionLoss = stationLength
                * (frictionSlope(upstreamDepth, unitDischarge, roughness)
                        + frictionSlope(downstreamDepth, unitDischarge, roughness))
                * 0.5;
        return downstreamBed + specificEnergy(downstreamDepth, unitDischarge) + frictionLoss - upstreamEnergy;
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

    private static double specificEnergy(double depth, double unitDischarge) {
        double velocity = unitDischarge / depth;
        return depth + velocity * velocity / (2.0 * GRAVITY_METRES_PER_SECOND_SQUARED);
    }

    private static double frictionSlope(double depth, double unitDischarge, double roughness) {
        return roughness * roughness * unitDischarge * unitDischarge / Math.pow(depth, 10.0 / 3.0);
    }

    private static double criticalDepth(double unitDischarge) {
        return Math.cbrt(
                unitDischarge * unitDischarge / GRAVITY_METRES_PER_SECOND_SQUARED);
    }

    private static void requirePositiveFinite(double value, String name) {
        if (!(value > 0.0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }

    @FunctionalInterface
    private interface Residual {
        double at(double value);
    }
}
