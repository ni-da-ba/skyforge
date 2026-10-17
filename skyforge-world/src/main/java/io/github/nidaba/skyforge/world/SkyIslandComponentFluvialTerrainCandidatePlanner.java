package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Builds an F3E-component-gated continuous fluvial terrain candidate.
 *
 * <p>Complete single reaches retain the accepted source-normal-depth path. Qualified combining
 * trees use one shared terrain candidate, mass-balanced discharge scale, HEC-style energy junctions,
 * and the same unchanged post-solve D2 qualification. CASCADE and unsupported terminal/regime cases
 * remain fail-closed.
 *
 * <p>The returned field remains quantitative evidence only and is not Minecraft authority.
 */
public final class SkyIslandComponentFluvialTerrainCandidatePlanner {
    private static final double EPSILON = 1.0e-9;

    private SkyIslandComponentFluvialTerrainCandidatePlanner() {}

    public static SkyIslandComponentFluvialTerrainCandidatePlan plan(
            SkyIslandDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        return plan(
                descriptor,
                SkyIslandHydraulicNetworkAssemblyPlanner.plan(descriptor),
                SkyIslandGeomorphicQualificationPolicy.firstEvidenceBacked());
    }

    /**
     * Rebuilds and qualifies channel and water together against the exact physical surface.
     * One fixed datum (the volume suspension elevation) is used everywhere, never a local offset.
     * Existing route/component admission and hydraulic solvers are unchanged.
     */
    public static SkyIslandComponentFluvialTerrainCandidatePlan planAssociated(
            SkyIslandAuthoredRealizationAssociation association) {
        Objects.requireNonNull(association, "association");
        var descriptor = association.authoredDescriptor();
        var volume = association.realizedVolume().compiledVolume();
        var physical = volume.descriptor();
        var interpreter = new SkyIslandTerrainInterpreter(volume, SkyIslandTerrainProfile.reference());
        double datum = physical.suspensionElevation();
        double scale = descriptor.reliefBudget();
        SkyIslandSemanticField physicalTerrain = position ->
                (interpreter.upperSurfaceHeight(
                        physical.centerX() + position.x(), physical.centerZ() + position.z())
                        - datum) / scale;
        return plan(descriptor, SkyIslandHydraulicNetworkAssemblyPlanner.plan(descriptor),
                SkyIslandGeomorphicQualificationPolicy.firstEvidenceBacked(),
                physicalTerrain, java.util.Optional.of(association));
    }

    static SkyIslandComponentFluvialTerrainCandidatePlan plan(
            SkyIslandDescriptor descriptor,
            SkyIslandHydraulicNetworkAssemblyPlan assemblyPlan,
            SkyIslandGeomorphicQualificationPolicy policy) {
        return plan(descriptor, assemblyPlan, policy,
                SkyIslandPreHydrologicTerrainField.create(descriptor), java.util.Optional.empty());
    }

    private static SkyIslandComponentFluvialTerrainCandidatePlan plan(
            SkyIslandDescriptor descriptor,
            SkyIslandHydraulicNetworkAssemblyPlan assemblyPlan,
            SkyIslandGeomorphicQualificationPolicy policy,
            SkyIslandSemanticField original,
            java.util.Optional<SkyIslandAuthoredRealizationAssociation> worldAssociation) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(original, "original");
        Objects.requireNonNull(assemblyPlan, "assemblyPlan");
        Objects.requireNonNull(policy, "policy");
        if (!descriptor.equals(assemblyPlan.descriptor())) {
            throw new IllegalArgumentException(
                    "F3E assembly descriptor must match F4A descriptor");
        }

        SkyIslandHydraulicGeometrySkeletonPlan skeletonPlan =
                assemblyPlan.ordinarySpanPlan()
                        .cascadePlan()
                        .transitionGeometry()
                        .topology()
                        .skeletonPlan();
        SkyIslandGeomorphicChannelNetworkPlan network =
                skeletonPlan.geomorphicNetwork();
        Map<Long, SkyIslandHydraulicReachSkeleton> skeletons =
                indexSkeletons(skeletonPlan.reaches());

        List<SkyIslandHydraulicTerminalComponent> realizedComponents =
                new ArrayList<>();
        List<SkyIslandComponentFluvialTerrainDeferral> deferredQualifiedComponents =
                new ArrayList<>();
        Map<Long, SkyIslandHydraulicReachGeometry> realizedByIdentity =
                new LinkedHashMap<>();
        Map<Long, SkyIslandGameScaleHydraulicModel.SourceReachSolution> coupledByIdentity =
                new LinkedHashMap<>();
        Map<Long, SkyIslandSemanticField> coupledTerrainByIdentity = new LinkedHashMap<>();

        for (SkyIslandHydraulicTerminalComponent component :
                assemblyPlan.terminalComponents()) {
            if (component.status() != SkyIslandHydraulicAssemblyStatus.QUALIFIED) {
                continue;
            }

            List<SkyIslandComponentFluvialTerrainDeferralReason> reasons =
                    supportReasons(component, network);
            if (!reasons.isEmpty()) {
                deferredQualifiedComponents.add(
                        new SkyIslandComponentFluvialTerrainDeferral(component, reasons));
                continue;
            }

            if (component.reaches().size() == 1) {
                SkyIslandHydraulicReachAssembly reachAssembly = component.reaches().getFirst();
                SkyIslandOrdinarySpanOutcome span = reachAssembly.ordinarySpans().getFirst();
                SkyIslandHydraulicReachSkeleton skeleton =
                        requireSkeleton(skeletons, reachAssembly.semanticReach());
                long identity = reachAssembly.identity();
                SkyIslandGameScaleHydraulicModel.SourceReachSolution coupled =
                        SkyIslandGameScaleHydraulicModel.solveSourceReach(
                                descriptor, skeleton, original);
                SkyIslandHydraulicReachGeometry geometry = reconstructCoupledReach(
                        descriptor, skeleton, span, original, coupled);
                if (realizedByIdentity.put(identity, geometry) != null
                        || coupledByIdentity.put(identity, coupled) != null) {
                    throw new IllegalStateException("F4A repeated single-reach component identity");
                }
            } else {
                Map<Long, SkyIslandHydraulicReachGeometry> componentReaches = new LinkedHashMap<>();
                Map<Long, SkyIslandSemanticField> componentSurfaces = new LinkedHashMap<>();
                try {
                    realizeNetworkComponent(
                            descriptor,
                            component,
                            skeletons,
                            original,
                            componentReaches,
                            componentSurfaces);
                } catch (IllegalArgumentException | IllegalStateException unsupported) {
                    deferredQualifiedComponents.add(new SkyIslandComponentFluvialTerrainDeferral(
                            component,
                            List.of(SkyIslandComponentFluvialTerrainDeferralReason
                                    .UNSUPPORTED_NETWORK_PROFILE),
                            unsupported.getClass().getSimpleName() + ": " + unsupported.getMessage()));
                    continue;
                }
                for (Map.Entry<Long, SkyIslandHydraulicReachGeometry> entry : componentReaches.entrySet()) {
                    if (realizedByIdentity.put(entry.getKey(), entry.getValue()) != null) {
                        throw new IllegalStateException("F4A network duplicated a realized reach");
                    }
                }
                for (Map.Entry<Long, SkyIslandSemanticField> entry : componentSurfaces.entrySet()) {
                    if (coupledTerrainByIdentity.put(entry.getKey(), entry.getValue()) != null) {
                        throw new IllegalStateException("F4A network duplicated a terrain surface");
                    }
                }
            }
            realizedComponents.add(component);
        }

        List<SkyIslandHydraulicReachGeometry> realizedReaches =
                new ArrayList<>(realizedByIdentity.values());
        realizedReaches.sort(Comparator
                .comparingInt((SkyIslandHydraulicReachGeometry reach) ->
                        reach.geomorphicRoute().semanticReach().startCellIndex())
                .thenComparingInt(reach ->
                        reach.geomorphicRoute().semanticReach().endCellIndex()));

        SkyIslandQualifiedFluvialTerrainField terrainField = coupledTerrainByIdentity.isEmpty()
                ? new SkyIslandQualifiedFluvialTerrainField(
                        original, realizedReaches, coupledByIdentity, descriptor.reliefBudget())
                : SkyIslandQualifiedFluvialTerrainField.withCoupledTerrainSurfaces(
                        original, realizedReaches, coupledTerrainByIdentity, descriptor.reliefBudget());

        List<SkyIslandGeomorphicReachQualification> postRealizationQualifications =
                new ArrayList<>(realizedReaches.size());
        double planningSpacing = network.planningSpacing();
        for (SkyIslandHydraulicReachGeometry reach : realizedReaches) {
            SkyIslandGeomorphicReachDiagnostics diagnostics =
                    SkyIslandGeomorphicReachDiagnosticsPlanner.measureReach(
                            descriptor, reach, terrainField, planningSpacing);
            SkyIslandGeomorphicReachQualification qualification =
                    SkyIslandGeomorphicQualificationEvaluator.evaluate(
                            diagnostics, policy);
            if (!qualification.accepted()) {
                SkyIslandSemanticChannelReach semantic =
                        reach.geomorphicRoute().semanticReach();
                throw new IllegalStateException(
                        "F4A terrain candidate violates D2 after realization for reach "
                                + semantic.startCellIndex()
                                + "->"
                                + semantic.endCellIndex()
                                + ": "
                                + qualification.violations()
                                + " (bank-containment deficit="
                                + diagnostics.maximumBankContainmentDeficitWorldUnits()
                                + " world units, maximum depth/width="
                                + diagnostics.maximumDepthToBankfullWidthRatio()
                                + ")");
            }
            postRealizationQualifications.add(qualification);
        }

        return new SkyIslandComponentFluvialTerrainCandidatePlan(
                descriptor,
                assemblyPlan,
                realizedComponents,
                deferredQualifiedComponents,
                postRealizationQualifications,
                terrainField,
                worldAssociation);
    }

    private static List<SkyIslandComponentFluvialTerrainDeferralReason> supportReasons(
            SkyIslandHydraulicTerminalComponent component,
            SkyIslandGeomorphicChannelNetworkPlan network) {
        EnumSet<SkyIslandComponentFluvialTerrainDeferralReason> reasons =
                EnumSet.noneOf(SkyIslandComponentFluvialTerrainDeferralReason.class);

        if (component.terminalFate().kind()
                != SkyIslandChannelTerminalFateKind.EDGE_OUTLET) {
            reasons.add(
                    SkyIslandComponentFluvialTerrainDeferralReason
                            .TERMINAL_TERRAIN_REQUIRED);
        }

        for (SkyIslandHydraulicReachAssembly reach : component.reaches()) {
            SkyIslandSemanticChannelReach semantic = reach.semanticReach();
            if (!reach.cascades().isEmpty()) {
                reasons.add(
                        SkyIslandComponentFluvialTerrainDeferralReason
                                .CASCADE_TERRAIN_REQUIRED);
            }

            SkyIslandGeomorphicNetworkNode start =
                    network.requireNode(semantic.startCellIndex());
            SkyIslandGeomorphicNetworkNode end =
                    network.requireNode(semantic.endCellIndex());
            boolean completeSourceReach = component.reaches().size() == 1
                    && start.kind() == SkyIslandGeomorphicNetworkNodeKind.SOURCE
                    && end.kind() == SkyIslandGeomorphicNetworkNodeKind.TERMINAL;
            boolean supportedTreeNodeKinds =
                    (start.kind() == SkyIslandGeomorphicNetworkNodeKind.SOURCE
                            || start.kind() == SkyIslandGeomorphicNetworkNodeKind.CONFLUENCE)
                    && (end.kind() == SkyIslandGeomorphicNetworkNodeKind.TERMINAL
                            || end.kind() == SkyIslandGeomorphicNetworkNodeKind.CONFLUENCE);
            if ((component.reaches().size() == 1 && !completeSourceReach)
                    || (component.reaches().size() > 1 && !supportedTreeNodeKinds)) {
                reasons.add(
                        SkyIslandComponentFluvialTerrainDeferralReason
                                .SOURCE_BOUNDARY_REQUIRED);
            }

            if (reach.ordinarySpans().size() != 1) {
                reasons.add(
                        SkyIslandComponentFluvialTerrainDeferralReason
                                .PARTIAL_SPAN_TERRAIN_REQUIRED);
                continue;
            }
            SkyIslandOrdinarySpanOutcome span = reach.ordinarySpans().getFirst();
            if (span.status() != SkyIslandOrdinarySpanStatus.SOLVED_QUALIFIED
                    || Math.abs(span.span().parentStartStationFraction()) > EPSILON
                    || Math.abs(span.span().parentEndStationFraction() - 1.0) > EPSILON) {
                reasons.add(
                        SkyIslandComponentFluvialTerrainDeferralReason
                                .PARTIAL_SPAN_TERRAIN_REQUIRED);
            }
        }

        return List.copyOf(reasons);
    }

    private static void realizeNetworkComponent(
            SkyIslandDescriptor descriptor,
            SkyIslandHydraulicTerminalComponent component,
            Map<Long, SkyIslandHydraulicReachSkeleton> skeletons,
            SkyIslandSemanticField originalTerrain,
            Map<Long, SkyIslandHydraulicReachGeometry> realizedByIdentity,
            Map<Long, SkyIslandSemanticField> coupledTerrainByIdentity) {
        String terminalNodeId = Integer.toString(component.terminalFate().channelTerminalCellIndex());
        Map<String, SkyIslandHydraulicReachAssembly> assembliesById = new TreeMap<>();
        Map<String, SkyIslandHydraulicReachSkeleton> skeletonsById = new TreeMap<>();
        List<SkyIslandGameScaleDrainageNetworkModel.ReachInput> inputs = new ArrayList<>();
        for (SkyIslandHydraulicReachAssembly assembly : component.reaches()) {
            SkyIslandSemanticChannelReach semantic = assembly.semanticReach();
            SkyIslandHydraulicReachSkeleton skeleton = requireSkeleton(skeletons, semantic);
            String id = networkReachId(semantic);
            var routeMetrics = skeleton.geomorphicRoute().route();
            var route = new SkyIslandGeomorphicCandidateRoute(
                    skeleton.centerline().points(),
                    routeMetrics.totalCost(),
                    skeleton.centerline().pathLength(),
                    routeMetrics.maxGuidanceDeviation(),
                    routeMetrics.uphillStepFraction(),
                    routeMetrics.ridgeSampleFraction(),
                    routeMetrics.meanValleyFloorAdvantage(),
                    routeMetrics.maxUphillStep());
            var configuration = SkyIslandGameScaleHydraulicModel.networkCandidateConfiguration(
                    descriptor, skeleton, originalTerrain);
            var candidateReach = new SkyIslandJointNetworkCandidateBuilder.ReachInput(
                    id,
                    Integer.toString(semantic.startCellIndex()),
                    Integer.toString(semantic.endCellIndex()),
                    route,
                    configuration);
            double[] relativeDischarge = skeleton.samples().stream()
                    .mapToDouble(SkyIslandHydraulicGeometrySkeletonSample::relativeDischarge)
                    .toArray();
            int intervalCount = relativeDischarge.length - 1;
            inputs.add(new SkyIslandGameScaleDrainageNetworkModel.ReachInput(
                    candidateReach,
                    relativeDischarge,
                    new double[intervalCount],
                    new double[intervalCount],
                    0.0,
                    // HEC-RAS gives 0.1/0.3 as typical small subcritical contraction/expansion
                    // coefficients; channel sections are otherwise constant within each reach.
                    new SkyIslandJunctionHydraulicInputAdapter.LossCoefficients(0.1, 0.3)));
            assembliesById.put(id, assembly);
            skeletonsById.put(id, skeleton);
        }
        double terminalRelativeDischarge = inputs.stream()
                .filter(input -> input.candidateReach().downstreamNodeId().equals(terminalNodeId))
                .mapToDouble(input -> input.relativeDischargeAtStations()[
                        input.relativeDischargeAtStations().length - 1])
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "qualified edge outlet component has no reach ending at its terminal node"));
        var solution = SkyIslandGameScaleDrainageNetworkModel.solve(
                inputs, terminalNodeId, terminalRelativeDischarge, originalTerrain);
        // HEC-RAS solves energy between the computational sections bounding a junction; the
        // mixing region itself is not a 1D section. For terrain realization only, continue the
        // receiving water-surface grade to the shared node, then bridge each tributary boundary to
        // that common stage. The bridge adds no hydraulic energy solve and is requalified by D2.
        Map<Integer, Double> stageAtJunctionNode = new TreeMap<>();
        for (var plan : solution.junctionSectionsByNodeId().values()) {
            var receiverGeometry = solution.terrainCandidate().reachesById().get(plan.receivingReachId());
            var receiverSlice = solution.reachSlicesById().get(plan.receivingReachId());
            var receiverProfile = solution.hydraulicProfile().reachesById().get(plan.receivingReachId());
            if (receiverGeometry == null || receiverSlice == null || receiverProfile == null) {
                throw new IllegalStateException("network solve omitted a planned receiving reach");
            }
            double[] stations = receiverGeometry.stationsMetres();
            double[] beds = receiverGeometry.bedElevationsMetres();
            double[] depths = receiverProfile.depthsMetres();
            if (depths.length < 2 || receiverSlice.firstStationIndex() + 1 > receiverSlice.lastStationIndex()) {
                throw new IllegalArgumentException(
                        "receiver section must contain two solved stations to bridge the junction gap");
            }
            int first = receiverSlice.firstStationIndex();
            double firstStage = beds[first] + depths[0];
            double nextStage = beds[first + 1] + depths[1];
            double grade = (nextStage - firstStage) / (stations[first + 1] - stations[first]);
            double nodeStage = firstStage - grade * stations[first];
            if (!Double.isFinite(nodeStage) || nodeStage <= beds[0]) {
                throw new IllegalArgumentException(
                        "receiver-profile continuation does not leave positive water depth at the shared junction bed");
            }
            if (stageAtJunctionNode.put(Integer.parseInt(plan.nodeId()), nodeStage) != null) {
                throw new IllegalArgumentException("duplicate solved stage for semantic junction node");
            }
        }
        for (Map.Entry<String, SkyIslandHydraulicReachAssembly> entry : assembliesById.entrySet()) {
            String id = entry.getKey();
            SkyIslandHydraulicReachAssembly assembly = entry.getValue();
            SkyIslandHydraulicReachSkeleton skeleton = skeletonsById.get(id);
            SkyIslandHydraulicReachGeometry geometry = reconstructNetworkReach(
                    descriptor, assembly, skeleton, originalTerrain, solution, stageAtJunctionNode);
            long identity = assembly.identity();
            if (realizedByIdentity.put(identity, geometry) != null) {
                throw new IllegalStateException("F4A network component repeated a reach identity");
            }
            SkyIslandSemanticField surface = position ->
                    solution.terrainCandidate().sampleTerrainMetres(position) / descriptor.reliefBudget();
            if (coupledTerrainByIdentity.put(identity, surface) != null) {
                throw new IllegalStateException("F4A network component repeated a terrain identity");
            }
        }
    }

    private static SkyIslandHydraulicReachGeometry reconstructNetworkReach(
            SkyIslandDescriptor descriptor,
            SkyIslandHydraulicReachAssembly assembly,
            SkyIslandHydraulicReachSkeleton skeleton,
            SkyIslandSemanticField originalTerrain,
            SkyIslandGameScaleDrainageNetworkModel.NetworkSolution solution,
            Map<Integer, Double> stageAtJunctionNode) {
        String id = networkReachId(assembly.semanticReach());
        var candidate = solution.terrainCandidate().reachesById().get(id);
        var slice = solution.reachSlicesById().get(id);
        var profile = solution.hydraulicProfile().reachesById().get(id);
        var flows = solution.massBalancedFlows().reachesById().get(id);
        if (candidate == null || slice == null || profile == null || flows == null) {
            throw new IllegalStateException("network solution is missing a reach realization input");
        }
        double[] stations = candidate.stationsMetres();
        double[] beds = candidate.bedElevationsMetres();
        double[] depths = profile.depthsMetres();
        double[] stage = new double[beds.length];
        int first = slice.firstStationIndex();
        int last = slice.lastStationIndex();
        for (int index = first; index <= last; index++) {
            stage[index] = beds[index] + depths[index - first];
        }
        int startNode = assembly.semanticReach().startCellIndex();
        if (first > 0) {
            Double nodeStage = stageAtJunctionNode.get(startNode);
            if (nodeStage == null) {
                throw new IllegalArgumentException("receiver reach begins at an unmodeled junction");
            }
            for (int index = 0; index < first; index++) {
                double fraction = stations[index] / stations[first];
                stage[index] = nodeStage + fraction * (stage[first] - nodeStage);
            }
        }
        int endNode = assembly.semanticReach().endCellIndex();
        if (last < beds.length - 1) {
            Double nodeStage = stageAtJunctionNode.get(endNode);
            if (nodeStage == null) {
                throw new IllegalArgumentException("tributary reach ends at an unmodeled junction");
            }
            for (int index = last + 1; index < beds.length; index++) {
                double fraction = (stations[index] - stations[last]) / (stations[beds.length - 1] - stations[last]);
                stage[index] = stage[last] + fraction * (nodeStage - stage[last]);
            }
        }
        double verticalScale = descriptor.reliefBudget();
        List<SkyIslandHydraulicGeometrySample> samples = new ArrayList<>(beds.length);
        double maximumLowering = 0.0;
        double totalLowering = 0.0;
        double maximumSlope = 0.0;
        double maximumDepth = 0.0;
        for (int i = 0; i < beds.length; i++) {
            double normalizedBed = beds[i] / verticalScale;
            double depth = stage[i] - beds[i];
            double normalizedDepth = depth / verticalScale;
            double normalizedStage = stage[i] / verticalScale;
            double terrain = originalTerrain.sample(skeleton.centerline().points().get(i));
            requireNormalized(normalizedBed, "network candidate bed");
            requireNormalized(normalizedDepth, "network water depth");
            requireNormalized(normalizedStage, "network water surface");
            if (!(depth > 0.0)) {
                throw new IllegalArgumentException("network junction bridge crosses or exposes the candidate bed");
            }
            double lowering = Math.max(0.0, terrain - normalizedBed);
            var source = skeleton.samples().get(i);
            samples.add(new SkyIslandHydraulicGeometrySample(
                    source.position(),
                    source.stationFraction(),
                    source.relativeDischarge(),
                    skeleton.maximumBankfullHalfWidth(),
                    normalizedDepth,
                    terrain,
                    normalizedStage,
                    normalizedBed,
                    lowering));
            maximumLowering = Math.max(maximumLowering, lowering);
            totalLowering += lowering;
            maximumDepth = Math.max(maximumDepth, normalizedDepth);
            if (i > 0) {
                double ds = source.arcLength() - skeleton.samples().get(i - 1).arcLength();
                if (!(ds > 0.0)) {
                    throw new IllegalStateException("network centerline stationing must increase");
                }
                maximumSlope = Math.max(maximumSlope,
                        Math.abs(normalizedStage - samples.get(i - 1).waterSurfacePotential()) / ds);
            }
        }
        return new SkyIslandHydraulicReachGeometry(
                skeleton.geomorphicRoute(),
                skeleton.centerline(),
                samples,
                skeleton.pathLength(),
                maximumLowering,
                totalLowering / samples.size(),
                maximumSlope,
                skeleton.maximumBankfullHalfWidth(),
                maximumDepth);
    }

    private static String networkReachId(SkyIslandSemanticChannelReach semantic) {
        return semantic.startCellIndex() + "->" + semantic.endCellIndex();
    }

    private static SkyIslandHydraulicReachGeometry reconstructCoupledReach(
            SkyIslandDescriptor descriptor,
            SkyIslandHydraulicReachSkeleton skeleton,
            SkyIslandOrdinarySpanOutcome spanOutcome,
            SkyIslandSemanticField originalTerrain,
            SkyIslandGameScaleHydraulicModel.SourceReachSolution solution) {
        if (spanOutcome.status() != SkyIslandOrdinarySpanStatus.SOLVED_QUALIFIED
                || Math.abs(spanOutcome.span().parentStartStationFraction()) > EPSILON
                || Math.abs(spanOutcome.span().parentEndStationFraction() - 1.0) > EPSILON) {
            throw new IllegalArgumentException(
                    "F4A reconstructs only full SOLVED_QUALIFIED ordinary spans");
        }
        List<SkyIslandHydraulicGeometrySample> previousSamples = spanOutcome.solvedSamples();
        List<SkyIslandHydraulicGeometrySkeletonSample> skeletonSamples = skeleton.samples();
        double[] beds = solution.profile().bedElevationsMetres();
        double[] depths = solution.profile().depthsMetres();
        double[] stages = solution.profile().waterSurfaceElevationsMetres();
        if (previousSamples.size() != skeletonSamples.size()
                || beds.length != skeletonSamples.size()
                || depths.length != skeletonSamples.size()
                || stages.length != skeletonSamples.size()) {
            throw new IllegalStateException(
                    "coupled hydraulic profile must preserve complete accepted C2 station geometry");
        }

        double verticalScale = descriptor.reliefBudget();
        double maximumHalfWidth = skeleton.maximumBankfullHalfWidth();
        List<SkyIslandHydraulicGeometrySample> samples =
                new ArrayList<>(skeletonSamples.size());
        double maximumLowering = 0.0;
        double totalLowering = 0.0;
        double maximumSlope = 0.0;
        double maximumDepthPotential = 0.0;
        for (int i = 0; i < skeletonSamples.size(); i++) {
            SkyIslandHydraulicGeometrySkeletonSample source = skeletonSamples.get(i);
            SkyIslandHydraulicGeometrySample prior = previousSamples.get(i);
            if (!prior.position().equals(source.position())
                    || Math.abs(prior.stationFraction() - source.stationFraction()) > EPSILON
                    || Math.abs(prior.relativeDischarge() - source.relativeDischarge()) > EPSILON
                    || Math.abs(prior.bankfullHalfWidth() - source.bankfullHalfWidth()) > EPSILON
                    || Math.abs(prior.waterDepthPotential() - source.waterDepthPotential()) > EPSILON) {
                throw new IllegalStateException(
                        "F3E-qualified span must preserve accepted C2 sample geometry");
            }

            double terrain = originalTerrain.sample(source.position());
            double bed = beds[i] / verticalScale;
            double depth = depths[i] / verticalScale;
            double stage = stages[i] / verticalScale;
            requireNormalized(bed, "candidate bed");
            requireNormalized(depth, "solved water depth");
            requireNormalized(stage, "candidate water surface");
            if (!(stage > bed) || !(depth > 0.0)) {
                throw new IllegalStateException("coupled profile must keep positive water depth above its bed");
            }
            double lowering = Math.max(0.0, terrain - bed);
            SkyIslandHydraulicGeometrySample sample = new SkyIslandHydraulicGeometrySample(
                    source.position(),
                    source.stationFraction(),
                    source.relativeDischarge(),
                    maximumHalfWidth,
                    depth,
                    terrain,
                    stage,
                    bed,
                    lowering);
            samples.add(sample);
            maximumLowering = Math.max(maximumLowering, lowering);
            totalLowering += lowering;
            maximumDepthPotential = Math.max(maximumDepthPotential, depth);
            if (i > 0) {
                double ds = source.arcLength() - skeletonSamples.get(i - 1).arcLength();
                if (!(ds > 0.0)) {
                    throw new IllegalStateException("accepted C2 skeleton arc length must increase strictly");
                }
                maximumSlope = Math.max(maximumSlope, Math.abs(stage - samples.get(i - 1).waterSurfacePotential()) / ds);
            }
        }

        return new SkyIslandHydraulicReachGeometry(
                skeleton.geomorphicRoute(),
                skeleton.centerline(),
                samples,
                skeleton.pathLength(),
                maximumLowering,
                totalLowering / samples.size(),
                maximumSlope,
                maximumHalfWidth,
                maximumDepthPotential);
    }

    private static void requireNormalized(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalStateException(
                    name + " value " + value + " is outside authored relief normalization [0, 1]");
        }
    }

    private static Map<Long, SkyIslandHydraulicReachSkeleton> indexSkeletons(
            List<SkyIslandHydraulicReachSkeleton> reaches) {
        Map<Long, SkyIslandHydraulicReachSkeleton> result = new HashMap<>();
        for (SkyIslandHydraulicReachSkeleton reach : reaches) {
            SkyIslandSemanticChannelReach semantic =
                    reach.geomorphicRoute().semanticReach();
            long identity = identity(
                    semantic.startCellIndex(), semantic.endCellIndex());
            if (result.put(identity, reach) != null) {
                throw new IllegalStateException(
                        "duplicate accepted C2 hydraulic skeleton identity");
            }
        }
        return Map.copyOf(result);
    }

    private static SkyIslandHydraulicReachSkeleton requireSkeleton(
            Map<Long, SkyIslandHydraulicReachSkeleton> skeletons,
            SkyIslandSemanticChannelReach semantic) {
        SkyIslandHydraulicReachSkeleton skeleton =
                skeletons.get(identity(
                        semantic.startCellIndex(), semantic.endCellIndex()));
        if (skeleton == null) {
            throw new IllegalStateException(
                    "missing accepted C2 skeleton for realized component reach");
        }
        return skeleton;
    }

    private static long identity(int start, int end) {
        return ((long) start << 32) ^ Integer.toUnsignedLong(end);
    }
}
