package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds finite geometry evidence for the explicit F3 transition topology.
 *
 * <p>Confluence legs use a deliberately minimal unit-hydraulic-scale hypothesis: each incident leg
 * retreats exactly one local node-boundary bankfull half-width along the accepted C2 centerline.
 * CASCADE geometry is the exact authored profile interval. Open-water basin geometry exposes only the
 * nearest continuous shoreline target and datum mismatch. None of these choices grants terrain or
 * solved-head authority.
 */
public final class SkyIslandHydraulicTransitionGeometryEvidencePlanner {
    private static final double EPSILON = 1.0e-10;

    private SkyIslandHydraulicTransitionGeometryEvidencePlanner() {}

    public static SkyIslandHydraulicTransitionGeometryEvidencePlan plan(
            SkyIslandDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        SkyIslandHydraulicTransitionTopologyPlan topology =
                SkyIslandHydraulicTransitionTopologyPlanner.plan(descriptor);
        return plan(descriptor, topology);
    }

    static SkyIslandHydraulicTransitionGeometryEvidencePlan plan(
            SkyIslandDescriptor descriptor,
            SkyIslandHydraulicTransitionTopologyPlan topology) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(topology, "topology");
        if (!descriptor.equals(topology.descriptor())) {
            throw new IllegalArgumentException("topology descriptor must match geometry descriptor");
        }

        SkyIslandSemanticField terrain =
                SkyIslandPreHydrologicTerrainField.create(descriptor);
        Map<Long, SkyIslandHydraulicReachSkeleton> reaches =
                indexReaches(topology.skeletonPlan().reaches());

        List<SkyIslandHydraulicConfluenceGeometryCandidate> confluences =
                new ArrayList<>(topology.confluences().size());
        for (SkyIslandHydraulicConfluenceTransitionSite site : topology.confluences()) {
            List<SkyIslandHydraulicTransitionLegGeometry> legs = new ArrayList<>();
            double maximumRetreat = 0.0;
            for (SkyIslandHydraulicTransitionBoundaryState boundary : site.boundaries()) {
                SkyIslandHydraulicReachSkeleton reach =
                        requireReach(reaches, boundary.reachStartCellIndex(), boundary.reachEndCellIndex());
                double retreat = boundary.bankfullHalfWidth();
                if (!(retreat < reach.pathLength() - EPSILON)) {
                    throw new IllegalStateException(
                            "confluence unit-width transition consumes entire incident reach "
                                    + boundary.reachStartCellIndex()
                                    + "->"
                                    + boundary.reachEndCellIndex());
                }
                double nodeArc = boundary.arcLength();
                double finiteArc =
                        boundary.role() == SkyIslandHydraulicTransitionBoundaryRole.INCOMING
                                ? nodeArc - retreat
                                : nodeArc + retreat;
                if (finiteArc < -EPSILON || finiteArc > reach.pathLength() + EPSILON) {
                    throw new IllegalStateException(
                            "confluence finite boundary escaped incident C2 reach");
                }
                double fraction =
                        Math.max(0.0, Math.min(1.0, finiteArc / reach.pathLength()));
                SkyIslandHydraulicTransitionBoundaryState finite =
                        SkyIslandHydraulicTransitionTopologyPlanner.sampleBoundaryState(
                                descriptor,
                                terrain,
                                reach,
                                fraction,
                                boundary.role());
                legs.add(new SkyIslandHydraulicTransitionLegGeometry(
                        boundary, finite, retreat));
                maximumRetreat = Math.max(maximumRetreat, retreat);
            }
            confluences.add(new SkyIslandHydraulicConfluenceGeometryCandidate(
                    site, legs, maximumRetreat));
        }

        List<SkyIslandHydraulicCascadeGeometryCandidate> cascades =
                new ArrayList<>(topology.cascades().size());
        for (SkyIslandHydraulicCascadeTransitionSite site : topology.cascades()) {
            SkyIslandHydraulicReachSkeleton reach =
                    requireReach(reaches, site.reachStartCellIndex(), site.reachEndCellIndex());
            cascades.add(new SkyIslandHydraulicCascadeGeometryCandidate(
                    site,
                    extractInterval(reach, site),
                    site.downstreamBoundary().arcLength()
                            - site.upstreamBoundary().arcLength()));
        }

        SkyIslandContinuousWaterbodyPlan waterbodies =
                SkyIslandContinuousWaterbodyPlanner.plan(descriptor);
        Map<Integer, SkyIslandContinuousWaterbodyBasin> basinBySink = new HashMap<>();
        for (SkyIslandContinuousWaterbodyBasin basin : waterbodies.basins()) {
            basinBySink.put(basin.sourceCandidate().sinkCellIndex(), basin);
        }

        List<SkyIslandHydraulicBasinInterfaceGeometry> openWater = new ArrayList<>();
        List<SkyIslandHydraulicBasinTransitionSite> wetlands = new ArrayList<>();
        for (SkyIslandHydraulicBasinTransitionSite site : topology.basins()) {
            if (site.terminalFate().kind()
                    == SkyIslandChannelTerminalFateKind.RETAINED_WETLAND) {
                wetlands.add(site);
                continue;
            }

            SkyIslandContinuousWaterbodyBasin basin =
                    basinBySink.get(site.terminalFate().watershedTerminalCellIndex());
            if (basin == null) {
                throw new IllegalStateException(
                        "retained-open-water fate has no continuous basin at watershed terminal "
                                + site.terminalFate().watershedTerminalCellIndex());
            }
            SkyIslandLocalPosition shoreline =
                    nearestShoreline(site.riverBoundary().position(), basin.shorelineCrossings());
            double gap = distance(site.riverBoundary().position(), shoreline);
            double preferredRiverHead = preferredWaterSurfacePotential(site.riverBoundary());
            double mismatch =
                    Math.abs(preferredRiverHead - basin.waterSurfacePotential())
                            * descriptor.reliefBudget();
            openWater.add(new SkyIslandHydraulicBasinInterfaceGeometry(
                    site, basin, shoreline, gap, mismatch));
        }

        confluences.sort(Comparator.comparingInt(
                candidate -> candidate.transitionSite().nodeCellIndex()));
        cascades.sort(Comparator
                .comparingInt((SkyIslandHydraulicCascadeGeometryCandidate candidate) ->
                        candidate.transitionSite().reachStartCellIndex())
                .thenComparingInt(candidate ->
                        candidate.transitionSite().reachEndCellIndex())
                .thenComparingInt(candidate ->
                        candidate.transitionSite().firstProfileIndex()));
        openWater.sort(Comparator.comparingInt(
                candidate ->
                        candidate.transitionSite().terminalFate().channelTerminalCellIndex()));
        wetlands.sort(Comparator.comparingInt(
                site -> site.terminalFate().channelTerminalCellIndex()));

        return new SkyIslandHydraulicTransitionGeometryEvidencePlan(
                descriptor, topology, confluences, cascades, openWater, wetlands);
    }

    private static Map<Long, SkyIslandHydraulicReachSkeleton> indexReaches(
            List<SkyIslandHydraulicReachSkeleton> reaches) {
        Map<Long, SkyIslandHydraulicReachSkeleton> result = new HashMap<>();
        for (SkyIslandHydraulicReachSkeleton reach : reaches) {
            SkyIslandSemanticChannelReach semantic =
                    reach.geomorphicRoute().semanticReach();
            long key = identity(semantic.startCellIndex(), semantic.endCellIndex());
            if (result.put(key, reach) != null) {
                throw new IllegalStateException("duplicate F2B reach identity");
            }
        }
        return result;
    }

    private static SkyIslandHydraulicReachSkeleton requireReach(
            Map<Long, SkyIslandHydraulicReachSkeleton> reaches,
            int start,
            int end) {
        SkyIslandHydraulicReachSkeleton reach = reaches.get(identity(start, end));
        if (reach == null) {
            throw new IllegalStateException("missing F2B reach " + start + "->" + end);
        }
        return reach;
    }

    private static long identity(int start, int end) {
        return ((long) start << 32) ^ Integer.toUnsignedLong(end);
    }

    private static List<SkyIslandLocalPosition> extractInterval(
            SkyIslandHydraulicReachSkeleton reach,
            SkyIslandHydraulicCascadeTransitionSite site) {
        double startArc = site.upstreamBoundary().arcLength();
        double endArc = site.downstreamBoundary().arcLength();
        List<SkyIslandLocalPosition> result = new ArrayList<>();
        result.add(site.upstreamBoundary().position());
        for (SkyIslandHydraulicGeometrySkeletonSample sample : reach.samples()) {
            if (sample.arcLength() > startArc + EPSILON
                    && sample.arcLength() < endArc - EPSILON) {
                result.add(sample.position());
            }
        }
        if (distance(result.getLast(), site.downstreamBoundary().position()) > EPSILON) {
            result.add(site.downstreamBoundary().position());
        }
        if (result.size() < 2) {
            throw new IllegalStateException("cascade transition interval collapsed");
        }
        double measured = polylineLength(result);
        double expected = endArc - startArc;
        if (Math.abs(measured - expected) > 1.0e-8) {
            throw new IllegalStateException(
                    "cascade transition interval does not preserve C2 arc length");
        }
        return List.copyOf(result);
    }

    private static SkyIslandLocalPosition nearestShoreline(
            SkyIslandLocalPosition origin,
            List<SkyIslandLocalPosition> shoreline) {
        if (shoreline.isEmpty()) {
            throw new IllegalStateException(
                    "retained-open-water transition has no continuous shoreline");
        }
        return shoreline.stream()
                .min(Comparator
                        .comparingDouble((SkyIslandLocalPosition point) ->
                                distance(origin, point))
                        .thenComparingDouble(SkyIslandLocalPosition::x)
                        .thenComparingDouble(SkyIslandLocalPosition::z))
                .orElseThrow();
    }

    private static double preferredWaterSurfacePotential(
            SkyIslandHydraulicTransitionBoundaryState boundary) {
        return Math.max(
                boundary.waterDepthPotential(),
                boundary.terrainElevation()
                        - SkyIslandHydraulicGeometryCalibration.freeboardFromDepthPotential(
                                boundary.waterDepthPotential()));
    }

    private static double polylineLength(List<SkyIslandLocalPosition> points) {
        double total = 0.0;
        for (int i = 0; i + 1 < points.size(); i++) {
            total += distance(points.get(i), points.get(i + 1));
        }
        return total;
    }

    private static double distance(
            SkyIslandLocalPosition a,
            SkyIslandLocalPosition b) {
        return Math.hypot(a.x() - b.x(), a.z() - b.z());
    }
}
