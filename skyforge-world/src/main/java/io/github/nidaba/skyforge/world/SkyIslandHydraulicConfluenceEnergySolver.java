package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/**
 * Energy-based closure for a subcritical downstream state at a combining-flow junction.
 *
 * <p>The upstream total head is discharge-weighted. The explicit local-loss coefficient is
 * applied to downstream velocity head; it is never inferred from terrain or D2. Callers must
 * provide the solved terminal state for each incoming branch and an outlet section whose
 * discharge equals the sum of those branch discharges.
 */
public final class SkyIslandHydraulicConfluenceEnergySolver {
    private static final double MINIMUM_DEPTH_METERS = 1.0e-12;

    private SkyIslandHydraulicConfluenceEnergySolver() {}

    public record IncomingState(
            SkyIslandGraduallyVariedFlowSolver.CrossSection section,
            double depthMeters) {
        public IncomingState {
            section = Objects.requireNonNull(section, "section");
            if (!Double.isFinite(depthMeters) || depthMeters <= 0.0) {
                throw new IllegalArgumentException("incoming depth must be finite and positive");
            }
        }
    }

    public record Result(
            double totalDischargeCubicMetersPerSecond,
            double dischargeWeightedIncomingTotalHeadMeters,
            double downstreamDepthMeters,
            double downstreamWaterSurfaceElevationMeters,
            double downstreamVelocityMetersPerSecond,
            double downstreamFroudeNumber,
            double junctionLossMeters,
            double energyResidualMeters,
            int iterations) {
        public Result {
            if (!Double.isFinite(totalDischargeCubicMetersPerSecond)
                    || totalDischargeCubicMetersPerSecond <= 0.0
                    || !Double.isFinite(dischargeWeightedIncomingTotalHeadMeters)
                    || !Double.isFinite(downstreamDepthMeters)
                    || downstreamDepthMeters <= 0.0
                    || !Double.isFinite(downstreamWaterSurfaceElevationMeters)
                    || !Double.isFinite(downstreamVelocityMetersPerSecond)
                    || downstreamVelocityMetersPerSecond <= 0.0
                    || !Double.isFinite(downstreamFroudeNumber)
                    || downstreamFroudeNumber <= 0.0
                    || downstreamFroudeNumber >= 1.0
                    || !Double.isFinite(junctionLossMeters)
                    || junctionLossMeters < 0.0
                    || !Double.isFinite(energyResidualMeters)
                    || energyResidualMeters < 0.0
                    || iterations < 1) {
                throw new IllegalArgumentException("confluence energy result must be finite and subcritical");
            }
        }
    }

    /**
     * Solves the downstream specific depth from the steady energy equation:
     *
     * <pre>
     * sum(Qi * Hi) / sum(Qi) = zDown + yDown
     *     + (alpha + K) * VDown^2 / (2g)
     * </pre>
     *
     * <p>Here Hi is each incoming branch's total head, alpha is the calibrated energy coefficient,
     * and K is an explicit, non-negative junction-loss coefficient referenced to downstream
     * velocity head. The root is restricted to the subcritical branch. The method fails closed
     * when discharge continuity or a subcritical energy root is unavailable.
     */
    public static Result solve(
            List<IncomingState> incoming,
            SkyIslandGraduallyVariedFlowSolver.CrossSection downstream,
            SkyIslandGraduallyVariedFlowSolver.Parameters parameters,
            double junctionLossCoefficient) {
        Objects.requireNonNull(incoming, "incoming");
        Objects.requireNonNull(downstream, "downstream");
        Objects.requireNonNull(parameters, "parameters");
        if (incoming.size() < 2) {
            throw new IllegalArgumentException("confluence requires at least two incoming branches");
        }
        if (!Double.isFinite(junctionLossCoefficient) || junctionLossCoefficient < 0.0) {
            throw new IllegalArgumentException("junction loss coefficient must be finite and non-negative");
        }

        double totalDischarge = 0.0;
        double weightedHead = 0.0;
        for (IncomingState state : incoming) {
            Objects.requireNonNull(state, "incoming state");
            SkyIslandGraduallyVariedFlowSolver.CrossSection section = state.section();
            double area = area(section, state.depthMeters());
            double velocity = section.dischargeCubicMetersPerSecond() / area;
            double totalHead = section.bedElevationMeters() + state.depthMeters()
                    + parameters.energyCoefficient() * velocity * velocity
                            / (2.0 * parameters.gravityMetersPerSecondSquared());
            totalDischarge += section.dischargeCubicMetersPerSecond();
            weightedHead += section.dischargeCubicMetersPerSecond() * totalHead;
        }
        double continuityTolerance = Math.max(
                1.0e-9, 100.0 * parameters.relativeTolerance())
                * Math.max(1.0, totalDischarge);
        if (Math.abs(downstream.dischargeCubicMetersPerSecond() - totalDischarge)
                > continuityTolerance) {
            throw new IllegalArgumentException(
                    "downstream discharge must equal the sum of incoming discharges");
        }
        double incomingHead = weightedHead / totalDischarge;
        double effectiveEnergyCoefficient =
                parameters.energyCoefficient() + junctionLossCoefficient;
        double criticalDepth = criticalDepth(
                downstream, effectiveEnergyCoefficient, parameters);
        double lowerResidual = energyResidual(
                downstream, criticalDepth, incomingHead,
                effectiveEnergyCoefficient, parameters);
        double energyTolerance = parameters.relativeTolerance()
                * Math.max(1.0, Math.abs(incomingHead));
        if (lowerResidual > energyTolerance) {
            throw new IllegalStateException(
                    "incoming total head is below the minimum subcritical junction energy");
        }

        double lower = criticalDepth;
        double upper = Math.max(2.0 * lower, lower + 1.0);
        double upperResidual = energyResidual(
                downstream, upper, incomingHead, effectiveEnergyCoefficient, parameters);
        int expansion = 0;
        while (upperResidual < 0.0 && expansion++ < parameters.maximumIterations()) {
            upper *= 2.0;
            if (!Double.isFinite(upper)) {
                break;
            }
            upperResidual = energyResidual(
                    downstream, upper, incomingHead, effectiveEnergyCoefficient, parameters);
        }
        if (!(upperResidual >= 0.0) || !Double.isFinite(upperResidual)) {
            throw new IllegalStateException("failed to bracket subcritical confluence energy depth");
        }

        double depth = Double.NaN;
        int iterations = 0;
        for (; iterations < parameters.maximumIterations(); iterations++) {
            double middle = lower + 0.5 * (upper - lower);
            double residual = energyResidual(
                    downstream, middle, incomingHead, effectiveEnergyCoefficient, parameters);
            if (Math.abs(residual) <= energyTolerance
                    || upper - lower <= parameters.relativeTolerance() * Math.max(1.0, middle)) {
                depth = middle;
                iterations++;
                break;
            }
            if (residual < 0.0) {
                lower = middle;
            } else {
                upper = middle;
            }
        }
        if (!Double.isFinite(depth)) {
            throw new IllegalStateException("subcritical confluence energy iteration did not converge");
        }

        double area = area(downstream, depth);
        double velocity = downstream.dischargeCubicMetersPerSecond() / area;
        double froude = SkyIslandGraduallyVariedFlowSolver.froudeNumber(
                downstream, depth, parameters);
        if (!(froude < 1.0 - 10.0 * parameters.relativeTolerance())) {
            throw new IllegalStateException("confluence energy root is not strictly subcritical");
        }
        double loss = junctionLossCoefficient * velocity * velocity
                / (2.0 * parameters.gravityMetersPerSecondSquared());
        double residual = Math.abs(energyResidual(
                downstream, depth, incomingHead, effectiveEnergyCoefficient, parameters));
        if (residual > Math.max(energyTolerance, 1.0e-9)) {
            throw new IllegalStateException("confluence energy residual exceeds tolerance");
        }
        return new Result(
                totalDischarge,
                incomingHead,
                depth,
                downstream.bedElevationMeters() + depth,
                velocity,
                froude,
                loss,
                residual,
                iterations);
    }

    private static double energyResidual(
            SkyIslandGraduallyVariedFlowSolver.CrossSection section,
            double depth,
            double incomingHead,
            double energyCoefficient,
            SkyIslandGraduallyVariedFlowSolver.Parameters parameters) {
        double velocity = section.dischargeCubicMetersPerSecond() / area(section, depth);
        double downstreamHead = section.bedElevationMeters() + depth
                + energyCoefficient * velocity * velocity
                        / (2.0 * parameters.gravityMetersPerSecondSquared());
        return downstreamHead - incomingHead;
    }

    private static double criticalDepth(
            SkyIslandGraduallyVariedFlowSolver.CrossSection section,
            double energyCoefficient,
            SkyIslandGraduallyVariedFlowSolver.Parameters parameters) {
        double lower = Math.max(MINIMUM_DEPTH_METERS, Math.ulp(section.bottomWidthMeters()));
        double upper = Math.max(1.0, section.bottomWidthMeters());
        int expansion = 0;
        while (effectiveFroudeSquared(section, upper, energyCoefficient, parameters) > 1.0
                && expansion++ < parameters.maximumIterations()) {
            upper *= 2.0;
        }
        if (!Double.isFinite(upper)
                || effectiveFroudeSquared(section, lower, energyCoefficient, parameters) < 1.0) {
            throw new IllegalStateException("could not bracket critical junction energy depth");
        }
        for (int iteration = 0; iteration < parameters.maximumIterations(); iteration++) {
            double middle = lower + 0.5 * (upper - lower);
            if (effectiveFroudeSquared(section, middle, energyCoefficient, parameters) > 1.0) {
                lower = middle;
            } else {
                upper = middle;
            }
            if (upper - lower <= parameters.relativeTolerance() * Math.max(1.0, middle)) {
                return lower + 0.5 * (upper - lower);
            }
        }
        throw new IllegalStateException("critical junction energy depth iteration did not converge");
    }

    private static double effectiveFroudeSquared(
            SkyIslandGraduallyVariedFlowSolver.CrossSection section,
            double depth,
            double energyCoefficient,
            SkyIslandGraduallyVariedFlowSolver.Parameters parameters) {
        double area = area(section, depth);
        double topWidth = section.bottomWidthMeters()
                + 2.0 * section.sideSlopeHorizontalToVertical() * depth;
        return energyCoefficient * section.dischargeCubicMetersPerSecond()
                * section.dischargeCubicMetersPerSecond() * topWidth
                / (parameters.gravityMetersPerSecondSquared() * area * area * area);
    }

    private static double area(
            SkyIslandGraduallyVariedFlowSolver.CrossSection section,
            double depth) {
        return depth * (section.bottomWidthMeters()
                + section.sideSlopeHorizontalToVertical() * depth);
    }
}
