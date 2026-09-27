package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.recipes.skyisland.SemanticSkyIslandVolumeRecipe;
import org.junit.jupiter.api.Test;

class SkyIslandWorldHeadRefinedTerrainPlannerTest {
    private static final long SEED = 0x534B59464F524745L;
    private static final long REALIZATION_ROOT = 0x5245414C495A4552L;
    private static final double EPSILON = 1.0e-9;

    @Test
    void ordinary77RefinesOnlySolvedComponentAndNeverDeepensTerrain() {
        SkyIslandDescriptor descriptor = descriptor(8L, 81L, 77L);
        SkyIslandComponentFluvialTerrainCandidatePlan candidate =
                SkyIslandComponentFluvialTerrainCandidatePlanner.plan(descriptor);
        SkyIslandAuthoredRealizationAssociation association =
                productionAssociation(descriptor, 910_077L);
        SkyIslandFluvialVoxelQuantizationPlan voxel =
                SkyIslandFluvialVoxelQuantizationPlanner.plan(association, candidate);
        SkyIslandWorldWaterProjectionQualificationPlan direct =
                SkyIslandWorldWaterProjectionQualificationPlanner.plan(voxel);
        SkyIslandWorldWaterHeadRefinementPlan head =
                SkyIslandWorldWaterHeadRefinementPlanner.plan(direct);
        SkyIslandWorldHeadRefinedTerrainPlan refined =
                SkyIslandWorldHeadRefinedTerrainPlanner.plan(head);

        assertEquals(1, refined.refinedReachCount());
        assertEquals(2, refined.reaches().size());
        assertEquals(2, refined.postRefinementQualifications().size());
        assertTrue(refined.postRefinementQualifications().stream()
                .allMatch(SkyIslandGeomorphicReachQualification::accepted));

        SkyIslandHydraulicReachGeometry originalSolved =
                reach(candidate, 709, 559);
        SkyIslandHydraulicReachGeometry refinedSolved =
                reach(refined, 709, 559);
        boolean raised = false;
        for (int i = 0; i < originalSolved.samples().size(); i++) {
            SkyIslandHydraulicGeometrySample a = originalSolved.samples().get(i);
            SkyIslandHydraulicGeometrySample b = refinedSolved.samples().get(i);
            assertTrue(b.waterSurfacePotential() + EPSILON >= a.waterSurfacePotential());
            assertTrue(b.bedElevationPotential() + EPSILON >= a.bedElevationPotential());
            assertTrue(b.requiredCenterlineLowering() <= a.requiredCenterlineLowering() + EPSILON);
            assertEquals(
                    a.waterSurfacePotential() - a.bedElevationPotential(),
                    b.waterSurfacePotential() - b.bedElevationPotential(),
                    EPSILON);
            if (b.bedElevationPotential() > a.bedElevationPotential() + EPSILON) {
                raised = true;
            }
        }
        assertTrue(raised);

        assertEquals(
                reach(candidate, 1742, 1842),
                reach(refined, 1742, 1842));

        double centerX =
                association.realizedVolume().compiledVolume().descriptor().centerX();
        double centerZ =
                association.realizedVolume().compiledVolume().descriptor().centerZ();
        int shallower = 0;
        double maximumRecoveryWorld = 0.0;
        for (SkyIslandFluvialVoxelColumn column : voxel.authorizedColumns()) {
            SkyIslandLocalPosition local =
                    new SkyIslandLocalPosition(
                            column.worldX() - centerX,
                            column.worldZ() - centerZ);
            SkyIslandQualifiedFluvialSample before =
                    candidate.terrainField().sampleDetailed(local);
            SkyIslandQualifiedFluvialSample after =
                    refined.terrainField().sampleDetailed(local);
            assertTrue(
                    after.targetTerrainPotential() + EPSILON
                            >= before.targetTerrainPotential());
            assertTrue(
                    after.targetTerrainPotential()
                            <= after.originalTerrainPotential() + EPSILON);
            double recoveryWorld =
                    (after.targetTerrainPotential() - before.targetTerrainPotential())
                            * descriptor.reliefBudget();
            assertTrue(recoveryWorld >= -EPSILON);
            maximumRecoveryWorld = Math.max(maximumRecoveryWorld, recoveryWorld);
            if (recoveryWorld > EPSILON) {
                shallower++;
            }
        }
        assertTrue(shallower > 0);
        assertTrue(maximumRecoveryWorld <= 0.168878403 + EPSILON);
    }

    private static SkyIslandHydraulicReachGeometry reach(
            SkyIslandComponentFluvialTerrainCandidatePlan plan,
            int start,
            int end) {
        return plan.terrainField().acceptedReaches().stream()
                .filter(reach -> {
                    var semantic = reach.geomorphicRoute().semanticReach();
                    return semantic.startCellIndex() == start
                            && semantic.endCellIndex() == end;
                })
                .findFirst()
                .orElseThrow();
    }

    private static SkyIslandHydraulicReachGeometry reach(
            SkyIslandWorldHeadRefinedTerrainPlan plan,
            int start,
            int end) {
        return plan.reaches().stream()
                .filter(reach -> {
                    var semantic = reach.geomorphicRoute().semanticReach();
                    return semantic.startCellIndex() == start
                            && semantic.endCellIndex() == end;
                })
                .findFirst()
                .orElseThrow();
    }

    private static SkyIslandAuthoredRealizationAssociation productionAssociation(
            SkyIslandDescriptor descriptor,
            long geometrySeed) {
        double radius = descriptor.nominalRadius();
        double centerX = 1200.0;
        double centerZ = -900.0;
        SkyIslandVolumeDescriptor physical =
                SkyIslandVolumeDescriptor.schema2(
                        geometrySeed,
                        centerX,
                        centerZ,
                        256.0,
                        radius,
                        72.0,
                        104.0,
                        Math.min(32.0, radius),
                        0.43,
                        0.62,
                        0.57,
                        0.18,
                        descriptor.morphologyFamily(),
                        0.22,
                        38.0,
                        0.31);
        CompiledSkyIslandVolume compiled =
                new SemanticSkyIslandVolumeRecipe().compile(physical);
        SkyIslandWorldVolume volume =
                new SkyIslandWorldVolume(
                        new SkyIslandWorldVolumeId(
                                REALIZATION_ROOT,
                                "f4g-test",
                                0,
                                0,
                                geometrySeed),
                        new WorldBounds(
                                centerX - radius,
                                centerX + radius,
                                64.0,
                                448.0,
                                centerZ - radius,
                                centerZ + radius),
                        compiled);
        return SkyIslandAuthoredRealizationAssociation.of(descriptor, volume);
    }

    private static SkyIslandDescriptor descriptor(long province, long cluster, long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, province, cluster, key));
    }
}
