package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Builds C2 centerlines and discharge-scaled width/depth geometry without solving water-surface head.
 *
 * <p>This is the shared pre-profile substrate for both the historical clipped hydraulic planner and
 * the F2 bounded longitudinal solver. It deliberately carries no node datum, water surface, bed, or
 * terrain-lowering authority.
 */
public final class SkyIslandHydraulicGeometrySkeletonPlanner {
    private static final double EPSILON = 1.0e-12;
    private static final double TRANSITION_BOUNDARY_EPSILON = 1.0e-10;

    private SkyIslandHydraulicGeometrySkeletonPlanner() {}

    public static SkyIslandHydraulicGeometrySkeletonPlan plan(SkyIslandDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        SkyIslandGeomorphicChannelNetworkPlan network =
                SkyIslandGeomorphicChannelNetworkPlanner.plan(descriptor);
        SkyIslandPreHydrologicTerrainField terrain =
                SkyIslandPreHydrologicTerrainField.create(descriptor);
        SkyIslandSemanticField interiority =
                SkyIslandSemanticFieldSet.create(descriptor).interiority();
        return plan(descriptor, network, terrain, interiority);
    }

    static SkyIslandHydraulicGeometrySkeletonPlan plan(
            SkyIslandDescriptor descriptor,
            SkyIslandGeomorphicChannelNetworkPlan network,
            SkyIslandSemanticField terrain,
            SkyIslandSemanticField interiority) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(network, "network");
        Objects.requireNonNull(terrain, "terrain");
        Objects.requireNonNull(interiority, "interiority");
        if (!descriptor.equals(network.descriptor())) {
            throw new IllegalArgumentException("geomorphic network descriptor must match skeleton descriptor");
        }

        List<SkyIslandHydraulicReachSkeleton> reaches = new ArrayList<>(network.routes().size());

        for (SkyIslandGeomorphicReachRoute route : network.routes()) {
            CenterlineRefinement refinement =
                    refineCenterline(descriptor, network, route, terrain, interiority);
            reaches.add(sampleReach(descriptor, terrain, route, refinement.centerline()));
        }

        reaches.sort(Comparator
                .comparingInt((SkyIslandHydraulicReachSkeleton reach) ->
                        reach.geomorphicRoute().semanticReach().startCellIndex())
                .thenComparingInt(reach ->
                        reach.geomorphicRoute().semanticReach().endCellIndex()));

        return new SkyIslandHydraulicGeometrySkeletonPlan(descriptor, network, reaches);
    }

    static CenterlineRefinement refineCenterline(
            SkyIslandDescriptor descriptor,
            SkyIslandGeomorphicChannelNetworkPlan network,
            SkyIslandGeomorphicReachRoute route,
            SkyIslandSemanticField terrain,
            SkyIslandSemanticField interiority) {
        SkyIslandSemanticChannelReach semantic = route.semanticReach();
        SemanticDischargeProfile dischargeProfile = semanticDischargeProfile(semantic);
        double maximumBankfullWidth =
                2.0 * SkyIslandHydraulicGeometryCalibration.bankfullHalfWidth(
                        descriptor.nominalRadius(), dischargeProfile.maximumDischarge());
        SkyIslandGeomorphicQualificationPolicy policy =
                SkyIslandGeomorphicQualificationPolicy.firstEvidenceBacked();
        Optional<SkyIslandChannelProfileKind> ordinaryProfileKind =
                singleOrdinaryProfileKind(semantic);
        boolean startsAtSource = network.nodes().stream()
                .anyMatch(node -> node.cellIndex() == semantic.startCellIndex()
                        && node.kind() == SkyIslandGeomorphicNetworkNodeKind.SOURCE);
        var outcome = SkyIslandSemanticCorridorCenterlinePlanner.refineWithDiagnostics(
                route.route(),
                semantic.guidancePoints(),
                terrain,
                interiority,
                network.planningSpacing(),
                network.planningSpacing()
                        * SkyIslandGeomorphicChannelNetworkPlanner.ROUTE_CORRIDOR_SPACING_FRACTION,
                maximumBankfullWidth,
                station -> SkyIslandHydraulicGeometryCalibration.bankfullHalfWidth(
                        descriptor.nominalRadius(), dischargeProfile.atStation(station)),
                (position, station, tangentX, tangentZ, halfWidth) -> {
                    SkyIslandChannelProfileKind kind =
                            SkyIslandHydraulicHeadEnvelopePlanner.profileKind(
                                    semantic.profiles(), station);
                    if (kind == SkyIslandChannelProfileKind.CASCADE
                            || ordinaryProfileKind.isEmpty()) {
                        return 0.0;
                    }
                    SkyIslandChannelProfileKind ordinaryKind =
                            ordinaryProfileKind.orElseThrow();
                    SkyIslandGeomorphicQualificationClass qualificationClass =
                            qualificationClass(ordinaryKind);
                    SkyIslandHydraulicHeadEnvelope envelope =
                            SkyIslandHydraulicHeadEnvelopePlanner.evaluateForKind(
                                    descriptor,
                                    ordinaryKind,
                                    position,
                                    halfWidth,
                                    SkyIslandHydraulicGeometryCalibration.waterDepthPotential(
                                            dischargeProfile.atStation(station)),
                                    clamp01(terrain.sample(position)),
                                    -tangentZ,
                                    tangentX,
                                    terrain,
                                    policy.limits(qualificationClass));
                    return Math.max(
                            0.0, envelope.lowerHead() - envelope.upperHead());
                },
                points -> longitudinalHeadFeasibilityGap(
                        descriptor, network, startsAtSource, semantic, points,
                        dischargeProfile, terrain, policy));
        return new CenterlineRefinement(outcome.centerline(), outcome.diagnostics());
    }

    private static SkyIslandCenterlineLongitudinalHeadFeasibility.Score longitudinalHeadFeasibilityGap(
            SkyIslandDescriptor descriptor,
            SkyIslandGeomorphicChannelNetworkPlan network,
            boolean startsAtSource,
            SkyIslandSemanticChannelReach semantic,
            List<SkyIslandLocalPosition> points,
            SemanticDischargeProfile dischargeProfile,
            SkyIslandSemanticField terrain,
            SkyIslandGeomorphicQualificationPolicy policy) {
        double[] cumulative = cumulativeDistance(points);
        double pathLength = cumulative[cumulative.length - 1];
        double reachableLower = Double.NaN;
        double reachableUpper = Double.NaN;
        SkyIslandChannelProfileKind previousKind = null;
        SkyIslandChannelProfileKind[] kinds =
                new SkyIslandChannelProfileKind[points.size()];
        double[] squaredConflicts = new double[points.size()];
        double maximumLocalEnvelopeConflict = 0.0;
        double maximumSourceEndpointEnvelopeConflict = 0.0;
        double maximumGradePropagationConflict = 0.0;
        for (int i = 0; i < points.size(); i++) {
            SkyIslandLocalPosition position = points.get(i);
            double station = cumulative[i] / pathLength;
            SkyIslandChannelProfileKind kind =
                    SkyIslandHydraulicHeadEnvelopePlanner.profileKind(
                            semantic.profiles(), station);
            kinds[i] = kind;
            if (kind == SkyIslandChannelProfileKind.CASCADE) {
                reachableLower = Double.NaN;
                reachableUpper = Double.NaN;
                previousKind = null;
                continue;
            }
            if (previousKind != null && previousKind != kind) {
                reachableLower = Double.NaN;
                reachableUpper = Double.NaN;
            }
            previousKind = kind;
            SkyIslandGeomorphicQualificationClass qualificationClass =
                    qualificationClass(kind);
            SkyIslandGeomorphicProfileLimits limits =
                    policy.limits(qualificationClass);
            SkyIslandLocalPosition before = points.get(Math.max(0, i - 1));
            SkyIslandLocalPosition after = points.get(Math.min(points.size() - 1, i + 1));
            double tangentX = after.x() - before.x();
            double tangentZ = after.z() - before.z();
            double tangentLength = Math.hypot(tangentX, tangentZ);
            if (!(tangentLength > 0.0)) {
                throw new IllegalStateException(
                        "longitudinal feasibility requires non-zero centerline tangents");
            }
            double discharge = dischargeProfile.atStation(station);
            double halfWidth = SkyIslandHydraulicGeometryCalibration.bankfullHalfWidth(
                    descriptor.nominalRadius(), discharge);
            double depth = SkyIslandHydraulicGeometryCalibration.waterDepthPotential(discharge);
            SkyIslandHydraulicHeadEnvelope envelope =
                    SkyIslandHydraulicHeadEnvelopePlanner.evaluateForKind(
                            descriptor,
                            kind,
                            position,
                            halfWidth,
                            depth,
                            clamp01(terrain.sample(position)),
                            -tangentZ / tangentLength,
                            tangentX / tangentLength,
                            terrain,
                            limits);
            double localLower = envelope.lowerHead();
            double localUpper = envelope.upperHead();
            double localConflict = Math.max(0.0, localLower - localUpper);
            if (i == 0 && startsAtSource) {
                maximumSourceEndpointEnvelopeConflict = localConflict;
            }
            if (localConflict > 0.0) {
                maximumLocalEnvelopeConflict =
                        Math.max(maximumLocalEnvelopeConflict, localConflict);
                squaredConflicts[i] += localConflict * localConflict;
                double midpoint = 0.5 * (localLower + localUpper);
                localLower = midpoint;
                localUpper = midpoint;
            }
            if (Double.isNaN(reachableLower)) {
                reachableLower = localLower;
                reachableUpper = localUpper;
                continue;
            }
            double ds = cumulative[i] - cumulative[i - 1];
            double maximumDrop = limits.maximumLongitudinalGrade() * ds;
            double nextLower = Math.max(localLower, reachableLower - maximumDrop);
            double nextUpper = Math.min(localUpper, reachableUpper);
            double conflict = Math.max(0.0, nextLower - nextUpper);
            if (conflict > 0.0) {
                maximumGradePropagationConflict =
                        Math.max(maximumGradePropagationConflict, conflict);
                squaredConflicts[i] += conflict * conflict;
                // Continue scoring downstream from the nearest admissible local interval. This
                // keeps the objective informative without pretending the violated path is feasible.
                double midpoint = 0.5 * (nextLower + nextUpper);
                nextLower = midpoint;
                nextUpper = midpoint;
            }
            reachableLower = nextLower;
            reachableUpper = nextUpper;
        }
        double integratedSquaredConflict = 0.0;
        double ordinaryLength = 0.0;
        for (int i = 0; i + 1 < points.size(); i++) {
            if (kinds[i] == SkyIslandChannelProfileKind.CASCADE
                    || kinds[i + 1] == SkyIslandChannelProfileKind.CASCADE
                    || kinds[i] != kinds[i + 1]) {
                continue;
            }
            double ds = cumulative[i + 1] - cumulative[i];
            integratedSquaredConflict +=
                    0.5 * (squaredConflicts[i] + squaredConflicts[i + 1]) * ds;
            ordinaryLength += ds;
        }
        ConfluenceCascadeGradeConflictDetails confluenceCascadeGradeConflict =
                maximumConfluenceCascadeGradeConflict(
                        descriptor, network, semantic, points, cumulative,
                        dischargeProfile, terrain, policy);
        return new SkyIslandCenterlineLongitudinalHeadFeasibility.Score(
                maximumLocalEnvelopeConflict,
                maximumSourceEndpointEnvelopeConflict,
                maximumGradePropagationConflict,
                confluenceCascadeGradeConflict.maximumConflict(),
                ordinaryLength > 0.0
                        ? integratedSquaredConflict / ordinaryLength
                        : 0.0);
    }

    private static ConfluenceCascadeGradeConflictDetails maximumConfluenceCascadeGradeConflict(
            SkyIslandDescriptor descriptor,
            SkyIslandGeomorphicChannelNetworkPlan network,
            SkyIslandSemanticChannelReach semantic,
            List<SkyIslandLocalPosition> points,
            double[] cumulative,
            SemanticDischargeProfile dischargeProfile,
            SkyIslandSemanticField terrain,
            SkyIslandGeomorphicQualificationPolicy policy) {
        boolean startsAtConfluence = network.nodes().stream()
                .anyMatch(node -> node.cellIndex() == semantic.startCellIndex()
                        && node.kind() == SkyIslandGeomorphicNetworkNodeKind.CONFLUENCE);
        if (!startsAtConfluence) {
            return new ConfluenceCascadeGradeConflictDetails(
                    0.0, Double.NaN, Double.NaN, 0, "not-confluence", -1, Double.NaN);
        }
        List<SkyIslandChannelProfile> profiles = semantic.profiles();
        double pathLength = cumulative[cumulative.length - 1];
        double[] sampledDischarge = new double[points.size()];
        for (int index = 0; index < points.size(); index++) {
            sampledDischarge[index] = dischargeProfile.atStation(cumulative[index] / pathLength);
        }
        double startArc = SkyIslandHydraulicGeometryCalibration.bankfullHalfWidth(
                descriptor.nominalRadius(), sampledDischarge[0]);
        double startStation = startArc / pathLength;

        // F3D owns a confluence-adjacent CASCADE as its complete authored profile run. The
        // ordinary reach begins at the first ordinary profile boundary after that run, not at an
        // interior CASCADE profile boundary merely because the physical confluence retreat lies
        // inside the run.
        int startProfile = Math.min(
                profiles.size() - 1,
                (int) Math.floor(Math.min(0.999999999, startStation) * profiles.size()));
        if (profiles.get(startProfile).kind() == SkyIslandChannelProfileKind.CASCADE) {
            while (startProfile < profiles.size()
                    && profiles.get(startProfile).kind() == SkyIslandChannelProfileKind.CASCADE) {
                startProfile++;
            }
            if (startProfile >= profiles.size()) {
                return new ConfluenceCascadeGradeConflictDetails(
                        0.0, startStation, Double.NaN, 0, "no-ordinary-profile-after-confluence-cascade",
                        -1, Double.NaN);
            }
            startStation = (double) startProfile / profiles.size();
        }

        // Score through the next downstream CASCADE boundary, matching F3D's ordinary-span slice.
        int cascadeStart = -1;
        for (int index = startProfile + 1; index < profiles.size(); index++) {
            double profileStart = (double) index / profiles.size();
            if (profiles.get(index).kind() == SkyIslandChannelProfileKind.CASCADE
                    && profileStart > startStation + TRANSITION_BOUNDARY_EPSILON) {
                cascadeStart = index;
                break;
            }
        }
        if (cascadeStart < 0) {
            return new ConfluenceCascadeGradeConflictDetails(
                    0.0, startStation, Double.NaN, 0, "no-downstream-cascade", -1, Double.NaN);
        }
        double endStation = (double) cascadeStart / profiles.size();
        if (!(endStation > startStation)) {
            return new ConfluenceCascadeGradeConflictDetails(
                    0.0, startStation, endStation, 0, "empty-ordinary-window", -1, Double.NaN);
        }

        List<SkyIslandLocalPosition> spanPoints = new ArrayList<>();
        List<Double> spanStations = new ArrayList<>();
        List<Double> spanDischarges = new ArrayList<>();
        appendStationSample(
                points, cumulative, pathLength, startStation, sampledDischarge,
                spanPoints, spanStations, spanDischarges);
        for (int index = 1; index + 1 < points.size(); index++) {
            double station = cumulative[index] / pathLength;
            if (station > startStation + EPSILON && station < endStation - EPSILON) {
                spanPoints.add(points.get(index));
                spanStations.add(station);
                spanDischarges.add(sampledDischarge[index]);
            }
        }
        appendStationSample(
                points, cumulative, pathLength, endStation, sampledDischarge,
                spanPoints, spanStations, spanDischarges);

        List<SkyIslandChannelProfileKind> overlappingKinds = new ArrayList<>();
        for (int index = 0; index < profiles.size(); index++) {
            double profileStart = (double) index / profiles.size();
            double profileEnd = (double) (index + 1) / profiles.size();
            if (Math.min(endStation, profileEnd) - Math.max(startStation, profileStart)
                            <= TRANSITION_BOUNDARY_EPSILON) {
                continue;
            }
            SkyIslandChannelProfileKind kind = profiles.get(index).kind();
            if (kind == SkyIslandChannelProfileKind.CASCADE) {
                return new ConfluenceCascadeGradeConflictDetails(
                        0.0,
                        startStation,
                        endStation,
                        0,
                        "cascade-overlap-profile-" + index + "-[" + profileStart + "," + profileEnd + "]",
                        -1,
                        Double.NaN);
            }
            overlappingKinds.add(kind);
        }
        SkyIslandGeomorphicQualificationClass spanClass =
                SkyIslandGeomorphicQualificationClass.classifyKinds(overlappingKinds);
        SkyIslandGeomorphicProfileLimits spanLimits = policy.limits(spanClass);

        double[] spanCumulative = cumulativeDistance(spanPoints);
        double reachableLower = Double.NaN;
        double reachableUpper = Double.NaN;
        double maximumConflict = 0.0;
        int maximumConflictIndex = -1;
        double maximumConflictStation = Double.NaN;
        for (int index = 0; index < spanPoints.size(); index++) {
            SkyIslandLocalPosition position = spanPoints.get(index);
            double station = spanStations.get(index);
            SkyIslandLocalPosition before = spanPoints.get(Math.max(0, index - 1));
            SkyIslandLocalPosition after =
                    spanPoints.get(Math.min(spanPoints.size() - 1, index + 1));
            double tangentX = after.x() - before.x();
            double tangentZ = after.z() - before.z();
            double tangentLength = Math.hypot(tangentX, tangentZ);
            if (!(tangentLength > 0.0)) {
                throw new IllegalStateException(
                        "confluence-to-CASCADE grade scoring requires non-zero tangents");
            }
            SkyIslandChannelProfileKind kind =
                    index + 1 == spanPoints.size()
                            ? profiles.get(cascadeStart - 1).kind()
                            : SkyIslandHydraulicHeadEnvelopePlanner.profileKind(profiles, station);
            double discharge = spanDischarges.get(index);
            SkyIslandHydraulicHeadEnvelope envelope =
                    SkyIslandHydraulicHeadEnvelopePlanner.evaluateForKind(
                            descriptor,
                            kind,
                            position,
                            SkyIslandHydraulicGeometryCalibration.bankfullHalfWidth(
                                    descriptor.nominalRadius(), discharge),
                            SkyIslandHydraulicGeometryCalibration.waterDepthPotential(discharge),
                            clamp01(terrain.sample(position)),
                            -tangentZ / tangentLength,
                            tangentX / tangentLength,
                            terrain,
                            spanLimits);
            double localLower = envelope.lowerHead();
            double localUpper = envelope.upperHead();
            if (localLower > localUpper) {
                double midpoint = 0.5 * (localLower + localUpper);
                localLower = midpoint;
                localUpper = midpoint;
            }
            if (Double.isNaN(reachableLower)) {
                reachableLower = localLower;
                reachableUpper = localUpper;
                continue;
            }
            double ds = spanCumulative[index] - spanCumulative[index - 1];
            double maxDrop = spanLimits.maximumLongitudinalGrade() * ds;
            double nextLower = Math.max(localLower, reachableLower - maxDrop);
            double nextUpper = Math.min(localUpper, reachableUpper);
            double conflict = Math.max(0.0, nextLower - nextUpper);
            if (conflict > maximumConflict) {
                maximumConflict = conflict;
                maximumConflictIndex = index;
                maximumConflictStation = station;
            }
            if (conflict > 0.0) {
                double midpoint = 0.5 * (nextLower + nextUpper);
                nextLower = midpoint;
                nextUpper = midpoint;
            }
            reachableLower = nextLower;
            reachableUpper = nextUpper;
        }
        return new ConfluenceCascadeGradeConflictDetails(
                maximumConflict, startStation, endStation, spanPoints.size(),
                spanClass.name(), maximumConflictIndex, maximumConflictStation);
    }

    static ConfluenceCascadeGradeConflictDetails confluenceCascadeGradeConflictDetails(
            SkyIslandDescriptor descriptor,
            SkyIslandGeomorphicChannelNetworkPlan network,
            SkyIslandSemanticChannelReach semantic,
            List<SkyIslandLocalPosition> points,
            SkyIslandSemanticField terrain) {
        return maximumConfluenceCascadeGradeConflict(
                descriptor,
                network,
                semantic,
                points,
                cumulativeDistance(points),
                semanticDischargeProfile(semantic),
                terrain,
                SkyIslandGeomorphicQualificationPolicy.firstEvidenceBacked());
    }

    record ConfluenceCascadeGradeConflictDetails(
            double maximumConflict,
            double startStation,
            double endStation,
            int sampleCount,
            String qualificationClass,
            int maximumConflictIndex,
            double maximumConflictStation) {}

    private static void appendStationSample(
            List<SkyIslandLocalPosition> points,
            double[] cumulative,
            double pathLength,
            double station,
            double[] sampledDischarge,
            List<SkyIslandLocalPosition> spanPoints,
            List<Double> spanStations,
            List<Double> spanDischarges) {
        double targetArc = station * pathLength;
        for (int index = 0; index + 1 < points.size(); index++) {
            if (targetArc > cumulative[index + 1] + EPSILON) continue;
            double ds = cumulative[index + 1] - cumulative[index];
            double fraction = (targetArc - cumulative[index]) / ds;
            SkyIslandLocalPosition a = points.get(index);
            SkyIslandLocalPosition b = points.get(index + 1);
            spanPoints.add(new SkyIslandLocalPosition(
                    a.x() + fraction * (b.x() - a.x()),
                    a.z() + fraction * (b.z() - a.z())));
            spanStations.add(station);
            spanDischarges.add(
                    sampledDischarge[index]
                            + fraction * (sampledDischarge[index + 1] - sampledDischarge[index]));
            return;
        }
        throw new IllegalStateException("transition station escaped centerline");
    }

    record CenterlineRefinement(
            SkyIslandContinuousChannelCenterline centerline,
            SkyIslandSemanticCorridorCenterlinePlanner.SearchDiagnostics diagnostics) {}

    private static Optional<SkyIslandChannelProfileKind> singleOrdinaryProfileKind(
            SkyIslandSemanticChannelReach semantic) {
        List<SkyIslandChannelProfileKind> kinds =
                semantic.profiles().stream()
                        .map(profile -> profile.kind())
                        .filter(kind -> kind != SkyIslandChannelProfileKind.CASCADE)
                        .distinct()
                        .toList();
        return kinds.size() == 1 ? Optional.of(kinds.getFirst()) : Optional.empty();
    }

    private static SkyIslandGeomorphicQualificationClass qualificationClass(
            SkyIslandChannelProfileKind profileKind) {
        return switch (profileKind) {
            case ALLUVIAL -> SkyIslandGeomorphicQualificationClass.ALLUVIAL;
            case INCISED -> SkyIslandGeomorphicQualificationClass.INCISED;
            case CASCADE -> throw new IllegalArgumentException(
                    "CASCADE has no ordinary centerline head-gap objective");
        };
    }

    static double relativeDischargeAtStation(
            SkyIslandSemanticChannelReach semantic, double station) {
        Objects.requireNonNull(semantic, "semantic");
        return semanticDischargeProfile(semantic).atStation(station);
    }

    private static SemanticDischargeProfile semanticDischargeProfile(
            SkyIslandSemanticChannelReach semantic) {
        List<SkyIslandLocalPosition> guidance = semantic.guidancePoints();
        double[] cumulativeDistance = cumulativeDistance(guidance);
        double totalLength = cumulativeDistance[cumulativeDistance.length - 1];
        if (!(totalLength > 0.0)) {
            throw new IllegalArgumentException("semantic reach must have positive physical length");
        }
        double[] discharge = new double[semantic.profiles().size()];
        double previous = SkyIslandHydraulicGeometryCalibration.MINIMUM_DISCHARGE;
        for (int i = 0; i < discharge.length; i++) {
            double value = Math.max(
                    SkyIslandHydraulicGeometryCalibration.MINIMUM_DISCHARGE,
                    semantic.profiles().get(i).segment().relativeDischarge());
            if (value + 1.0e-12 < previous) {
                throw new IllegalStateException(
                        "semantic reach discharge must not decrease downstream");
            }
            discharge[i] = value;
            previous = value;
            if (!(cumulativeDistance[i + 1] > cumulativeDistance[i])) {
                throw new IllegalStateException(
                        "semantic reach profile segment must have positive physical length");
            }
        }
        return new SemanticDischargeProfile(
                cumulativeDistance, totalLength, discharge);
    }

    private static SkyIslandHydraulicReachSkeleton sampleReach(
            SkyIslandDescriptor descriptor,
            SkyIslandSemanticField terrain,
            SkyIslandGeomorphicReachRoute route,
            SkyIslandContinuousChannelCenterline centerline) {
        List<SkyIslandLocalPosition> points = centerline.points();
        double[] cumulative = cumulativeDistance(points);
        double pathLength = centerline.pathLength();
        if (!(pathLength > 0.0)) {
            throw new IllegalStateException("candidate geomorphic route must have positive length");
        }

        SkyIslandSemanticChannelReach semantic = route.semanticReach();
        SemanticDischargeProfile dischargeProfile = semanticDischargeProfile(semantic);

        List<SkyIslandHydraulicGeometrySkeletonSample> samples =
                new ArrayList<>(points.size());
        double maximumWidth = 0.0;
        double maximumDepth = 0.0;

        for (int i = 0; i < points.size(); i++) {
            double station = cumulative[i] / pathLength;
            double discharge = dischargeProfile.atStation(station);
            double width =
                    SkyIslandHydraulicGeometryCalibration.bankfullHalfWidth(
                            descriptor.nominalRadius(), discharge);
            double depth =
                    SkyIslandHydraulicGeometryCalibration.waterDepthPotential(discharge);
            double terrainElevation = clamp01(terrain.sample(points.get(i)));
            samples.add(new SkyIslandHydraulicGeometrySkeletonSample(
                    points.get(i),
                    cumulative[i],
                    station,
                    discharge,
                    width,
                    depth,
                    terrainElevation));
            maximumWidth = Math.max(maximumWidth, width);
            maximumDepth = Math.max(maximumDepth, depth);
        }

        return new SkyIslandHydraulicReachSkeleton(
                route,
                centerline,
                samples,
                pathLength,
                maximumWidth,
                maximumDepth);
    }

    private record SemanticDischargeProfile(
            double[] cumulativeDistance,
            double totalLength,
            double[] dischargeAtSegmentStart) {
        private double maximumDischarge() {
            return dischargeAtSegmentStart[dischargeAtSegmentStart.length - 1];
        }

        private int segmentIndexAtStation(double station) {
            if (!Double.isFinite(station) || station < 0.0 || station > 1.0) {
                throw new IllegalArgumentException("station must be finite and in [0, 1]");
            }
            double targetDistance = station * totalLength;
            for (int i = 0; i < dischargeAtSegmentStart.length; i++) {
                if (targetDistance <= cumulativeDistance[i + 1]
                        || i == dischargeAtSegmentStart.length - 1) {
                    return i;
                }
            }
            throw new IllegalStateException("station escaped semantic discharge profile");
        }

        private double atStation(double station) {
            int i = segmentIndexAtStation(station);
            double startDistance = cumulativeDistance[i];
            double endDistance = cumulativeDistance[i + 1];
            double startDischarge = dischargeAtSegmentStart[i];
            double endDischarge = i + 1 < dischargeAtSegmentStart.length
                    ? dischargeAtSegmentStart[i + 1]
                    : startDischarge;
            double fraction = Math.max(
                    0.0,
                    Math.min(
                            1.0,
                            (station * totalLength - startDistance)
                                    / (endDistance - startDistance)));
            return lerp(startDischarge, endDischarge, fraction);
        }
    }

    private static double[] cumulativeDistance(List<SkyIslandLocalPosition> points) {
        double[] cumulative = new double[points.size()];
        for (int i = 1; i < points.size(); i++) {
            cumulative[i] = cumulative[i - 1]
                    + Math.hypot(
                            points.get(i).x() - points.get(i - 1).x(),
                            points.get(i).z() - points.get(i - 1).z());
        }
        return cumulative;
    }

    private static double lerp(double a, double b, double fraction) {
        return a + (b - a) * fraction;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
