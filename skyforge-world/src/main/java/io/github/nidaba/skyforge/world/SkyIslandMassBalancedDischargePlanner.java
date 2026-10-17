package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;

/**
 * Maps authored catchment-relative flow to one mass-balanced steady-flow drainage tree.
 *
 * <p>A single positive scale converts all relative flows to SI discharge. At a confluence, the
 * receiving section starts with the exact sum of tributary outlet flows. Any local catchment
 * contribution at that node is represented in the first downstream interval through the authored
 * cumulative-flow profile; no tributary is independently rescaled. The final terminal-cell
 * contribution is included in the outlet interval.
 *
 * <p>This planner does not solve hydraulics or choose a physical discharge scale. It produces
 * section-wise discharges and nonnegative interval increments for a caller that calibrates one
 * component scale against the joint channel candidate, then passes the resulting discharges to the
 * standard-step/energy solver.
 */
public final class SkyIslandMassBalancedDischargePlanner {
    private static final double FLOW_TOLERANCE = 1.0e-10;

    private SkyIslandMassBalancedDischargePlanner() {}

    public static NetworkFlows plan(
            List<ReachInput> reaches,
            String terminalNodeId,
            double terminalRelativeDischarge,
            double cubicMetresPerSecondPerRelativeUnit) {
        Objects.requireNonNull(reaches, "reaches");
        Objects.requireNonNull(terminalNodeId, "terminalNodeId");
        if (terminalNodeId.isBlank()) {
            throw new IllegalArgumentException("terminalNodeId must not be blank");
        }
        requirePositiveFinite(terminalRelativeDischarge, "terminalRelativeDischarge");
        requirePositiveFinite(
                cubicMetresPerSecondPerRelativeUnit,
                "cubicMetresPerSecondPerRelativeUnit");

        Map<String, ReachInput> byId = new java.util.TreeMap<>();
        Map<String, ReachInput> outgoing = new HashMap<>();
        Map<String, List<ReachInput>> incoming = new HashMap<>();
        for (ReachInput reach : List.copyOf(reaches)) {
            Objects.requireNonNull(reach, "reach");
            if (byId.put(reach.id(), reach) != null) {
                throw new IllegalArgumentException("duplicate reach identity: " + reach.id());
            }
            if (outgoing.put(reach.upstreamNodeId(), reach) != null) {
                throw new IllegalArgumentException(
                        "drainage network cannot split into multiple downstream reaches at node "
                                + reach.upstreamNodeId());
            }
            incoming.computeIfAbsent(reach.downstreamNodeId(), ignored -> new ArrayList<>()).add(reach);
        }
        if (byId.isEmpty()) {
            throw new IllegalArgumentException("drainage network requires at least one reach");
        }
        if (outgoing.containsKey(terminalNodeId)) {
            throw new IllegalArgumentException("terminal node cannot have a downstream reach");
        }
        if (!incoming.containsKey(terminalNodeId)) {
            throw new IllegalArgumentException("terminal node is disconnected from the network");
        }
        incoming.values().forEach(list -> list.sort(Comparator.comparing(ReachInput::id)));

        // Process reaches from headwaters downstream. A confluence receiver becomes ready only
        // after every tributary outlet has a solved discharge.
        PriorityQueue<String> ready = new PriorityQueue<>();
        Map<String, Integer> unresolvedTributaries = new HashMap<>();
        for (ReachInput reach : byId.values()) {
            int dependencies = incoming.getOrDefault(reach.upstreamNodeId(), List.of()).size();
            unresolvedTributaries.put(reach.id(), dependencies);
            if (dependencies == 0) {
                ready.add(reach.id());
            }
        }

        Map<String, ReachFlows> solved = new LinkedHashMap<>();
        while (!ready.isEmpty()) {
            String id = ready.remove();
            ReachInput reach = byId.get(id);
            List<ReachInput> tributaries = incoming.getOrDefault(reach.upstreamNodeId(), List.of());
            double upstreamFlow = 0.0;
            for (ReachInput tributary : tributaries) {
                ReachFlows tributaryFlow = solved.get(tributary.id());
                if (tributaryFlow == null) {
                    throw new IllegalStateException("tributary was not solved before its receiving reach");
                }
                double[] discharges = tributaryFlow.dischargeAtStationsCubicMetresPerSecond();
                upstreamFlow += discharges[discharges.length - 1];
            }

            double[] relative = reach.relativeDischargeAtStations();
            double[] discharge = new double[relative.length];
            if (tributaries.isEmpty()) {
                discharge[0] = relative[0] * cubicMetresPerSecondPerRelativeUnit;
            } else {
                double authoredNodeFlow = relative[0] * cubicMetresPerSecondPerRelativeUnit;
                if (authoredNodeFlow + flowTolerance(authoredNodeFlow, upstreamFlow) < upstreamFlow) {
                    throw new IllegalArgumentException(
                            "authored confluence accumulation is below incoming flow at node "
                                    + reach.upstreamNodeId());
                }
                discharge[0] = upstreamFlow;
            }

            for (int station = 1; station < relative.length; station++) {
                double authoredFlow = relative[station] * cubicMetresPerSecondPerRelativeUnit;
                double tolerance = flowTolerance(authoredFlow, discharge[station - 1]);
                if (authoredFlow + tolerance < discharge[station - 1]) {
                    throw new IllegalArgumentException(
                            "relative discharge requires negative lateral inflow on reach "
                                    + reach.id() + " at station " + station);
                }
                discharge[station] = Math.max(authoredFlow, discharge[station - 1]);
            }

            if (reach.downstreamNodeId().equals(terminalNodeId)) {
                double terminalFlow =
                        terminalRelativeDischarge * cubicMetresPerSecondPerRelativeUnit;
                double previousFlow = discharge[discharge.length - 1];
                if (terminalFlow + flowTolerance(terminalFlow, previousFlow) < previousFlow) {
                    throw new IllegalArgumentException(
                            "terminal accumulation is below incoming reach flow at node "
                                    + terminalNodeId);
                }
                discharge[discharge.length - 1] = Math.max(terminalFlow, previousFlow);
            }

            double[] intervalLateralInflow = new double[discharge.length - 1];
            for (int interval = 0; interval < intervalLateralInflow.length; interval++) {
                double increment = discharge[interval + 1] - discharge[interval];
                if (increment < 0.0) {
                    throw new IllegalStateException("mass-balanced discharge produced negative lateral flow");
                }
                intervalLateralInflow[interval] = increment;
            }
            ReachFlows flow = new ReachFlows(reach, discharge, intervalLateralInflow);
            solved.put(id, flow);

            ReachInput receiver = outgoing.get(reach.downstreamNodeId());
            if (receiver != null) {
                int remaining = unresolvedTributaries.merge(receiver.id(), -1, Integer::sum);
                if (remaining == 0) {
                    ready.add(receiver.id());
                }
            }
        }

        if (solved.size() != byId.size()) {
            throw new IllegalArgumentException(
                    "drainage network contains a cycle, disconnected reach, or nonterminal sink");
        }
        for (ReachInput reach : byId.values()) {
            if (!reach.downstreamNodeId().equals(terminalNodeId)
                    && !outgoing.containsKey(reach.downstreamNodeId())) {
                throw new IllegalArgumentException(
                        "reach ends at a sink other than the declared terminal: " + reach.id());
            }
        }

        Map<String, ReachFlows> ordered = new LinkedHashMap<>();
        solved.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> ordered.put(entry.getKey(), entry.getValue()));
        double terminalFlow = ordered.values().stream()
                .filter(flow -> flow.reach().downstreamNodeId().equals(terminalNodeId))
                .mapToDouble(flow -> {
                    double[] values = flow.dischargeAtStationsCubicMetresPerSecond();
                    return values[values.length - 1];
                })
                .sum();
        return new NetworkFlows(
                terminalNodeId,
                cubicMetresPerSecondPerRelativeUnit,
                terminalFlow,
                ordered);
    }

    private static double flowTolerance(double first, double second) {
        return FLOW_TOLERANCE * Math.max(1.0, Math.max(Math.abs(first), Math.abs(second)));
    }

    private static void requirePositiveFinite(double value, String name) {
        if (!(value > 0.0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }

    /** One directed reach's authored relative flow samples, upstream to downstream. */
    public record ReachInput(
            String id,
            String upstreamNodeId,
            String downstreamNodeId,
            double[] stationsMetres,
            double[] relativeDischargeAtStations) {
        public ReachInput {
            id = Objects.requireNonNull(id, "id");
            upstreamNodeId = Objects.requireNonNull(upstreamNodeId, "upstreamNodeId");
            downstreamNodeId = Objects.requireNonNull(downstreamNodeId, "downstreamNodeId");
            stationsMetres = Objects.requireNonNull(stationsMetres, "stationsMetres").clone();
            relativeDischargeAtStations =
                    Objects.requireNonNull(relativeDischargeAtStations, "relativeDischargeAtStations")
                            .clone();
            if (id.isBlank() || upstreamNodeId.isBlank() || downstreamNodeId.isBlank()) {
                throw new IllegalArgumentException("reach and node identities must not be blank");
            }
            if (upstreamNodeId.equals(downstreamNodeId)) {
                throw new IllegalArgumentException("reach endpoints must have distinct node identities");
            }
            if (stationsMetres.length < 2
                    || stationsMetres.length != relativeDischargeAtStations.length) {
                throw new IllegalArgumentException(
                        "reach requires matching discharge and station arrays with at least two samples");
            }
            for (int i = 0; i < stationsMetres.length; i++) {
                if (!Double.isFinite(stationsMetres[i])
                        || (i > 0 && !(stationsMetres[i] > stationsMetres[i - 1]))) {
                    throw new IllegalArgumentException(
                            "reach stations must be finite and increase strictly downstream");
                }
                double value = relativeDischargeAtStations[i];
                if (!(value > 0.0) || !Double.isFinite(value)) {
                    throw new IllegalArgumentException(
                            "relative station discharge must be finite and positive");
                }
                if (i > 0 && value + FLOW_TOLERANCE < relativeDischargeAtStations[i - 1]) {
                    throw new IllegalArgumentException(
                            "relative discharge must not decrease downstream");
                }
            }
        }

        @Override
        public double[] stationsMetres() {
            return stationsMetres.clone();
        }

        @Override
        public double[] relativeDischargeAtStations() {
            return relativeDischargeAtStations.clone();
        }
    }

    /** Immutable section-wise discharge and interval runoff for a complete reach. */
    public record ReachFlows(
            ReachInput reach,
            double[] dischargeAtStationsCubicMetresPerSecond,
            double[] lateralInflowByIntervalCubicMetresPerSecond) {
        public ReachFlows {
            reach = Objects.requireNonNull(reach, "reach");
            dischargeAtStationsCubicMetresPerSecond =
                    Objects.requireNonNull(
                                    dischargeAtStationsCubicMetresPerSecond,
                                    "dischargeAtStationsCubicMetresPerSecond")
                            .clone();
            lateralInflowByIntervalCubicMetresPerSecond =
                    Objects.requireNonNull(
                                    lateralInflowByIntervalCubicMetresPerSecond,
                                    "lateralInflowByIntervalCubicMetresPerSecond")
                            .clone();
            if (dischargeAtStationsCubicMetresPerSecond.length != reach.stationsMetres().length
                    || lateralInflowByIntervalCubicMetresPerSecond.length
                            != dischargeAtStationsCubicMetresPerSecond.length - 1) {
                throw new IllegalArgumentException("flow arrays must cover every reach station and interval");
            }
            for (double discharge : dischargeAtStationsCubicMetresPerSecond) {
                if (!(discharge > 0.0) || !Double.isFinite(discharge)) {
                    throw new IllegalArgumentException("station discharge must be finite and positive");
                }
            }
            for (double lateral : lateralInflowByIntervalCubicMetresPerSecond) {
                if (!(lateral >= 0.0) || !Double.isFinite(lateral)) {
                    throw new IllegalArgumentException("interval lateral inflow must be finite and nonnegative");
                }
            }
        }

        @Override
        public double[] dischargeAtStationsCubicMetresPerSecond() {
            return dischargeAtStationsCubicMetresPerSecond.clone();
        }

        @Override
        public double[] lateralInflowByIntervalCubicMetresPerSecond() {
            return lateralInflowByIntervalCubicMetresPerSecond.clone();
        }
    }

    /** Deterministically ordered component flows and their single calibrated scale. */
    public record NetworkFlows(
            String terminalNodeId,
            double cubicMetresPerSecondPerRelativeUnit,
            double terminalDischargeCubicMetresPerSecond,
            Map<String, ReachFlows> reachesById) {
        public NetworkFlows {
            terminalNodeId = Objects.requireNonNull(terminalNodeId, "terminalNodeId");
            if (terminalNodeId.isBlank()
                    || !(cubicMetresPerSecondPerRelativeUnit > 0.0)
                    || !Double.isFinite(cubicMetresPerSecondPerRelativeUnit)
                    || !(terminalDischargeCubicMetresPerSecond > 0.0)
                    || !Double.isFinite(terminalDischargeCubicMetresPerSecond)) {
                throw new IllegalArgumentException("invalid mass-balanced network discharge result");
            }
            reachesById = Collections.unmodifiableMap(new LinkedHashMap<>(
                    new java.util.TreeMap<>(Objects.requireNonNull(reachesById, "reachesById"))));
            if (reachesById.isEmpty()) {
                throw new IllegalArgumentException("network discharge result must contain reaches");
            }
        }
    }
}