package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Couples an admitted ordinary drainage tree to one terrain-supported candidate and one hydraulic
 * solution. This is the network-scale F4 planning seam; callers still must compose overlapping
 * junction terrain explicitly and requalify the complete field before granting realization authority.
 */
public final class SkyIslandComponentNetworkHydraulicPlanner {
    private static final double MINIMUM_BED_SLOPE = 1.0e-5;
    private static final double MINIMUM_BANKFULL_BOTTOM_WIDTH_FRACTION = 0.10;
    private static final double MAXIMUM_SIDE_SLOPE = 2.0;
    private static final double VALLEY_BLEND_WIDTH_FACTOR = 2.0;
    private static final double GRAVITY = 9.81;
    private static final double CONTRACTION_COEFFICIENT = 0.10;
    private static final double EXPANSION_COEFFICIENT = 0.30;
    private static final double EPSILON = 1.0e-9;

    private SkyIslandComponentNetworkHydraulicPlanner() {}

    /**
     * Solves a complete qualified combining component against one original-terrain field.
     * Unsupported terminal, cascade, span, topology, discharge, or flow-regime cases fail closed.
     */
    public static NetworkSolution solve(
            SkyIslandDescriptor descriptor,
            SkyIslandHydraulicTerminalComponent component,
            Map<Long, SkyIslandHydraulicReachSkeleton> skeletons,
            SkyIslandSemanticField originalTerrain) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(component, "component");
        Objects.requireNonNull(skeletons, "skeletons");
        Objects.requireNonNull(originalTerrain, "originalTerrain");
        if (component.status() != SkyIslandHydraulicAssemblyStatus.QUALIFIED
                || component.terminalFate().kind() != SkyIslandChannelTerminalFateKind.EDGE_OUTLET) {
            throw new IllegalArgumentException("network hydraulics require a qualified edge-outlet component");
        }

        List<ReachContext> contexts = new ArrayList<>();
        Map<Integer, List<ReachContext>> incoming = new HashMap<>();
        Map<Integer, ReachContext> outgoing = new HashMap<>();
        for (SkyIslandHydraulicReachAssembly assembly : component.reaches().stream()
                .sorted(Comparator.comparingLong(SkyIslandHydraulicReachAssembly::identity))
                .toList()) {
            if (assembly.status() != SkyIslandHydraulicAssemblyStatus.QUALIFIED
                    || !assembly.cascades().isEmpty()
                    || assembly.ordinarySpans().size() != 1) {
                throw new IllegalArgumentException(
                        "network hydraulics require complete qualified ordinary reaches without cascades");
            }
            SkyIslandOrdinarySpanOutcome span = assembly.ordinarySpans().getFirst();
            if (span.status() != SkyIslandOrdinarySpanStatus.SOLVED_QUALIFIED
                    || Math.abs(span.span().parentStartStationFraction()) > EPSILON
                    || Math.abs(span.span().parentEndStationFraction() - 1.0) > EPSILON) {
                throw new IllegalArgumentException("network hydraulics require full qualified ordinary spans");
            }
            SkyIslandHydraulicReachSkeleton skeleton = skeletons.get(assembly.identity());
            if (skeleton == null
                    || !skeleton.geomorphicRoute().semanticReach().equals(assembly.semanticReach())) {
                throw new IllegalArgumentException("missing exact C2 skeleton for network reach");
            }
            ReachContext context = buildContext(descriptor, assembly, span, skeleton, originalTerrain);
            contexts.add(context);
            int start = assembly.semanticReach().startCellIndex();
            int end = assembly.semanticReach().endCellIndex();
            if (outgoing.put(start, context) != null) {
                throw new IllegalArgumentException("network contains an unsupported downstream flow split");
            }
            incoming.computeIfAbsent(end, ignored -> new ArrayList<>()).add(context);
        }
        if (contexts.isEmpty()) {
            throw new IllegalArgumentException("qualified network component contains no reaches");
        }

        String terminalNode = Integer.toString(component.terminalFate().channelTerminalCellIndex());
        if (outgoing.containsKey(component.terminalFate().channelTerminalCellIndex())) {
            throw new IllegalArgumentException("edge outlet cannot have a downstream reach");
        }
        List<ReachContext> terminalIncoming =
                incoming.getOrDefault(component.terminalFate().channelTerminalCellIndex(), List.of());
        if (terminalIncoming.size() != 1) {
            throw new IllegalArgumentException("edge outlet must have exactly one terminal reach");
        }

        SkyIslandJointNetworkBedSolver.ComponentProfile jointBed =
                SkyIslandJointNetworkBedSolver.solve(
                        contexts.stream().map(ReachContext::bedInput).toList());
        if (jointBed.reaches().size() != contexts.size()) {
            throw new IllegalStateException("joint bed solver did not preserve every component reach");
        }

        Map<String, ReachContext> contextById = new LinkedHashMap<>();
        Map<String, SkyIslandHydraulicCandidateBuilder.Candidate> candidates = new LinkedHashMap<>();
        Map<String, SkyIslandMassBalancedDischargePlanner.ReachInput> flowInputs =
                new LinkedHashMap<>();
        for (int i = 0; i < contexts.size(); i++) {
            ReachContext context = contexts.get(i);
            SkyIslandJointNetworkBedSolver.ReachProfile bed = jointBed.reaches().get(i);
            if (!bed.nodeIds().equals(context.bedInput().stations().stream()
                    .map(SkyIslandJointNetworkBedSolver.Station::nodeId).toList())) {
                throw new IllegalStateException("joint bed solver changed deterministic reach station identity");
            }
            SkyIslandHydraulicCandidateBuilder.Candidate candidate =
                    SkyIslandHydraulicCandidateBuilder.build(
                            context.route(), originalTerrain, context.configuration(),
                            bed.bedElevationsMetres());
            contextById.put(context.id(), context);
            candidates.put(context.id(), candidate);
            flowInputs.put(context.id(), new SkyIslandMassBalancedDischargePlanner.ReachInput(
                    context.id(),
                    Integer.toString(context.assembly().semanticReach().startCellIndex()),
                    Integer.toString(context.assembly().semanticReach().endCellIndex()),
                    candidate.stationsMetres(),
                    context.skeleton().samples().stream()
                            .mapToDouble(SkyIslandHydraulicGeometrySkeletonSample::relativeDischarge)
                            .toArray()));
        }

        ReachContext terminalContext = terminalIncoming.getFirst();
        double terminalRelativeFlow = terminalContext.skeleton().samples().getLast().relativeDischarge();
        SkyIslandMassBalancedDischargePlanner.NetworkFlows unitFlows =
                SkyIslandMassBalancedDischargePlanner.plan(
                        List.copyOf(flowInputs.values()), terminalNode, terminalRelativeFlow, 1.0);

        List<SkyIslandGameScaleHydraulicModel.BankfullCapacityProfile> capacityProfiles =
                new ArrayList<>();
        for (ReachContext context : contexts) {
            double[] beds = candidates.get(context.id()).bedElevationsMetres();
            double[] stations = candidates.get(context.id()).stationsMetres();
            double[] slopes = localBedSlopes(stations, beds);
            capacityProfiles.add(new SkyIslandGameScaleHydraulicModel.BankfullCapacityProfile(
                    unitFlows.reachesById().get(context.id())
                            .dischargeAtStationsCubicMetresPerSecond(),
                    slopes,
                    context.configuration().bottomWidthMetres(),
                    context.configuration().sideSlopeHorizontalToVertical(),
                    context.configuration().bankfullDepthMetres()));
        }
        double dischargeScale =
                SkyIslandGameScaleHydraulicModel.calibrateComponentDischargeScale(capacityProfiles);
        SkyIslandMassBalancedDischargePlanner.NetworkFlows flows =
                SkyIslandMassBalancedDischargePlanner.plan(
                        List.copyOf(flowInputs.values()), terminalNode, terminalRelativeFlow, dischargeScale);

        List<SkyIslandSubcriticalJunctionNetworkSolver.Reach> hydraulicReaches = new ArrayList<>();
        for (ReachContext context : contexts) {
            SkyIslandHydraulicCandidateBuilder.Candidate candidate = candidates.get(context.id());
            SkyIslandMassBalancedDischargePlanner.ReachFlows reachFlows =
                    flows.reachesById().get(context.id());
            SkyIslandStandardStepSolver.CrossSection[] sections =
                    new SkyIslandStandardStepSolver.CrossSection[candidate.stationsMetres().length];
            Arrays.fill(sections, trapezoid(context.configuration()));
            hydraulicReaches.add(new SkyIslandSubcriticalJunctionNetworkSolver.Reach(
                    candidate.stationsMetres(),
                    candidate.bedElevationsMetres(),
                    sections,
                    reachFlows.dischargeAtStationsCubicMetresPerSecond(),
                    SkyIslandGameScaleHydraulicModel.MANNING_ROUGHNESS));
        }

        List<SkyIslandSubcriticalDrainageTreeSolver.Junction> junctions = new ArrayList<>();
        for (Map.Entry<Integer, List<ReachContext>> nodeEntry : incoming.entrySet()) {
            List<ReachContext> tributaries = nodeEntry.getValue();
            if (tributaries.size() < 2) {
                continue;
            }
            ReachContext receiver = outgoing.get(nodeEntry.getKey());
            if (receiver == null) {
                throw new IllegalArgumentException("nonterminal confluence has no receiving reach");
            }
            List<SkyIslandSubcriticalDrainageTreeSolver.Tributary> branches =
                    tributaries.stream()
                            .sorted(Comparator.comparing(ReachContext::id))
                            .map(context -> new SkyIslandSubcriticalDrainageTreeSolver.Tributary(
                                    context.id(), 0.0, CONTRACTION_COEFFICIENT, EXPANSION_COEFFICIENT))
                            .toList();
            junctions.add(new SkyIslandSubcriticalDrainageTreeSolver.Junction(receiver.id(), branches));
        }
        for (Map.Entry<Integer, ReachContext> edge : outgoing.entrySet()) {
            if (edge.getKey() != component.terminalFate().channelTerminalCellIndex()
                    && incoming.getOrDefault(edge.getKey(), List.of()).size() != 0) {
                int incomingCount = incoming.get(edge.getKey()).size();
                if (incomingCount < 2) {
                    throw new IllegalArgumentException(
                            "ordinary one-in/one-out macro nodes must be collapsed before network realization");
                }
            }
        }
        String terminalReachId = terminalContext.id();
        SkyIslandHydraulicCandidateBuilder.Candidate terminalCandidate = candidates.get(terminalReachId);
        double[] terminalStations = terminalCandidate.stationsMetres();
        double[] terminalBeds = terminalCandidate.bedElevationsMetres();
        int last = terminalStations.length - 1;
        double outletSlope = (terminalBeds[last - 1] - terminalBeds[last])
                / (terminalStations[last] - terminalStations[last - 1]);
        if (!(outletSlope > 0.0) || !Double.isFinite(outletSlope)) {
            throw new IllegalArgumentException("downstream normal-depth boundary requires a positive candidate outlet slope");
        }
        SkyIslandMassBalancedDischargePlanner.ReachFlows terminalFlows =
                flows.reachesById().get(terminalReachId);
        double terminalDischarge =
                terminalFlows.dischargeAtStationsCubicMetresPerSecond()[last];
        SkyIslandStandardStepSolver.CrossSection terminalSection =
                trapezoid(contextById.get(terminalReachId).configuration());
        double terminalDepth = SkyIslandManningNormalDepthSolver.solve(
                terminalDischarge,
                SkyIslandGameScaleHydraulicModel.MANNING_ROUGHNESS,
                outletSlope,
                new SkyIslandManningNormalDepthSolver.CrossSection() {
                    @Override
                    public double area(double depthMetres) {
                        return terminalSection.area(depthMetres);
                    }

                    @Override
                    public double wettedPerimeter(double depthMetres) {
                        return terminalSection.wettedPerimeter(depthMetres);
                    }
                });
        if (froudeNumber(terminalDischarge, terminalDepth, terminalSection) >= 1.0) {
            throw new IllegalArgumentException("supercritical terminal boundary is unsupported by the subcritical tree solver");
        }

        List<SkyIslandSubcriticalDrainageTreeSolver.Reach> treeReaches = hydraulicReaches.stream()
                .map(reach -> {
                    for (ReachContext context : contexts) {
                        if (context.id().equals(reachId(contexts, hydraulicReaches, reach))) {
                            return new SkyIslandSubcriticalDrainageTreeSolver.Reach(context.id(), reach);
                        }
                    }
                    throw new IllegalStateException("missing network reach identity");
                })
                .toList();
        SkyIslandSubcriticalDrainageTreeSolver.NetworkProfile hydraulicProfile =
                SkyIslandSubcriticalDrainageTreeSolver.solve(
                        treeReaches, junctions, terminalReachId, terminalDepth);

        Map<String, ReachSolution> solved = new LinkedHashMap<>();
        for (ReachContext context : contexts) {
            SkyIslandHydraulicCandidateBuilder.Candidate candidate = candidates.get(context.id());
            SkyIslandMassBalancedDischargePlanner.ReachFlows reachFlows =
                    flows.reachesById().get(context.id());
            double[] beds = candidate.bedElevationsMetres();
            double[] depths = hydraulicProfile.reachesById().get(context.id()).depthsMetres();
            double[] stages = new double[beds.length];
            for (int i = 0; i < beds.length; i++) {
                stages[i] = beds[i] + depths[i];
            }
            SkyIslandHydraulicCandidateBuilder.Profile profile =
                    new SkyIslandHydraulicCandidateBuilder.Profile(
                            candidate.stationsMetres(), beds, depths, stages);
            solved.put(context.id(), new ReachSolution(candidate, profile, reachFlows));
        }
        return new NetworkSolution(jointBed, dischargeScale, flows, solved, terminalReachId);
    }

    private static ReachContext buildContext(
            SkyIslandDescriptor descriptor,
            SkyIslandHydraulicReachAssembly assembly,
            SkyIslandOrdinarySpanOutcome span,
            SkyIslandHydraulicReachSkeleton skeleton,
            SkyIslandSemanticField terrain) {
        List<SkyIslandLocalPosition> points = skeleton.centerline().points();
        List<SkyIslandHydraulicGeometrySkeletonSample> samples = skeleton.samples();
        if (points.size() != samples.size() || points.size() != span.solvedSamples().size()) {
            throw new IllegalArgumentException("accepted C2 route, skeleton, and F3E span stations must match");
        }
        for (int i = 0; i < points.size(); i++) {
            if (!points.get(i).equals(samples.get(i).position())
                    || !points.get(i).equals(span.solvedSamples().get(i).position())) {
                throw new IllegalArgumentException("network candidate requires exact shared station geometry");
            }
        }

        SkyIslandSemanticChannelReach semantic = assembly.semanticReach();
        double verticalScale = descriptor.reliefBudget();
        double bankfullWidth = 2.0 * skeleton.maximumBankfullHalfWidth();
        double bankfullDepth = skeleton.maximumWaterDepthPotential() * verticalScale;
        double sideSlope = Math.min(
                MAXIMUM_SIDE_SLOPE,
                (1.0 - MINIMUM_BANKFULL_BOTTOM_WIDTH_FRACTION)
                        * bankfullWidth / (2.0 * bankfullDepth));
        double bottomWidth = bankfullWidth - 2.0 * sideSlope * bankfullDepth;
        if (!(bottomWidth > 0.0) || !Double.isFinite(bottomWidth)) {
            throw new IllegalArgumentException("network reach cannot contain its authored bankfull trapezoid");
        }

        double startTerrain = terrain.sample(points.getFirst()) * verticalScale;
        double endTerrain = terrain.sample(points.getLast()) * verticalScale;
        double measuredGrade = (startTerrain - endTerrain) / skeleton.pathLength();
        double minimumGrade = Math.max(MINIMUM_BED_SLOPE, measuredGrade);
        SkyIslandGeomorphicProfileLimits limits =
                SkyIslandGeomorphicQualificationPolicy.firstEvidenceBacked().limits(semantic);
        double maximumEdit = limits.maximumCenterlineLoweringPotential() * verticalScale;
        double bankEdge = bottomWidth * 0.5 + sideSlope * bankfullDepth;
        double valleyHalfWidth = bankEdge
                + Math.max(bottomWidth, bankfullDepth) * VALLEY_BLEND_WIDTH_FACTOR;
        SkyIslandHydraulicCandidateBuilder.Configuration configuration =
                new SkyIslandHydraulicCandidateBuilder.Configuration(
                        SkyIslandGameScaleHydraulicModel.HORIZONTAL_METRES_PER_LOCAL_UNIT,
                        verticalScale,
                        0.0,
                        minimumGrade,
                        0.0,
                        bottomWidth,
                        sideSlope,
                        bankfullDepth,
                        valleyHalfWidth,
                        maximumEdit);
        SkyIslandGeomorphicCandidateRoute sourceRoute = skeleton.geomorphicRoute().route();
        SkyIslandGeomorphicCandidateRoute route =
                new SkyIslandGeomorphicCandidateRoute(
                        points,
                        sourceRoute.totalCost(),
                        skeleton.centerline().pathLength(),
                        sourceRoute.maxGuidanceDeviation(),
                        sourceRoute.uphillStepFraction(),
                        sourceRoute.ridgeSampleFraction(),
                        sourceRoute.meanValleyFloorAdvantage(),
                        sourceRoute.maxUphillStep());

        double[] stations = stationMetres(points);
        String id = reachId(semantic.startCellIndex(), semantic.endCellIndex());
        List<SkyIslandJointNetworkBedSolver.Station> bedStations = new ArrayList<>(points.size());
        for (int i = 0; i < points.size(); i++) {
            String nodeId = i == 0
                    ? Integer.toString(semantic.startCellIndex())
                    : i == points.size() - 1
                            ? Integer.toString(semantic.endCellIndex())
                            : id + ":" + i;
            bedStations.add(new SkyIslandJointNetworkBedSolver.Station(
                    nodeId,
                    stations[i],
                    terrain.sample(points.get(i)) * verticalScale,
                    maximumEdit));
        }
        SkyIslandJointNetworkBedSolver.Reach bedInput =
                new SkyIslandJointNetworkBedSolver.Reach(bedStations, minimumGrade);
        return new ReachContext(id, assembly, span, skeleton, route, configuration, bedInput);
    }

    private static double[] stationMetres(List<SkyIslandLocalPosition> points) {
        double[] stations = new double[points.size()];
        for (int i = 1; i < points.size(); i++) {
            stations[i] = stations[i - 1] + Math.hypot(
                    points.get(i).x() - points.get(i - 1).x(),
                    points.get(i).z() - points.get(i - 1).z())
                    * SkyIslandGameScaleHydraulicModel.HORIZONTAL_METRES_PER_LOCAL_UNIT;
        }
        return stations;
    }

    private static double[] localBedSlopes(double[] stations, double[] beds) {
        if (stations.length != beds.length || stations.length < 2) {
            throw new IllegalArgumentException("bed slope requires matching network stations");
        }
        double[] interval = new double[stations.length - 1];
        for (int i = 0; i < interval.length; i++) {
            interval[i] = (beds[i] - beds[i + 1]) / (stations[i + 1] - stations[i]);
            if (!(interval[i] > 0.0) || !Double.isFinite(interval[i])) {
                throw new IllegalArgumentException("bankfull capacity requires positive candidate bed grades");
            }
        }
        double[] slopes = new double[stations.length];
        slopes[0] = interval[0];
        slopes[slopes.length - 1] = interval[interval.length - 1];
        for (int i = 1; i < slopes.length - 1; i++) {
            slopes[i] = Math.min(interval[i - 1], interval[i]);
        }
        return slopes;
    }

    private static SkyIslandStandardStepSolver.CrossSection trapezoid(
            SkyIslandHydraulicCandidateBuilder.Configuration configuration) {
        return new SkyIslandStandardStepSolver.CrossSection() {
            @Override
            public double area(double depthMetres) {
                return configuration.bottomWidthMetres() * depthMetres
                        + configuration.sideSlopeHorizontalToVertical() * depthMetres * depthMetres;
            }

            @Override
            public double wettedPerimeter(double depthMetres) {
                return configuration.bottomWidthMetres()
                        + 2.0 * depthMetres
                                * Math.hypot(1.0, configuration.sideSlopeHorizontalToVertical());
            }

            @Override
            public double topWidth(double depthMetres) {
                return configuration.bottomWidthMetres()
                        + 2.0 * configuration.sideSlopeHorizontalToVertical() * depthMetres;
            }
        };
    }

    private static double froudeNumber(
            double discharge,
            double depth,
            SkyIslandStandardStepSolver.CrossSection section) {
        double area = section.area(depth);
        double hydraulicDepth = area / section.topWidth(depth);
        return (discharge / area) / Math.sqrt(GRAVITY * hydraulicDepth);
    }

    private static String reachId(int start, int end) {
        return start + "->" + end;
    }

    private static String reachId(
            List<ReachContext> contexts,
            List<SkyIslandSubcriticalJunctionNetworkSolver.Reach> hydraulicReaches,
            SkyIslandSubcriticalJunctionNetworkSolver.Reach target) {
        int index = hydraulicReaches.indexOf(target);
        if (index < 0 || index >= contexts.size()) {
            throw new IllegalStateException("hydraulic reach order changed");
        }
        return contexts.get(index).id();
    }

    private record ReachContext(
            String id,
            SkyIslandHydraulicReachAssembly assembly,
            SkyIslandOrdinarySpanOutcome span,
            SkyIslandHydraulicReachSkeleton skeleton,
            SkyIslandGeomorphicCandidateRoute route,
            SkyIslandHydraulicCandidateBuilder.Configuration configuration,
            SkyIslandJointNetworkBedSolver.Reach bedInput) {}

    public record ReachSolution(
            SkyIslandHydraulicCandidateBuilder.Candidate candidate,
            SkyIslandHydraulicCandidateBuilder.Profile profile,
            SkyIslandMassBalancedDischargePlanner.ReachFlows flows) {
        public ReachSolution {
            candidate = Objects.requireNonNull(candidate, "candidate");
            profile = Objects.requireNonNull(profile, "profile");
            flows = Objects.requireNonNull(flows, "flows");
        }
    }

    public record NetworkSolution(
            SkyIslandJointNetworkBedSolver.ComponentProfile jointBed,
            double dischargeScale,
            SkyIslandMassBalancedDischargePlanner.NetworkFlows flows,
            Map<String, ReachSolution> reachesById,
            String terminalReachId) {
        public NetworkSolution {
            jointBed = Objects.requireNonNull(jointBed, "jointBed");
            flows = Objects.requireNonNull(flows, "flows");
            reachesById = Map.copyOf(new LinkedHashMap<>(reachesById));
            terminalReachId = Objects.requireNonNull(terminalReachId, "terminalReachId");
            if (!(dischargeScale > 0.0) || !Double.isFinite(dischargeScale)
                    || reachesById.isEmpty() || !reachesById.containsKey(terminalReachId)) {
                throw new IllegalArgumentException("invalid solved component network");
            }
        }
    }
}
