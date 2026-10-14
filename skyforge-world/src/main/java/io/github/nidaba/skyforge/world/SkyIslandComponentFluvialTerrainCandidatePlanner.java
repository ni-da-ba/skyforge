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

/**
 * Builds the first F3E-component-gated continuous fluvial terrain candidate.
 *
 * <p>F4A deliberately supports only complete single-source ordinary reaches with no confluence or
 * CASCADE terrain ownership. It builds channel/valley candidates from accepted C2 geometry, solves
 * hydraulics against each candidate bed, then reapplies D2 to that same candidate terrain surface.
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

    static SkyIslandComponentFluvialTerrainCandidatePlan plan(
            SkyIslandDescriptor descriptor,
            SkyIslandHydraulicNetworkAssemblyPlan assemblyPlan,
            SkyIslandGeomorphicQualificationPolicy policy) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(assemblyPlan, "assemblyPlan");
        Objects.requireNonNull(policy, "policy");
        if (!descriptor.equals(assemblyPlan.descriptor())) {
            throw new IllegalArgumentException(
                    "F3E assembly descriptor must match F4A descriptor");
        }

        SkyIslandPreHydrologicTerrainField original =
                SkyIslandPreHydrologicTerrainField.create(descriptor);
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

            for (SkyIslandHydraulicReachAssembly reachAssembly : component.reaches()) {
                SkyIslandOrdinarySpanOutcome span =
                        reachAssembly.ordinarySpans().getFirst();
                SkyIslandHydraulicReachSkeleton skeleton =
                        requireSkeleton(skeletons, reachAssembly.semanticReach());
                long identity = reachAssembly.identity();
                SkyIslandGameScaleHydraulicModel.SourceReachSolution coupled =
                        SkyIslandGameScaleHydraulicModel.solveSourceReach(
                                descriptor, skeleton, original);
                SkyIslandHydraulicReachGeometry geometry = reconstructCoupledReach(
                        descriptor, skeleton, span, original, coupled);
                if (realizedByIdentity.put(identity, geometry) != null) {
                    throw new IllegalStateException(
                            "F4A component selection repeated reach "
                                    + reachAssembly.semanticReach().startCellIndex()
                                    + "->"
                                    + reachAssembly.semanticReach().endCellIndex());
                }
                if (coupledByIdentity.put(identity, coupled) != null) {
                    throw new IllegalStateException("F4A repeated coupled hydraulic candidate");
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

        SkyIslandQualifiedFluvialTerrainField terrainField =
                new SkyIslandQualifiedFluvialTerrainField(
                        original, realizedReaches, coupledByIdentity, descriptor.reliefBudget());

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
                                + qualification.violations());
            }
            postRealizationQualifications.add(qualification);
        }

        return new SkyIslandComponentFluvialTerrainCandidatePlan(
                descriptor,
                assemblyPlan,
                realizedComponents,
                deferredQualifiedComponents,
                postRealizationQualifications,
                terrainField);
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

        if (component.reaches().size() != 1) {
            reasons.add(
                    SkyIslandComponentFluvialTerrainDeferralReason
                            .MULTI_REACH_HYDRAULICS_REQUIRED);
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
            if (start.kind() != SkyIslandGeomorphicNetworkNodeKind.SOURCE
                    || end.kind() != SkyIslandGeomorphicNetworkNodeKind.TERMINAL) {
                reasons.add(
                        SkyIslandComponentFluvialTerrainDeferralReason
                                .SOURCE_BOUNDARY_REQUIRED);
            }
            if (start.kind() == SkyIslandGeomorphicNetworkNodeKind.CONFLUENCE
                    || end.kind() == SkyIslandGeomorphicNetworkNodeKind.CONFLUENCE) {
                reasons.add(
                        SkyIslandComponentFluvialTerrainDeferralReason
                                .CONFLUENCE_TERRAIN_REQUIRED);
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
            throw new IllegalStateException(name + " exceeds the authored relief normalization");
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
