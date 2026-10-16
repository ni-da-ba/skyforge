package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Solves a steady subcritical combining junction by the HEC-RAS energy procedure.
 *
 * <p>The downstream receiving reach is solved first from its explicit downstream depth boundary.
 * Each tributary is then solved independently from the resulting upstream receiving-section
 * boundary, across its own junction length and transition losses, and upstream through its reach.
 * Tributary stages are intentionally independent; this one-dimensional energy method does not force
 * them to coincide.
 *
 * <p>All reach arrays are in upstream-to-downstream order. The receiving reach's first section is
 * immediately downstream of the junction; each tributary's last section is its section immediately
 * upstream of the junction. The separate junction length connects those sections. This solver
 * inherits {@link SkyIslandStandardStepSolver}'s assumptions, including alpha=1; compound
 * overbank-energy weighting is outside its validated domain.
 */
public final class SkyIslandSubcriticalJunctionNetworkSolver {
    private static final double FLOW_BALANCE_RELATIVE_TOLERANCE = 1.0e-9;
    private static final double FLOW_BALANCE_ABSOLUTE_TOLERANCE = 1.0e-9;

    private SkyIslandSubcriticalJunctionNetworkSolver() {}

    /**
     * Solves a receiving reach and all tributary profiles for one subcritical combining junction.
     *
     * @param receivingReach the combined-flow reach from junction toward its downstream control
     * @param downstreamBoundaryDepthMetres explicit subcritical depth at the receiving reach's
     *     downstream-most section
     * @param tributaries at least two upstream branches, each with its own junction distance/losses
     */
    public static NetworkProfile solve(
            Reach receivingReach,
            double downstreamBoundaryDepthMetres,
            List<Tributary> tributaries) {
        Objects.requireNonNull(receivingReach, "receivingReach");
        Objects.requireNonNull(tributaries, "tributaries");
        List<Tributary> branches = List.copyOf(tributaries);
        if (branches.size() < 2) {
            throw new IllegalArgumentException(
                    "a combining-junction network requires at least two tributaries");
        }

        double receivingJunctionDischarge = receivingReach.dischargeAtStationsCubicMetresPerSecond[0];
        double tributaryDischargeSum = 0.0;
        for (Tributary tributary : branches) {
            Objects.requireNonNull(tributary, "tributary");
            double[] branchDischarges =
                    tributary.reach().dischargeAtStationsCubicMetresPerSecond;
            tributaryDischargeSum += branchDischarges[branchDischarges.length - 1];
        }
        if (!Double.isFinite(tributaryDischargeSum)
                || Math.abs(tributaryDischargeSum - receivingJunctionDischarge)
                        > Math.max(
                                FLOW_BALANCE_ABSOLUTE_TOLERANCE,
                                receivingJunctionDischarge * FLOW_BALANCE_RELATIVE_TOLERANCE)) {
            throw new IllegalArgumentException(
                    "tributary junction discharges must sum to receiving-reach discharge");
        }

        double[] receivingDepths = SkyIslandStandardStepSolver.solve(
                receivingReach.stationsMetres,
                receivingReach.bedElevationsMetres,
                receivingReach.sections,
                receivingReach.dischargeAtStationsCubicMetresPerSecond,
                receivingReach.manningRoughness,
                downstreamBoundaryDepthMetres,
                SkyIslandStandardStepSolver.FlowRegime.SUBCRITICAL,
                SkyIslandStandardStepSolver.BoundaryLocation.DOWNSTREAM);

        double junctionBedElevation = receivingReach.bedElevationsMetres[0];
        SkyIslandStandardStepSolver.CrossSection junctionSection = receivingReach.sections[0];
        double junctionDepth = receivingDepths[0];
        List<TributaryProfile> solvedTributaries = new ArrayList<>(branches.size());
        for (Tributary tributary : branches) {
            Reach branch = tributary.reach();
            int last = branch.stationsMetres.length - 1;
            SkyIslandStandardStepSolver.JunctionStepResult junctionStep =
                    SkyIslandStandardStepSolver.solveSubcriticalCombiningJunction(
                            branch.bedElevationsMetres[last],
                            junctionBedElevation,
                            junctionDepth,
                            branch.sections[last],
                            junctionSection,
                            branch.dischargeAtStationsCubicMetresPerSecond[last],
                            receivingJunctionDischarge,
                            branch.manningRoughness,
                            receivingReach.manningRoughness,
                            tributary.junctionLengthMetres(),
                            tributary.contractionCoefficient(),
                            tributary.expansionCoefficient());
            double[] branchDepths = SkyIslandStandardStepSolver.solve(
                    branch.stationsMetres,
                    branch.bedElevationsMetres,
                    branch.sections,
                    branch.dischargeAtStationsCubicMetresPerSecond,
                    branch.manningRoughness,
                    junctionStep.upstreamDepthMetres(),
                    SkyIslandStandardStepSolver.FlowRegime.SUBCRITICAL,
                    SkyIslandStandardStepSolver.BoundaryLocation.DOWNSTREAM);
            solvedTributaries.add(new TributaryProfile(tributary, junctionStep, branchDepths));
        }
        return new NetworkProfile(receivingReach, receivingDepths, solvedTributaries);
    }

    /** One complete, dimensioned steady reach, ordered from upstream to downstream. */
    public record Reach(
            double[] stationsMetres,
            double[] bedElevationsMetres,
            SkyIslandStandardStepSolver.CrossSection[] sections,
            double[] dischargeAtStationsCubicMetresPerSecond,
            double manningRoughness) {
        public Reach {
            stationsMetres = Objects.requireNonNull(stationsMetres, "stationsMetres").clone();
            bedElevationsMetres =
                    Objects.requireNonNull(bedElevationsMetres, "bedElevationsMetres").clone();
            sections = Objects.requireNonNull(sections, "sections").clone();
            dischargeAtStationsCubicMetresPerSecond =
                    Objects.requireNonNull(
                                    dischargeAtStationsCubicMetresPerSecond,
                                    "dischargeAtStationsCubicMetresPerSecond")
                            .clone();
            if (stationsMetres.length < 2
                    || stationsMetres.length != bedElevationsMetres.length
                    || stationsMetres.length != sections.length
                    || stationsMetres.length != dischargeAtStationsCubicMetresPerSecond.length) {
                throw new IllegalArgumentException(
                        "reach stations, beds, sections, and discharges must have equal length of at least two");
            }
            if (!(manningRoughness > 0.0) || !Double.isFinite(manningRoughness)) {
                throw new IllegalArgumentException("Manning roughness must be finite and positive");
            }
            for (int i = 0; i < stationsMetres.length; i++) {
                if (!Double.isFinite(stationsMetres[i])
                        || !Double.isFinite(bedElevationsMetres[i])
                        || !(dischargeAtStationsCubicMetresPerSecond[i] > 0.0)
                        || !Double.isFinite(dischargeAtStationsCubicMetresPerSecond[i])) {
                    throw new IllegalArgumentException(
                            "reach stations/beds must be finite and discharges finite and positive");
                }
                Objects.requireNonNull(sections[i], "section");
                if (i > 0 && !(stationsMetres[i] > stationsMetres[i - 1])) {
                    throw new IllegalArgumentException(
                            "reach stations must increase strictly upstream to downstream");
                }
            }
        }

        @Override
        public double[] stationsMetres() {
            return stationsMetres.clone();
        }

        @Override
        public double[] bedElevationsMetres() {
            return bedElevationsMetres.clone();
        }

        @Override
        public SkyIslandStandardStepSolver.CrossSection[] sections() {
            return sections.clone();
        }

        @Override
        public double[] dischargeAtStationsCubicMetresPerSecond() {
            return dischargeAtStationsCubicMetresPerSecond.clone();
        }
    }

    /** A tributary reach and its explicit one-dimensional junction transition data. */
    public record Tributary(
            Reach reach,
            double junctionLengthMetres,
            double contractionCoefficient,
            double expansionCoefficient) {
        public Tributary {
            reach = Objects.requireNonNull(reach, "reach");
            if (!(junctionLengthMetres >= 0.0) || !Double.isFinite(junctionLengthMetres)) {
                throw new IllegalArgumentException(
                        "junction length must be finite and nonnegative");
            }
            if (!(contractionCoefficient >= 0.0)
                    || !Double.isFinite(contractionCoefficient)
                    || !(expansionCoefficient >= 0.0)
                    || !Double.isFinite(expansionCoefficient)) {
                throw new IllegalArgumentException(
                        "junction loss coefficients must be finite and nonnegative");
            }
        }
    }

    /** Immutable solved tributary profile and its auditable junction energy step. */
    public record TributaryProfile(
            Tributary tributary,
            SkyIslandStandardStepSolver.JunctionStepResult junctionStep,
            double[] depthsMetres) {
        public TributaryProfile {
            tributary = Objects.requireNonNull(tributary, "tributary");
            junctionStep = Objects.requireNonNull(junctionStep, "junctionStep");
            depthsMetres = Objects.requireNonNull(depthsMetres, "depthsMetres").clone();
            if (depthsMetres.length != tributary.reach().stationsMetres.length) {
                throw new IllegalArgumentException("tributary profile must cover every reach station");
            }
            for (double depth : depthsMetres) {
                if (!(depth > 0.0) || !Double.isFinite(depth)) {
                    throw new IllegalArgumentException("tributary depths must be finite and positive");
                }
            }
        }

        @Override
        public double[] depthsMetres() {
            return depthsMetres.clone();
        }
    }

    /** Immutable network result, with receiving and tributary profiles kept separate. */
    public record NetworkProfile(
            Reach receivingReach,
            double[] receivingDepthsMetres,
            List<TributaryProfile> tributaries) {
        public NetworkProfile {
            receivingReach = Objects.requireNonNull(receivingReach, "receivingReach");
            receivingDepthsMetres =
                    Objects.requireNonNull(receivingDepthsMetres, "receivingDepthsMetres").clone();
            tributaries = List.copyOf(Objects.requireNonNull(tributaries, "tributaries"));
            if (receivingDepthsMetres.length != receivingReach.stationsMetres.length) {
                throw new IllegalArgumentException("receiving profile must cover every reach station");
            }
            for (double depth : receivingDepthsMetres) {
                if (!(depth > 0.0) || !Double.isFinite(depth)) {
                    throw new IllegalArgumentException("receiving depths must be finite and positive");
                }
            }
        }

        @Override
        public double[] receivingDepthsMetres() {
            return receivingDepthsMetres.clone();
        }
    }
}
