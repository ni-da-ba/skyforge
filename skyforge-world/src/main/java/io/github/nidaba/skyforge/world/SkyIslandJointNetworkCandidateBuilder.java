package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Builds terrain-bounded channel candidates from one shared component-wide bed solution.
 *
 * <p>Every semantic junction has one node identity and one physical position. Reach-local
 * candidate builders consume the corresponding slices of the same solved bed, so they cannot
 * independently reproject the bed at shared nodes. The returned terrain is the order-independent
 * lower envelope of the original surface and all reach valleys; overlap is therefore explicit
 * union-of-carvings rather than reach iteration priority.
 *
 * <p>This class constructs geometry only. It does not assign water or confer realization authority;
 * hydraulics and D2 must qualify against this exact composite terrain before publication.
 */
public final class SkyIslandJointNetworkCandidateBuilder {
    private static final double ELEVATION_TOLERANCE_METRES = 1.0e-8;

    private SkyIslandJointNetworkCandidateBuilder() {}

    public static NetworkCandidate build(
            List<ReachInput> reachInputs,
            String terminalNodeId,
            SkyIslandSemanticField originalTerrain) {
        Objects.requireNonNull(reachInputs, "reachInputs");
        Objects.requireNonNull(terminalNodeId, "terminalNodeId");
        Objects.requireNonNull(originalTerrain, "originalTerrain");
        if (terminalNodeId.isBlank()) {
            throw new IllegalArgumentException("terminal node identity must not be blank");
        }
        List<ReachInput> inputs = List.copyOf(reachInputs);
        if (inputs.isEmpty()) {
            throw new IllegalArgumentException("network candidate requires at least one reach");
        }
        validateTopology(inputs, terminalNodeId);

        Map<String, SkyIslandLocalPosition> positionsByNode = new HashMap<>();
        List<SkyIslandJointNetworkBedSolver.Reach> bedInputs = new ArrayList<>(inputs.size());
        for (ReachInput input : inputs) {
            Objects.requireNonNull(input, "reach input");
            List<SkyIslandLocalPosition> points = input.route().points();
            List<SkyIslandJointNetworkBedSolver.Station> stations = new ArrayList<>(points.size());
            double station = 0.0;
            for (int i = 0; i < points.size(); i++) {
                SkyIslandLocalPosition point = points.get(i);
                if (i > 0) {
                    SkyIslandLocalPosition previous = points.get(i - 1);
                    station += Math.hypot(point.x() - previous.x(), point.z() - previous.z())
                            * input.configuration().horizontalMetresPerLocalUnit();
                }
                String nodeId = i == 0
                        ? input.upstreamNodeId()
                        : i == points.size() - 1
                                ? input.downstreamNodeId()
                                : input.id() + "#station-" + i;
                if (i == 0 || i == points.size() - 1) {
                    SkyIslandLocalPosition prior = positionsByNode.putIfAbsent(nodeId, point);
                    if (prior != null && !prior.equals(point)) {
                        throw new IllegalArgumentException(
                                "shared network node must use one exact physical position: " + nodeId);
                    }
                }
                double normalizedTerrain = originalTerrain.sample(point);
                if (!Double.isFinite(normalizedTerrain)
                        || normalizedTerrain < 0.0
                        || normalizedTerrain > 1.0) {
                    throw new IllegalArgumentException(
                            "network terrain must be finite and normalized to [0, 1]");
                }
                double elevation = input.configuration().verticalDatumMetres()
                        + normalizedTerrain
                                * input.configuration().verticalMetresPerNormalizedUnit();
                stations.add(new SkyIslandJointNetworkBedSolver.Station(
                        nodeId,
                        station,
                        elevation,
                        input.configuration().maximumTerrainEditMetres()));
            }
            bedInputs.add(new SkyIslandJointNetworkBedSolver.Reach(
                    stations, input.configuration().minimumBedSlope()));
        }

        SkyIslandJointNetworkBedSolver.ComponentProfile jointBed =
                SkyIslandJointNetworkBedSolver.solve(bedInputs);
        Map<String, SkyIslandHydraulicCandidateBuilder.Candidate> candidates = new LinkedHashMap<>();
        for (int i = 0; i < inputs.size(); i++) {
            ReachInput input = inputs.get(i);
            SkyIslandJointNetworkBedSolver.ReachProfile profile = jointBed.reaches().get(i);
            if (profile.bedElevationsMetres().length != input.route().points().size()) {
                throw new IllegalStateException("joint bed profile lost a route station");
            }
            SkyIslandHydraulicCandidateBuilder.Candidate candidate =
                    SkyIslandHydraulicCandidateBuilder.build(
                            input.route(),
                            originalTerrain,
                            input.configuration(),
                            profile.bedElevationsMetres());
            if (candidates.put(input.id(), candidate) != null) {
                throw new IllegalStateException("joint network repeated a reach identity");
            }
        }

        double verticalDatum = inputs.getFirst().configuration().verticalDatumMetres();
        double verticalScale = inputs.getFirst().configuration().verticalMetresPerNormalizedUnit();
        for (ReachInput input : inputs) {
            if (Math.abs(input.configuration().verticalDatumMetres() - verticalDatum)
                            > ELEVATION_TOLERANCE_METRES
                    || Math.abs(input.configuration().verticalMetresPerNormalizedUnit() - verticalScale)
                            > ELEVATION_TOLERANCE_METRES) {
                throw new IllegalArgumentException(
                        "all reaches in a joint candidate must share one vertical datum and scale");
            }
        }
        return new NetworkCandidate(
                terminalNodeId, jointBed, candidates, originalTerrain, verticalDatum, verticalScale);
    }

    private static void validateTopology(List<ReachInput> inputs, String terminalNodeId) {
        Map<String, ReachInput> byId = new HashMap<>();
        Map<String, ReachInput> outgoing = new HashMap<>();
        Map<String, List<String>> incomingReachIds = new HashMap<>();
        for (ReachInput input : inputs) {
            Objects.requireNonNull(input, "reach input");
            if (byId.put(input.id(), input) != null) {
                throw new IllegalArgumentException("duplicate network reach identity: " + input.id());
            }
            if (outgoing.put(input.upstreamNodeId(), input) != null) {
                throw new IllegalArgumentException(
                        "network candidate does not support flow splits at node " + input.upstreamNodeId());
            }
            incomingReachIds.computeIfAbsent(input.downstreamNodeId(), ignored -> new ArrayList<>())
                    .add(input.id());
        }
        if (outgoing.containsKey(terminalNodeId)
                || !incomingReachIds.containsKey(terminalNodeId)) {
            throw new IllegalArgumentException("declared terminal must be the unique downstream sink");
        }
        for (String nodeId : incomingReachIds.keySet()) {
            if (!outgoing.containsKey(nodeId) && !nodeId.equals(terminalNodeId)) {
                throw new IllegalArgumentException("network has a nonterminal sink: " + nodeId);
            }
        }

        Map<String, Integer> pending = new HashMap<>();
        List<String> ready = new ArrayList<>();
        for (ReachInput input : inputs) {
            int tributaries = incomingReachIds.getOrDefault(input.upstreamNodeId(), List.of()).size();
            pending.put(input.id(), tributaries);
            if (tributaries == 0) {
                ready.add(input.id());
            }
        }
        ready.sort(String::compareTo);
        int visited = 0;
        while (!ready.isEmpty()) {
            String reachId = ready.removeFirst();
            visited++;
            ReachInput reach = byId.get(reachId);
            if (reach == null) {
                throw new IllegalStateException("ready network reach is missing its input: " + reachId);
            }
            ReachInput receiver = outgoing.get(reach.downstreamNodeId());
            if (receiver != null) {
                int remaining = pending.merge(receiver.id(), -1, Integer::sum);
                if (remaining == 0) {
                    ready.add(receiver.id());
                    ready.sort(String::compareTo);
                }
            }
        }
        if (visited != inputs.size()) {
            throw new IllegalArgumentException("network candidate contains a cycle or disconnected reaches");
        }
        List<String> terminalReaches = incomingReachIds.get(terminalNodeId);
        if (terminalReaches == null || terminalReaches.size() != 1) {
            throw new IllegalArgumentException(
                    "network candidate requires one receiving reach at its terminal");
        }
    }

    private static double distance(SkyIslandLocalPosition first, SkyIslandLocalPosition second) {
        return Math.hypot(first.x() - second.x(), first.z() - second.z());
    }

    /** One directed reach with a route that starts/ends at its shared network node positions. */
    public record ReachInput(
            String id,
            String upstreamNodeId,
            String downstreamNodeId,
            SkyIslandGeomorphicCandidateRoute route,
            SkyIslandHydraulicCandidateBuilder.Configuration configuration) {
        public ReachInput {
            id = Objects.requireNonNull(id, "id");
            upstreamNodeId = Objects.requireNonNull(upstreamNodeId, "upstreamNodeId");
            downstreamNodeId = Objects.requireNonNull(downstreamNodeId, "downstreamNodeId");
            route = Objects.requireNonNull(route, "route");
            configuration = Objects.requireNonNull(configuration, "configuration");
            if (id.isBlank() || upstreamNodeId.isBlank() || downstreamNodeId.isBlank()
                    || upstreamNodeId.equals(downstreamNodeId)) {
                throw new IllegalArgumentException("reach and endpoint identities must be distinct and non-blank");
            }
        }
    }

    /** Geometry-only output; hydraulic qualification is deliberately a separate required step. */
    public static final class NetworkCandidate {
        private final String terminalNodeId;
        private final SkyIslandJointNetworkBedSolver.ComponentProfile bedProfile;
        private final Map<String, SkyIslandHydraulicCandidateBuilder.Candidate> reachesById;
        private final SkyIslandSemanticField originalTerrain;
        private final double verticalDatumMetres;
        private final double verticalMetresPerNormalizedUnit;

        private NetworkCandidate(
                String terminalNodeId,
                SkyIslandJointNetworkBedSolver.ComponentProfile bedProfile,
                Map<String, SkyIslandHydraulicCandidateBuilder.Candidate> reachesById,
                SkyIslandSemanticField originalTerrain,
                double verticalDatumMetres,
                double verticalMetresPerNormalizedUnit) {
            this.terminalNodeId = terminalNodeId;
            this.bedProfile = bedProfile;
            this.reachesById = Collections.unmodifiableMap(new LinkedHashMap<>(new TreeMap<>(reachesById)));
            this.originalTerrain = originalTerrain;
            this.verticalDatumMetres = verticalDatumMetres;
            this.verticalMetresPerNormalizedUnit = verticalMetresPerNormalizedUnit;
        }

        public String terminalNodeId() {
            return terminalNodeId;
        }

        public SkyIslandJointNetworkBedSolver.ComponentProfile bedProfile() {
            return bedProfile;
        }

        public Map<String, SkyIslandHydraulicCandidateBuilder.Candidate> reachesById() {
            return reachesById;
        }

        /**
         * Explicitly unions overlapping valleys by taking their lowest terrain value.
         * This is deterministic, never raises original terrain, and has no reach-order priority.
         */
        public double sampleTerrainMetres(SkyIslandLocalPosition position) {
            Objects.requireNonNull(position, "position");
            double normalized = originalTerrain.sample(position);
            if (!Double.isFinite(normalized) || normalized < 0.0 || normalized > 1.0) {
                throw new IllegalArgumentException("network terrain must be finite and normalized to [0, 1]");
            }
            double result = verticalDatumMetres
                    + normalized * verticalMetresPerNormalizedUnit;
            for (SkyIslandHydraulicCandidateBuilder.Candidate reach : reachesById.values()) {
                result = Math.min(result, reach.sampleTerrainMetres(position));
            }
            return result;
        }
    }
}
