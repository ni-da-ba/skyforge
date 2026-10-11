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
 * depth matches the authored bankfull width. Bed-incision scale and maximum
 * downstream bed slope are explicit in the canonical constructor. Compatibility constructors retain
 * the former 1.0 baselines; calibration sweeps must provide and report both profile scales explicitly.
 * No mapping is production-ready
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
        double bedIncisionScale,
        double maximumDownstreamBedSlope) {
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
                1.0,
                1.0);
    }

    public SkyIslandGameScaleHydraulicCalibration(
            double metersPerWorldUnit,
            double dischargeCubicMetersPerSecondPerRelativeUnit,
            double manningRoughness,
            double sideSlopeHorizontalToVertical,
            double energyCoefficient,
            double gravityMetersPerSecondSquared,
            double relativeTolerance,
            int maximumIterations,
            double bedIncisionScale) {
        this(
                metersPerWorldUnit,
                dischargeCubicMetersPerSecondPerRelativeUnit,
                manningRoughness,
                sideSlopeHorizontalToVertical,
                energyCoefficient,
                gravityMetersPerSecondSquared,
                relativeTolerance,
                maximumIterations,
                bedIncisionScale,
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
                || !Double.isFinite(bedIncisionScale) || bedIncisionScale <= 0.0
                || !Double.isFinite(maximumDownstreamBedSlope)
                || maximumDownstreamBedSlope <= 0.0) {
            throw new IllegalArgumentException("game-scale hydraulic calibration must be finite and physical");
        }
    }

    /**
     * Maximum geometric flow depth for the authored trapezoid while retaining a positive bottom
     * width. The tiny relative reserve is numerical, not an incision calibration.
     */
    public double maximumCrossSectionDepthMeters(double bankfullHalfWidthWorldUnits) {
        if (!Double.isFinite(bankfullHalfWidthWorldUnits) || bankfullHalfWidthWorldUnits <= 0.0) {
            throw new IllegalArgumentException("bankfullHalfWidthWorldUnits must be finite and positive");
        }
        if (sideSlopeHorizontalToVertical == 0.0) {
            return Double.POSITIVE_INFINITY;
        }
        double geometricLimit =
                bankfullHalfWidthWorldUnits * metersPerWorldUnit / sideSlopeHorizontalToVertical;
        return geometricLimit * (1.0 - 1.0e-6);
    }

    public double maximumCrossSectionDepthMeters(
            SkyIslandHydraulicGeometrySkeletonSample sample) {
        Objects.requireNonNull(sample, "sample");
        return maximumCrossSectionDepthMeters(sample.bankfullHalfWidth());
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
        double bankfullDepthMeters =
                bedIncisionScale * sample.waterDepthPotential() * reliefMeters;
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
     * <p>The candidate bed is the pointwise-greatest no-fill, non-increasing profile below the raw
     * station candidates whose downstream grade does not exceed the explicit calibration limit.
     * This slope-limited lower envelope removes uphill reversals and abrupt drops without raising
     * the bed into terrain. D2 independently evaluates the solved bed; this is an exploratory
     * profile constraint, not production selection.
     */
    public List<SkyIslandGraduallyVariedFlowSolver.CrossSection> crossSections(
            SkyIslandDescriptor descriptor,
            List<SkyIslandHydraulicGeometrySkeletonSample> samples,
            List<SkyIslandHydraulicGeometrySkeletonSample> parentReachSamples) {
        return crossSections(descriptor, samples, parentReachSamples, 0.0);
    }

    /**
     * Maps a local-arc subspan against its parent-reach bed profile.
     *
     * @param parentStartArcLengthWorldUnits global parent-reach arc coordinate represented by
     *     local sample arc zero
     */
    public List<SkyIslandGraduallyVariedFlowSolver.CrossSection> crossSections(
            SkyIslandDescriptor descriptor,
            List<SkyIslandHydraulicGeometrySkeletonSample> samples,
            List<SkyIslandHydraulicGeometrySkeletonSample> parentReachSamples,
            double parentStartArcLengthWorldUnits) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(samples, "samples");
        Objects.requireNonNull(parentReachSamples, "parentReachSamples");
        if (!Double.isFinite(parentStartArcLengthWorldUnits)
                || parentStartArcLengthWorldUnits < 0.0) {
            throw new IllegalArgumentException(
                    "parent start arc length must be finite and non-negative");
        }
        if (parentReachSamples.size() < 2) {
            throw new IllegalArgumentException("parent hydraulic profile requires at least two samples");
        }

        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> rawParent =
                new ArrayList<>(parentReachSamples.size());
        double previousChainage = Double.NEGATIVE_INFINITY;
        for (SkyIslandHydraulicGeometrySkeletonSample sample : parentReachSamples) {
            SkyIslandGraduallyVariedFlowSolver.CrossSection raw = crossSection(descriptor, sample);
            if (!(raw.chainageMeters() > previousChainage)) {
                throw new IllegalArgumentException("parent hydraulic stations must increase strictly downstream");
            }
            previousChainage = raw.chainageMeters();
            rawParent.add(raw);
        }

        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> parent =
                new ArrayList<>(rawParent.size());
        for (SkyIslandGraduallyVariedFlowSolver.CrossSection station : rawParent) {
            double conditionedBed = Double.POSITIVE_INFINITY;
            for (SkyIslandGraduallyVariedFlowSolver.CrossSection constraint : rawParent) {
                double downstreamDistance = Math.max(
                        0.0, constraint.chainageMeters() - station.chainageMeters());
                conditionedBed = Math.min(
                        conditionedBed,
                        constraint.bedElevationMeters()
                                + maximumDownstreamBedSlope * downstreamDistance);
            }
            parent.add(withBedElevation(station, conditionedBed));
        }

        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> sections =
                new ArrayList<>(samples.size());
        double parentOffsetMeters =
                parentStartArcLengthWorldUnits * metersPerWorldUnit;
        for (SkyIslandHydraulicGeometrySkeletonSample sample : samples) {
            SkyIslandGraduallyVariedFlowSolver.CrossSection raw = crossSection(descriptor, sample);
            double parentChainageMeters = raw.chainageMeters() + parentOffsetMeters;
            SkyIslandGraduallyVariedFlowSolver.CrossSection parentStation =
                    withChainage(raw, parentChainageMeters);
            sections.add(withBedElevation(
                    parentStation,
                    interpolatedBedElevation(parent, parentChainageMeters)));
        }
        return List.copyOf(sections);
    }

    private static SkyIslandGraduallyVariedFlowSolver.CrossSection withChainage(
            SkyIslandGraduallyVariedFlowSolver.CrossSection section,
            double chainageMeters) {
        return new SkyIslandGraduallyVariedFlowSolver.CrossSection(
                chainageMeters,
                section.bedElevationMeters(),
                section.dischargeCubicMetersPerSecond(),
                section.bottomWidthMeters(),
                section.sideSlopeHorizontalToVertical());
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
