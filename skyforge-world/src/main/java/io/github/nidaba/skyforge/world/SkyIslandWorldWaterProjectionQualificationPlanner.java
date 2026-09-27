package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Builds F4E component-level admission for the direct F4A/F4B world-water projection. */
public final class SkyIslandWorldWaterProjectionQualificationPlanner {
    private SkyIslandWorldWaterProjectionQualificationPlanner() {}

    public static SkyIslandWorldWaterProjectionQualificationPlan plan(
            SkyIslandFluvialVoxelQuantizationPlan terrainVoxelPlan) {
        Objects.requireNonNull(terrainVoxelPlan, "terrainVoxelPlan");
        SkyIslandComponentFluvialTerrainCandidatePlan candidate =
                terrainVoxelPlan.candidatePlan();
        SkyIslandComponentFluvialWorldWaterProjection projection =
                new SkyIslandComponentFluvialWorldWaterProjection(
                        terrainVoxelPlan.association(), candidate);
        Map<Long, SkyIslandHydraulicReachGeometry> geometryByReach =
                geometryByReach(candidate.terrainField().acceptedReaches());
        SkyIslandGeomorphicQualificationPolicy policy =
                SkyIslandGeomorphicQualificationPolicy.firstEvidenceBacked();

        List<SkyIslandWorldWaterComponentQualification> outcomes =
                new ArrayList<>();
        for (SkyIslandFluvialVoxelComponentPlan terrainComponent :
                terrainVoxelPlan.components()) {
            SkyIslandHydraulicTerminalComponent component =
                    terrainComponent.component();
            if (terrainComponent.status()
                    != SkyIslandFluvialVoxelComponentStatus.QUALIFIED) {
                outcomes.add(new SkyIslandWorldWaterComponentQualification(
                        component,
                        SkyIslandWorldWaterComponentStatus.TERRAIN_REJECTED,
                        List.of(),
                        List.of("F4C terrain voxel component is physically rejected")));
                continue;
            }

            List<SkyIslandWorldWaterReachQualification> reaches =
                    new ArrayList<>();
            List<String> blockers = new ArrayList<>();
            for (SkyIslandHydraulicReachAssembly reachAssembly :
                    component.reaches()) {
                SkyIslandHydraulicReachGeometry geometry =
                        geometryByReach.get(reachAssembly.identity());
                if (geometry == null) {
                    blockers.add(
                            "missing F4A realized geometry for reach "
                                    + reachAssembly.semanticReach().startCellIndex()
                                    + "->"
                                    + reachAssembly.semanticReach().endCellIndex());
                    continue;
                }
                SkyIslandWorldWaterReachQualification qualification =
                        SkyIslandWorldWaterReachQualificationEvaluator.evaluate(
                                SkyIslandWorldWaterReachDiagnosticsPlanner.measure(
                                        projection, geometry),
                                policy);
                reaches.add(qualification);
                if (!qualification.accepted()) {
                    blockers.add(
                            "reach "
                                    + reachAssembly.semanticReach().startCellIndex()
                                    + "->"
                                    + reachAssembly.semanticReach().endCellIndex()
                                    + " direct world-water projection violates "
                                    + qualification.violations());
                }
            }

            SkyIslandWorldWaterComponentStatus status =
                    blockers.isEmpty()
                            ? SkyIslandWorldWaterComponentStatus.QUALIFIED
                            : SkyIslandWorldWaterComponentStatus.HEAD_REFINEMENT_REQUIRED;
            outcomes.add(new SkyIslandWorldWaterComponentQualification(
                    component, status, reaches, blockers));
        }

        outcomes.sort(Comparator.comparingInt(
                SkyIslandWorldWaterComponentQualification::terminalCellIndex));
        return new SkyIslandWorldWaterProjectionQualificationPlan(
                terrainVoxelPlan, outcomes);
    }

    private static Map<Long, SkyIslandHydraulicReachGeometry> geometryByReach(
            List<SkyIslandHydraulicReachGeometry> reaches) {
        Map<Long, SkyIslandHydraulicReachGeometry> result = new HashMap<>();
        for (SkyIslandHydraulicReachGeometry reach : reaches) {
            SkyIslandSemanticChannelReach semantic =
                    reach.geomorphicRoute().semanticReach();
            long identity =
                    ((long) semantic.startCellIndex() << 32)
                            ^ Integer.toUnsignedLong(semantic.endCellIndex());
            if (result.put(identity, reach) != null) {
                throw new IllegalStateException(
                        "duplicate F4A realized reach identity");
            }
        }
        return Map.copyOf(result);
    }
}
