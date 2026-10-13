package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * Unit-width adapter for the finite-section standard-step energy solver.
 *
 * <p>Unit discharge q in m²/s is numerically the total discharge through a 1 m wide section.
 * The unit-width Manning approximation uses area=h, top width=1, and hydraulic radius=h
 * (perimeter=1), giving Sf=n²q²/h^(10/3). Use
 * {@link SkyIslandStandardStepSolver} directly when finite wetted geometry is available.
 */
public final class SkyIslandUnitWidthStandardStepSolver {
    private SkyIslandUnitWidthStandardStepSolver() {}

    public enum FlowRegime {
        SUBCRITICAL,
        SUPERCRITICAL
    }

    private static final SkyIslandStandardStepSolver.CrossSection UNIT_WIDTH_SECTION =
            new SkyIslandStandardStepSolver.CrossSection() {
                @Override
                public double area(double depthMetres) {
                    return depthMetres;
                }

                @Override
                public double wettedPerimeter(double depthMetres) {
                    return 1.0;
                }

                @Override
                public double topWidth(double depthMetres) {
                    return 1.0;
                }
            };

    /**
     * Solves a unit-width profile using strictly increasing upstream-to-downstream stations.
     *
     * @param stationsMetres station coordinates in metres
     * @param bedElevationsMetres bed elevation at each station in metres
     * @param unitDischargeSquareMetresPerSecond discharge per unit width in m²/s
     * @param manningRoughness Manning roughness in SI units
     * @param boundaryDepthMetres downstream subcritical or upstream supercritical depth
     * @param regime flow regime preserved at every station
     * @return depth at each station in metres
     */
    public static double[] solve(
            double[] stationsMetres,
            double[] bedElevationsMetres,
            double unitDischargeSquareMetresPerSecond,
            double manningRoughness,
            double boundaryDepthMetres,
            FlowRegime regime) {
        Objects.requireNonNull(stationsMetres, "stationsMetres");
        Objects.requireNonNull(regime, "regime");
        SkyIslandStandardStepSolver.CrossSection[] sections =
                new SkyIslandStandardStepSolver.CrossSection[stationsMetres.length];
        java.util.Arrays.fill(sections, UNIT_WIDTH_SECTION);
        return SkyIslandStandardStepSolver.solve(
                stationsMetres,
                bedElevationsMetres,
                sections,
                unitDischargeSquareMetresPerSecond,
                manningRoughness,
                boundaryDepthMetres,
                regime == FlowRegime.SUBCRITICAL
                        ? SkyIslandStandardStepSolver.FlowRegime.SUBCRITICAL
                        : SkyIslandStandardStepSolver.FlowRegime.SUPERCRITICAL);
    }
}
