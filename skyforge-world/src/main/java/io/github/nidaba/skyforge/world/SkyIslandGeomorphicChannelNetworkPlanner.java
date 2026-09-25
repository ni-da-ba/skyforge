package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Chooses one shared physical point for every semantic source, confluence, and terminal, then routes
 * all incident macro reaches against those shared points.
 *
 * <p>Node placement is evaluated on the same globally aligned fine lattice used by
 * {@link SkyIslandTerrainAwareRouteSolver}. This prevents adjacent reaches from independently choosing
 * almost-the-same confluence and relying on a backend carve or fluid fill to join them.
 */
public final class SkyIslandGeomorphicChannelNetworkPlanner {
    private static final double SOURCE_SEARCH_RADIUS_SPACING_FRACTION = 0.55;
    private static final double CONFLUENCE_SEARCH_RADIUS_SPACING_FRACTION = 0.85;
    private static final double TERMINAL_SEARCH_RADIUS_SPACING_FRACTION = 0.35;
    private static final double ROUTE_CORRIDOR_SPACING_FRACTION = 1.65;

    private static final double RIDGE_WEIGHT = 26.0;
    private static final double TERRAIN_LEVEL_WEIGHT = 2.0;
    private static final double DISPLACEMENT_WEIGHT = 1.25;
    private static final double EXTERIOR_WEIGHT = 35.0;
    private static final double LOW_INTERIORITY_THRESHOLD = 0.08;
    private static final double EPSILON = 1.0e-12;

    private SkyIslandGeomorphicChannelNetworkPlanner() {}

    public static SkyIslandGeomorphicChannelNetworkPlan plan(SkyIslandDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        SkyIslandSemanticChannelReachPlan semantics = SkyIslandSemanticChannelReachPlanner.plan(descriptor);
        SkyIslandContinuousHydrologicTerrainField terrain =
                SkyIslandContinuousHydrologicTerrainField.create(descriptor);
        SkyIslandSemanticField interiority = SkyIslandSemanticFieldSet.create(descriptor).interiority();
        return plan(descriptor, semantics, terrain, interiority);
    }

    static SkyIslandGeomorphicChannelNetworkPlan plan(
            SkyIslandDescriptor descriptor,
            SkyIslandSemanticChannelReachPlan semantics,
            SkyIslandSemanticField terrain,
            SkyIslandSemanticField interiority) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(semantics, "semantics");
        Objects.requireNonNull(terrain, "terrain");
        Objects.requireNonNull(interiority, "interiority");
        if (!descriptor.equals(semantics.descriptor())) {
            throw new IllegalArgumentException("semantic reach descriptor must match network descriptor");
        }

        Map<Integer, Integer> incoming = new HashMap<>();
        Map<Integer, Integer> outgoing = new HashMap<>();
        Map<Integer, SkyIslandLocalPosition> semanticCenters = new HashMap<>();

        for (SkyIslandSemanticChannelReach reach : semantics.reaches()) {
            incoming.merge(reach.endCellIndex(), 1, Integer::sum);
            incoming.putIfAbsent(reach.startCellIndex(), 0);
            outgoing.merge(reach.startCellIndex(), 1, Integer::sum);
            outgoing.putIfAbsent(reach.endCellIndex(), 0);
            putCenter(
                    semanticCenters,
                    reach.startCellIndex(),
                    reach.guidancePoints().getFirst());
            putCenter(
                    semanticCenters,
                    reach.endCellIndex(),
                    reach.guidancePoints().getLast());
        }

        List<Integer> nodeIds = semanticCenters.keySet().stream().sorted().toList();
        List<SkyIslandGeomorphicNetworkNode> nodes = new ArrayList<>(nodeIds.size());
        Map<Integer, SkyIslandGeomorphicNetworkNode> byId = new HashMap<>();

        for (int cellIndex : nodeIds) {
            int in = incoming.getOrDefault(cellIndex, 0);
            int out = outgoing.getOrDefault(cellIndex, 0);
            SkyIslandGeomorphicNetworkNodeKind kind;
            if (in == 0 && out > 0) {
                kind = SkyIslandGeomorphicNetworkNodeKind.SOURCE;
            } else if (in >= 2 && out > 0) {
                kind = SkyIslandGeomorphicNetworkNodeKind.CONFLUENCE;
            } else if (out == 0 && in > 0) {
                kind = SkyIslandGeomorphicNetworkNodeKind.TERMINAL;
            } else if (in == 1 && out > 0) {
                // A semantic macro-reach planner should have collapsed this ordinary node.
                throw new IllegalStateException(
                        "ordinary 1-in/1-out node leaked into geomorphic network anchors: " + cellIndex);
            } else {
                throw new IllegalStateException(
                        "unsupported semantic network node degree at " + cellIndex + ": in=" + in + ", out=" + out);
            }

            double radius = semantics.planningSpacing() * switch (kind) {
                case SOURCE -> SOURCE_SEARCH_RADIUS_SPACING_FRACTION;
                case CONFLUENCE -> CONFLUENCE_SEARCH_RADIUS_SPACING_FRACTION;
                case TERMINAL -> TERMINAL_SEARCH_RADIUS_SPACING_FRACTION;
            };
            SkyIslandGeomorphicNetworkNode selected = selectNode(
                    cellIndex,
                    kind,
                    semanticCenters.get(cellIndex),
                    radius,
                    semantics.planningSpacing(),
                    terrain,
                    interiority);
            nodes.add(selected);
            byId.put(cellIndex, selected);
        }

        List<SkyIslandGeomorphicReachRoute> routes = new ArrayList<>(semantics.reaches().size());
        double corridorHalfWidth = semantics.planningSpacing() * ROUTE_CORRIDOR_SPACING_FRACTION;
        for (SkyIslandSemanticChannelReach reach : semantics.reaches()) {
            SkyIslandGeomorphicNetworkNode start = requireNode(byId, reach.startCellIndex());
            SkyIslandGeomorphicNetworkNode end = requireNode(byId, reach.endCellIndex());
            SkyIslandGeomorphicCandidateRoute route = SkyIslandTerrainAwareRouteSolver.solve(
                    terrain,
                    interiority,
                    reach.guidancePoints(),
                    semantics.planningSpacing(),
                    corridorHalfWidth,
                    new SkyIslandGeomorphicRouteAnchor(start.physicalPosition(), 0.0),
                    new SkyIslandGeomorphicRouteAnchor(end.physicalPosition(), 0.0));
            if (!route.points().getFirst().equals(start.physicalPosition())
                    || !route.points().getLast().equals(end.physicalPosition())) {
                throw new IllegalStateException("fine route did not preserve selected shared network anchors");
            }
            routes.add(new SkyIslandGeomorphicReachRoute(reach, route));
        }

        nodes.sort(Comparator.comparingInt(SkyIslandGeomorphicNetworkNode::cellIndex));
        routes.sort(Comparator
                .comparingInt((SkyIslandGeomorphicReachRoute route) ->
                        route.semanticReach().startCellIndex())
                .thenComparingInt(route -> route.semanticReach().endCellIndex()));
        return new SkyIslandGeomorphicChannelNetworkPlan(
                descriptor, semantics.planningSpacing(), nodes, routes);
    }

    private static SkyIslandGeomorphicNetworkNode selectNode(
            int cellIndex,
            SkyIslandGeomorphicNetworkNodeKind kind,
            SkyIslandLocalPosition center,
            double radius,
            double planningSpacing,
            SkyIslandSemanticField terrain,
            SkyIslandSemanticField interiority) {
        double step = planningSpacing / SkyIslandTerrainAwareRouteSolver.FINE_DIVISIONS_PER_PLANNING_CELL;
        double probeRadius = 1.5 * step;
        int minX = (int) Math.ceil((center.x() - radius) / step);
        int maxX = (int) Math.floor((center.x() + radius) / step);
        int minZ = (int) Math.ceil((center.z() - radius) / step);
        int maxZ = (int) Math.floor((center.z() + radius) / step);

        Candidate best = null;
        for (int gz = minZ; gz <= maxZ; gz++) {
            for (int gx = minX; gx <= maxX; gx++) {
                SkyIslandLocalPosition position =
                        new SkyIslandLocalPosition(gx * step, gz * step);
                double displacement = distance(center, position);
                if (displacement > radius + EPSILON) {
                    continue;
                }

                double elevation = terrain.sample(position);
                double surrounding = surroundingMean(terrain, position, probeRadius);
                double ridge = Math.max(0.0, elevation - surrounding);
                double valley = surrounding - elevation;
                double exteriorPenalty =
                        Math.max(0.0, LOW_INTERIORITY_THRESHOLD - interiority.sample(position));
                double normalizedDisplacement = radius <= EPSILON ? 0.0 : displacement / radius;
                double cost;
                if (kind == SkyIslandGeomorphicNetworkNodeKind.TERMINAL) {
                    // Terminal fate is not yet resolved here. An endpoint may later belong to an
                    // edge outlet, retained basin, or hidden-transfer boundary, so this tranche
                    // preserves its semantic neighborhood instead of pulling it toward an
                    // interior valley-floor optimum that could contradict the eventual fate.
                    cost = normalizedDisplacement * normalizedDisplacement;
                } else {
                    cost =
                            RIDGE_WEIGHT * ridge
                                    + TERRAIN_LEVEL_WEIGHT * elevation
                                    + DISPLACEMENT_WEIGHT
                                            * normalizedDisplacement
                                            * normalizedDisplacement
                                    + EXTERIOR_WEIGHT * exteriorPenalty;
                }
                Candidate candidate = new Candidate(
                        position, cost, elevation, valley, gx, gz);
                if (best == null || candidate.compareTo(best) < 0) {
                    best = candidate;
                }
            }
        }

        if (best == null) {
            throw new IllegalStateException("no globally aligned candidate exists in semantic node search region");
        }
        return new SkyIslandGeomorphicNetworkNode(
                cellIndex,
                kind,
                center,
                best.position(),
                radius,
                best.elevation(),
                best.valleyAdvantage());
    }

    private static void putCenter(
            Map<Integer, SkyIslandLocalPosition> centers,
            int cellIndex,
            SkyIslandLocalPosition position) {
        SkyIslandLocalPosition previous = centers.putIfAbsent(cellIndex, position);
        if (previous != null && !previous.equals(position)) {
            throw new IllegalStateException("semantic graph gives one node multiple coarse centers");
        }
    }

    private static SkyIslandGeomorphicNetworkNode requireNode(
            Map<Integer, SkyIslandGeomorphicNetworkNode> nodes,
            int cellIndex) {
        SkyIslandGeomorphicNetworkNode result = nodes.get(cellIndex);
        if (result == null) {
            throw new IllegalStateException("missing shared geomorphic network node " + cellIndex);
        }
        return result;
    }

    private static double surroundingMean(
            SkyIslandSemanticField terrain,
            SkyIslandLocalPosition center,
            double radius) {
        double sum = 0.0;
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4.0;
            sum += terrain.sample(new SkyIslandLocalPosition(
                    center.x() + Math.cos(angle) * radius,
                    center.z() + Math.sin(angle) * radius));
        }
        return sum / 8.0;
    }

    private static double distance(
            SkyIslandLocalPosition a,
            SkyIslandLocalPosition b) {
        return Math.hypot(a.x() - b.x(), a.z() - b.z());
    }

    private record Candidate(
            SkyIslandLocalPosition position,
            double cost,
            double elevation,
            double valleyAdvantage,
            int gridX,
            int gridZ) implements Comparable<Candidate> {

        @Override
        public int compareTo(Candidate other) {
            int byCost = Double.compare(cost, other.cost);
            if (byCost != 0) {
                return byCost;
            }
            int byX = Integer.compare(gridX, other.gridX);
            return byX != 0 ? byX : Integer.compare(gridZ, other.gridZ);
        }
    }
}
