package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Generates bounded 4/8/16 C3 route-functional evidence for selected semantic reaches. */
public final class SkyIslandRouteResolutionConvergencePlanner {
    private static final int COARSE_DIVISIONS = 4;
    private static final int MEDIUM_DIVISIONS = 8;
    private static final int FINE_DIVISIONS = 16;
    private static final double EPSILON = 1.0e-12;

    private SkyIslandRouteResolutionConvergencePlanner() {}

    public static SkyIslandRouteResolutionConvergenceDiagnostics measureReach(
            SkyIslandDescriptor descriptor,
            int startCellIndex,
            int endCellIndex) {
        Objects.requireNonNull(descriptor, "descriptor");
        SkyIslandGeomorphicChannelNetworkPlan network =
                SkyIslandGeomorphicChannelNetworkPlanner.plan(descriptor);
        SkyIslandGeomorphicReachRoute routed = network.routes().stream()
                .filter(route ->
                        route.semanticReach().startCellIndex() == startCellIndex
                                && route.semanticReach().endCellIndex() == endCellIndex)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "semantic reach not present in geomorphic network: "
                                + startCellIndex + "->" + endCellIndex));

        SkyIslandSemanticField terrain =
                SkyIslandPreHydrologicTerrainField.create(descriptor);
        SkyIslandSemanticField interiority =
                SkyIslandSemanticFieldSet.create(descriptor).interiority();
        double spacing = network.planningSpacing();
        double corridorHalfWidth =
                spacing * SkyIslandGeomorphicChannelNetworkPlanner.ROUTE_CORRIDOR_SPACING_FRACTION;
        SkyIslandGeomorphicRouteAnchor start =
                new SkyIslandGeomorphicRouteAnchor(
                        network.requireNode(startCellIndex).physicalPosition(), 0.0);
        SkyIslandGeomorphicRouteAnchor end =
                new SkyIslandGeomorphicRouteAnchor(
                        network.requireNode(endCellIndex).physicalPosition(), 0.0);
        List<SkyIslandLocalPosition> guidance =
                routed.semanticReach().guidancePoints();

        SkyIslandGeomorphicCandidateRoute coarse = solve(
                terrain,
                interiority,
                guidance,
                spacing,
                corridorHalfWidth,
                start,
                end,
                COARSE_DIVISIONS);
        SkyIslandGeomorphicCandidateRoute medium = solve(
                terrain,
                interiority,
                guidance,
                spacing,
                corridorHalfWidth,
                start,
                end,
                MEDIUM_DIVISIONS);
        SkyIslandGeomorphicCandidateRoute fine = solve(
                terrain,
                interiority,
                guidance,
                spacing,
                corridorHalfWidth,
                start,
                end,
                FINE_DIVISIONS);

        requireSameExactAnchors(coarse, medium, fine);

        double comparisonSpacing = spacing / 32.0;
        return new SkyIslandRouteResolutionConvergenceDiagnostics(
                startCellIndex,
                endCellIndex,
                SkyIslandRouteFunctionalDiagnosticsPlanner.measure(coarse, terrain, spacing),
                SkyIslandRouteFunctionalDiagnosticsPlanner.measure(medium, terrain, spacing),
                SkyIslandRouteFunctionalDiagnosticsPlanner.measure(fine, terrain, spacing),
                symmetricSampledPolylineDistance(
                        coarse.points(), medium.points(), comparisonSpacing),
                symmetricSampledPolylineDistance(
                        medium.points(), fine.points(), comparisonSpacing));
    }

    private static SkyIslandGeomorphicCandidateRoute solve(
            SkyIslandSemanticField terrain,
            SkyIslandSemanticField interiority,
            List<SkyIslandLocalPosition> guidance,
            double spacing,
            double corridorHalfWidth,
            SkyIslandGeomorphicRouteAnchor start,
            SkyIslandGeomorphicRouteAnchor end,
            int divisions) {
        return SkyIslandTerrainAwareRouteSolver.solveAtResolution(
                terrain,
                interiority,
                guidance,
                spacing,
                corridorHalfWidth,
                start,
                end,
                divisions);
    }

    private static void requireSameExactAnchors(
            SkyIslandGeomorphicCandidateRoute a,
            SkyIslandGeomorphicCandidateRoute b,
            SkyIslandGeomorphicCandidateRoute c) {
        SkyIslandLocalPosition start = a.points().getFirst();
        SkyIslandLocalPosition end = a.points().getLast();
        if (!start.equals(b.points().getFirst())
                || !start.equals(c.points().getFirst())
                || !end.equals(b.points().getLast())
                || !end.equals(c.points().getLast())) {
            throw new IllegalStateException(
                    "C3 resolution sweep changed exact semantic endpoint anchors");
        }
    }

    /**
     * Symmetric maximum distance using fixed physical arc-length sampling on both polylines.
     *
     * <p>The sampling interval is independent of either search lattice, so this diagnostic does not
     * privilege the coarser or finer route representation.
     */
    static double symmetricSampledPolylineDistance(
            List<SkyIslandLocalPosition> a,
            List<SkyIslandLocalPosition> b,
            double sampleSpacing) {
        if (!Double.isFinite(sampleSpacing) || sampleSpacing <= 0.0) {
            throw new IllegalArgumentException("sampleSpacing must be finite and positive");
        }
        return Math.max(
                directedSampledDistance(a, b, sampleSpacing),
                directedSampledDistance(b, a, sampleSpacing));
    }

    private static double directedSampledDistance(
            List<SkyIslandLocalPosition> source,
            List<SkyIslandLocalPosition> target,
            double sampleSpacing) {
        double maximum = 0.0;
        for (SkyIslandLocalPosition point : sampleByArcLength(source, sampleSpacing)) {
            maximum = Math.max(maximum, distanceToPolyline(point, target));
        }
        return maximum;
    }

    private static List<SkyIslandLocalPosition> sampleByArcLength(
            List<SkyIslandLocalPosition> polyline,
            double spacing) {
        if (polyline.size() < 2) {
            throw new IllegalArgumentException("polyline requires at least two points");
        }
        double[] cumulative = new double[polyline.size()];
        for (int i = 1; i < polyline.size(); i++) {
            cumulative[i] = cumulative[i - 1]
                    + Math.hypot(
                            polyline.get(i).x() - polyline.get(i - 1).x(),
                            polyline.get(i).z() - polyline.get(i - 1).z());
        }
        double total = cumulative[cumulative.length - 1];
        if (!(total > EPSILON)) {
            throw new IllegalArgumentException("polyline requires positive length");
        }

        int intervals = Math.max(1, (int) Math.ceil(total / spacing));
        List<SkyIslandLocalPosition> result = new ArrayList<>(intervals + 1);
        int segment = 1;
        for (int i = 0; i <= intervals; i++) {
            double targetArc = total * i / intervals;
            while (segment < cumulative.length - 1
                    && cumulative[segment] < targetArc - EPSILON) {
                segment++;
            }
            double aArc = cumulative[segment - 1];
            double bArc = cumulative[segment];
            double t = bArc - aArc <= EPSILON
                    ? 0.0
                    : (targetArc - aArc) / (bArc - aArc);
            SkyIslandLocalPosition a = polyline.get(segment - 1);
            SkyIslandLocalPosition b = polyline.get(segment);
            result.add(new SkyIslandLocalPosition(
                    a.x() + (b.x() - a.x()) * t,
                    a.z() + (b.z() - a.z()) * t));
        }
        return List.copyOf(result);
    }

    private static double distanceToPolyline(
            SkyIslandLocalPosition point,
            List<SkyIslandLocalPosition> polyline) {
        double best = Double.POSITIVE_INFINITY;
        for (int i = 1; i < polyline.size(); i++) {
            best = Math.min(
                    best,
                    distanceToSegment(point, polyline.get(i - 1), polyline.get(i)));
        }
        return best;
    }

    private static double distanceToSegment(
            SkyIslandLocalPosition point,
            SkyIslandLocalPosition a,
            SkyIslandLocalPosition b) {
        double dx = b.x() - a.x();
        double dz = b.z() - a.z();
        double lengthSquared = dx * dx + dz * dz;
        if (lengthSquared <= EPSILON) {
            return Math.hypot(point.x() - a.x(), point.z() - a.z());
        }
        double t = ((point.x() - a.x()) * dx + (point.z() - a.z()) * dz) / lengthSquared;
        t = Math.max(0.0, Math.min(1.0, t));
        double x = a.x() + t * dx;
        double z = a.z() + t * dz;
        return Math.hypot(point.x() - x, point.z() - z);
    }
}
