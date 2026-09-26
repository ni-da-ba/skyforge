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
 * <p>F4A deliberately supports only complete ordinary reaches with no confluence or CASCADE terrain
 * ownership. It reconstructs hydraulic geometry from F3D solved samples and accepted C2 skeletons,
 * then re-applies D2 after the cross-section terrain response is composed.
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
                SkyIslandHydraulicReachGeometry geometry =
                        reconstructFullReach(skeleton, span);
                long identity = reachAssembly.identity();
                if (realizedByIdentity.put(identity, geometry) != null) {
                    throw new IllegalStateException(
                            "F4A component selection repeated reach "
                                    + reachAssembly.semanticReach().startCellIndex()
                                    + "->"
                                    + reachAssembly.semanticReach().endCellIndex());
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
                new SkyIslandQualifiedFluvialTerrainField(original, realizedReaches);

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

    private static SkyIslandHydraulicReachGeometry reconstructFullReach(
            SkyIslandHydraulicReachSkeleton skeleton,
            SkyIslandOrdinarySpanOutcome spanOutcome) {
        SkyIslandOrdinaryHydraulicSpan span = spanOutcome.span();
        if (spanOutcome.status() != SkyIslandOrdinarySpanStatus.SOLVED_QUALIFIED
                || Math.abs(span.parentStartStationFraction()) > EPSILON
                || Math.abs(span.parentEndStationFraction() - 1.0) > EPSILON) {
            throw new IllegalArgumentException(
                    "F4A reconstructs only full SOLVED_QUALIFIED ordinary spans");
        }

        List<SkyIslandHydraulicGeometrySample> samples =
                spanOutcome.solvedSamples();
        if (samples.size() != skeleton.samples().size()) {
            throw new IllegalStateException(
                    "F3D full-span samples must match accepted C2 skeleton sample count");
        }

        double maximumLowering = 0.0;
        double totalLowering = 0.0;
        double maximumSlope = 0.0;
        for (int i = 0; i < samples.size(); i++) {
            SkyIslandHydraulicGeometrySample sample = samples.get(i);
            SkyIslandHydraulicGeometrySkeletonSample source =
                    skeleton.samples().get(i);
            if (!sample.position().equals(source.position())
                    || Math.abs(sample.stationFraction() - source.stationFraction())
                            > EPSILON
                    || Math.abs(sample.relativeDischarge() - source.relativeDischarge())
                            > EPSILON
                    || Math.abs(sample.bankfullHalfWidth() - source.bankfullHalfWidth())
                            > EPSILON
                    || Math.abs(sample.waterDepthPotential() - source.waterDepthPotential())
                            > EPSILON) {
                throw new IllegalStateException(
                        "F3D full-span hydraulic solution must preserve accepted C2 sample geometry");
            }
            maximumLowering =
                    Math.max(maximumLowering, sample.requiredCenterlineLowering());
            totalLowering += sample.requiredCenterlineLowering();

            if (i > 0) {
                double ds =
                        skeleton.samples().get(i).arcLength()
                                - skeleton.samples().get(i - 1).arcLength();
                if (!(ds > 0.0)) {
                    throw new IllegalStateException(
                            "accepted C2 skeleton arc length must increase strictly");
                }
                maximumSlope =
                        Math.max(
                                maximumSlope,
                                Math.abs(
                                                sample.waterSurfacePotential()
                                                        - samples.get(i - 1)
                                                                .waterSurfacePotential())
                                        / ds);
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
                skeleton.maximumWaterDepthPotential());
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
