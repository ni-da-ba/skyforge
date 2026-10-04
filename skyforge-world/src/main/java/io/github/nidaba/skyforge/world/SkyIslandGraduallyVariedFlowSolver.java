package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Standard-step gradually-varied-flow solver for a single, prismatic-or-gradually-varied
 * ordinary reach in one declared flow regime. Subcritical reaches are marched upstream from a
 * downstream control; supercritical reaches are marched downstream from an upstream control.
 *
 * <p>Distances, elevations, widths, and depths are metres; discharge is cubic metres per second;
 * roughness is Manning's SI coefficient in s/m^(1/3). Discharge may vary gradually by section to represent distributed inflow.
 * A downstream depth boundary is marched upstream by solving the specific-energy equation with
 * average-conveyance friction slope between adjacent sections. Junctions, lateral inflows, hydraulic
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
     * gradually varying discharge for distributed inflow; split reaches at confluences, drops, or abrupt flow discontinuities.
     */
    public static Result solveSubcriticalUpstream(
            List<CrossSection> sections,
            double downstreamDepthMeters,
            Parameters parameters) {
        return solveSubcriticalUpstreamInternal(
                sections, downstreamDepthMeters, parameters, false);
    }

    /**
     * Solves upstream from an explicit critical-depth control at a free outfall.
     *
     * <p>The caller must already have authority to model this terminal as a free outfall. The
     * control does not authorize or imply an edge outlet; terminal-fate policy remains separate.
     */
    public static Result solveSubcriticalUpstreamFromCriticalControl(
            List<CrossSection> sections, Parameters parameters) {
        Objects.requireNonNull(sections, "sections");
        Objects.requireNonNull(parameters, "parameters");
        List<CrossSection> reach = List.copyOf(sections);
        if (reach.size() < 2) {
            throw new IllegalArgumentException("GVF reach requires at least two cross sections");
        }
        double criticalDepth = criticalDepth(reach.getLast(), parameters);
        return solveSubcriticalUpstreamInternal(
                reach, criticalDepth, parameters, true);
    }

    private static Result solveSubcriticalUpstreamInternal(
            List<CrossSection> sections,
            double downstreamDepthMeters,
            Parameters parameters,
            boolean criticalDownstreamControl) {
        Objects.requireNonNull(sections, "sections");
        Objects.requireNonNull(parameters, "parameters");
        if (sections.size() < 2) {
            throw new IllegalArgumentException("GVF reach requires at least two cross sections");
        }
        if (!Double.isFinite(downstreamDepthMeters) || downstreamDepthMeters <= 0.0) {
            throw new IllegalArgumentException("downstream depth must be finite and positive");
        }
        List<CrossSection> reach = List.copyOf(sections);
        for (int i = 0; i < reach.size(); i++) {
            CrossSection section = Objects.requireNonNull(reach.get(i), "section");
            if (i > 0 && !(section.chainageMeters() > reach.get(i - 1).chainageMeters())) {
                throw new IllegalArgumentException("section chainage must increase strictly downstream");
            }
        }

        int last = reach.size() - 1;
        double criticalDownstream = criticalDepth(reach.get(last), parameters);
        if (!criticalDownstreamControl
                && !(downstreamDepthMeters > criticalDownstream
                        * (1.0 + 10.0 * parameters.relativeTolerance()))) {
            throw new IllegalArgumentException(
                    "downstream boundary is critical or supercritical; use the explicit critical-control entry point only for an authorized free outfall");
        }
        if (criticalDownstreamControl
                && Math.abs(downstreamDepthMeters - criticalDownstream)
                        > 10.0 * parameters.relativeTolerance()
                                * Math.max(1.0, criticalDownstream)) {
            throw new IllegalArgumentException("critical-control depth must equal computed critical depth");
        }

        double[] depths = new double[reach.size()];
        depths[last] = downstreamDepthMeters;
        double maximumResidual = 0.0;
        for (int i = last - 1; i >= 0; i--) {
            CrossSection upstream = reach.get(i);
            CrossSection downstream = reach.get(i + 1);
            double spacing = downstream.chainageMeters() - upstream.chainageMeters();
            double downstreamEnergy = specificEnergy(downstream, depths[i + 1], parameters);
            double critical = criticalDepth(upstream, parameters);
            double lower = critical * (1.0 + 10.0 * parameters.relativeTolerance());
            double lowerResidual = energyResidual(
                    upstream, downstream, depths[i + 1], lower, spacing,
                    downstreamEnergy, parameters);
            if (lowerResidual > energyTolerance(upstream, lower, downstream, depths[i + 1], parameters)) {
                throw new IllegalStateException(
                        "no subcritical standard-step solution before critical depth at section " + i);
            }

            double upper = Math.max(Math.max(depths[i + 1], lower) * 2.0, lower + 1.0);
            double upperResidual = energyResidual(
                    upstream, downstream, depths[i + 1], upper, spacing,
                    downstreamEnergy, parameters);
            int expansion = 0;
            while (upperResidual < 0.0 && expansion++ < parameters.maximumIterations()) {
                upper *= 2.0;
                if (!Double.isFinite(upper)) {
                    break;
                }
                upperResidual = energyResidual(
                        upstream, downstream, depths[i + 1], upper, spacing,
                        downstreamEnergy, parameters);
            }
            if (!(upperResidual >= 0.0) || !Double.isFinite(upperResidual)) {
                throw new IllegalStateException("failed to bracket subcritical standard-step depth at section " + i);
            }

            double root = Double.NaN;
            for (int iteration = 0; iteration < parameters.maximumIterations(); iteration++) {
                double middle = lower + 0.5 * (upper - lower);
                double residual = energyResidual(
                        upstream, downstream, depths[i + 1], middle, spacing,
                        downstreamEnergy, parameters);
                if (Math.abs(residual) <= energyTolerance(upstream, middle, downstream, depths[i + 1], parameters)
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
                    downstreamEnergy, parameters)));
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

    /**
     * Solves a supercritical profile downstream from an explicit upstream depth control.
     *
     * <p>Each section is solved on the shallow branch below critical depth. The method rejects
     * critical transitions rather than silently switching regime; hydraulic jumps and mixed
     * profiles require a separate control/jump model.
     */
    public static Result solveSupercriticalDownstream(
            List<CrossSection> sections,
            double upstreamDepthMeters,
            Parameters parameters) {
        Objects.requireNonNull(sections, "sections");
        Objects.requireNonNull(parameters, "parameters");
        if (sections.size() < 2) {
            throw new IllegalArgumentException("GVF reach requires at least two cross sections");
        }
        if (!Double.isFinite(upstreamDepthMeters) || upstreamDepthMeters <= 0.0) {
            throw new IllegalArgumentException("upstream depth must be finite and positive");
        }
        List<CrossSection> reach = List.copyOf(sections);
        for (int i = 0; i < reach.size(); i++) {
            CrossSection section = Objects.requireNonNull(reach.get(i), "section");
            if (i > 0 && !(section.chainageMeters() > reach.get(i - 1).chainageMeters())) {
                throw new IllegalArgumentException("section chainage must increase strictly downstream");
            }
        }
        double upstreamCritical = criticalDepth(reach.getFirst(), parameters);
        if (!(upstreamDepthMeters < upstreamCritical
                * (1.0 - 10.0 * parameters.relativeTolerance()))) {
            throw new IllegalArgumentException("upstream boundary is not strictly supercritical");
        }

        double[] depths = new double[reach.size()];
        depths[0] = upstreamDepthMeters;
        double maximumResidual = 0.0;
        for (int i = 0; i < reach.size() - 1; i++) {
            CrossSection upstream = reach.get(i);
            CrossSection downstream = reach.get(i + 1);
            double spacing = downstream.chainageMeters() - upstream.chainageMeters();
            double critical = criticalDepth(downstream, parameters);
            double lower = Math.max(1.0e-12, Math.ulp(downstream.bottomWidthMeters()));
            double upper = critical * (1.0 - 10.0 * parameters.relativeTolerance());
            double lowerResidual = energyResidual(
                    upstream, downstream, lower, depths[i], spacing,
                    specificEnergy(downstream, lower, parameters), parameters);
            double upperResidual = energyResidual(
                    upstream, downstream, upper, depths[i], spacing,
                    specificEnergy(downstream, upper, parameters), parameters);
            if (!(lowerResidual <= 0.0 && upperResidual >= 0.0)) {
                throw new IllegalStateException(
                        "no supercritical standard-step solution before critical depth at section "
                                + (i + 1));
            }

            double root = Double.NaN;
            for (int iteration = 0; iteration < parameters.maximumIterations(); iteration++) {
                double middle = lower + 0.5 * (upper - lower);
                double residual = energyResidual(
                        upstream, downstream, middle, depths[i], spacing,
                        specificEnergy(downstream, middle, parameters), parameters);
                if (Math.abs(residual) <= energyTolerance(
                                upstream, depths[i], downstream, middle, parameters)
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
                throw new IllegalStateException(
                        "supercritical standard-step depth iteration did not converge at section "
                                + (i + 1));
            }
            depths[i + 1] = root;
            maximumResidual = Math.max(maximumResidual, Math.abs(energyResidual(
                    upstream, downstream, root, depths[i], spacing,
                    specificEnergy(downstream, root, parameters), parameters)));
        }

        List<ProfilePoint> points = new ArrayList<>(reach.size());
        for (int i = 0; i < reach.size(); i++) {
            CrossSection section = reach.get(i);
            double depth = depths[i];
            double area = area(section, depth);
            points.add(new ProfilePoint(
                    section,
                    depth,
                    section.bedElevationMeters() + depth,
                    section.dischargeCubicMetersPerSecond() / area,
                    frictionSlope(section, depth, parameters),
                    froudeNumber(section, depth, parameters)));
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
            Parameters parameters) {
        double upstreamEnergy = specificEnergy(upstream, upstreamDepth, parameters);
        double averageFrictionSlope = averageConveyanceFrictionSlope(
                upstream.dischargeCubicMetersPerSecond(),
                conveyance(upstream, upstreamDepth, parameters),
                downstream.dischargeCubicMetersPerSecond(),
                conveyance(downstream, downstreamDepth, parameters));
        return upstream.bedElevationMeters() - downstream.bedElevationMeters()
                + upstreamEnergy - downstreamEnergy
                - averageFrictionSlope * spacing;
    }

    private static double specificEnergy(
            CrossSection section, double depth, Parameters parameters) {
        double velocity = section.dischargeCubicMetersPerSecond() / area(section, depth);
        return depth + parameters.energyCoefficient() * velocity * velocity
                / (2.0 * parameters.gravityMetersPerSecondSquared());
    }

    private static double frictionSlope(
            CrossSection section, double depth, Parameters parameters) {
        double conveyance = conveyance(section, depth, parameters);
        double scaledDischarge = section.dischargeCubicMetersPerSecond() / conveyance;
        return scaledDischarge * scaledDischarge;
    }

    private static double conveyance(
            CrossSection section, double depth, Parameters parameters) {
        double area = area(section, depth);
        double hydraulicRadius = area / wettedPerimeter(section, depth);
        return area * Math.pow(hydraulicRadius, 2.0 / 3.0)
                / parameters.manningRoughness();
    }

    private static double averageConveyanceFrictionSlope(
            double upstreamDischarge,
            double upstreamConveyance,
            double downstreamDischarge,
            double downstreamConveyance) {
        // HEC-RAS average-conveyance expression, including varying section discharge.
        double meanDischarge = 0.5 * (upstreamDischarge + downstreamDischarge);
        double meanConveyance = 0.5 * (upstreamConveyance + downstreamConveyance);
        return Math.pow(meanDischarge / meanConveyance, 2.0);
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

    public static double froudeNumber(
            CrossSection section, double depth, Parameters parameters) {
        Objects.requireNonNull(section, "section");
        Objects.requireNonNull(parameters, "parameters");
        if (!Double.isFinite(depth) || depth <= 0.0) {
            throw new IllegalArgumentException("depth must be finite and positive");
        }
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

    private static double energyTolerance(
            CrossSection upstream,
            double upstreamDepth,
            CrossSection downstream,
            double downstreamDepth,
            Parameters parameters) {
        double upstreamSpecificEnergy = upstreamDepth
                + parameters.energyCoefficient()
                        * Math.pow(upstream.dischargeCubicMetersPerSecond()
                                        / area(upstream, upstreamDepth),
                                2.0)
                        / (2.0 * parameters.gravityMetersPerSecondSquared());
        double downstreamSpecificEnergy = downstreamDepth
                + parameters.energyCoefficient()
                        * Math.pow(downstream.dischargeCubicMetersPerSecond()
                                        / area(downstream, downstreamDepth),
                                2.0)
                        / (2.0 * parameters.gravityMetersPerSecondSquared());
        // Tolerance is based on local hydraulic head, not absolute elevation datum.
        return parameters.relativeTolerance()
                * Math.max(1.0, Math.max(upstreamSpecificEnergy, downstreamSpecificEnergy));
    }
}
