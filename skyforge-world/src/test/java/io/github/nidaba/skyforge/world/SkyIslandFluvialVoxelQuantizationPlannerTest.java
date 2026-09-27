package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.recipes.skyisland.SemanticSkyIslandVolumeRecipe;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkyIslandFluvialVoxelQuantizationPlannerTest {
    private static final long SEED = 0x534B59464F524745L;
    private static final long REALIZATION_ROOT = 0x5245414C495A4552L;
    private static final double EPSILON = 1.0e-9;

    @Test
    void ordinary77UsesRemovalOnlyCeilingQuantizationInsideOneBlock() {
        SkyIslandDescriptor descriptor = descriptor(8L, 81L, 77L);
        SkyIslandComponentFluvialTerrainCandidatePlan candidate =
                SkyIslandComponentFluvialTerrainCandidatePlanner.plan(descriptor);
        SkyIslandFluvialVoxelQuantizationPlan plan =
                SkyIslandFluvialVoxelQuantizationPlanner.plan(
                        productionAssociation(descriptor, 910_077L), candidate);

        assertEquals(8969, plan.authorizedColumns().size());
        assertFalse(plan.mutatedColumns().isEmpty());
        assertTrue(plan.mutatedColumns().size() <= 5858);
        assertTrue(plan.maximumUndercutResidualWorld() >= 0.0);
        assertTrue(plan.maximumUndercutResidualWorld() < 1.0);

        for (SkyIslandFluvialVoxelColumn column : plan.authorizedColumns()) {
            assertTrue(column.targetMaximumSolidY()
                    <= column.originalSupport().maximumSolidY());
            assertTrue(column.targetMaximumSolidY()
                    >= column.originalSupport().minimumSolidY());
            assertTrue(column.removedSolidBlocks() >= 0);
            assertTrue(column.undercutResidualWorld() >= -EPSILON);
            assertTrue(column.undercutResidualWorld() < 1.0 + EPSILON);
            assertTrue(column.quantizedUpperBoundaryWorldY() + EPSILON
                    >= column.projection().targetUpperSurfaceWorldY());
        }
    }

    @Test
    void rejectedOrDeferredSemanticControlsHaveNoVoxelAuthority() {
        for (long key : List.of(287L, 632L, 609L)) {
            SkyIslandDescriptor descriptor = descriptor(8L, 81L, key);
            SkyIslandComponentFluvialTerrainCandidatePlan candidate =
                    SkyIslandComponentFluvialTerrainCandidatePlanner.plan(descriptor);
            SkyIslandFluvialVoxelQuantizationPlan plan =
                    SkyIslandFluvialVoxelQuantizationPlanner.plan(
                            productionAssociation(descriptor, 910_000L + key),
                            candidate);
            assertTrue(plan.authorizedColumns().isEmpty());
            assertTrue(plan.mutatedColumns().isEmpty());
            assertEquals(0, plan.totalRemovedSolidBlocks());
        }
    }

    @Test
    void weakPhysicalRealizationFailsClosedRatherThanReconciling() {
        SkyIslandDescriptor descriptor = descriptor(8L, 81L, 77L);
        SkyIslandComponentFluvialTerrainCandidatePlan candidate =
                SkyIslandComponentFluvialTerrainCandidatePlanner.plan(descriptor);

        assertThrows(
                RuntimeException.class,
                () -> SkyIslandFluvialVoxelQuantizationPlanner.plan(
                        weakAssociation(descriptor, 920_077L), candidate));
    }

    @Test
    void repeatedQuantizationHasIdenticalColumnEvidence() {
        SkyIslandDescriptor descriptor = descriptor(8L, 81L, 77L);
        SkyIslandComponentFluvialTerrainCandidatePlan candidate =
                SkyIslandComponentFluvialTerrainCandidatePlanner.plan(descriptor);
        SkyIslandAuthoredRealizationAssociation association =
                productionAssociation(descriptor, 930_077L);

        SkyIslandFluvialVoxelQuantizationPlan first =
                SkyIslandFluvialVoxelQuantizationPlanner.plan(association, candidate);
        SkyIslandFluvialVoxelQuantizationPlan second =
                SkyIslandFluvialVoxelQuantizationPlanner.plan(association, candidate);

        assertEquals(first.authorizedColumns(), second.authorizedColumns());
        assertEquals(first.totalRemovedSolidBlocks(), second.totalRemovedSolidBlocks());
        assertEquals(
                first.maximumUndercutResidualWorld(),
                second.maximumUndercutResidualWorld(),
                EPSILON);
    }

    private static SkyIslandAuthoredRealizationAssociation productionAssociation(
            SkyIslandDescriptor descriptor,
            long geometrySeed) {
        return association(
                descriptor,
                SkyIslandVolumeDescriptor.schema2(
                        geometrySeed,
                        1200.0,
                        -900.0,
                        256.0,
                        descriptor.nominalRadius(),
                        72.0,
                        104.0,
                        Math.min(32.0, descriptor.nominalRadius()),
                        0.43,
                        0.62,
                        0.57,
                        0.18,
                        descriptor.morphologyFamily(),
                        0.22,
                        38.0,
                        0.31),
                "f4c-production");
    }

    private static SkyIslandAuthoredRealizationAssociation weakAssociation(
            SkyIslandDescriptor descriptor,
            long geometrySeed) {
        return association(
                descriptor,
                SkyIslandVolumeDescriptor.schema2(
                        geometrySeed,
                        96.0,
                        -64.0,
                        220.0,
                        descriptor.nominalRadius(),
                        1.0,
                        1.0,
                        Math.min(18.0, descriptor.nominalRadius() * 0.10),
                        0.0,
                        0.24,
                        0.62,
                        0.0,
                        descriptor.morphologyFamily(),
                        0.0,
                        28.0,
                        0.0),
                "f4c-weak");
    }

    private static SkyIslandAuthoredRealizationAssociation association(
            SkyIslandDescriptor descriptor,
            SkyIslandVolumeDescriptor physical,
            String group) {
        CompiledSkyIslandVolume compiled =
                new SemanticSkyIslandVolumeRecipe().compile(physical);
        double radius = physical.nominalRadius();
        SkyIslandWorldVolume volume =
                new SkyIslandWorldVolume(
                        new SkyIslandWorldVolumeId(
                                REALIZATION_ROOT,
                                group,
                                0,
                                0,
                                physical.seed()),
                        new WorldBounds(
                                physical.centerX() - radius,
                                physical.centerX() + radius,
                                physical.suspensionElevation() - 192.0,
                                physical.suspensionElevation() + 192.0,
                                physical.centerZ() - radius,
                                physical.centerZ() + radius),
                        compiled);
        return SkyIslandAuthoredRealizationAssociation.of(descriptor, volume);
    }

    private static SkyIslandDescriptor descriptor(long province, long cluster, long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, province, cluster, key));
    }
}
