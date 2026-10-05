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
        Objects.requireNonNull(samples, "samples");
        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> sections =
                new ArrayList<>(samples.size());
        for (SkyIslandHydraulicGeometrySkeletonSample sample : samples) {
            sections.add(crossSection(descriptor, sample));
        }
        return List.copyOf(sections);
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
