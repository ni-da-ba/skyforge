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
    private SkyIslandHydraulicCascadeTransitionSolver() {}

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
