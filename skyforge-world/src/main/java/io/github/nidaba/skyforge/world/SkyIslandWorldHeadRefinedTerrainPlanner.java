package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Builds F4G continuous cross-section evidence from solved F4F centerline corrections. */
public final class SkyIslandWorldHeadRefinedTerrainPlanner {
    private static final double EPSILON = 1.0e-9;

    private SkyIslandWorldHeadRefinedTerrainPlanner() {}

    public static SkyIslandWorldHeadRefinedTerrainPlan plan(
            SkyIslandWorldWaterHeadRefinementPlan refinement) {
        Objects.requireNonNull(refinement, "refinement");

        SkyIslandWorldWaterProjectionQualificationPlan direct =
                refinement.directQualification();
        SkyIslandComponentFluvialTerrainCandidatePlan candidate =
                direct.terrainVoxelPlan().candidatePlan();
        SkyIslandDescriptor descriptor = candidate.descriptor();
        double relief = descriptor.reliefBudget();
        if (!Double.isFinite(relief) || relief <= 0.0) {
            throw new IllegalStateException("F4G requires positive authored relief budget");
        }

        Map<Long, SkyIslandWorldWaterHeadRefinementReach> solvedByIdentity =
                solvedRefinements(refinement);
        Set<Long> refinedIdentities = new HashSet<>();
        List<SkyIslandHydraulicReachGeometry> reaches = new ArrayList<>();

        for (SkyIslandHydraulicReachGeometry original :
                candidate.terrainField().acceptedReaches()) {
            long identity = identity(original);
            SkyIslandWorldWaterHeadRefinementReach solved =
                    solvedByIdentity.get(identity);
            if (solved == null) {
                reaches.add(original);
                continue;
            }
            SkyIslandHydraulicReachGeometry refined =
                    refineReach(original, solved, relief);
            reaches.add(refined);
            refinedIdentities.add(identity);
        }

        reaches.sort(Comparator
                .comparingInt((SkyIslandHydraulicReachGeometry reach) ->
                        reach.geomorphicRoute().semanticReach().startCellIndex())
                .thenComparingInt(reach ->
                        reach.geomorphicRoute().semanticReach().endCellIndex()));

        SkyIslandPreHydrologicTerrainField originalTerrain =
                SkyIslandPreHydrologicTerrainField.create(descriptor);
        SkyIslandQualifiedFluvialTerrainField field =
                new SkyIslandQualifiedFluvialTerrainField(originalTerrain, reaches);

        SkyIslandGeomorphicQualificationPolicy policy =
                SkyIslandGeomorphicQualificationPolicy.firstEvidenceBacked();
        double planningSpacing =
                candidate.assemblyPlan()
                        .ordinarySpanPlan()
                        .cascadePlan()
                        .transitionGeometry()
                        .topology()
                        .skeletonPlan()
                        .geomorphicNetwork()
                        .planningSpacing();

        List<SkyIslandGeomorphicReachQualification> qualifications =
                new ArrayList<>();
        for (SkyIslandHydraulicReachGeometry reach : reaches) {
            SkyIslandGeomorphicReachDiagnostics diagnostics =
                    SkyIslandGeomorphicReachDiagnosticsPlanner.measureReach(
                            descriptor, reach, field, planningSpacing);
            SkyIslandGeomorphicReachQualification qualification =
                    SkyIslandGeomorphicQualificationEvaluator.evaluate(
                            diagnostics, policy);
            if (!qualification.accepted()) {
                SkyIslandSemanticChannelReach semantic =
                        reach.geomorphicRoute().semanticReach();
                throw new IllegalStateException(
                        "F4G refined terrain violates D2 for reach "
                                + semantic.startCellIndex()
                                + "->"
                                + semantic.endCellIndex()
                                + ": "
                                + qualification.violations());
            }
            qualifications.add(qualification);
        }

        return new SkyIslandWorldHeadRefinedTerrainPlan(
                refinement,
                reaches,
                refinedIdentities,
                qualifications,
                field);
    }

    private static SkyIslandHydraulicReachGeometry refineReach(
            SkyIslandHydraulicReachGeometry original,
            SkyIslandWorldWaterHeadRefinementReach solved,
            double relief) {
        if (solved.status() != SkyIslandWorldWaterHeadRefinementStatus.SOLVED) {
            throw new IllegalArgumentException("F4G may consume only solved F4F reach evidence");
        }
        if (solved.reach() != original && !solved.reach().equals(original)) {
            throw new IllegalArgumentException("F4F solved reach differs from F4A source geometry");
        }
        if (solved.samples().size() != original.samples().size()) {
            throw new IllegalArgumentException("F4F sample count differs from F4A reach");
        }

        List<SkyIslandHydraulicGeometrySample> samples =
                new ArrayList<>(original.samples().size());
        double maximumLowering = 0.0;
        double totalLowering = 0.0;
        double maximumSlope = 0.0;

        for (int i = 0; i < original.samples().size(); i++) {
            SkyIslandHydraulicGeometrySample source = original.samples().get(i);
            SkyIslandWorldWaterHeadRefinementSample correction =
                    solved.samples().get(i);
            if (!source.position().equals(correction.localPosition())) {
                throw new IllegalStateException("F4F correction position differs from F4A sample");
            }

            double raisePotential = correction.terrainRaiseWorld() / relief;
            double water = source.waterSurfacePotential() + raisePotential;
            double bed = source.bedElevationPotential() + raisePotential;
            if (water > 1.0 + EPSILON || bed > 1.0 + EPSILON) {
                throw new IllegalStateException("F4G refined geometry escaped authored vertical domain");
            }
            water = Math.min(1.0, water);
            bed = Math.min(1.0, bed);
            double depthBefore = source.waterSurfacePotential() - source.bedElevationPotential();
            double depthAfter = water - bed;
            if (Math.abs(depthBefore - depthAfter) > EPSILON) {
                throw new IllegalStateException("F4G changed accepted hydraulic depth");
            }

            double lowering = Math.max(0.0, source.terrainElevation() - bed);
            maximumLowering = Math.max(maximumLowering, lowering);
            totalLowering += lowering;

            if (i > 0) {
                double ds = Math.hypot(
                        source.position().x() - original.samples().get(i - 1).position().x(),
                        source.position().z() - original.samples().get(i - 1).position().z());
                if (!(ds > 0.0)) {
                    throw new IllegalStateException("F4G centerline spacing must be positive");
                }
                maximumSlope = Math.max(
                        maximumSlope,
                        Math.abs(water - samples.get(i - 1).waterSurfacePotential()) / ds);
            }

            samples.add(new SkyIslandHydraulicGeometrySample(
                    source.position(),
                    source.stationFraction(),
                    source.relativeDischarge(),
                    source.bankfullHalfWidth(),
                    source.waterDepthPotential(),
                    source.terrainElevation(),
                    water,
                    bed,
                    lowering));
        }

        return new SkyIslandHydraulicReachGeometry(
                original.geomorphicRoute(),
                original.centerline(),
                samples,
                original.pathLength(),
                maximumLowering,
                totalLowering / samples.size(),
                maximumSlope,
                original.maximumBankfullHalfWidth(),
                original.maximumWaterDepthPotential());
    }

    private static Map<Long, SkyIslandWorldWaterHeadRefinementReach> solvedRefinements(
            SkyIslandWorldWaterHeadRefinementPlan refinement) {
        Map<Long, SkyIslandWorldWaterHeadRefinementReach> result = new HashMap<>();
        for (SkyIslandWorldWaterHeadRefinementComponent component :
                refinement.solvedComponents()) {
            for (SkyIslandWorldWaterHeadRefinementReach reach : component.reaches()) {
                long identity = identity(reach.reach());
                if (result.put(identity, reach) != null) {
                    throw new IllegalStateException("F4G repeated a solved reach refinement");
                }
            }
        }
        return Map.copyOf(result);
    }

    private static long identity(SkyIslandHydraulicReachGeometry reach) {
        SkyIslandSemanticChannelReach semantic =
                reach.geomorphicRoute().semanticReach();
        return ((long) semantic.startCellIndex() << 32)
                ^ Integer.toUnsignedLong(semantic.endCellIndex());
    }
}
