package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * Hydrostatic specific-force closure for a short, prismatic hydraulic jump.
 *
 * <p>This computes conjugate depths only; callers still need explicit upstream and downstream
 * controls and must locate the jump within the reach. The jump is assumed short enough that bed
 * force, friction, and distributed lateral inflow momentum across the jump are negligible.
 */
public final class SkyIslandHydraulicJumpSolver {
    private SkyIslandHydraulicJumpSolver() {}

    public record Result(
            double upstreamDepthMeters,
            double downstreamDepthMeters,
            double upstreamFroudeNumber,
            double downstreamFroudeNumber,
            double energyLossMeters,
            double specificForceResidualCubicMeters) {}

    /**
     * Solves the subcritical conjugate depth from momentum (specific-force) conservation.
     * Momentum coefficient is explicitly unity; use is limited to a single trapezoidal section
     * with effectively unchanged discharge across the jump.
     */
    public static Result solveConjugateDepth(
            SkyIslandGraduallyVariedFlowSolver.CrossSection section,
            double upstreamDepthMeters,
            double gravityMetersPerSecondSquared,
            double relativeTolerance,
            int maximumIterations) {
        Objects.requireNonNull(section, "section");
        if (!Double.isFinite(upstreamDepthMeters) || upstreamDepthMeters <= 0.0
                || !Double.isFinite(gravityMetersPerSecondSquared)
                || gravityMetersPerSecondSquared <= 0.0
                || !Double.isFinite(relativeTolerance)
                || relativeTolerance <= 0.0 || relativeTolerance >= 1.0
                || maximumIterations < 1) {
            throw new IllegalArgumentException("hydraulic-jump inputs must be finite and physical");
        }

        double upstreamFroude = froudeNumber(section, upstreamDepthMeters, gravityMetersPerSecondSquared);
        if (!(upstreamFroude > 1.0 + 10.0 * relativeTolerance)) {
            throw new IllegalArgumentException("hydraulic jump requires a supercritical upstream state");
        }
        double upstreamForce = specificForce(section, upstreamDepthMeters, gravityMetersPerSecondSquared);
        double criticalDepth = criticalDepth(section, gravityMetersPerSecondSquared, relativeTolerance, maximumIterations);
        double lower = criticalDepth;
        double lowerResidual = specificForce(section, lower, gravityMetersPerSecondSquared) - upstreamForce;
        double forceTolerance = relativeTolerance * Math.max(1.0, Math.abs(upstreamForce));
        if (!(lowerResidual < -forceTolerance)) {
            throw new IllegalArgumentException("supercritical state has no distinct conjugate-depth branch");
        }

        double upper = Math.max(2.0 * upstreamDepthMeters, 2.0 * criticalDepth);
        double upperResidual = specificForce(section, upper, gravityMetersPerSecondSquared) - upstreamForce;
        int expansion = 0;
        while (upperResidual < 0.0 && expansion++ < maximumIterations) {
            upper *= 2.0;
            if (!Double.isFinite(upper)) {
                break;
            }
            upperResidual = specificForce(section, upper, gravityMetersPerSecondSquared) - upstreamForce;
        }
        if (!(upperResidual >= 0.0) || !Double.isFinite(upperResidual)) {
            throw new IllegalStateException("failed to bracket hydraulic-jump conjugate depth");
        }

        double downstreamDepth = Double.NaN;
        for (int iteration = 0; iteration < maximumIterations; iteration++) {
            double middle = lower + 0.5 * (upper - lower);
            double residual = specificForce(section, middle, gravityMetersPerSecondSquared) - upstreamForce;
            if (Math.abs(residual) <= forceTolerance
                    || upper - lower <= relativeTolerance * Math.max(1.0, middle)) {
                downstreamDepth = middle;
                break;
            }
            if (residual < 0.0) {
                lower = middle;
            } else {
                upper = middle;
            }
        }
        if (!Double.isFinite(downstreamDepth)) {
            throw new IllegalStateException("hydraulic-jump conjugate-depth iteration did not converge");
        }

        double downstreamFroude =
                froudeNumber(section, downstreamDepth, gravityMetersPerSecondSquared);
        if (!(downstreamFroude < 1.0 - 10.0 * relativeTolerance)) {
            throw new IllegalStateException("conjugate depth did not resolve to a subcritical state");
        }
        double energyLoss = specificEnergy(section, upstreamDepthMeters, gravityMetersPerSecondSquared)
                - specificEnergy(section, downstreamDepth, gravityMetersPerSecondSquared);
        if (!Double.isFinite(energyLoss) || energyLoss <= 0.0) {
            throw new IllegalStateException("hydraulic jump must dissipate positive specific energy");
        }
        double forceResidual =
                specificForce(section, downstreamDepth, gravityMetersPerSecondSquared) - upstreamForce;
        return new Result(
                upstreamDepthMeters,
                downstreamDepth,
                upstreamFroude,
                downstreamFroude,
                energyLoss,
                forceResidual);
    }

    public static double specificForce(
            SkyIslandGraduallyVariedFlowSolver.CrossSection section,
            double depthMeters,
            double gravityMetersPerSecondSquared) {
        Objects.requireNonNull(section, "section");
        if (!Double.isFinite(depthMeters) || depthMeters <= 0.0
                || !Double.isFinite(gravityMetersPerSecondSquared)
                || gravityMetersPerSecondSquared <= 0.0) {
            throw new IllegalArgumentException("specific-force inputs must be finite and positive");
        }
        double area = area(section, depthMeters);
        double pressureMoment = section.bottomWidthMeters() * depthMeters * depthMeters / 2.0
                + section.sideSlopeHorizontalToVertical()
                        * depthMeters * depthMeters * depthMeters / 3.0;
        return section.dischargeCubicMetersPerSecond()
                        * section.dischargeCubicMetersPerSecond()
                        / (gravityMetersPerSecondSquared * area)
                + pressureMoment;
    }

    private static double criticalDepth(
            SkyIslandGraduallyVariedFlowSolver.CrossSection section,
            double gravity,
            double tolerance,
            int maximumIterations) {
        double lower = Math.max(1.0e-12, Math.ulp(section.bottomWidthMeters()));
        double upper = Math.max(1.0, section.bottomWidthMeters());
        while (froudeSquared(section, upper, gravity) > 1.0) {
            upper *= 2.0;
            if (!Double.isFinite(upper)) {
                throw new IllegalStateException("could not bracket critical depth for hydraulic jump");
            }
        }
        for (int iteration = 0; iteration < maximumIterations; iteration++) {
            double middle = lower + 0.5 * (upper - lower);
            if (froudeSquared(section, middle, gravity) > 1.0) {
                lower = middle;
            } else {
                upper = middle;
            }
            if (upper - lower <= tolerance * Math.max(1.0, middle)) {
                return 0.5 * (lower + upper);
            }
        }
        throw new IllegalStateException("critical-depth iteration did not converge for hydraulic jump");
    }

    private static double froudeNumber(
            SkyIslandGraduallyVariedFlowSolver.CrossSection section, double depth, double gravity) {
        return Math.sqrt(froudeSquared(section, depth, gravity));
    }

    private static double froudeSquared(
            SkyIslandGraduallyVariedFlowSolver.CrossSection section, double depth, double gravity) {
        double area = area(section, depth);
        double topWidth = section.bottomWidthMeters()
                + 2.0 * section.sideSlopeHorizontalToVertical() * depth;
        return section.dischargeCubicMetersPerSecond()
                * section.dischargeCubicMetersPerSecond() * topWidth
                / (gravity * area * area * area);
    }

    private static double specificEnergy(
            SkyIslandGraduallyVariedFlowSolver.CrossSection section, double depth, double gravity) {
        double area = area(section, depth);
        double velocity = section.dischargeCubicMetersPerSecond() / area;
        return depth + velocity * velocity / (2.0 * gravity);
    }

    private static double area(
            SkyIslandGraduallyVariedFlowSolver.CrossSection section, double depth) {
        return depth * (section.bottomWidthMeters()
                + section.sideSlopeHorizontalToVertical() * depth);
    }
}
