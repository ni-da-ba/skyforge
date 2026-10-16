package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Solves a steady subcritical, acyclic drainage tree from its downstream boundary upstream.
 *
 * <p>Each reach is solved with the standard-step energy equation. At each combining junction, the
 * already-solved receiving reach supplies the downstream control; every tributary is then solved
 * independently through its own junction length and transition losses. Nested confluences are
 * handled recursively after their receiving reach has acquired its downstream-controlled profile.
 *
 * <p>This composes HEC-RAS-style reach and energy-junction calculations. It does not solve loops,
 * flow splits, mixed/supercritical junctions, or two-dimensional mixing; those cases fail closed or
 * belong to a different model.
 */
public final class SkyIslandSubcriticalDrainageTreeSolver {
    private static final double FLOW_BALANCE_RELATIVE_TOLERANCE = 1.0e-9;
    private static final double FLOW_BALANCE_ABSOLUTE_TOLERANCE = 1.0e-9;

    private SkyIslandSubcriticalDrainageTreeSolver() {}

    /**
     * Solves a complete combining tree from the terminal reach's explicit downstream depth.
     *
     * @param reaches all unique reach segments in the drainage component
     * @param junctions one record per reach whose upstream end receives two or more tributaries
     * @param terminalReachId the single downstream-most reach terminating at the boundary
     * @param terminalDepthMetres explicit subcritical depth at that reach's downstream section
     */
    public static NetworkProfile solve(
            List<Reach> reaches,
            List<Junction> junctions,
            String terminalReachId,
            double terminalDepthMetres) {
        Objects.requireNonNull(reaches, "reaches");
        Objects.requireNonNull(junctions, "junctions");
        Objects.requireNonNull(terminalReachId, "terminalReachId");
        if (terminalReachId.isBlank()) {
            throw new IllegalArgumentException("terminal reach identity must not be blank");
        }
        if (!(terminalDepthMetres > 0.0) || !Double.isFinite(terminalDepthMetres)) {
            throw new IllegalArgumentException("terminal depth must be finite and positive");
        }

        Map<String, Reach> reachesById = new TreeMap<>();
        for (Reach reach : List.copyOf(reaches)) {
            Objects.requireNonNull(reach, "reach");
            if (reachesById.put(reach.id(), reach) != null) {
                throw new IllegalArgumentException("duplicate drainage reach identity: " + reach.id());
            }
        }
        if (!reachesById.containsKey(terminalReachId)) {
            throw new IllegalArgumentException("terminal reach is absent from the network");
        }

        Map<String, Junction> junctionByReceivingReach = new HashMap<>();
        Set<String> tributaryReachIds = new HashSet<>();
        for (Junction junction : List.copyOf(junctions)) {
            Objects.requireNonNull(junction, "junction");
            Reach receiving = reachesById.get(junction.receivingReachId());
            if (receiving == null) {
                throw new IllegalArgumentException(
                        "junction receiving reach is absent: " + junction.receivingReachId());
            }
            if (junctionByReceivingReach.put(junction.receivingReachId(), junction) != null) {
                throw new IllegalArgumentException(
                        "split a receiving reach at each distinct upstream confluence");
            }
            double receivingDischarge =
                    receiving.hydraulicReach().dischargeAtStationsCubicMetresPerSecond()[0];
            double tributaryDischargeSum = 0.0;
            for (Tributary tributary : junction.tributaries()) {
                Reach branch = reachesById.get(tributary.reachId());
                if (branch == null) {
                    throw new IllegalArgumentException(
                            "junction tributary reach is absent: " + tributary.reachId());
                }
                if (branch.id().equals(receiving.id())) {
                    throw new IllegalArgumentException("a reach cannot flow into itself");
                }
                if (!tributaryReachIds.add(branch.id())) {
                    throw new IllegalArgumentException(
                            "each reach may connect to only one downstream junction");
                }
                double[] branchDischarge =
                        branch.hydraulicReach().dischargeAtStationsCubicMetresPerSecond();
                tributaryDischargeSum += branchDischarge[branchDischarge.length - 1];
            }
            if (!flowBalanced(receivingDischarge, tributaryDischargeSum)) {
                throw new IllegalArgumentException(
                        "tributary junction discharges must sum to receiving-reach discharge at "
                                + receiving.id());
            }
        }
        if (tributaryReachIds.contains(terminalReachId)) {
            throw new IllegalArgumentException("terminal reach cannot feed another receiving reach");
        }
        for (String reachId : reachesById.keySet()) {
            if (!reachId.equals(terminalReachId) && !tributaryReachIds.contains(reachId)) {
                throw new IllegalArgumentException(
                        "nonterminal reach has no downstream receiving junction: " + reachId);
            }
        }

        Map<String, ReachProfile> solved = new TreeMap<>();
        Set<String> visiting = new HashSet<>();
        solveUpstream(
                terminalReachId,
                terminalDepthMetres,
                Optional.empty(),
                reachesById,
                junctionByReceivingReach,
                solved,
                visiting);
        if (solved.size() != reachesById.size()) {
            throw new IllegalArgumentException(
                    "drainage network contains a disconnected reach or a directed cycle");
        }
        return new NetworkProfile(terminalReachId, solved);
    }

    private static void solveUpstream(
            String reachId,
            double downstreamBoundaryDepthMetres,
            Optional<SkyIslandStandardStepSolver.JunctionStepResult> downstreamJunctionStep,
            Map<String, Reach> reachesById,
            Map<String, Junction> junctionByReceivingReach,
            Map<String, ReachProfile> solved,
            Set<String> visiting) {
        if (solved.containsKey(reachId)) {
            throw new IllegalArgumentException("drainage reach is connected more than once: " + reachId);
        }
        if (!visiting.add(reachId)) {
            throw new IllegalArgumentException("drainage network contains a directed cycle at " + reachId);
        }
        Reach reach = reachesById.get(reachId);
        SkyIslandSubcriticalJunctionNetworkSolver.Reach hydraulic = reach.hydraulicReach();
        double[] depths = SkyIslandStandardStepSolver.solve(
                hydraulic.stationsMetres(),
                hydraulic.bedElevationsMetres(),
                hydraulic.sections(),
                hydraulic.dischargeAtStationsCubicMetresPerSecond(),
                hydraulic.manningRoughness(),
                downstreamBoundaryDepthMetres,
                SkyIslandStandardStepSolver.FlowRegime.SUBCRITICAL,
                SkyIslandStandardStepSolver.BoundaryLocation.DOWNSTREAM);
        ReachProfile profile = new ReachProfile(reach, depths, downstreamJunctionStep);
        solved.put(reachId, profile);

        Junction junction = junctionByReceivingReach.get(reachId);
        if (junction != null) {
            int receivingIndex = 0;
            double receivingDepth = depths[receivingIndex];
            double receivingBed = hydraulic.bedElevationsMetres()[receivingIndex];
            SkyIslandStandardStepSolver.CrossSection receivingSection =
                    hydraulic.sections()[receivingIndex];
            double receivingDischarge =
                    hydraulic.dischargeAtStationsCubicMetresPerSecond()[receivingIndex];
            for (Tributary tributary : junction.tributaries()) {
                Reach branch = reachesById.get(tributary.reachId());
                SkyIslandSubcriticalJunctionNetworkSolver.Reach branchHydraulic =
                        branch.hydraulicReach();
                int branchEnd = branchHydraulic.stationsMetres().length - 1;
                SkyIslandStandardStepSolver.JunctionStepResult step =
                        SkyIslandStandardStepSolver.solveSubcriticalCombiningJunction(
                                branchHydraulic.bedElevationsMetres()[branchEnd],
                                receivingBed,
                                receivingDepth,
                                branchHydraulic.sections()[branchEnd],
                                receivingSection,
                                branchHydraulic.dischargeAtStationsCubicMetresPerSecond()[branchEnd],
                                receivingDischarge,
                                branchHydraulic.manningRoughness(),
                                hydraulic.manningRoughness(),
                                tributary.junctionLengthMetres(),
                                tributary.contractionCoefficient(),
                                tributary.expansionCoefficient());
                solveUpstream(
                        branch.id(),
                        step.upstreamDepthMetres(),
                        Optional.of(step),
                        reachesById,
                        junctionByReceivingReach,
                        solved,
                        visiting);
            }
        }
        visiting.remove(reachId);
    }

    private static boolean flowBalanced(double receiving, double incoming) {
        return Double.isFinite(incoming)
                && Math.abs(incoming - receiving)
                        <= Math.max(
                                FLOW_BALANCE_ABSOLUTE_TOLERANCE,
                                receiving * FLOW_BALANCE_RELATIVE_TOLERANCE);
    }

    /** One reach segment, ordered upstream to downstream. */
    public record Reach(
            String id,
            SkyIslandSubcriticalJunctionNetworkSolver.Reach hydraulicReach) {
        public Reach {
            id = Objects.requireNonNull(id, "id");
            hydraulicReach = Objects.requireNonNull(hydraulicReach, "hydraulicReach");
            if (id.isBlank()) {
                throw new IllegalArgumentException("reach identity must not be blank");
            }
        }
    }

    /** One upstream branch's measured junction distance and explicit transition coefficients. */
    public record Tributary(
            String reachId,
            double junctionLengthMetres,
            double contractionCoefficient,
            double expansionCoefficient) {
        public Tributary {
            reachId = Objects.requireNonNull(reachId, "reachId");
            if (reachId.isBlank()) {
                throw new IllegalArgumentException("tributary reach identity must not be blank");
            }
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

    /** A combining junction with an explicit receiving reach and at least two inflow branches. */
    public record Junction(String receivingReachId, List<Tributary> tributaries) {
        public Junction {
            receivingReachId = Objects.requireNonNull(receivingReachId, "receivingReachId");
            tributaries = List.copyOf(Objects.requireNonNull(tributaries, "tributaries"));
            if (receivingReachId.isBlank()) {
                throw new IllegalArgumentException("receiving reach identity must not be blank");
            }
            if (tributaries.size() < 2) {
                throw new IllegalArgumentException(
                        "a combining junction requires at least two tributary reaches");
            }
            tributaries.forEach(value -> Objects.requireNonNull(value, "tributary"));
        }
    }

    /** Solved reach depths and optional energy step connecting its downstream end to its receiver. */
    public record ReachProfile(
            Reach reach,
            double[] depthsMetres,
            Optional<SkyIslandStandardStepSolver.JunctionStepResult> downstreamJunctionStep) {
        public ReachProfile {
            reach = Objects.requireNonNull(reach, "reach");
            depthsMetres = Objects.requireNonNull(depthsMetres, "depthsMetres").clone();
            downstreamJunctionStep =
                    Objects.requireNonNull(downstreamJunctionStep, "downstreamJunctionStep");
            if (depthsMetres.length != reach.hydraulicReach().stationsMetres().length) {
                throw new IllegalArgumentException("reach profile must cover every reach station");
            }
            for (double depth : depthsMetres) {
                if (!(depth > 0.0) || !Double.isFinite(depth)) {
                    throw new IllegalArgumentException("reach depths must be finite and positive");
                }
            }
        }

        @Override
        public double[] depthsMetres() {
            return depthsMetres.clone();
        }
    }

    /** Immutable complete network solution with deterministically ordered reach identities. */
    public record NetworkProfile(
            String terminalReachId,
            Map<String, ReachProfile> reachesById) {
        public NetworkProfile {
            terminalReachId = Objects.requireNonNull(terminalReachId, "terminalReachId");
            reachesById = Collections.unmodifiableMap(
                    new LinkedHashMap<>(new TreeMap<>(reachesById)));
            if (terminalReachId.isBlank() || reachesById.isEmpty()
                    || !reachesById.containsKey(terminalReachId)) {
                throw new IllegalArgumentException("invalid solved drainage-tree profile");
            }
        }
    }
}
