package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Builds and solves a game-calibrated, terrain-coupled subcritical drainage tree.
 *
 * <p>The order is intentional: construct one shared terrain/bed candidate; map authored relative
 * discharge through one component-wide scale; place sections outside explicit junction mixing
 * clearances; derive outlet normal depth from the solved outlet bed; then run the standard-step
 * energy tree solve. Every unsupported topology or infeasible hydraulic transition fails closed.
 * The result is diagnostic/model output only and grants no Minecraft realization authority.
 */
public final class SkyIslandGameScaleDrainageNetworkModel {
    private SkyIslandGameScaleDrainageNetworkModel() {}

    public static NetworkSolution solve(
            List<ReachInput> reachInputs,
            String terminalNodeId,
            double terminalRelativeDischarge,
            SkyIslandSemanticField originalTerrain) {
        Objects.requireNonNull(reachInputs, "reachInputs");
        Objects.requireNonNull(terminalNodeId, "terminalNodeId");
        Objects.requireNonNull(originalTerrain, "originalTerrain");
        List<ReachInput> inputs = List.copyOf(reachInputs);
        if (inputs.isEmpty()) {
            throw new IllegalArgumentException("game-scale drainage solve requires at least one reach");
        }

        Map<String, ReachInput> inputById = new TreeMap<>();
        for (ReachInput input : inputs) {
            Objects.requireNonNull(input, "reach input");
            if (inputById.put(input.candidateReach().id(), input) != null) {
                throw new IllegalArgumentException(
                        "duplicate game-scale drainage reach identity: " + input.candidateReach().id());
            }
        }
        SkyIslandJointNetworkCandidateBuilder.NetworkCandidate candidate =
                SkyIslandJointNetworkCandidateBuilder.build(
                        inputs.stream().map(ReachInput::candidateReach).toList(),
                        terminalNodeId,
                        originalTerrain);

        List<SkyIslandMassBalancedDischargePlanner.ReachInput> relativeFlows = new ArrayList<>();
        for (ReachInput input : inputs) {
            var hydraulicCandidate = candidate.reachesById().get(input.candidateReach().id());
            relativeFlows.add(new SkyIslandMassBalancedDischargePlanner.ReachInput(
                    input.candidateReach().id(),
                    input.candidateReach().upstreamNodeId(),
                    input.candidateReach().downstreamNodeId(),
                    hydraulicCandidate.stationsMetres(),
                    input.relativeDischargeAtStations()));
        }
        SkyIslandMassBalancedDischargePlanner.NetworkFlows unitFlows =
                SkyIslandMassBalancedDischargePlanner.plan(
                        relativeFlows, terminalNodeId, terminalRelativeDischarge, 1.0);
        Map<String, SkyIslandSubcriticalJunctionNetworkSolver.Reach> hydraulicReaches =
                new TreeMap<>();

        Map<String, List<ReachInput>> incomingByNode = new TreeMap<>();
        Map<String, ReachInput> outgoingByNode = new TreeMap<>();
        for (ReachInput input : inputs) {
            incomingByNode.computeIfAbsent(
                            input.candidateReach().downstreamNodeId(), ignored -> new ArrayList<>())
                    .add(input);
            outgoingByNode.put(input.candidateReach().upstreamNodeId(), input);
        }
        Map<String, SkyIslandJunctionCrossSectionPlanner.JunctionSections> plans = new TreeMap<>();
        for (Map.Entry<String, List<ReachInput>> entry : incomingByNode.entrySet()) {
            List<ReachInput> tributaries = entry.getValue();
            if (tributaries.size() < 2) {
                continue;
            }
            ReachInput receiver = outgoingByNode.get(entry.getKey());
            if (receiver == null) {
                throw new IllegalArgumentException(
                        "confluence requires a receiving reach at node " + entry.getKey());
            }
            List<SkyIslandJunctionCrossSectionPlanner.ReachInput> junctionReachInputs =
                    new ArrayList<>(tributaries.size() + 1);
            junctionReachInputs.add(toSectionInput(receiver, candidate));
            tributaries.stream()
                    .sorted(java.util.Comparator.comparing(value -> value.candidateReach().id()))
                    .map(input -> toSectionInput(input, candidate))
                    .forEach(junctionReachInputs::add);
            var plan = SkyIslandJunctionCrossSectionPlanner.plan(entry.getKey(), junctionReachInputs);
            if (plans.put(entry.getKey(), plan) != null) {
                throw new IllegalStateException("duplicate semantic confluence identity: " + entry.getKey());
            }
        }

        // Keep runoff outside each excluded mixing gap: branch contributions enter before
        // the tributary computational section, and receiver-reach additions begin after its
        // computational section. Thus the HEC energy-junction sections close exactly in flow.
        relativeFlows = deferJunctionGapRunoff(relativeFlows, unitFlows, plans.values());
        unitFlows = SkyIslandMassBalancedDischargePlanner.plan(
                relativeFlows, terminalNodeId, terminalRelativeDischarge, 1.0);
        final var adjustedUnitFlows = unitFlows;
        double dischargeScale = SkyIslandGameScaleHydraulicModel.calibrateComponentDischargeScale(
                inputs.stream()
                        .map(input -> capacityProfile(
                                input,
                                candidate.reachesById().get(input.candidateReach().id()),
                                adjustedUnitFlows.reachesById().get(input.candidateReach().id())))
                        .toList());
        SkyIslandMassBalancedDischargePlanner.NetworkFlows flows =
                SkyIslandMassBalancedDischargePlanner.plan(
                relativeFlows,
                terminalNodeId,
                terminalRelativeDischarge,
                dischargeScale);
        for (ReachInput input : inputs) {
            String id = input.candidateReach().id();
            var geometry = candidate.reachesById().get(id);
            var configuration = input.candidateReach().configuration();
            var discharges = flows.reachesById().get(id).dischargeAtStationsCubicMetresPerSecond();
            var section = section(configuration);
            SkyIslandStandardStepSolver.CrossSection[] sections =
                    new SkyIslandStandardStepSolver.CrossSection[discharges.length];
            Arrays.fill(sections, section);
            hydraulicReaches.put(
                    id,
                    new SkyIslandSubcriticalJunctionNetworkSolver.Reach(
                            geometry.stationsMetres(),
                            geometry.bedElevationsMetres(),
                            sections,
                            discharges,
                            SkyIslandGameScaleHydraulicModel.MANNING_ROUGHNESS,
                            input.contractionCoefficientsByInterval(),
                            input.expansionCoefficientsByInterval()));
        }

        Map<String, Integer> firstStationByReach = new TreeMap<>();
        Map<String, Integer> lastStationByReach = new TreeMap<>();
        for (ReachInput input : inputs) {
            String id = input.candidateReach().id();
            int last = candidate.reachesById().get(id).stationsMetres().length - 1;
            firstStationByReach.put(id, 0);
            lastStationByReach.put(id, last);
        }
        List<SkyIslandJunctionHydraulicInputAdapter.JunctionInput> solverJunctions =
                new ArrayList<>(plans.size());
        for (var plan : plans.values()) {
            firstStationByReach.put(plan.receivingReachId(), plan.receivingStationIndex());
            Map<String, SkyIslandJunctionHydraulicInputAdapter.LossCoefficients> losses =
                    new TreeMap<>();
            for (var tributary : plan.tributaries()) {
                lastStationByReach.put(tributary.reachId(), tributary.stationIndex());
                losses.put(
                        tributary.reachId(),
                        inputById.get(tributary.reachId()).junctionLossCoefficients());
            }
            solverJunctions.add(
                    new SkyIslandJunctionHydraulicInputAdapter.JunctionInput(plan, losses));
        }

        List<SkyIslandJunctionHydraulicInputAdapter.ReachSlice> slices = new ArrayList<>(inputs.size());
        Map<String, SkyIslandJunctionHydraulicInputAdapter.ReachSlice> slicesById = new TreeMap<>();
        for (ReachInput input : inputs) {
            String id = input.candidateReach().id();
            var slice = new SkyIslandJunctionHydraulicInputAdapter.ReachSlice(
                    id,
                    hydraulicReaches.get(id),
                    firstStationByReach.get(id),
                    lastStationByReach.get(id));
            slices.add(slice);
            slicesById.put(id, slice);
        }

        ReachInput terminalReach = inputs.stream()
                .filter(input -> input.candidateReach().downstreamNodeId().equals(terminalNodeId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "terminal node must have one receiving reach: " + terminalNodeId));
        String terminalReachId = terminalReach.candidateReach().id();
        var terminalGeometry = candidate.reachesById().get(terminalReachId);
        double[] terminalBeds = terminalGeometry.bedElevationsMetres();
        double[] terminalStations = terminalGeometry.stationsMetres();
        int terminalIndex = terminalBeds.length - 1;
        double outletEnergySlope = (terminalBeds[terminalIndex - 1] - terminalBeds[terminalIndex])
                / (terminalStations[terminalIndex] - terminalStations[terminalIndex - 1]);
        double terminalDischarge =
                flows.reachesById().get(terminalReachId)
                        .dischargeAtStationsCubicMetresPerSecond()[terminalIndex];
        double terminalNormalDepth = normalDepth(
                terminalReach.candidateReach().configuration(),
                terminalDischarge,
                outletEnergySlope);

        SkyIslandSubcriticalDrainageTreeSolver.NetworkProfile profile =
                SkyIslandJunctionHydraulicInputAdapter.solveNetwork(
                        slices, solverJunctions, terminalReachId, terminalNormalDepth);
        return new NetworkSolution(
                candidate,
                flows,
                terminalReachId,
                terminalNormalDepth,
                profile,
                plans,
                slicesById);
    }

    private static List<SkyIslandMassBalancedDischargePlanner.ReachInput> deferJunctionGapRunoff(
            List<SkyIslandMassBalancedDischargePlanner.ReachInput> reachInputs,
            SkyIslandMassBalancedDischargePlanner.NetworkFlows initialFlows,
            java.util.Collection<SkyIslandJunctionCrossSectionPlanner.JunctionSections> plans) {
        Map<String, Integer> receiverIndexByReach = new TreeMap<>();
        Map<String, Double> receiverFlowByReach = new TreeMap<>();
        Map<String, Integer> tributaryIndexByReach = new TreeMap<>();
        Map<String, Double> tributaryFlowByReach = new TreeMap<>();
        for (var plan : plans) {
            double incomingFlow = 0.0;
            for (var tributary : plan.tributaries()) {
                var branchFlows = initialFlows.reachesById().get(tributary.reachId());
                if (branchFlows == null) {
                    throw new IllegalArgumentException(
                            "junction plan references a missing mass-balanced tributary");
                }
                double[] branchDischarge = branchFlows.dischargeAtStationsCubicMetresPerSecond();
                incomingFlow += branchDischarge[branchDischarge.length - 1];
                if (tributaryIndexByReach.put(tributary.reachId(), tributary.stationIndex()) != null
                        || tributaryFlowByReach.put(tributary.reachId(), branchDischarge[branchDischarge.length - 1])
                                != null) {
                    throw new IllegalArgumentException(
                            "a reach cannot feed more than one semantic junction");
                }
            }
            if (receiverIndexByReach.put(plan.receivingReachId(), plan.receivingStationIndex()) != null
                    || receiverFlowByReach.put(plan.receivingReachId(), incomingFlow) != null) {
                throw new IllegalArgumentException(
                        "a reach cannot receive more than one semantic junction");
            }
        }

        List<SkyIslandMassBalancedDischargePlanner.ReachInput> adjusted =
                new ArrayList<>(reachInputs.size());
        for (var reach : reachInputs) {
            double[] relative = reach.relativeDischargeAtStations();
            Integer receiverIndex = receiverIndexByReach.get(reach.id());
            Integer tributaryIndex = tributaryIndexByReach.get(reach.id());
            if (receiverIndex != null && tributaryIndex != null && receiverIndex >= tributaryIndex) {
                throw new IllegalArgumentException(
                        "reach has no modeled interval between its upstream and downstream junction sections: "
                                + reach.id());
            }
            if (receiverIndex != null) {
                double incomingFlow = receiverFlowByReach.get(reach.id());
                for (int station = 1; station <= receiverIndex; station++) {
                    relative[station] = incomingFlow;
                }
            }
            if (tributaryIndex != null) {
                double outletFlow = tributaryFlowByReach.get(reach.id());
                for (int station = tributaryIndex; station < relative.length; station++) {
                    relative[station] = outletFlow;
                }
            }
            adjusted.add(new SkyIslandMassBalancedDischargePlanner.ReachInput(
                    reach.id(),
                    reach.upstreamNodeId(),
                    reach.downstreamNodeId(),
                    reach.stationsMetres(),
                    relative));
        }
        return List.copyOf(adjusted);
    }

    private static SkyIslandJunctionCrossSectionPlanner.ReachInput toSectionInput(
            ReachInput input,
            SkyIslandJointNetworkCandidateBuilder.NetworkCandidate candidate) {
        var definition = input.candidateReach();
        var configuration = definition.configuration();
        return new SkyIslandJunctionCrossSectionPlanner.ReachInput(
                definition.id(),
                definition.upstreamNodeId(),
                definition.downstreamNodeId(),
                definition.route().points(),
                candidate.reachesById().get(definition.id()).stationsMetres(),
                configuration.horizontalMetresPerLocalUnit(),
                topWidth(configuration) * 0.5,
                input.minimumMixingClearanceMetres());
    }

    private static SkyIslandGameScaleHydraulicModel.BankfullCapacityProfile capacityProfile(
            ReachInput input,
            SkyIslandHydraulicCandidateBuilder.Candidate candidate,
            SkyIslandMassBalancedDischargePlanner.ReachFlows flows) {
        double[] stations = candidate.stationsMetres();
        double[] beds = candidate.bedElevationsMetres();
        double[] intervalSlopes = new double[stations.length - 1];
        for (int i = 0; i < intervalSlopes.length; i++) {
            intervalSlopes[i] = (beds[i] - beds[i + 1]) / (stations[i + 1] - stations[i]);
            if (!(intervalSlopes[i] > 0.0) || !Double.isFinite(intervalSlopes[i])) {
                throw new IllegalArgumentException(
                        "component bankfull capacity requires positive bed grade at every interval");
            }
        }
        double[] stationSlopes = new double[stations.length];
        stationSlopes[0] = intervalSlopes[0];
        stationSlopes[stationSlopes.length - 1] = intervalSlopes[intervalSlopes.length - 1];
        for (int i = 1; i < stationSlopes.length - 1; i++) {
            stationSlopes[i] = Math.min(intervalSlopes[i - 1], intervalSlopes[i]);
        }
        var configuration = input.candidateReach().configuration();
        return new SkyIslandGameScaleHydraulicModel.BankfullCapacityProfile(
                flows.dischargeAtStationsCubicMetresPerSecond(),
                stationSlopes,
                configuration.bottomWidthMetres(),
                configuration.sideSlopeHorizontalToVertical(),
                configuration.bankfullDepthMetres());
    }

    private static double normalDepth(
            SkyIslandHydraulicCandidateBuilder.Configuration configuration,
            double discharge,
            double energySlope) {
        var section = section(configuration);
        return SkyIslandManningNormalDepthSolver.solve(
                discharge,
                SkyIslandGameScaleHydraulicModel.MANNING_ROUGHNESS,
                energySlope,
                new SkyIslandManningNormalDepthSolver.CrossSection() {
                    @Override
                    public double area(double depth) {
                        return section.area(depth);
                    }

                    @Override
                    public double wettedPerimeter(double depth) {
                        return section.wettedPerimeter(depth);
                    }
                });
    }

    private static SkyIslandStandardStepSolver.CrossSection section(
            SkyIslandHydraulicCandidateBuilder.Configuration configuration) {
        double bottomWidth = configuration.bottomWidthMetres();
        double sideSlope = configuration.sideSlopeHorizontalToVertical();
        return new SkyIslandStandardStepSolver.CrossSection() {
            @Override
            public double area(double depth) {
                return bottomWidth * depth + sideSlope * depth * depth;
            }

            @Override
            public double wettedPerimeter(double depth) {
                return bottomWidth + 2.0 * depth * Math.hypot(1.0, sideSlope);
            }

            @Override
            public double topWidth(double depth) {
                return bottomWidth + 2.0 * sideSlope * depth;
            }
        };
    }

    private static double topWidth(SkyIslandHydraulicCandidateBuilder.Configuration configuration) {
        return configuration.bottomWidthMetres()
                + 2.0 * configuration.sideSlopeHorizontalToVertical()
                        * configuration.bankfullDepthMetres();
    }

    /** One candidate-backed reach with authored cumulative flows and explicit junction assumptions. */
    public record ReachInput(
            SkyIslandJointNetworkCandidateBuilder.ReachInput candidateReach,
            double[] relativeDischargeAtStations,
            double[] contractionCoefficientsByInterval,
            double[] expansionCoefficientsByInterval,
            double minimumMixingClearanceMetres,
            SkyIslandJunctionHydraulicInputAdapter.LossCoefficients junctionLossCoefficients) {
        public ReachInput {
            candidateReach = Objects.requireNonNull(candidateReach, "candidateReach");
            relativeDischargeAtStations =
                    Objects.requireNonNull(relativeDischargeAtStations, "relativeDischargeAtStations").clone();
            contractionCoefficientsByInterval =
                    Objects.requireNonNull(contractionCoefficientsByInterval, "contractionCoefficientsByInterval")
                            .clone();
            expansionCoefficientsByInterval =
                    Objects.requireNonNull(expansionCoefficientsByInterval, "expansionCoefficientsByInterval")
                            .clone();
            junctionLossCoefficients =
                    Objects.requireNonNull(junctionLossCoefficients, "junctionLossCoefficients");
            int stationCount = candidateReach.route().points().size();
            if (relativeDischargeAtStations.length != stationCount
                    || contractionCoefficientsByInterval.length != stationCount - 1
                    || expansionCoefficientsByInterval.length != stationCount - 1) {
                throw new IllegalArgumentException(
                        "flow samples and interval losses must match the authored route");
            }
            if (!(minimumMixingClearanceMetres >= 0.0)
                    || !Double.isFinite(minimumMixingClearanceMetres)) {
                throw new IllegalArgumentException("mixing clearance must be finite and nonnegative");
            }
            for (double value : contractionCoefficientsByInterval) {
                if (!(value >= 0.0) || !Double.isFinite(value)) {
                    throw new IllegalArgumentException("contraction coefficients must be finite and nonnegative");
                }
            }
            for (double value : expansionCoefficientsByInterval) {
                if (!(value >= 0.0) || !Double.isFinite(value)) {
                    throw new IllegalArgumentException("expansion coefficients must be finite and nonnegative");
                }
            }
        }

        @Override
        public double[] relativeDischargeAtStations() {
            return relativeDischargeAtStations.clone();
        }

        @Override
        public double[] contractionCoefficientsByInterval() {
            return contractionCoefficientsByInterval.clone();
        }

        @Override
        public double[] expansionCoefficientsByInterval() {
            return expansionCoefficientsByInterval.clone();
        }
    }

    /** Diagnostic result; consumers must still apply unchanged D2 and realization qualification. */
    public record NetworkSolution(
            SkyIslandJointNetworkCandidateBuilder.NetworkCandidate terrainCandidate,
            SkyIslandMassBalancedDischargePlanner.NetworkFlows massBalancedFlows,
            String terminalReachId,
            double terminalNormalDepthMetres,
            SkyIslandSubcriticalDrainageTreeSolver.NetworkProfile hydraulicProfile,
            Map<String, SkyIslandJunctionCrossSectionPlanner.JunctionSections> junctionSectionsByNodeId,
            Map<String, SkyIslandJunctionHydraulicInputAdapter.ReachSlice> reachSlicesById) {
        public NetworkSolution {
            terrainCandidate = Objects.requireNonNull(terrainCandidate, "terrainCandidate");
            massBalancedFlows = Objects.requireNonNull(massBalancedFlows, "massBalancedFlows");
            terminalReachId = Objects.requireNonNull(terminalReachId, "terminalReachId");
            hydraulicProfile = Objects.requireNonNull(hydraulicProfile, "hydraulicProfile");
            junctionSectionsByNodeId = Collections.unmodifiableMap(
                    new LinkedHashMap<>(new TreeMap<>(junctionSectionsByNodeId)));
            reachSlicesById = Collections.unmodifiableMap(
                    new LinkedHashMap<>(new TreeMap<>(reachSlicesById)));
            if (terminalReachId.isBlank()
                    || !(terminalNormalDepthMetres > 0.0)
                    || !Double.isFinite(terminalNormalDepthMetres)
                    || reachSlicesById.isEmpty()) {
                throw new IllegalArgumentException("invalid game-scale drainage network solution");
            }
        }
    }
}
