package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/**
 * Hydraulic model for an interior authored CASCADE with a free critical control at its inlet.
 *
 * <p>The control is explicit and local to the authored drop. The finite chute is propagated on the
 * supercritical branch using the standard-step energy equation and Manning friction. This does not
 * model a hydraulic jump or infer an outlet; callers must fail closed when the reach needs either.
 */
public final class SkyIslandHydraulicCascadeTransitionSolver {
    /** Physical profiles for a source-controlled reach joined to its owned CASCADE chute. */
    public record SourceCascadeResult(
            SkyIslandGraduallyVariedFlowSolver.Result upstreamProfile,
            SkyIslandGraduallyVariedFlowSolver.Result cascadeProfile,
            double sourceNormalDepthMeters,
            boolean criticalControlAtCascadeInlet) {
        public SourceCascadeResult {
            upstreamProfile = Objects.requireNonNull(upstreamProfile, "upstreamProfile");
            cascadeProfile = Objects.requireNonNull(cascadeProfile, "cascadeProfile");
            if (!Double.isFinite(sourceNormalDepthMeters) || sourceNormalDepthMeters <= 0.0) {
                throw new IllegalArgumentException("source normal depth must be finite and positive");
            }
        }
    }

    private SkyIslandHydraulicCascadeTransitionSolver() {}

    /**
     * Closes one source-controlled ordinary reach into its explicitly owned cascade chute.
     *
     * <p>Subcritical source flow is solved upstream from the cascade's critical inlet, then checked
     * against the source normal-depth boundary. Supercritical source flow is instead marched
     * downstream to the lip and continued through the chute without imposing a critical inlet.
     * Near-critical flow, an incompatible subcritical source boundary, or any regime change that
     * needs a jump fails closed. The lip cross-section must be shared exactly by both profiles.
     */
    public static SourceCascadeResult solveSourceControlledCascade(
            List<SkyIslandGraduallyVariedFlowSolver.CrossSection> upstreamSections,
            List<SkyIslandGraduallyVariedFlowSolver.CrossSection> cascadeSections,
            double sourceNormalDepthMeters,
            SkyIslandGraduallyVariedFlowSolver.Parameters parameters) {
        Objects.requireNonNull(upstreamSections, "upstreamSections");
        Objects.requireNonNull(cascadeSections, "cascadeSections");
        Objects.requireNonNull(parameters, "parameters");
        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> upstream =
                List.copyOf(upstreamSections);
        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> chute =
                List.copyOf(cascadeSections);
        if (upstream.size() < 3) {
            throw new IllegalArgumentException(
                    "source-controlled reach requires at least three cross sections");
        }
        if (chute.size() < 2) {
            throw new IllegalArgumentException("CASCADE chute requires at least two cross sections");
        }
        if (!upstream.getLast().equals(chute.getFirst())) {
            throw new IllegalArgumentException(
                    "source reach and CASCADE chute must share the exact lip cross-section");
        }
        if (!Double.isFinite(sourceNormalDepthMeters) || sourceNormalDepthMeters <= 0.0) {
            throw new IllegalArgumentException("source normal depth must be finite and positive");
        }

        SkyIslandGraduallyVariedFlowSolver.CrossSection source = upstream.getFirst();
        double sourceFroude = SkyIslandGraduallyVariedFlowSolver.froudeNumber(
                source, sourceNormalDepthMeters, parameters);
        double regimeMargin = Math.max(1.0e-6, 10.0 * parameters.relativeTolerance());
        if (sourceFroude > 1.0 + regimeMargin) {
            SkyIslandGraduallyVariedFlowSolver.Result incoming =
                    SkyIslandGraduallyVariedFlowSolver.solveSupercriticalDownstream(
                            upstream, sourceNormalDepthMeters, parameters);
            double lipDepth = incoming.points().getLast().depthMeters();
            SkyIslandGraduallyVariedFlowSolver.Result cascade =
                    solveFromSupercriticalInlet(chute, lipDepth, parameters);
            return new SourceCascadeResult(
                    incoming, cascade, sourceNormalDepthMeters, false);
        }
        if (sourceFroude >= 1.0 - regimeMargin) {
            throw new IllegalStateException(
                    "source normal-depth boundary is near critical; CASCADE regime is unresolved");
        }

        SkyIslandGraduallyVariedFlowSolver.Result incoming =
                SkyIslandGraduallyVariedFlowSolver.solveSubcriticalUpstreamFromCriticalControl(
                        upstream, parameters);
        double solvedSourceDepth = incoming.points().getFirst().depthMeters();
        double sourceDepthTolerance = Math.max(
                1.0e-6,
                100.0 * parameters.relativeTolerance()
                        * Math.max(1.0, sourceNormalDepthMeters));
        if (Math.abs(solvedSourceDepth - sourceNormalDepthMeters) > sourceDepthTolerance) {
            throw new IllegalStateException(
                    "source normal-depth boundary conflicts with cascade critical control"
                            + "; normalDepthMeters=" + sourceNormalDepthMeters
                            + "; solvedSourceDepthMeters=" + solvedSourceDepth
                            + "; toleranceMeters=" + sourceDepthTolerance);
        }
        SkyIslandGraduallyVariedFlowSolver.Result cascade =
                solveFromCriticalInlet(chute, parameters);
        return new SourceCascadeResult(incoming, cascade, sourceNormalDepthMeters, true);
    }

    /**
     * Continues an already-supercritical incoming state through a finite cascade chute.
     *
     * <p>Unlike {@link #solveFromCriticalInlet(List,
     * SkyIslandGraduallyVariedFlowSolver.Parameters)}, this entry point does not invent a critical
     * control at the lip. The upstream depth must be supplied by the solved incoming reach. The
     * profile remains on the supercritical branch throughout; a regime transition requires a
     * separately resolved jump/control and fails closed.
     */
    public static SkyIslandGraduallyVariedFlowSolver.Result solveFromSupercriticalInlet(
            List<SkyIslandGraduallyVariedFlowSolver.CrossSection> sections,
            double upstreamDepthMeters,
            SkyIslandGraduallyVariedFlowSolver.Parameters parameters) {
        Objects.requireNonNull(sections, "sections");
        Objects.requireNonNull(parameters, "parameters");
        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> reach = List.copyOf(sections);
        if (reach.size() < 2) {
            throw new IllegalArgumentException("CASCADE chute requires at least two cross sections");
        }
        SkyIslandGraduallyVariedFlowSolver.Result result =
                SkyIslandGraduallyVariedFlowSolver.solveSupercriticalDownstream(
                        reach, upstreamDepthMeters, parameters);
        double regimeMargin = Math.max(1.0e-6, 10.0 * parameters.relativeTolerance());
        for (int i = 0; i < result.points().size(); i++) {
            if (!(result.points().get(i).froudeNumber() > 1.0 + regimeMargin)) {
                throw new IllegalStateException(
                        "CASCADE chute left the strictly supercritical regime at section " + i);
            }
        }
        return result;
    }

    public static SkyIslandGraduallyVariedFlowSolver.Result solveFromCriticalInlet(
            List<SkyIslandGraduallyVariedFlowSolver.CrossSection> sections,
            SkyIslandGraduallyVariedFlowSolver.Parameters parameters) {
        Objects.requireNonNull(sections, "sections");
        Objects.requireNonNull(parameters, "parameters");
        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> reach = List.copyOf(sections);
        if (reach.size() < 2) {
            throw new IllegalArgumentException("CASCADE chute requires at least two cross sections");
        }
        SkyIslandGraduallyVariedFlowSolver.Result result =
                SkyIslandGraduallyVariedFlowSolver.solveSupercriticalDownstreamFromCriticalControl(
                        reach, parameters);
        double criticalTolerance = Math.max(1.0e-6, 100.0 * parameters.relativeTolerance());
        if (Math.abs(result.points().getFirst().froudeNumber() - 1.0) > criticalTolerance) {
            throw new IllegalStateException("CASCADE inlet did not resolve to critical flow");
        }
        for (int i = 1; i < result.points().size(); i++) {
            if (!(result.points().get(i).froudeNumber() > 1.0)) {
                throw new IllegalStateException(
                        "CASCADE chute left the supported supercritical regime at section " + i);
            }
        }
        return result;
    }
}
