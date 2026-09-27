package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.recipes.skyisland.SemanticSkyIslandVolumeRecipe;
import org.junit.jupiter.api.Test;

class SkyIslandWorldHeadRefinedVoxelPlannerTest {
    private static final long SEED = 0x534B59464F524745L;
    private static final long REALIZATION_ROOT = 0x5245414C495A4552L;
    private static final double EPSILON = 1.0e-9;

    @Test
    void ordinary77RefinedVoxelRemovalsAreStrictSubsetEvidence() {
        SkyIslandDescriptor descriptor = descriptor(8L, 81L, 77L);
        SkyIslandAuthoredRealizationAssociation association =
                productionAssociation(descriptor, 910_077L);
        SkyIslandWorldHeadRefinedVoxelPlan refined =
                refinedVoxelPlan(descriptor, association);

        assertEquals(2, refined.components().size());
        assertEquals(8969, refined.columns().size());
        assertEquals(15849, refined.directRemovedSolidBlocks());
        assertTrue(refined.refinedRemovedSolidBlocks()
                <= refined.directRemovedSolidBlocks());
        assertTrue(refined.returnedSolidBlocks() >= 0);

        for (SkyIslandWorldHeadRefinedVoxelColumn column : refined.columns()) {
            assertTrue(column.refinedTargetMaximumSolidY()
                    >= column.directColumn().targetMaximumSolidY());
            assertTrue(column.refinedRemovedSolidBlocks()
                    <= column.directColumn().removedSolidBlocks());
            assertTrue(column.returnedSolidBlocks() >= 0);
            assertTrue(column.refinedProjection().targetUpperSurfaceWorldY() + EPSILON
                    >= column.directColumn().projection().targetUpperSurfaceWorldY());
            assertTrue(column.refinedUndercutResidualWorld() >= -EPSILON);
            assertTrue(column.refinedUndercutResidualWorld() < 1.0 + EPSILON);
        }
    }

    @Test
    void bankBlockedTerminal1842RetainsExactF4CQuantization() {
        SkyIslandDescriptor descriptor = descriptor(8L, 81L, 77L);
        SkyIslandAuthoredRealizationAssociation association =
                productionAssociation(descriptor, 920_077L);
        SkyIslandWorldHeadRefinedVoxelPlan refined =
                refinedVoxelPlan(descriptor, association);

        SkyIslandWorldHeadRefinedVoxelComponentPlan component =
                refined.components().stream()
                        .filter(value -> value.terminalCellIndex() == 1842)
                        .findFirst()
                        .orElseThrow();

        assertEquals(component.directRemovedSolidBlocks(), component.refinedRemovedSolidBlocks());
        assertEquals(0, component.returnedSolidBlocks());
        for (SkyIslandWorldHeadRefinedVoxelColumn column : component.columns()) {
            assertEquals(
                    column.directColumn().targetMaximumSolidY(),
                    column.refinedTargetMaximumSolidY());
            assertEquals(
                    column.directColumn().removedSolidBlocks(),
                    column.refinedRemovedSolidBlocks());
            assertEquals(
                    column.directColumn().projection().targetUpperSurfaceWorldY(),
                    column.refinedProjection().targetUpperSurfaceWorldY(),
                    EPSILON);
        }
    }

    @Test
    void repeatedRefinedQuantizationIsDeterministic() {
        SkyIslandDescriptor descriptor = descriptor(8L, 81L, 77L);
        SkyIslandAuthoredRealizationAssociation association =
                productionAssociation(descriptor, 930_077L);

        SkyIslandWorldHeadRefinedVoxelPlan a =
                refinedVoxelPlan(descriptor, association);
        SkyIslandWorldHeadRefinedVoxelPlan b =
                refinedVoxelPlan(descriptor, association);

        assertEquals(a.components(), b.components());
        assertEquals(a.directRemovedSolidBlocks(), b.directRemovedSolidBlocks());
        assertEquals(a.refinedRemovedSolidBlocks(), b.refinedRemovedSolidBlocks());
        assertEquals(a.returnedSolidBlocks(), b.returnedSolidBlocks());
    }

    private static SkyIslandWorldHeadRefinedVoxelPlan refinedVoxelPlan(
            SkyIslandDescriptor descriptor,
            SkyIslandAuthoredRealizationAssociation association) {
        SkyIslandComponentFluvialTerrainCandidatePlan candidate =
                SkyIslandComponentFluvialTerrainCandidatePlanner.plan(descriptor);
        SkyIslandFluvialVoxelQuantizationPlan voxel =
                SkyIslandFluvialVoxelQuantizationPlanner.plan(association, candidate);
        SkyIslandWorldWaterProjectionQualificationPlan direct =
                SkyIslandWorldWaterProjectionQualificationPlanner.plan(voxel);
        SkyIslandWorldWaterHeadRefinementPlan head =
                SkyIslandWorldWaterHeadRefinementPlanner.plan(direct);
        SkyIslandWorldHeadRefinedTerrainPlan terrain =
                SkyIslandWorldHeadRefinedTerrainPlanner.plan(head);
        return SkyIslandWorldHeadRefinedVoxelPlanner.plan(terrain);
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
                                "f4h-test",
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
