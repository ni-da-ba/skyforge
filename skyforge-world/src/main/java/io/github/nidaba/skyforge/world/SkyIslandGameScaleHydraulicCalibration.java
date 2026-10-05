package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Explicit mapping from authored game-scale hydraulic geometry to the SI open-channel solver.
 *
 * <p>The calibration maps normalized terrain and bankfull-depth potential through the
 * descriptor's relief budget, and derives trapezoid bottom width so the top width at that bankfull
 * depth matches the authored bankfull width. The bed-incision scale is explicit in the canonical
 * constructor. The eight-argument compatibility constructor retains the former baseline value 1.0;
 * calibration sweeps must provide and report the scale explicitly. No mapping is production-ready
 * until fixed-control evidence qualifies it.
 */
public record SkyIslandGameScaleHydraulicCalibration(
        double metersPerWorldUnit,
        double dischargeCubicMetersPerSecondPerRelativeUnit,
        double manningRoughness,
        double sideSlopeHorizontalToVertical,
        double energyCoefficient,
        double gravityMetersPerSecondSquared,
        double relativeTolerance,
        int maximumIterations,
        double bedIncisionScale) {
    public SkyIslandGameScaleHydraulicCalibration(
            double metersPerWorldUnit,
            double dischargeCubicMetersPerSecondPerRelativeUnit,
            double manningRoughness,
            double sideSlopeHorizontalToVertical,
            double energyCoefficient,
            double gravityMetersPerSecondSquared,
            double relativeTolerance,
            int maximumIterations) {
        this(
                metersPerWorldUnit,
                dischargeCubicMetersPerSecondPerRelativeUnit,
                manningRoughness,
                sideSlopeHorizontalToVertical,
                energyCoefficient,
                gravityMetersPerSecondSquared,
                relativeTolerance,
                maximumIterations,
                1.0);
    }

    public SkyIslandGameScaleHydraulicCalibration {
        if (!Double.isFinite(metersPerWorldUnit) || metersPerWorldUnit <= 0.0
                || !Double.isFinite(dischargeCubicMetersPerSecondPerRelativeUnit)
                || dischargeCubicMetersPerSecondPerRelativeUnit <= 0.0
                || !Double.isFinite(manningRoughness) || manningRoughness <= 0.0
                || !Double.isFinite(sideSlopeHorizontalToVertical)
                || sideSlopeHorizontalToVertical < 0.0
                || !Double.isFinite(energyCoefficient) || energyCoefficient <= 0.0
                || !Double.isFinite(gravityMetersPerSecondSquared)
                || gravityMetersPerSecondSquared <= 0.0
                || !Double.isFinite(relativeTolerance)
                || relativeTolerance <= 0.0 || relativeTolerance >= 1.0
                || maximumIterations < 1
                || !Double.isFinite(bedIncisionScale) || bedIncisionScale <= 0.0) {
            throw new IllegalArgumentException("game-scale hydraulic calibration must be finite and physical");
        }
    }

    public SkyIslandGraduallyVariedFlowSolver.Parameters solverParameters() {
        return new SkyIslandGraduallyVariedFlowSolver.Parameters(
                manningRoughness,
                energyCoefficient,
                gravityMetersPerSecondSquared,
                relativeTolerance,
                maximumIterations);
    }

    public SkyIslandGraduallyVariedFlowSolver.CrossSection crossSection(
            SkyIslandDescriptor descriptor,
            SkyIslandHydraulicGeometrySkeletonSample sample) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(sample, "sample");
        double reliefMeters = descriptor.reliefBudget() * metersPerWorldUnit;
        double bankfullDepthMeters = sample.waterDepthPotential() * reliefMeters;
        double bankfullWidthMeters = 2.0 * sample.bankfullHalfWidth() * metersPerWorldUnit;
        double bottomWidthMeters = bankfullWidthMeters
                - 2.0 * sideSlopeHorizontalToVertical * bankfullDepthMeters;
        if (!Double.isFinite(bottomWidthMeters) || bottomWidthMeters <= 0.0) {
            throw new IllegalArgumentException(
                    "authored bankfull width/depth cannot form a positive trapezoidal bottom width");
        }
        double bedElevationMeters = (sample.terrainElevation()
                        - bedIncisionScale * sample.waterDepthPotential())
                * reliefMeters;
        double discharge = sample.relativeDischarge()
                * dischargeCubicMetersPerSecondPerRelativeUnit;
        return new SkyIslandGraduallyVariedFlowSolver.CrossSection(
                sample.arcLength() * metersPerWorldUnit,
                bedElevationMeters,
                discharge,
                bottomWidthMeters,
                sideSlopeHorizontalToVertical);
    }

    public List<SkyIslandGraduallyVariedFlowSolver.CrossSection> crossSections(
            SkyIslandDescriptor descriptor,
            List<SkyIslandHydraulicGeometrySkeletonSample> samples) {
        return crossSections(descriptor, samples, samples);
    }

    /**
     * Maps a subspan using one bed profile computed for its entire semantic parent reach.
     *
     * <p>The candidate bed is the maximum no-fill, non-increasing profile lying at or below each
     * raw station candidate: a downstream running minimum. This removes uphill bed reversals
     * without raising the bed into terrain. D2 still independently rejects excessive incision or
     * grade; this is an exploratory profile constraint, not production selection.
     */
    public List<SkyIslandGraduallyVariedFlowSolver.CrossSection> crossSections(
            SkyIslandDescriptor descriptor,
            List<SkyIslandHydraulicGeometrySkeletonSample> samples,
            List<SkyIslandHydraulicGeometrySkeletonSample> parentReachSamples) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(samples, "samples");
        Objects.requireNonNull(parentReachSamples, "parentReachSamples");
        if (parentReachSamples.size() < 2) {
            throw new IllegalArgumentException("parent hydraulic profile requires at least two samples");
        }

        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> parent =
                new ArrayList<>(parentReachSamples.size());
        double previousChainage = Double.NEGATIVE_INFINITY;
        double runningMinimumBed = Double.POSITIVE_INFINITY;
        for (SkyIslandHydraulicGeometrySkeletonSample sample : parentReachSamples) {
            SkyIslandGraduallyVariedFlowSolver.CrossSection raw = crossSection(descriptor, sample);
            if (!(raw.chainageMeters() > previousChainage)) {
                throw new IllegalArgumentException("parent hydraulic stations must increase strictly downstream");
            }
            previousChainage = raw.chainageMeters();
            runningMinimumBed = Math.min(runningMinimumBed, raw.bedElevationMeters());
            parent.add(withBedElevation(raw, runningMinimumBed));
        }

        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> sections =
                new ArrayList<>(samples.size());
        for (SkyIslandHydraulicGeometrySkeletonSample sample : samples) {
            SkyIslandGraduallyVariedFlowSolver.CrossSection raw = crossSection(descriptor, sample);
            sections.add(withBedElevation(
                    raw, interpolatedBedElevation(parent, raw.chainageMeters())));
        }
        return List.copyOf(sections);
    }

    private static SkyIslandGraduallyVariedFlowSolver.CrossSection withBedElevation(
            SkyIslandGraduallyVariedFlowSolver.CrossSection section, double bedElevationMeters) {
        return new SkyIslandGraduallyVariedFlowSolver.CrossSection(
                section.chainageMeters(),
                bedElevationMeters,
                section.dischargeCubicMetersPerSecond(),
                section.bottomWidthMeters(),
                section.sideSlopeHorizontalToVertical());
    }

    private static double interpolatedBedElevation(
            List<SkyIslandGraduallyVariedFlowSolver.CrossSection> profile, double chainageMeters) {
        double first = profile.getFirst().chainageMeters();
        double last = profile.getLast().chainageMeters();
        double tolerance = 1.0e-9 * Math.max(1.0, Math.max(Math.abs(first), Math.abs(last)));
        if (chainageMeters < first - tolerance || chainageMeters > last + tolerance) {
            throw new IllegalArgumentException("subspan station lies outside its parent bed profile");
        }
        if (chainageMeters <= first + tolerance) {
            return profile.getFirst().bedElevationMeters();
        }
        if (chainageMeters >= last - tolerance) {
            return profile.getLast().bedElevationMeters();
        }
        for (int i = 0; i + 1 < profile.size(); i++) {
            SkyIslandGraduallyVariedFlowSolver.CrossSection upstream = profile.get(i);
            SkyIslandGraduallyVariedFlowSolver.CrossSection downstream = profile.get(i + 1);
            if (Math.abs(chainageMeters - upstream.chainageMeters()) <= tolerance) {
                return upstream.bedElevationMeters();
            }
            if (chainageMeters <= downstream.chainageMeters()) {
                double fraction = (chainageMeters - upstream.chainageMeters())
                        / (downstream.chainageMeters() - upstream.chainageMeters());
                return upstream.bedElevationMeters()
                        + fraction * (downstream.bedElevationMeters() - upstream.bedElevationMeters());
            }
        }
        return profile.getLast().bedElevationMeters();
    }


    /**
     * Applies a critical-depth terminal control for a separately authorized free outfall.
     * In particular, this must not be used to turn a CASCADE, basin, or unknown fate into an outlet.
     */
    public SkyIslandGraduallyVariedFlowSolver.Result solveFreeOutfall(
            SkyIslandDescriptor descriptor,
            List<SkyIslandHydraulicGeometrySkeletonSample> samples) {
        return SkyIslandGraduallyVariedFlowSolver.solveSubcriticalUpstreamFromCriticalControl(
                crossSections(descriptor, samples), solverParameters());
    }
}
