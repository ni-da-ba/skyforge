package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Places one-dimensional computational sections around a combining junction.
 *
 * <p>The semantic junction anchor is not used as a hydraulic cross section. Candidate sections
 * must be at least the caller-specified mixing-clearance distance from the anchor, and their
 * section lines may not intersect. Among valid placements, the planner chooses the minimum total
 * distance to the junction, matching HEC-RAS guidance to keep sections close while avoiding the
 * turbulent mixing region and overlapping section data.
 *
 * <p>This planner selects geometry only. The caller supplies a defensible mixing-clearance distance
 * and must use the returned junction lengths with a validated energy-loss method. If the route
 * samples cannot provide a valid placement, the junction fails closed.
 */
public final class SkyIslandJunctionCrossSectionPlanner {
    private static final double GEOMETRY_TOLERANCE = 1.0e-10;
    private static final double SCORE_TOLERANCE = 1.0e-10;

    private SkyIslandJunctionCrossSectionPlanner() {}

    /**
     * Plans sections at one combining junction. Exactly one reach must leave the node downstream;
     * two or more reaches must enter it upstream.
     */
    public static JunctionSections plan(
            String junctionNodeId,
            List<ReachInput> reachInputs) {
        Objects.requireNonNull(junctionNodeId, "junctionNodeId");
        Objects.requireNonNull(reachInputs, "reachInputs");
        if (junctionNodeId.isBlank()) {
            throw new IllegalArgumentException("junction node identity must not be blank");
        }
        List<ReachInput> inputs = List.copyOf(reachInputs);
        if (inputs.size() < 3) {
            throw new IllegalArgumentException(
                    "combining junction requires one receiving reach and at least two tributaries");
        }

        Map<String, ReachInput> byId = new HashMap<>();
        List<ReachInput> tributaries = new ArrayList<>();
        ReachInput receiver = null;
        Double sharedHorizontalScale = null;
        SkyIslandLocalPosition sharedNodePosition = null;
        for (ReachInput input : inputs) {
            Objects.requireNonNull(input, "reach input");
            if (byId.put(input.id(), input) != null) {
                throw new IllegalArgumentException("duplicate junction reach identity: " + input.id());
            }
            if (sharedHorizontalScale == null) {
                sharedHorizontalScale = input.horizontalMetresPerLocalUnit();
            } else if (Math.abs(sharedHorizontalScale - input.horizontalMetresPerLocalUnit())
                    > GEOMETRY_TOLERANCE) {
                throw new IllegalArgumentException(
                        "junction reaches must share one horizontal coordinate scale");
            }

            SkyIslandLocalPosition endpoint;
            if (input.upstreamNodeId().equals(junctionNodeId)) {
                if (receiver != null) {
                    throw new IllegalArgumentException(
                            "a combining junction requires exactly one receiving reach");
                }
                receiver = input;
                endpoint = input.points().getFirst();
            } else if (input.downstreamNodeId().equals(junctionNodeId)) {
                tributaries.add(input);
                endpoint = input.points().getLast();
            } else {
                throw new IllegalArgumentException(
                        "reach is not incident to the requested junction: " + input.id());
            }
            if (sharedNodePosition == null) {
                sharedNodePosition = endpoint;
            } else if (!sharedNodePosition.equals(endpoint)) {
                throw new IllegalArgumentException(
                        "all reaches at a junction must share exactly one physical anchor");
            }
        }
        if (receiver == null || tributaries.size() < 2) {
            throw new IllegalArgumentException(
                    "combining junction requires one receiving reach and at least two tributaries");
        }
        tributaries.sort(Comparator.comparing(ReachInput::id));

        List<ReachInput> all = new ArrayList<>(tributaries.size() + 1);
        all.add(receiver);
        all.addAll(tributaries);
        all.sort(Comparator.comparing(ReachInput::id));
        Map<String, List<SectionCandidate>> candidatesByReach = new HashMap<>();
        for (ReachInput input : all) {
            boolean entersJunction = input.downstreamNodeId().equals(junctionNodeId);
            List<SectionCandidate> candidates = candidates(input, entersJunction);
            if (candidates.isEmpty()) {
                throw new IllegalArgumentException(
                        "no cross section lies outside the mixing clearance on reach " + input.id());
            }
            candidatesByReach.put(input.id(), candidates);
        }

        BestPlacement best = new BestPlacement();
        chooseSections(
                0,
                all,
                candidatesByReach,
                new ArrayList<>(),
                0.0,
                best);
        if (best.sections == null) {
            throw new IllegalArgumentException(
                    "junction has no non-overlapping cross-section placement outside the mixing region");
        }

        Map<String, SectionCandidate> selected = new HashMap<>();
        best.sections.forEach(value -> selected.put(value.reachId(), value));
        SectionCandidate receiverSection = selected.get(receiver.id());
        double receivingOffset = receiverSection.distanceFromJunctionMetres();
        List<TributarySection> selectedTributaries = tributaries.stream()
                .map(branch -> {
                    SectionCandidate branchSection = selected.get(branch.id());
                    return new TributarySection(
                            branch.id(),
                            branchSection.stationIndex(),
                            branchSection.distanceFromJunctionMetres(),
                            branchSection.distanceFromJunctionMetres() + receivingOffset,
                            branchSection.sectionLine());
                })
                .toList();
        return new JunctionSections(
                junctionNodeId,
                receiver.id(),
                receiverSection.stationIndex(),
                receivingOffset,
                receiverSection.sectionLine(),
                selectedTributaries,
                best.totalDistance);
    }

    private static List<SectionCandidate> candidates(ReachInput input, boolean entersJunction) {
        int count = input.points().size();
        List<SectionCandidate> result = new ArrayList<>(count - 2);
        int firstIndex = entersJunction ? count - 2 : 1;
        int lastIndex = entersJunction ? 1 : count - 2;
        int step = entersJunction ? -1 : 1;
        for (int index = firstIndex; entersJunction ? index >= lastIndex : index <= lastIndex; index += step) {
            double distance = entersJunction
                    ? input.stationsMetres()[count - 1] - input.stationsMetres()[index]
                    : input.stationsMetres()[index] - input.stationsMetres()[0];
            if (distance + GEOMETRY_TOLERANCE < input.minimumMixingClearanceMetres()) {
                continue;
            }
            result.add(new SectionCandidate(
                    input.id(),
                    index,
                    distance,
                    sectionLine(input, index)));
        }
        result.sort(Comparator.comparingDouble(SectionCandidate::distanceFromJunctionMetres)
                .thenComparingInt(SectionCandidate::stationIndex));
        return List.copyOf(result);
    }

    private static SkyIslandJunctionCrossSection sectionLine(ReachInput input, int index) {
        List<SkyIslandLocalPosition> points = input.points();
        SkyIslandLocalPosition before = points.get(index - 1);
        SkyIslandLocalPosition after = points.get(index + 1);
        double tangentX = after.x() - before.x();
        double tangentZ = after.z() - before.z();
        double tangentLength = Math.hypot(tangentX, tangentZ);
        if (!(tangentLength > 0.0) || !Double.isFinite(tangentLength)) {
            throw new IllegalArgumentException(
                    "junction reach tangent is degenerate at section " + input.id() + ":" + index);
        }
        double halfWidthLocalUnits = input.sectionHalfWidthMetres()
                / input.horizontalMetresPerLocalUnit();
        double normalX = -tangentZ / tangentLength;
        double normalZ = tangentX / tangentLength;
        SkyIslandLocalPosition center = points.get(index);
        return new SkyIslandJunctionCrossSection(
                new SkyIslandLocalPosition(
                        center.x() - normalX * halfWidthLocalUnits,
                        center.z() - normalZ * halfWidthLocalUnits),
                new SkyIslandLocalPosition(
                        center.x() + normalX * halfWidthLocalUnits,
                        center.z() + normalZ * halfWidthLocalUnits));
    }

    private static void chooseSections(
            int index,
            List<ReachInput> reaches,
            Map<String, List<SectionCandidate>> candidatesByReach,
            List<SectionCandidate> selected,
            double totalDistance,
            BestPlacement best) {
        if (index == reaches.size()) {
            if (totalDistance < best.totalDistance - SCORE_TOLERANCE
                    || Math.abs(totalDistance - best.totalDistance) <= SCORE_TOLERANCE
                            && lexicographicallyBefore(selected, best.sections)) {
                best.sections = List.copyOf(selected);
                best.totalDistance = totalDistance;
            }
            return;
        }

        ReachInput reach = reaches.get(index);
        for (SectionCandidate candidate : candidatesByReach.get(reach.id())) {
            double nextDistance = totalDistance + candidate.distanceFromJunctionMetres();
            if (nextDistance > best.totalDistance + SCORE_TOLERANCE) {
                continue;
            }
            boolean overlaps = selected.stream()
                    .anyMatch(existing -> sectionsIntersect(
                            existing.sectionLine(), candidate.sectionLine()));
            if (overlaps) {
                continue;
            }
            selected.add(candidate);
            chooseSections(
                    index + 1,
                    reaches,
                    candidatesByReach,
                    selected,
                    nextDistance,
                    best);
            selected.removeLast();
        }
    }

    private static boolean lexicographicallyBefore(
            List<SectionCandidate> candidate,
            List<SectionCandidate> current) {
        if (current == null) {
            return true;
        }
        for (int i = 0; i < candidate.size(); i++) {
            int compare = Integer.compare(
                    candidate.get(i).stationIndex(), current.get(i).stationIndex());
            if (compare != 0) {
                return compare < 0;
            }
        }
        return false;
    }

    private static boolean sectionsIntersect(
            SkyIslandJunctionCrossSection first,
            SkyIslandJunctionCrossSection second) {
        SkyIslandLocalPosition a = first.start();
        SkyIslandLocalPosition b = first.end();
        SkyIslandLocalPosition c = second.start();
        SkyIslandLocalPosition d = second.end();
        double abc = orientation(a, b, c);
        double abd = orientation(a, b, d);
        double cda = orientation(c, d, a);
        double cdb = orientation(c, d, b);
        double tolerance = GEOMETRY_TOLERANCE;
        if (((abc > tolerance && abd < -tolerance) || (abc < -tolerance && abd > tolerance))
                && ((cda > tolerance && cdb < -tolerance) || (cda < -tolerance && cdb > tolerance))) {
            return true;
        }
        return Math.abs(abc) <= tolerance && onSegment(a, b, c)
                || Math.abs(abd) <= tolerance && onSegment(a, b, d)
                || Math.abs(cda) <= tolerance && onSegment(c, d, a)
                || Math.abs(cdb) <= tolerance && onSegment(c, d, b);
    }

    private static double orientation(
            SkyIslandLocalPosition a,
            SkyIslandLocalPosition b,
            SkyIslandLocalPosition c) {
        return (b.x() - a.x()) * (c.z() - a.z())
                - (b.z() - a.z()) * (c.x() - a.x());
    }

    private static boolean onSegment(
            SkyIslandLocalPosition start,
            SkyIslandLocalPosition end,
            SkyIslandLocalPosition point) {
        return point.x() >= Math.min(start.x(), end.x()) - GEOMETRY_TOLERANCE
                && point.x() <= Math.max(start.x(), end.x()) + GEOMETRY_TOLERANCE
                && point.z() >= Math.min(start.z(), end.z()) - GEOMETRY_TOLERANCE
                && point.z() <= Math.max(start.z(), end.z()) + GEOMETRY_TOLERANCE;
    }

    private static final class BestPlacement {
        private List<SectionCandidate> sections;
        private double totalDistance = Double.POSITIVE_INFINITY;
    }

    private record SectionCandidate(
            String reachId,
            int stationIndex,
            double distanceFromJunctionMetres,
            SkyIslandJunctionCrossSection sectionLine) {}

    /** One directed route with stationwise channel sections and a caller-supported mixing clearance. */
    public record ReachInput(
            String id,
            String upstreamNodeId,
            String downstreamNodeId,
            List<SkyIslandLocalPosition> points,
            double[] stationsMetres,
            double horizontalMetresPerLocalUnit,
            double sectionHalfWidthMetres,
            double minimumMixingClearanceMetres) {
        public ReachInput {
            id = Objects.requireNonNull(id, "id");
            upstreamNodeId = Objects.requireNonNull(upstreamNodeId, "upstreamNodeId");
            downstreamNodeId = Objects.requireNonNull(downstreamNodeId, "downstreamNodeId");
            points = List.copyOf(Objects.requireNonNull(points, "points"));
            stationsMetres = Objects.requireNonNull(stationsMetres, "stationsMetres").clone();
            if (id.isBlank() || upstreamNodeId.isBlank() || downstreamNodeId.isBlank()
                    || upstreamNodeId.equals(downstreamNodeId)) {
                throw new IllegalArgumentException("reach and endpoint identities must be distinct and nonblank");
            }
            if (points.size() < 3 || points.size() != stationsMetres.length) {
                throw new IllegalArgumentException(
                        "junction reach requires matching route/station arrays with an interior section");
            }
            if (!(horizontalMetresPerLocalUnit > 0.0) || !Double.isFinite(horizontalMetresPerLocalUnit)
                    || !(sectionHalfWidthMetres > 0.0) || !Double.isFinite(sectionHalfWidthMetres)
                    || !(minimumMixingClearanceMetres >= 0.0)
                    || !Double.isFinite(minimumMixingClearanceMetres)) {
                throw new IllegalArgumentException("junction section scales and clearances are invalid");
            }
            for (int i = 0; i < stationsMetres.length; i++) {
                if (!Double.isFinite(stationsMetres[i])
                        || i == 0 && Math.abs(stationsMetres[i]) > GEOMETRY_TOLERANCE
                        || i > 0 && !(stationsMetres[i] > stationsMetres[i - 1])) {
                    throw new IllegalArgumentException(
                            "reach stations must start at zero and increase strictly downstream");
                }
                Objects.requireNonNull(points.get(i), "route point");
            }
        }

        @Override
        public double[] stationsMetres() {
            return stationsMetres.clone();
        }
    }

    /** Immutable locations and lengths of every computational cross section at the junction. */
    public record JunctionSections(
            String junctionNodeId,
            String receivingReachId,
            int receivingStationIndex,
            double receivingDistanceFromJunctionMetres,
            SkyIslandJunctionCrossSection receivingSection,
            List<TributarySection> tributaries,
            double totalSectionDistanceMetres) {
        public JunctionSections {
            junctionNodeId = Objects.requireNonNull(junctionNodeId, "junctionNodeId");
            receivingReachId = Objects.requireNonNull(receivingReachId, "receivingReachId");
            receivingSection = Objects.requireNonNull(receivingSection, "receivingSection");
            tributaries = List.copyOf(Objects.requireNonNull(tributaries, "tributaries"));
            if (junctionNodeId.isBlank() || receivingReachId.isBlank()
                    || receivingStationIndex < 1
                    || !(receivingDistanceFromJunctionMetres >= 0.0)
                    || !Double.isFinite(receivingDistanceFromJunctionMetres)
                    || !(totalSectionDistanceMetres >= receivingDistanceFromJunctionMetres)
                    || !Double.isFinite(totalSectionDistanceMetres)
                    || tributaries.size() < 2) {
                throw new IllegalArgumentException("invalid junction cross-section plan");
            }
        }
    }

    /** One tributary section and the HEC-style distance from it to the receiving section. */
    public record TributarySection(
            String reachId,
            int stationIndex,
            double distanceFromJunctionMetres,
            double junctionLengthMetres,
            SkyIslandJunctionCrossSection section) {
        public TributarySection {
            reachId = Objects.requireNonNull(reachId, "reachId");
            section = Objects.requireNonNull(section, "section");
            if (reachId.isBlank() || stationIndex < 1
                    || !(distanceFromJunctionMetres >= 0.0)
                    || !Double.isFinite(distanceFromJunctionMetres)
                    || !(junctionLengthMetres >= distanceFromJunctionMetres)
                    || !Double.isFinite(junctionLengthMetres)) {
                throw new IllegalArgumentException("invalid tributary cross-section placement");
            }
        }
    }

    /** A straight cross-section line perpendicular to its reach tangent. */
    public record SkyIslandJunctionCrossSection(
            SkyIslandLocalPosition start,
            SkyIslandLocalPosition end) {
        public SkyIslandJunctionCrossSection {
            start = Objects.requireNonNull(start, "start");
            end = Objects.requireNonNull(end, "end");
            if (Math.hypot(start.x() - end.x(), start.z() - end.z()) <= GEOMETRY_TOLERANCE) {
                throw new IllegalArgumentException("junction cross-section line must have positive width");
            }
        }
    }
}
