package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Solves and independently geomorphically qualifies one finite ordinary open-channel span.
 *
 * <p>The hydraulic profile is computed first from the declared boundary controls and explicit
 * game-scale calibration, with marching direction selected from the controlled regime. D2 is
 * applied only to the resulting geometry; it never supplies a pointwise water-surface interval. Confluence, CASCADE, basin, and unauthorized
 * terminal controls remain outside this ordinary-span solver and fail closed.
 */
public final class SkyIslandOpenChannelOrdinarySpanSolver {
    private static final double MINIMUM_STAGE_RESIDUAL_METERS = 1.0e-5;

    private SkyIslandOpenChannelOrdinarySpanSolver() {}

    public static Outcome solve(
            SkyIslandDescriptor descriptor,
            SkyIslandOrdinaryHydraulicSpan span,
            SkyIslandSemanticField terrain,
            SkyIslandGeomorphicQualificationPolicy policy,
            double planningSpacing,
            SkyIslandGameScaleHydraulicCalibration calibration,
            Map<Integer, SkyIslandChannelTerminalFateKind> terminalFates) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(span, "span");
        Objects.requireNonNull(terrain, "terrain");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(calibration, "calibration");
        Objects.requireNonNull(terminalFates, "terminalFates");
        if (span.boundaryDeferred()) {
            throw new IllegalArgumentException("cannot solve an ordinary span with deferred boundaries");
        }
        if (!Double.isFinite(planningSpacing) || planningSpacing <= 0.0) {
            throw new IllegalArgumentException("planningSpacing must be finite and positive");
        }

        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> sections =
                calibration.crossSections(descriptor, span.samples());
        SkyIslandGraduallyVariedFlowSolver.Result hydraulicProfile =
                solveHydraulics(descriptor, span, sections, calibration, terminalFates);
        List<SkyIslandHydraulicGeometrySample> solvedSamples =
                reconstruct(span, hydraulicProfile, descriptor, calibration);
        List<SkyIslandLocalPosition> points = span.samples().stream()
                .map(SkyIslandHydraulicGeometrySkeletonSample::position)
                .toList();
        SkyIslandGeomorphicMeasurements measurements =
                SkyIslandGeomorphicReachDiagnosticsPlanner.measureGeometry(
                        descriptor,
                        points,
                        solvedSamples,
                        span.sampleProfileKinds(),
                        terrain,
                        planningSpacing);
        SkyIslandGeomorphicProfileLimits limits = policy.limits(span.qualificationClass());
        List<SkyIslandGeomorphicQualificationViolation> violations =
                SkyIslandGeomorphicQualificationEvaluator.violations(measurements, limits);
        Optional<Double> upstreamStageResidual =
                upstreamStageResidual(span, hydraulicProfile, calibration);
        double stageTolerance = Math.max(
                MINIMUM_STAGE_RESIDUAL_METERS,
                100.0 * calibration.relativeTolerance());

        return new Outcome(
                hydraulicProfile,
                solvedSamples,
                measurements,
                violations,
                upstreamStageResidual,
                stageTolerance);
    }

    private static SkyIslandGraduallyVariedFlowSolver.Result solveHydraulics(
            SkyIslandDescriptor descriptor,
            SkyIslandOrdinaryHydraulicSpan span,
            List<SkyIslandGraduallyVariedFlowSolver.CrossSection> sections,
            SkyIslandGameScaleHydraulicCalibration calibration,
            Map<Integer, SkyIslandChannelTerminalFateKind> terminalFates) {
        if (span.downstreamBoundary().status() == SkyIslandOrdinarySpanBoundaryStatus.FIXED_HEAD) {
            double downstreamStageMeters =
                    span.downstreamBoundary().fixedHeadWorldUnits().orElseThrow()
                            * calibration.metersPerWorldUnit();
            double downstreamDepthMeters =
                    downstreamStageMeters - sections.getLast().bedElevationMeters();
            return SkyIslandGraduallyVariedFlowSolver.solveSubcriticalUpstream(
                    sections,
                    downstreamDepthMeters,
                    calibration.solverParameters());
        }
        if (span.downstreamBoundary().status() == SkyIslandOrdinarySpanBoundaryStatus.FREE
                && terminalFates.get(span.parentReachEndCellIndex())
                        == SkyIslandChannelTerminalFateKind.EDGE_OUTLET) {
            if (span.upstreamBoundary().status() == SkyIslandOrdinarySpanBoundaryStatus.FIXED_HEAD) {
                double upstreamStageMeters =
                        span.upstreamBoundary().fixedHeadWorldUnits().orElseThrow()
                                * calibration.metersPerWorldUnit();
                double upstreamDepthMeters =
                        upstreamStageMeters - sections.getFirst().bedElevationMeters();
                if (!(upstreamDepthMeters > 0.0)) {
                    throw new IllegalArgumentException(
                            "upstream fixed stage must lie above the modeled channel bed");
                }
                double upstreamFroude = SkyIslandGraduallyVariedFlowSolver.froudeNumber(
                        sections.getFirst(), upstreamDepthMeters, calibration.solverParameters());
                double regimeMargin = Math.max(
                        1.0e-6, 10.0 * calibration.relativeTolerance());
                if (upstreamFroude > 1.0 + regimeMargin) {
                    return SkyIslandGraduallyVariedFlowSolver.solveSupercriticalDownstream(
                            sections, upstreamDepthMeters, calibration.solverParameters());
                }
                if (upstreamFroude >= 1.0 - regimeMargin) {
                    throw new IllegalStateException(
                            "upstream fixed stage is near critical; mixed-regime control is unsupported");
                }
            }
            try {
                return calibration.solveFreeOutfall(descriptor, span.samples());
            } catch (IllegalStateException subcriticalFailure) {
                if (span.upstreamBoundary().status()
                        != SkyIslandOrdinarySpanBoundaryStatus.FIXED_HEAD) {
                    throw subcriticalFailure;
                }
                double upstreamStageMeters =
                        span.upstreamBoundary().fixedHeadWorldUnits().orElseThrow()
                                * calibration.metersPerWorldUnit();
                double upstreamDepthMeters =
                        upstreamStageMeters - sections.getFirst().bedElevationMeters();
                try {
                    return solveMixedRegimeAtInternalCriticalControl(
                            sections,
                            upstreamDepthMeters,
                            calibration,
                            subcriticalFailure);
                } catch (IllegalStateException mixedFailure) {
                    throw new IllegalStateException(
                            subcriticalFailure.getMessage()
                                    + "; mixed-regime critical-control solve failed: "
                                    + mixedFailure.getMessage(),
                            subcriticalFailure);
                }
            }
        }
        throw new IllegalArgumentException(
                "ordinary span has no authorized downstream hydraulic control");
    }

    /**
     * Resolves a gradual subcritical-to-supercritical transition in the failed standard-step
     * interval. The transition location is an unknown, solved by matching the upstream stage;
     * both sides must independently satisfy the energy equation and the critical control.
     */
    static SkyIslandGraduallyVariedFlowSolver.Result
            solveMixedRegimeAtInternalCriticalControl(
                    List<SkyIslandGraduallyVariedFlowSolver.CrossSection> sections,
                    double upstreamDepthMeters,
                    SkyIslandGameScaleHydraulicCalibration calibration,
                    IllegalStateException subcriticalFailure) {
        int failedSection = failedSectionIndex(subcriticalFailure.getMessage());
        if (failedSection < 0) {
            throw new IllegalStateException(
                    "subcritical failure did not identify its critical-control interval");
        }
        int maximumInterval = sections.size() - 2;
        int primaryInterval = Math.min(failedSection, maximumInterval);
        if (primaryInterval < 0) {
            throw new IllegalStateException("critical-control interval lies outside the reach");
        }

        List<Integer> intervals = new ArrayList<>();
        intervals.add(primaryInterval);
        if (primaryInterval > 0) {
            intervals.add(primaryInterval - 1);
        }
        if (primaryInterval < maximumInterval) {
            intervals.add(primaryInterval + 1);
        }

        double stageTolerance = Math.max(
                MINIMUM_STAGE_RESIDUAL_METERS,
                100.0 * calibration.relativeTolerance());
        SkyIslandGraduallyVariedFlowSolver.Parameters parameters =
                calibration.solverParameters();
        RuntimeException lastFailure = null;
        for (int interval : intervals) {
            List<MixedStepTrial> trials = new ArrayList<>();
            for (int sample = 0; sample <= 8; sample++) {
                double fraction = sample / 8.0;
                try {
                    MixedStepTrial trial = evaluateCriticalControl(
                            sections,
                            interval,
                            fraction,
                            upstreamDepthMeters,
                            stageTolerance,
                            parameters);
                    if (Math.abs(trial.upstreamStageResidualMeters()) <= stageTolerance) {
                        return joinMixedProfiles(
                                sections,
                                interval,
                                fraction,
                                trial.subcriticalProfile(),
                                parameters);
                    }
                    trials.add(trial);
                } catch (IllegalArgumentException | IllegalStateException invalidControl) {
                    lastFailure = invalidControl;
                    trials.add(null);
                }
            }

            for (int sample = 0; sample < 8; sample++) {
                MixedStepTrial lowerTrial = trials.get(sample);
                MixedStepTrial upperTrial = trials.get(sample + 1);
                if (lowerTrial == null || upperTrial == null
                        || lowerTrial.upstreamStageResidualMeters()
                                * upperTrial.upstreamStageResidualMeters() > 0.0) {
                    continue;
                }
                double lowerFraction = lowerTrial.controlFraction();
                double upperFraction = upperTrial.controlFraction();
                MixedStepTrial lower = lowerTrial;
                for (int iteration = 0; iteration < parameters.maximumIterations(); iteration++) {
                    double middleFraction = 0.5 * (lowerFraction + upperFraction);
                    MixedStepTrial middle;
                    try {
                        middle = evaluateCriticalControl(
                                sections,
                                interval,
                                middleFraction,
                                upstreamDepthMeters,
                                stageTolerance,
                                parameters);
                    } catch (IllegalArgumentException | IllegalStateException invalidControl) {
                        lastFailure = invalidControl;
                        break;
                    }
                    if (Math.abs(middle.upstreamStageResidualMeters()) <= stageTolerance) {
                        try {
                            return joinMixedProfiles(
                                    sections,
                                    interval,
                                    middleFraction,
                                    middle.subcriticalProfile(),
                                    parameters);
                        } catch (IllegalArgumentException | IllegalStateException invalidControl) {
                            lastFailure = invalidControl;
                            break;
                        }
                    }
                    if (upperFraction - lowerFraction <= parameters.relativeTolerance()) {
                        lastFailure = new IllegalStateException(
                                "critical-control location converged without matching upstream stage");
                        break;
                    }
                    if (lower.upstreamStageResidualMeters()
                                    * middle.upstreamStageResidualMeters()
                            <= 0.0) {
                        upperFraction = middleFraction;
                    } else {
                        lowerFraction = middleFraction;
                        lower = middle;
                    }
                }
            }
        }
        throw new IllegalStateException(
                "no bracketed interior critical control matches the upstream stage"
                        + (lastFailure == null ? "" : ": " + lastFailure.getMessage()));
    }

    private static MixedStepTrial evaluateCriticalControl(
            List<SkyIslandGraduallyVariedFlowSolver.CrossSection> sections,
            int interval,
            double controlFraction,
            double requestedUpstreamDepthMeters,
            double stageTolerance,
            SkyIslandGraduallyVariedFlowSolver.Parameters parameters) {
        SkyIslandGraduallyVariedFlowSolver.CrossSection control =
                interpolate(sections.get(interval), sections.get(interval + 1), controlFraction);
        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> prefix =
                criticalPrefix(sections, interval, controlFraction, control);
        SkyIslandGraduallyVariedFlowSolver.Result upstreamProfile =
                SkyIslandGraduallyVariedFlowSolver.solveSubcriticalUpstreamFromCriticalControl(
                        prefix, parameters);
        double requestedStage =
                sections.getFirst().bedElevationMeters() + requestedUpstreamDepthMeters;
        double residual = upstreamProfile.points().getFirst().waterSurfaceElevationMeters()
                - requestedStage;
        if (!Double.isFinite(residual) || stageTolerance <= 0.0) {
            throw new IllegalStateException("critical-control boundary residual is invalid");
        }
        return new MixedStepTrial(controlFraction, upstreamProfile, residual);
    }

    private static List<SkyIslandGraduallyVariedFlowSolver.CrossSection> criticalPrefix(
            List<SkyIslandGraduallyVariedFlowSolver.CrossSection> sections,
            int interval,
            double controlFraction,
            SkyIslandGraduallyVariedFlowSolver.CrossSection control) {
        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> result =
                new ArrayList<>(sections.subList(0, interval + 1));
        if (controlFraction > 0.0) {
            result.add(control);
        }
        return List.copyOf(result);
    }

    private static SkyIslandGraduallyVariedFlowSolver.Result joinMixedProfiles(
            List<SkyIslandGraduallyVariedFlowSolver.CrossSection> sections,
            int interval,
            double controlFraction,
            SkyIslandGraduallyVariedFlowSolver.Result subcritical,
            SkyIslandGraduallyVariedFlowSolver.Parameters parameters) {
        SkyIslandGraduallyVariedFlowSolver.CrossSection control =
                interpolate(sections.get(interval), sections.get(interval + 1), controlFraction);
        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> suffix =
                new ArrayList<>();
        if (controlFraction < 1.0) {
            suffix.add(control);
            suffix.addAll(sections.subList(interval + 1, sections.size()));
        } else {
            suffix.addAll(sections.subList(interval + 1, sections.size()));
        }
        if (suffix.size() < 2) {
            throw new IllegalStateException(
                    "critical control leaves no downstream supercritical interval");
        }
        SkyIslandGraduallyVariedFlowSolver.Result supercritical =
                SkyIslandGraduallyVariedFlowSolver.solveSupercriticalDownstreamFromCriticalControl(
                        suffix, parameters);

        List<SkyIslandGraduallyVariedFlowSolver.ProfilePoint> points =
                new ArrayList<>(sections.size());
        int upstreamPointCount = subcritical.points().size();
        if (controlFraction > 0.0 && controlFraction < 1.0) {
            upstreamPointCount--;
        }
        points.addAll(subcritical.points().subList(0, upstreamPointCount));
        points.addAll(supercritical.points().subList(1, supercritical.points().size()));
        if (points.size() != sections.size()) {
            throw new IllegalStateException(
                    "mixed-regime profile must preserve every original cross section");
        }
        double residual = Math.max(
                subcritical.maximumEnergyResidualMeters(),
                supercritical.maximumEnergyResidualMeters());
        return new SkyIslandGraduallyVariedFlowSolver.Result(points, residual);
    }

    private static SkyIslandGraduallyVariedFlowSolver.CrossSection interpolate(
            SkyIslandGraduallyVariedFlowSolver.CrossSection first,
            SkyIslandGraduallyVariedFlowSolver.CrossSection second,
            double fraction) {
        return new SkyIslandGraduallyVariedFlowSolver.CrossSection(
                lerp(first.chainageMeters(), second.chainageMeters(), fraction),
                lerp(first.bedElevationMeters(), second.bedElevationMeters(), fraction),
                lerp(first.dischargeCubicMetersPerSecond(),
                        second.dischargeCubicMetersPerSecond(), fraction),
                lerp(first.bottomWidthMeters(), second.bottomWidthMeters(), fraction),
                lerp(first.sideSlopeHorizontalToVertical(),
                        second.sideSlopeHorizontalToVertical(), fraction));
    }

    private static double lerp(double first, double second, double fraction) {
        return first + (second - first) * fraction;
    }

    private static int failedSectionIndex(String diagnostic) {
        if (diagnostic == null) {
            return -1;
        }
        String marker = "section ";
        int start = diagnostic.lastIndexOf(marker);
        if (start < 0) {
            return -1;
        }
        start += marker.length();
        int end = start;
        while (end < diagnostic.length() && Character.isDigit(diagnostic.charAt(end))) {
            end++;
        }
        if (end == start) {
            return -1;
        }
        try {
            return Integer.parseInt(diagnostic.substring(start, end));
        } catch (NumberFormatException invalidIndex) {
            return -1;
        }
    }

    private record MixedStepTrial(
            double controlFraction,
            SkyIslandGraduallyVariedFlowSolver.Result subcriticalProfile,
            double upstreamStageResidualMeters) {}

    private static List<SkyIslandHydraulicGeometrySample> reconstruct(
            SkyIslandOrdinaryHydraulicSpan span,
            SkyIslandGraduallyVariedFlowSolver.Result result,
            SkyIslandDescriptor descriptor,
            SkyIslandGameScaleHydraulicCalibration calibration) {
        if (result.points().size() != span.samples().size()) {
            throw new IllegalStateException(
                    "standard-step profile count must match ordinary-span cross sections");
        }
        double reliefMeters = descriptor.reliefBudget() * calibration.metersPerWorldUnit();
        List<SkyIslandHydraulicGeometrySample> samples =
                new ArrayList<>(span.samples().size());
        for (int i = 0; i < span.samples().size(); i++) {
            SkyIslandHydraulicGeometrySkeletonSample source = span.samples().get(i);
            SkyIslandGraduallyVariedFlowSolver.ProfilePoint profile = result.points().get(i);
            double waterSurfacePotential =
                    profile.waterSurfaceElevationMeters() / reliefMeters;
            double bedPotential =
                    source.terrainElevation() - source.waterDepthPotential();
            if (!Double.isFinite(waterSurfacePotential)
                    || waterSurfacePotential < 0.0
                    || waterSurfacePotential > 1.0
                    || bedPotential < 0.0
                    || bedPotential >= waterSurfacePotential) {
                throw new IllegalArgumentException(
                        "standard-step profile escaped the authored vertical domain");
            }
            samples.add(new SkyIslandHydraulicGeometrySample(
                    source.position(),
                    source.stationFraction(),
                    source.relativeDischarge(),
                    source.bankfullHalfWidth(),
                    source.waterDepthPotential(),
                    source.terrainElevation(),
                    waterSurfacePotential,
                    bedPotential,
                    Math.max(0.0, source.terrainElevation() - bedPotential)));
        }
        return List.copyOf(samples);
    }

    private static Optional<Double> upstreamStageResidual(
            SkyIslandOrdinaryHydraulicSpan span,
            SkyIslandGraduallyVariedFlowSolver.Result result,
            SkyIslandGameScaleHydraulicCalibration calibration) {
        if (span.upstreamBoundary().status() != SkyIslandOrdinarySpanBoundaryStatus.FIXED_HEAD) {
            return Optional.empty();
        }
        double requestedStageMeters =
                span.upstreamBoundary().fixedHeadWorldUnits().orElseThrow()
                        * calibration.metersPerWorldUnit();
        return Optional.of(Math.abs(
                requestedStageMeters
                        - result.points().getFirst().waterSurfaceElevationMeters()));
    }

    public record Outcome(
            SkyIslandGraduallyVariedFlowSolver.Result hydraulicProfile,
            List<SkyIslandHydraulicGeometrySample> solvedSamples,
            SkyIslandGeomorphicMeasurements measurements,
            List<SkyIslandGeomorphicQualificationViolation> violations,
            Optional<Double> upstreamStageResidualMeters,
            double upstreamStageToleranceMeters) {
        public Outcome {
            hydraulicProfile = Objects.requireNonNull(hydraulicProfile, "hydraulicProfile");
            solvedSamples = List.copyOf(solvedSamples);
            measurements = Objects.requireNonNull(measurements, "measurements");
            violations = List.copyOf(violations);
            upstreamStageResidualMeters =
                    Objects.requireNonNull(upstreamStageResidualMeters, "upstreamStageResidualMeters");
            if (solvedSamples.isEmpty()
                    || !Double.isFinite(upstreamStageToleranceMeters)
                    || upstreamStageToleranceMeters <= 0.0) {
                throw new IllegalArgumentException("ordinary-span hydraulic outcome is invalid");
            }
            if (upstreamStageResidualMeters.isPresent()
                    && (!Double.isFinite(upstreamStageResidualMeters.orElseThrow())
                            || upstreamStageResidualMeters.orElseThrow() < 0.0)) {
                throw new IllegalArgumentException("upstream stage residual must be finite and non-negative");
            }
        }

        public boolean geomorphicallyQualified() {
            return violations.isEmpty();
        }

        public boolean upstreamStageCompatible() {
            return upstreamStageResidualMeters.isEmpty()
                    || upstreamStageResidualMeters.orElseThrow() <= upstreamStageToleranceMeters;
        }
    }
}
