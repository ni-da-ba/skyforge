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
 * <p>The hydraulic profile is computed first from the declared downstream control and explicit
 * game-scale calibration. D2 is applied only to the resulting geometry; it never supplies a
 * pointwise water-surface interval to this solve. Confluence, CASCADE, basin, and unauthorized
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
            return calibration.solveFreeOutfall(descriptor, span.samples());
        }
        throw new IllegalArgumentException(
                "ordinary span has no authorized downstream hydraulic control");
    }

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
                    || measurements == null
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
