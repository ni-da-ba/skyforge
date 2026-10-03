package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Bounded experiment for the natural key-700 component: retain the authored graph and all
 * qualification limits while comparing a small deterministic set of smooth C2 centerline modes
 * against the exact assembled F3E outcome.
 */
class SkyIslandHydraulicComponentGeometryRefinementTest {
    private static final long SEED = 0x534B59464F524745L;
    private static final double EPSILON = 1.0e-9;
    private static final List<String> TARGET_REACHES =
            List.of("660->801", "801->1951", "1140->801");
    private static final Pattern CYCLE_GAP =
            Pattern.compile("negativeCycleGapWorld=([0-9.eE+-]+)");

    @Test
    void key700BoundedSmoothModesAreScoredByExactTerminalComponentAssembly() {
        SkyIslandDescriptor descriptor = descriptor();
        SkyIslandHydraulicGeometrySkeletonPlan base =
                SkyIslandHydraulicGeometrySkeletonPlanner.plan(descriptor);
        assertEquals(3L, base.reaches().stream()
                .filter(reach -> TARGET_REACHES.contains(reachId(reach)))
                .count());

        CandidateState baseline = evaluate(descriptor, base, base, "baseline");
        CandidateState best = baseline;
        assertEquals(
                SkyIslandHydraulicAssemblyStatus.PHYSICAL_REJECTION,
                terminalComponent(best.assembly()).status(),
                "the fixed mainline baseline should continue to expose the known key-700 rejection");

        double corridorHalfWidth =
                base.geomorphicNetwork().planningSpacing()
                        * SkyIslandGeomorphicChannelNetworkPlanner.ROUTE_CORRIDOR_SPACING_FRACTION;
        double[] amplitudeFractions = {0.125, 0.25};
        int[] supportScales = {2, 4, 8};
        int evaluated = 0;
        List<String> acceptedMoves = new ArrayList<>();
        Map<String, Integer> rejectedModes = new TreeMap<>();

        // Deterministic local cosine-windowed lateral moves target exact F3E-blocked span
        // boundaries/interiors. Endpoints remain fixed; existing C2 bounds stay hard.
        for (String routeId : TARGET_REACHES) {
            CandidateState bestForRoute = best;
            SkyIslandHydraulicReachSkeleton reach = findReach(best.skeleton(), routeId);
            double nominalSpacing =
                    reach.pathLength() / (reach.centerline().points().size() - 1.0);
            double amplitudeBudget = Math.min(
                    corridorHalfWidth,
                    Math.max(nominalSpacing, reach.maximumBankfullHalfWidth()));
            List<Double> centers = candidateStations(
                    terminalComponent(baseline.assembly()), routeId);
            for (double station : centers) {
                for (int supportScale : supportScales) {
                    double supportLength = nominalSpacing * supportScale;
                    for (double fraction : amplitudeFractions) {
                        double amplitude = amplitudeBudget * fraction;
                        for (int sign : new int[] {-1, 1}) {
                            ModeCandidate modeCandidate = lateralMode(
                                    descriptor, reach, station, supportLength,
                                    sign, amplitude, corridorHalfWidth);
                            SkyIslandContinuousChannelCenterline candidateCenterline =
                                    modeCandidate.centerline();
                            if (candidateCenterline == null) {
                                rejectedModes.merge(modeCandidate.rejection(), 1, Integer::sum);
                                continue;
                            }
                            SkyIslandHydraulicGeometrySkeletonPlan candidateSkeleton =
                                    replaceReach(
                                            descriptor, best.skeleton(), reach, candidateCenterline);
                            CandidateState candidate =
                                    evaluate(descriptor, candidateSkeleton, base, routeId);
                            evaluated++;
                            if (candidate.score().compareTo(bestForRoute.score()) < 0) {
                                bestForRoute = candidate;
                            }
                        }
                    }
                }
            }
            if (bestForRoute.score().compareTo(best.score()) < 0) {
                acceptedMoves.add(routeId);
                best = bestForRoute;
            }
        }

        assertTrue(best.score().compareTo(baseline.score()) <= 0);
        System.out.printf(
                Locale.ROOT,
                "F3O_KEY700 boundedModes=%d rejectedModes=%s acceptedMoves=%s baseline=%s best=%s shared=%s "
                        + "excluded=%d transitionBlockers=%d negativeCycleGapWorld=%.12f blockers=%s%n",
                evaluated,
                rejectedModes,
                acceptedMoves,
                scoreLabel(baseline),
                scoreLabel(best),
                terminalComponent(best.assembly()).sharedHeadSolve().status(),
                terminalComponent(best.assembly()).sharedHeadSolve().excludedSpans().size(),
                terminalComponent(best.assembly()).sharedHeadSolve().transitionBlockers().size(),
                best.score().negativeCycleGapWorld(),
                terminalComponent(best.assembly()).blockers());

        if (terminalComponent(best.assembly()).status()
                == SkyIslandHydraulicAssemblyStatus.QUALIFIED) {
            assertTrue(terminalComponent(best.assembly()).sharedHeadSolve().complete());
        }
    }

    private static CandidateState evaluate(
            SkyIslandDescriptor descriptor,
            SkyIslandHydraulicGeometrySkeletonPlan skeleton,
            SkyIslandHydraulicGeometrySkeletonPlan baseline,
            String changedRoute) {
        SkyIslandHydraulicNetworkAssemblyPlan assembly = assemble(descriptor, skeleton);
        SkyIslandHydraulicTerminalComponent component = terminalComponent(assembly);
        double displacement = totalDisplacement(baseline, skeleton);
        return new CandidateState(
                skeleton,
                assembly,
                score(component, displacement),
                changedRoute);
    }

    private static SkyIslandHydraulicNetworkAssemblyPlan assemble(
            SkyIslandDescriptor descriptor,
            SkyIslandHydraulicGeometrySkeletonPlan skeleton) {
        SkyIslandHydraulicTransitionTopologyPlan topology =
                SkyIslandHydraulicTransitionTopologyPlanner.plan(descriptor, skeleton);
        SkyIslandHydraulicTransitionGeometryEvidencePlan geometry =
                SkyIslandHydraulicTransitionGeometryEvidencePlanner.plan(descriptor, topology);
        SkyIslandSemanticField terrain =
                SkyIslandPreHydrologicTerrainField.create(descriptor);
        SkyIslandGeomorphicQualificationPolicy policy =
                SkyIslandGeomorphicQualificationPolicy.firstEvidenceBacked();
        SkyIslandWatershedPlan watershed = SkyIslandWatershedPlanner.plan(descriptor);
        SkyIslandConfluenceHeadCompatibilityPlan confluences =
                SkyIslandConfluenceHeadCompatibilityPlanner.plan(
                        descriptor, geometry, terrain, policy);
        SkyIslandCascadeHeadCompatibilityPlan cascades =
                SkyIslandCascadeHeadCompatibilityPlanner.plan(
                        descriptor, geometry, terrain, policy, watershed);
        SkyIslandConfluenceCascadeHeadCompatibilityPlan joint =
                SkyIslandConfluenceCascadeHeadCompatibilityPlanner.plan(
                        descriptor, geometry, terrain, policy, watershed);
        SkyIslandOrdinarySpanPlan ordinary =
                SkyIslandOrdinarySpanPlanner.plan(
                        descriptor, confluences, cascades, joint, terrain, policy);
        return SkyIslandHydraulicNetworkAssemblyPlanner.plan(descriptor, ordinary);
    }

    private static SkyIslandHydraulicTerminalComponent terminalComponent(
            SkyIslandHydraulicNetworkAssemblyPlan assembly) {
        return assembly.terminalComponents().stream()
                .filter(component -> component.reaches().stream()
                        .anyMatch(reach -> reachId(reach.semanticReach().startCellIndex(),
                                reach.semanticReach().endCellIndex()).equals("801->1951")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("key-700 terminal component 801->1951 missing"));
    }

    private static SkyIslandHydraulicReachSkeleton findReach(
            SkyIslandHydraulicGeometrySkeletonPlan skeleton, String routeId) {
        return skeleton.reaches().stream()
                .filter(reach -> routeId.equals(reachId(reach)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing target reach " + routeId));
    }

    private static List<Double> candidateStations(
            SkyIslandHydraulicTerminalComponent component, String routeId) {
        TreeSet<Double> stations = new TreeSet<>();
        stations.add(0.0);
        stations.add(1.0);
        component.reaches().stream()
                .filter(reach -> routeId.equals(reachId(
                        reach.semanticReach().startCellIndex(),
                        reach.semanticReach().endCellIndex())))
                .flatMap(reach -> reach.ordinarySpans().stream())
                .filter(outcome -> outcome.status()
                        != SkyIslandOrdinarySpanStatus.SOLVED_QUALIFIED)
                .map(SkyIslandOrdinarySpanOutcome::span)
                .forEach(span -> {
                    double startStation = span.parentStartStationFraction();
                    double endStation = span.parentEndStationFraction();
                    stations.add(startStation);
                    stations.add(0.5 * (startStation + endStation));
                    stations.add(endStation);
                });
        return List.copyOf(stations);
    }

    private static SkyIslandHydraulicGeometrySkeletonPlan replaceReach(
            SkyIslandDescriptor descriptor,
            SkyIslandHydraulicGeometrySkeletonPlan source,
            SkyIslandHydraulicReachSkeleton oldReach,
            SkyIslandContinuousChannelCenterline candidate) {
        SkyIslandSemanticField terrain = SkyIslandPreHydrologicTerrainField.create(descriptor);
        List<SkyIslandHydraulicReachSkeleton> reaches = new ArrayList<>(source.reaches().size());
        for (SkyIslandHydraulicReachSkeleton reach : source.reaches()) {
            if (reachId(reach).equals(reachId(oldReach))) {
                reaches.add(SkyIslandHydraulicGeometrySkeletonPlanner.sampleReach(
                        descriptor, terrain, reach.geomorphicRoute(), candidate));
            } else {
                reaches.add(reach);
            }
        }
        return new SkyIslandHydraulicGeometrySkeletonPlan(
                descriptor, source.geomorphicNetwork(), reaches);
    }

    private static ModeCandidate lateralMode(
            SkyIslandDescriptor descriptor,
            SkyIslandHydraulicReachSkeleton reach,
            double centerStation,
            double supportLength,
            int sign,
            double amplitude,
            double corridor) {
        List<SkyIslandLocalPosition> source = reach.centerline().points();
        double[] arc = cumulativeArc(source);
        double length = arc[arc.length - 1];
        if (!(length > 0.0)) {
            return new ModeCandidate(null, "zero-length-route");
        }
        List<SkyIslandLocalPosition> points = new ArrayList<>(source.size());
        points.add(source.getFirst());
        for (int i = 1; i < source.size() - 1; i++) {
            SkyIslandLocalPosition before = source.get(i - 1);
            SkyIslandLocalPosition after = source.get(i + 1);
            double tangentX = after.x() - before.x();
            double tangentZ = after.z() - before.z();
            double tangentLength = Math.hypot(tangentX, tangentZ);
            if (!(tangentLength > EPSILON)) {
                return new ModeCandidate(null, "degenerate-tangent");
            }
            double distanceFromCenter = Math.abs(arc[i] - centerStation * length);
            double weight = distanceFromCenter < supportLength
                    ? 0.5 * (1.0 + Math.cos(Math.PI * distanceFromCenter / supportLength))
                    : 0.0;
            double offset = sign * amplitude * weight;
            points.add(new SkyIslandLocalPosition(
                    source.get(i).x() - tangentZ / tangentLength * offset,
                    source.get(i).z() + tangentX / tangentLength * offset));
        }
        points.add(source.getLast());

        SkyIslandSemanticChannelReach semantic =
                reach.geomorphicRoute().semanticReach();
        SkyIslandPreHydrologicTerrainField terrain =
                SkyIslandPreHydrologicTerrainField.create(descriptor);
        SkyIslandSemanticField interiority =
                SkyIslandSemanticFieldSet.create(descriptor).interiority();
        for (int i = 0; i < points.size(); i++) {
            SkyIslandLocalPosition point = points.get(i);
            if (distanceToPolyline(point, semantic.guidancePoints()) > corridor + EPSILON) {
                return new ModeCandidate(null, "semantic-corridor");
            }
            if (interiority.sample(point)
                    < SkyIslandSemanticCorridorCenterlinePlanner.MINIMUM_INTERIORITY) {
                return new ModeCandidate(null, "interiority");
            }
            SkyIslandLocalPosition seed = projectToPolyline(
                    point, reach.geomorphicRoute().route().points());
            if (terrain.sample(point)
                    > terrain.sample(seed)
                            + SkyIslandSemanticCorridorCenterlinePlanner.MAXIMUM_TERRAIN_RISE_FROM_SEED
                            + EPSILON) {
                return new ModeCandidate(null, "terrain-rise");
            }
        }
        double minimumBendRadius = 2.0 * reach.maximumBankfullHalfWidth();
        if (maximumCurvature(points) * minimumBendRadius > 1.0 + EPSILON) {
            return new ModeCandidate(null, "width-scaled-curvature");
        }

        double pathLength = length(points);
        double maximumDeviation = 0.0;
        for (SkyIslandLocalPosition point : points) {
            maximumDeviation = Math.max(
                    maximumDeviation,
                    distanceToPolyline(point, reach.geomorphicRoute().route().points()));
        }
        double maximumTurn = maximumTurnAngle(points);
        return new ModeCandidate(
                new SkyIslandContinuousChannelCenterline(
                        reach.geomorphicRoute().route(),
                        points,
                        pathLength,
                        maximumDeviation,
                        maximumTurn),
                "accepted");
    }

    private static CandidateScore score(
            SkyIslandHydraulicTerminalComponent component, double displacement) {
        int statusRank = switch (component.status()) {
            case QUALIFIED -> 0;
            case TRANSITION_DEFERRED -> 1;
            case PHYSICAL_REJECTION -> 2;
            case TERMINAL_DEFERRED -> 3;
            case NUMERICAL_FAILURE -> 4;
        };
        SkyIslandHydraulicComponentHeadSolver.Outcome shared = component.sharedHeadSolve();
        int qpRank = shared.complete() ? 0 : switch (shared.status()) {
            case SOLVED -> 1;
            case INFEASIBLE -> 2;
            case NUMERICAL_FAILURE -> 3;
        };
        int rejectedReaches = (int) component.reaches().stream()
                .filter(reach -> reach.status() == SkyIslandHydraulicAssemblyStatus.PHYSICAL_REJECTION)
                .count();
        int blockerCount = component.blockers().size();
        double cycleGap = numberAfter(CYCLE_GAP, String.join(" ", component.blockers()), Double.POSITIVE_INFINITY);
        return new CandidateScore(
                statusRank,
                rejectedReaches,
                qpRank,
                cycleGap,
                shared.excludedSpans().size(),
                shared.transitionBlockers().size(),
                blockerCount,
                displacement);
    }

    private static double numberAfter(Pattern pattern, String value, double fallback) {
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? Double.parseDouble(matcher.group(1)) : fallback;
    }

    private static String scoreLabel(CandidateState state) {
        SkyIslandHydraulicTerminalComponent component = terminalComponent(state.assembly());
        return component.status() + "/shared=" + component.sharedHeadSolve().status()
                + "/excluded=" + component.sharedHeadSolve().excludedSpans().size()
                + "/blockers=" + component.blockers().size();
    }

    private static double totalDisplacement(
            SkyIslandHydraulicGeometrySkeletonPlan baseline,
            SkyIslandHydraulicGeometrySkeletonPlan candidate) {
        double result = 0.0;
        for (SkyIslandHydraulicReachSkeleton base : baseline.reaches()) {
            SkyIslandHydraulicReachSkeleton changed = findReach(candidate, reachId(base));
            if (!TARGET_REACHES.contains(reachId(base))) {
                continue;
            }
            List<SkyIslandLocalPosition> a = base.centerline().points();
            List<SkyIslandLocalPosition> b = changed.centerline().points();
            if (a.size() != b.size()) {
                return Double.POSITIVE_INFINITY;
            }
            for (int i = 0; i < a.size(); i++) {
                result += distance(a.get(i), b.get(i));
            }
        }
        return result;
    }

    private static SkyIslandLocalPosition projectToPolyline(
            SkyIslandLocalPosition point, List<SkyIslandLocalPosition> points) {
        SkyIslandLocalPosition projection = null;
        double minimumDistance = Double.POSITIVE_INFINITY;
        for (int i = 1; i < points.size(); i++) {
            SkyIslandLocalPosition a = points.get(i - 1);
            SkyIslandLocalPosition b = points.get(i);
            double dx = b.x() - a.x();
            double dz = b.z() - a.z();
            double lengthSquared = dx * dx + dz * dz;
            double t = lengthSquared == 0.0
                    ? 0.0
                    : Math.max(0.0, Math.min(1.0,
                            ((point.x() - a.x()) * dx + (point.z() - a.z()) * dz)
                                    / lengthSquared));
            SkyIslandLocalPosition candidate = new SkyIslandLocalPosition(
                    a.x() + t * dx, a.z() + t * dz);
            double candidateDistance = distance(point, candidate);
            if (candidateDistance < minimumDistance) {
                projection = candidate;
                minimumDistance = candidateDistance;
            }
        }
        if (projection == null) {
            throw new IllegalArgumentException("seed polyline requires at least two points");
        }
        return projection;
    }

    private static double distance(
            SkyIslandLocalPosition a, SkyIslandLocalPosition b) {
        return Math.hypot(a.x() - b.x(), a.z() - b.z());
    }

    private static double distanceToPolyline(
            SkyIslandLocalPosition point, List<SkyIslandLocalPosition> line) {
        double minimum = Double.POSITIVE_INFINITY;
        for (int i = 1; i < line.size(); i++) {
            SkyIslandLocalPosition a = line.get(i - 1);
            SkyIslandLocalPosition b = line.get(i);
            double dx = b.x() - a.x();
            double dz = b.z() - a.z();
            double lengthSquared = dx * dx + dz * dz;
            double t = lengthSquared == 0.0
                    ? 0.0
                    : Math.max(0.0, Math.min(1.0,
                            ((point.x() - a.x()) * dx + (point.z() - a.z()) * dz)
                                    / lengthSquared));
            double x = a.x() + t * dx;
            double z = a.z() + t * dz;
            minimum = Math.min(minimum, Math.hypot(point.x() - x, point.z() - z));
        }
        return minimum;
    }

    private static double maximumCurvature(List<SkyIslandLocalPosition> points) {
        double maximum = 0.0;
        for (int i = 1; i < points.size() - 1; i++) {
            SkyIslandLocalPosition a = points.get(i - 1);
            SkyIslandLocalPosition b = points.get(i);
            SkyIslandLocalPosition c = points.get(i + 1);
            double ax = b.x() - a.x();
            double az = b.z() - a.z();
            double bx = c.x() - b.x();
            double bz = c.z() - b.z();
            double ab = Math.hypot(ax, az);
            double bc = Math.hypot(bx, bz);
            if (ab <= EPSILON || bc <= EPSILON) {
                return Double.POSITIVE_INFINITY;
            }
            double cosine = Math.max(-1.0, Math.min(1.0, (ax * bx + az * bz) / (ab * bc)));
            maximum = Math.max(maximum, Math.acos(cosine) / (0.5 * (ab + bc)));
        }
        return maximum;
    }

    private static double maximumTurnAngle(List<SkyIslandLocalPosition> points) {
        double maximum = 0.0;
        for (int i = 1; i < points.size() - 1; i++) {
            SkyIslandLocalPosition a = points.get(i - 1);
            SkyIslandLocalPosition b = points.get(i);
            SkyIslandLocalPosition c = points.get(i + 1);
            double ux = b.x() - a.x();
            double uz = b.z() - a.z();
            double vx = c.x() - b.x();
            double vz = c.z() - b.z();
            double denominator = Math.hypot(ux, uz) * Math.hypot(vx, vz);
            if (denominator <= EPSILON) {
                return Math.PI;
            }
            double cosine = Math.max(-1.0, Math.min(1.0, (ux * vx + uz * vz) / denominator));
            maximum = Math.max(maximum, Math.acos(cosine));
        }
        return maximum;
    }

    private static double[] cumulativeArc(List<SkyIslandLocalPosition> points) {
        double[] arc = new double[points.size()];
        for (int i = 1; i < points.size(); i++) {
            arc[i] = arc[i - 1] + distance(points.get(i - 1), points.get(i));
        }
        return arc;
    }

    private static double length(List<SkyIslandLocalPosition> points) {
        return cumulativeArc(points)[points.size() - 1];
    }

    private static String reachId(SkyIslandHydraulicReachSkeleton reach) {
        return reachId(
                reach.geomorphicRoute().semanticReach().startCellIndex(),
                reach.geomorphicRoute().semanticReach().endCellIndex());
    }

    private static String reachId(int start, int end) {
        return start + "->" + end;
    }

    private static SkyIslandDescriptor descriptor() {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 8L, 81L, 700L));
    }

    private record ModeCandidate(
            SkyIslandContinuousChannelCenterline centerline, String rejection) {}

    private record CandidateState(
            SkyIslandHydraulicGeometrySkeletonPlan skeleton,
            SkyIslandHydraulicNetworkAssemblyPlan assembly,
            CandidateScore score,
            String changedRoute) {}

    private record CandidateScore(
            int statusRank,
            int rejectedReaches,
            int qpRank,
            double negativeCycleGapWorld,
            int excludedSpans,
            int transitionBlockers,
            int blockerCount,
            double displacement) implements Comparable<CandidateScore> {
        @Override
        public int compareTo(CandidateScore other) {
            int result = Integer.compare(statusRank, other.statusRank);
            if (result != 0) return result;
            result = Integer.compare(rejectedReaches, other.rejectedReaches);
            if (result != 0) return result;
            result = Integer.compare(qpRank, other.qpRank);
            if (result != 0) return result;
            result = Double.compare(negativeCycleGapWorld, other.negativeCycleGapWorld);
            if (result != 0) return result;
            result = Integer.compare(excludedSpans, other.excludedSpans);
            if (result != 0) return result;
            result = Integer.compare(transitionBlockers, other.transitionBlockers);
            if (result != 0) return result;
            result = Integer.compare(blockerCount, other.blockerCount);
            if (result != 0) return result;
            return Double.compare(displacement, other.displacement);
        }
    }
}
