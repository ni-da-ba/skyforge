package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;\nimport java.util.TreeMap;

/**
 * Adapts measured junction-section placements to the existing subcritical drainage-tree solver.
 *
 * <p>A reach slice is bounded by its computational sections adjacent to its upstream and
 * downstream semantic nodes. This keeps sections outside the mixing zone while retaining the
 * original mass-balanced station discharges and interval transition coefficients. The adapter does
 * not choose junction losses or clearances; those remain explicit caller-owned model inputs.
 */
public final class SkyIslandJunctionHydraulicInputAdapter {
    private SkyIslandJunctionHydraulicInputAdapter() {}

    /**
     * Creates one solver junction from a section plan and already bounded participant reaches.
     * The receiver slice must start at its planned section; each tributary slice must end at its
     * planned section. Every planned tributary must occur exactly once.
     */
    public static SkyIslandSubcriticalDrainageTreeSolver.Junction junction(
            SkyIslandJunctionCrossSectionPlanner.JunctionSections plan,
            ReachSlice receiver,
            List<ReachSlice> tributaries,
            Map<String, LossCoefficients> lossesByReachId) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(receiver, "receiver");
        List<ReachSlice> branchSlices = List.copyOf(Objects.requireNonNull(tributaries, "tributaries"));
        Objects.requireNonNull(lossesByReachId, "lossesByReachId");
        if (!receiver.reachId().equals(plan.receivingReachId())
                || receiver.firstStationIndex() != plan.receivingStationIndex()) {
            throw new IllegalArgumentException(
                    "receiving reach slice must begin at the planned junction cross section");
        }
        if (branchSlices.size() != plan.tributaries().size()) {
            throw new IllegalArgumentException("junction reach slices must match every planned tributary");
        }

        Set<String> seen = new HashSet<>();
        List<SkyIslandSubcriticalDrainageTreeSolver.Tributary> solverTributaries =
                new ArrayList<>(branchSlices.size());
        for (var planned : plan.tributaries()) {
            ReachSlice slice = branchSlices.stream()
                    .filter(candidate -> candidate.reachId().equals(planned.reachId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "planned tributary is missing its bounded reach slice: " + planned.reachId()));
            if (!seen.add(slice.reachId())
                    || slice.lastStationIndex() != planned.stationIndex()) {
                throw new IllegalArgumentException(
                        "tributary slice must end at its unique planned junction cross section");
            }
            LossCoefficients losses = Objects.requireNonNull(
                    lossesByReachId.get(slice.reachId()),
                    "explicit junction loss coefficients for " + slice.reachId());
            solverTributaries.add(new SkyIslandSubcriticalDrainageTreeSolver.Tributary(
                    slice.reachId(),
                    planned.junctionLengthMetres(),
                    losses.contractionCoefficient(),
                    losses.expansionCoefficient()));
        }
        if (seen.size() != branchSlices.size()
                || !lossesByReachId.keySet().equals(seen)) {
            throw new IllegalArgumentException(
                    "junction loss inputs must correspond exactly to planned tributary reaches");
        }
        return new SkyIslandSubcriticalDrainageTreeSolver.Junction(
                receiver.reachId(), solverTributaries);
    }

    /**
     * Slices a complete set of reaches at their planned junction sections and solves the resulting
     * acyclic subcritical tree from its explicit downstream depth boundary.
     */
    public static SkyIslandSubcriticalDrainageTreeSolver.NetworkProfile solveNetwork(
            List<ReachSlice> reachSlices,
            List<JunctionInput> junctionInputs,
            String terminalReachId,
            double terminalDepthMetres) {
        Objects.requireNonNull(reachSlices, "reachSlices");
        Objects.requireNonNull(junctionInputs, "junctionInputs");
        Map<String, ReachSlice> slicesById = new TreeMap<>();
        for (ReachSlice slice : List.copyOf(reachSlices)) {
            Objects.requireNonNull(slice, "reach slice");
            if (slicesById.put(slice.reachId(), slice) != null) {
                throw new IllegalArgumentException("duplicate bounded hydraulic reach: " + slice.reachId());
            }
        }
        if (slicesById.isEmpty()) {
            throw new IllegalArgumentException("network requires at least one bounded hydraulic reach");
        }

        List<SkyIslandSubcriticalDrainageTreeSolver.Reach> hydraulicReaches =
                slicesById.values().stream()
                        .map(slice -> new SkyIslandSubcriticalDrainageTreeSolver.Reach(
                                slice.reachId(), slice.toHydraulicReach()))
                        .toList();
        List<SkyIslandSubcriticalDrainageTreeSolver.Junction> junctions = new ArrayList<>();
        Set<String> receivingReachIds = new HashSet<>();
        for (JunctionInput input : List.copyOf(junctionInputs)) {
            Objects.requireNonNull(input, "junction input");
            var plan = input.plan();
            if (!receivingReachIds.add(plan.receivingReachId())) {
                throw new IllegalArgumentException(
                        "each receiving reach may have only one upstream junction");
            }
            ReachSlice receiver = slicesById.get(plan.receivingReachId());
            if (receiver == null) {
                throw new IllegalArgumentException(
                        "bounded reach is missing for junction receiver: " + plan.receivingReachId());
            }
            List<ReachSlice> tributaries = plan.tributaries().stream()
                    .map(branch -> {
                        ReachSlice slice = slicesById.get(branch.reachId());
                        if (slice == null) {
                            throw new IllegalArgumentException(
                                    "bounded reach is missing for junction tributary: " + branch.reachId());
                        }
                        return slice;
                    })
                    .toList();
            junctions.add(junction(plan, receiver, tributaries, input.lossesByReachId()));
        }
        return SkyIslandSubcriticalDrainageTreeSolver.solve(
                hydraulicReaches, junctions, terminalReachId, terminalDepthMetres);
    }

    /** One geometric junction plan and its explicit transition-loss inputs. */
    public record JunctionInput(
            SkyIslandJunctionCrossSectionPlanner.JunctionSections plan,
            Map<String, LossCoefficients> lossesByReachId) {
        public JunctionInput {
            plan = Objects.requireNonNull(plan, "plan");
            lossesByReachId = Map.copyOf(Objects.requireNonNull(lossesByReachId, "lossesByReachId"));
            Set<String> expected = new HashSet<>();
            plan.tributaries().forEach(branch -> expected.add(branch.reachId()));
            if (expected.size() != plan.tributaries().size()
                    || !lossesByReachId.keySet().equals(expected)) {
                throw new IllegalArgumentException(
                        "junction losses must exactly match the planned tributary identities");
            }
        }
    }

    /** One reach's computationally bounded interval in the original upstream-to-downstream arrays. */
    public record ReachSlice(
            String reachId,
            SkyIslandSubcriticalJunctionNetworkSolver.Reach fullReach,
            int firstStationIndex,
            int lastStationIndex) {
        public ReachSlice {
            reachId = Objects.requireNonNull(reachId, "reachId");
            fullReach = Objects.requireNonNull(fullReach, "fullReach");
            int count = fullReach.stationsMetres().length;
            if (reachId.isBlank() || firstStationIndex < 0
                    || lastStationIndex <= firstStationIndex || lastStationIndex >= count) {
                throw new IllegalArgumentException(
                        "reach slice must contain at least one interval within the source reach");
            }
        }

        /** Copies the selected stations, preserving SI interval lengths and sectionwise discharges. */
        public SkyIslandSubcriticalJunctionNetworkSolver.Reach toHydraulicReach() {
            double[] stations = fullReach.stationsMetres();
            double stationOrigin = stations[firstStationIndex];
            double[] slicedStations = Arrays.copyOfRange(stations, firstStationIndex, lastStationIndex + 1);
            for (int i = 0; i < slicedStations.length; i++) {
                slicedStations[i] -= stationOrigin;
            }
            return new SkyIslandSubcriticalJunctionNetworkSolver.Reach(
                    slicedStations,
                    Arrays.copyOfRange(
                            fullReach.bedElevationsMetres(), firstStationIndex, lastStationIndex + 1),
                    Arrays.copyOfRange(fullReach.sections(), firstStationIndex, lastStationIndex + 1),
                    Arrays.copyOfRange(
                            fullReach.dischargeAtStationsCubicMetresPerSecond(),
                            firstStationIndex,
                            lastStationIndex + 1),
                    fullReach.manningRoughness(),
                    Arrays.copyOfRange(
                            fullReach.contractionCoefficientsByInterval(),
                            firstStationIndex,
                            lastStationIndex),
                    Arrays.copyOfRange(
                            fullReach.expansionCoefficientsByInterval(),
                            firstStationIndex,
                            lastStationIndex));
        }
    }

    /** Explicit, caller-selected energy-junction transition coefficients. */
    public record LossCoefficients(double contractionCoefficient, double expansionCoefficient) {
        public LossCoefficients {
            if (!(contractionCoefficient >= 0.0) || !Double.isFinite(contractionCoefficient)
                    || !(expansionCoefficient >= 0.0) || !Double.isFinite(expansionCoefficient)) {
                throw new IllegalArgumentException("junction loss coefficients must be finite and nonnegative");
            }
        }
    }
}
