package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Standard-step gradually-varied-flow solver for a single, prismatic-or-gradually-varied
 * subcritical ordinary reach.
 *
 * <p>Distances, elevations, widths, and depths are metres; discharge is cubic metres per second;
 * roughness is Manning's SI coefficient in s/m^(1/3). Each section must carry the same discharge.
 * A downstream depth boundary is marched upstream by solving the specific-energy equation with
 * arithmetic-mean friction slope between adjacent sections. Junctions, lateral inflows, hydraulic
 * jumps, and supercritical controls are deliberately outside this solver's domain.
 */
public final class SkyIslandGraduallyVariedFlowSolver {
    private SkyIslandGraduallyVariedFlowSolver() {}

    public record CrossSection(
            double chainageMeters,
            double bedElevationMeters,
            double dischargeCubicMetersPerSecond,
            double bottomWidthMeters,
            double sideSlopeHorizontalToVertical) {
        public CrossSection {
            if (!Double.isFinite(chainageMeters)
                    || !Double.isFinite(bedElevationMeters)
                    || !Double.isFinite(dischargeCubicMetersPerSecond)
                    || !Double.isFinite(bottomWidthMeters)
                    || !Double.isFinite(sideSlopeHorizontalToVertical)
                    || dischargeCubicMetersPerSecond <= 0.0
                    || bottomWidthMeters <= 0.0
                    || sideSlopeHorizontalToVertical < 0.0) {
                throw new IllegalArgumentException("cross-section values must be finite and physical");
            }
        }
    }

    public record Parameters(
            double manningRoughness,
            double energyCoefficient,
            double gravityMetersPerSecondSquared,
            double relativeTolerance,
            int maximumIterations) {
        public Parameters {
            if (!Double.isFinite(manningRoughness) || manningRoughness <= 0.0
                    || !Double.isFinite(energyCoefficient) || energyCoefficient <= 0.0
                    || !Double.isFinite(gravityMetersPerSecondSquared)
                    || gravityMetersPerSecondSquared <= 0.0
                    || !Double.isFinite(relativeTolerance)
                    || relativeTolerance <= 0.0 || relativeTolerance >= 1.0
                    || maximumIterations < 1) {
                throw new IllegalArgumentException("GVF parameters must be finite and positive");
            }
        }
    }

    public record ProfilePoint(
            CrossSection section,
            double depthMeters,
            double waterSurfaceElevationMeters,
            double velocityMetersPerSecond,
            double frictionSlope,
            double froudeNumber) {}

    public record Result(List<ProfilePoint> points, double maximumEnergyResidualMeters) {
        public Result {
            points = List.copyOf(points);
            if (points.size() < 2
                    || !Double.isFinite(maximumEnergyResidualMeters)
                    || maximumEnergyResidualMeters < 0.0) {
                throw new IllegalArgumentException("GVF result must contain a valid reach profile");
            }
        }
    }

    /**
     * Solves a subcritical profile from a specified downstream depth, returning stations in
     * increasing upstream chainage order. The supplied sections must be strictly ordered and have
     * constant discharge; split reaches at confluences, withdrawals, or other flow discontinuities.
     */
    public static Result solveSubcriticalUpstream(
            List<CrossSection> sections,
            double downstreamDepthMeters,
            Parameters parameters) {
        Objects.requireNonNull(sections, "sections");
        Objects.requireNonNull(parameters, "parameters");
        if (sections.size() < 2) {
            throw new IllegalArgumentException("GVF reach requires at least two cross sections");
        }
        if (!Double.isFinite(downstreamDepthMeters) || downstreamDepthMeters <= 0.0) {
            throw new IllegalArgumentException("downstream depth must be finite and positive");
        }
        List<CrossSection> reach = List.copyOf(sections);
        double discharge = reach.getFirst().dischargeCubicMetersPerSecond();
        for (int i = 0; i < reach.size(); i++) {
            CrossSection section = Objects.requireNonNull(reach.get(i), "section");
            if (i > 0) {
                CrossSection previous = reach.get(i - 1);
                if (!(section.chainageMeters() > previous.chainageMeters())) {
                    throw new IllegalArgumentException("section chainage must increase strictly downstream");
                }
                double dischargeScale = Math.max(1.0, Math.abs(discharge));
                if (Math.abs(section.dischargeCubicMetersPerSecond() - discharge)
                        > parameters.relativeTolerance() * dischargeScale) {
                    throw new IllegalArgumentException(
                            "discharge changes within an ordinary reach; split at lateral-flow transitions");
                }
            }
        }

        int last = reach.size() - 1;
        double criticalDownstream = criticalDepth(reach.get(last), parameters);
        if (!(downstreamDepthMeters > criticalDownstream
                * (1.0 + 10.0 * parameters.relativeTolerance()))) {
            throw new IllegalArgumentException(
                    "downstream boundary is critical or supercritical; subcritical solver requires downstream control");
        }

        double[] depths = new double[reach.size()];
        depths[last] = downstreamDepthMeters;
        double maximumResidual = 0.0;
        for (int i = last - 1; i >= 0; i--) {
            CrossSection upstream = reach.get(i);
            CrossSection downstream = reach.get(i + 1);
            double spacing = downstream.chainageMeters() - upstream.chainageMeters();
            double downstreamEnergy = specificEnergy(downstream, depths[i + 1], parameters);
            double downstreamFriction = frictionSlope(downstream, depths[i + 1], parameters);
            double critical = criticalDepth(upstream, parameters);
            double lower = critical * (1.0 + 10.0 * parameters.relativeTolerance());
            double lowerResidual = energyResidual(
                    upstream, downstream, depths[i + 1], lower, spacing,
                    downstreamEnergy, downstreamFriction, parameters);
            if (lowerResidual > energyTolerance(downstreamEnergy, parameters)) {
                throw new IllegalStateException(
                        "no subcritical standard-step solution before critical depth at section " + i);
            }

            double upper = Math.max(Math.max(depths[i + 1], lower) * 2.0, lower + 1.0);
            double upperResidual = energyResidual(
                    upstream, downstream, depths[i + 1], upper, spacing,
                    downstreamEnergy, downstreamFriction, parameters);
            int expansion = 0;
            while (upperResidual < 0.0 && expansion++ < parameters.maximumIterations()) {
                upper *= 2.0;
                if (!Double.isFinite(upper)) {
                    break;
                }
                upperResidual = energyResidual(
                        upstream, downstream, depths[i + 1], upper, spacing,
                        downstreamEnergy, downstreamFriction, parameters);
            }
            if (!(upperResidual >= 0.0) || !Double.isFinite(upperResidual)) {
                throw new IllegalStateException("failed to bracket subcritical standard-step depth at section " + i);
            }

            double root = Double.NaN;
            for (int iteration = 0; iteration < parameters.maximumIterations(); iteration++) {
                double middle = lower + 0.5 * (upper - lower);
                double residual = energyResidual(
                        upstream, downstream, depths[i + 1], middle, spacing,
                        downstreamEnergy, downstreamFriction, parameters);
                if (Math.abs(residual) <= energyTolerance(downstreamEnergy, parameters)
                        || upper - lower <= parameters.relativeTolerance() * Math.max(1.0, middle)) {
                    root = middle;
                    break;
                }
                if (residual < 0.0) {
                    lower = middle;
                } else {
                    upper = middle;
                }
            }
            if (!Double.isFinite(root)) {
                throw new IllegalStateException("standard-step depth iteration did not converge at section " + i);
            }
            depths[i] = root;
            maximumResidual = Math.max(maximumResidual, Math.abs(energyResidual(
                    upstream, downstream, depths[i + 1], root, spacing,
                    downstreamEnergy, downstreamFriction, parameters)));
        }

        List<ProfilePoint> points = new ArrayList<>(reach.size());
        for (int i = 0; i < reach.size(); i++) {
            CrossSection section = reach.get(i);
            double depth = depths[i];
            double area = area(section, depth);
            double velocity = section.dischargeCubicMetersPerSecond() / area;
            double friction = frictionSlope(section, depth, parameters);
            double froude = froudeNumber(section, depth, parameters);
            points.add(new ProfilePoint(
                    section,
                    depth,
                    section.bedElevationMeters() + depth,
                    velocity,
                    friction,
                    froude));
        }
        return new Result(points, maximumResidual);
    }

    private static double energyResidual(
            CrossSection upstream,
            CrossSection downstream,
            double downstreamDepth,
            double upstreamDepth,
            double spacing,
            double downstreamEnergy,
            double downstreamFriction,
            Parameters parameters) {
        double upstreamEnergy = specificEnergy(upstream, upstreamDepth, parameters);
        double upstreamFriction = frictionSlope(upstream, upstreamDepth, parameters);
        return upstreamEnergy - downstreamEnergy
                - 0.5 * (upstreamFriction + downstreamFriction) * spacing;
    }

    private static double specificEnergy(
            CrossSection section, double depth, Parameters parameters) {
        double velocity = section.dischargeCubicMetersPerSecond() / area(section, depth);
        return section.bedElevationMeters() + depth
                + parameters.energyCoefficient() * velocity * velocity
                        / (2.0 * parameters.gravityMetersPerSecondSquared());
    }

    private static double frictionSlope(
            CrossSection section, double depth, Parameters parameters) {
        double area = area(section, depth);
        double wettedPerimeter = wettedPerimeter(section, depth);
        double hydraulicRadius = area / wettedPerimeter;
        double conveyanceFactor = area * Math.pow(hydraulicRadius, 2.0 / 3.0);
        double scaledDischarge = parameters.manningRoughness()
                * section.dischargeCubicMetersPerSecond() / conveyanceFactor;
        return scaledDischarge * scaledDischarge;
    }

    private static double criticalDepth(CrossSection section, Parameters parameters) {
        double lower = Math.max(1.0e-12, Math.ulp(section.bottomWidthMeters()));
        double upper = Math.max(1.0, section.bottomWidthMeters());
        int expansion = 0;
        while (froudeSquared(section, upper, parameters) > 1.0
                && expansion++ < parameters.maximumIterations()) {
            upper *= 2.0;
        }
        if (!Double.isFinite(upper) || froudeSquared(section, lower, parameters) < 1.0) {
            throw new IllegalStateException("could not bracket critical depth");
        }
        for (int iteration = 0; iteration < parameters.maximumIterations(); iteration++) {
            double middle = lower + 0.5 * (upper - lower);
            if (froudeSquared(section, middle, parameters) > 1.0) {
                lower = middle;
            } else {
                upper = middle;
            }
            if (upper - lower <= parameters.relativeTolerance() * Math.max(1.0, middle)) {
                return 0.5 * (lower + upper);
            }
        }
        throw new IllegalStateException("critical-depth iteration did not converge");
    }

    private static double froudeNumber(
            CrossSection section, double depth, Parameters parameters) {
        return Math.sqrt(froudeSquared(section, depth, parameters));
    }

    private static double froudeSquared(
            CrossSection section, double depth, Parameters parameters) {
        double area = area(section, depth);
        double topWidth = section.bottomWidthMeters()
                + 2.0 * section.sideSlopeHorizontalToVertical() * depth;
        return parameters.energyCoefficient()
                * section.dischargeCubicMetersPerSecond()
                * section.dischargeCubicMetersPerSecond()
                * topWidth
                / (parameters.gravityMetersPerSecondSquared * area * area * area);
    }

    private static double area(CrossSection section, double depth) {
        return depth * (section.bottomWidthMeters()
                + section.sideSlopeHorizontalToVertical() * depth);
    }

    private static double wettedPerimeter(CrossSection section, double depth) {
        return section.bottomWidthMeters() + 2.0 * depth
                * Math.hypot(1.0, section.sideSlopeHorizontalToVertical());
    }

    private static double energyTolerance(double energy, Parameters parameters) {
        return parameters.relativeTolerance() * Math.max(1.0, Math.abs(energy));
    }
}
