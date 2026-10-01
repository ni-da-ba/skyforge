package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.recipes.skyisland.SemanticSkyIslandVolumeRecipe;
import org.junit.jupiter.api.Test;

class SkyIslandWorldWaterHeadRefinementPlannerTest {
    private static final long SEED = 0x534B59464F524745L;
    private static final long REALIZATION_ROOT = 0x5245414C495A4552L;
    private static final double EPSILON = 1.0e-9;

    @Test
    void ordinary77RefinementIsComponentAtomicAndDepthPreserving() {
        SkyIslandDescriptor descriptor = descriptor(8L, 81L, 77L);
        SkyIslandComponentFluvialTerrainCandidatePlan candidate =
                SkyIslandComponentFluvialTerrainCandidatePlanner.plan(descriptor);
        SkyIslandAuthoredRealizationAssociation association =
                productionAssociation(descriptor, 910_077L);
        SkyIslandFluvialVoxelQuantizationPlan terrain =
                SkyIslandFluvialVoxelQuantizationPlanner.plan(
                        association, candidate);
        SkyIslandWorldWaterProjectionQualificationPlan direct =
                SkyIslandWorldWaterProjectionQualificationPlanner.plan(terrain);
        SkyIslandWorldWaterHeadRefinementPlan refinement =
                SkyIslandWorldWaterHeadRefinementPlanner.plan(direct);

        assertEquals(2, direct.refinementRequiredComponents().size());
        assertEquals(2, refinement.components().size());
        assertEquals(1, refinement.solvedComponents().size());
        assertEquals(559, refinement.solvedComponents().getFirst().terminalCellIndex());
        SkyIslandWorldWaterHeadRefinementComponent blocked =
                refinement.components().stream()
                        .filter(component -> component.terminalCellIndex() == 1842)
                        .findFirst()
                        .orElseThrow();
        assertEquals(SkyIslandWorldWaterHeadRefinementStatus.INFEASIBLE, blocked.status());
        assertFalse(blocked.blockers().isEmpty());
        assertTrue(blocked.blockers().getFirst().contains("BANK_CONTAINMENT"));

        for (SkyIslandWorldWaterHeadRefinementComponent component :
                refinement.components()) {
            assertNotEquals(
                    SkyIslandWorldWaterHeadRefinementStatus.NUMERICAL_FAILURE,
                    component.status());
            if (component.status() == SkyIslandWorldWaterHeadRefinementStatus.SOLVED) {
                assertEquals(1, component.reaches().size());
                SkyIslandWorldWaterHeadRefinementReach reach =
                        component.reaches().getFirst();
                assertEquals(0, reach.uphillSegments(EPSILON));
                assertTrue(reach.maximumTerrainRaiseWorld() >= 0.0);
                for (SkyIslandWorldWaterHeadRefinementSample sample : reach.samples()) {
                    assertTrue(sample.refinedWaterHeadWorld() + EPSILON
                            >= sample.directWaterHeadWorld());
                    assertTrue(sample.terrainRaiseWorld() >= -EPSILON);
                    assertTrue(sample.terrainRaiseWorld()
                            <= sample.maximumTerrainRaiseWorld() + EPSILON);
                    assertEquals(
                            sample.waterDepthWorld(),
                            sample.refinedWaterHeadWorld()
                                    - sample.refinedTerrainTargetWorld(),
                            EPSILON);
                }
            } else {
                assertFalse(component.blockers().isEmpty());
            }
        }
    }

    @Test
    void repeatedRefinementIsDeterministic() {
        SkyIslandDescriptor descriptor = descriptor(8L, 81L, 77L);
        SkyIslandComponentFluvialTerrainCandidatePlan candidate =
                SkyIslandComponentFluvialTerrainCandidatePlanner.plan(descriptor);
        SkyIslandAuthoredRealizationAssociation association =
                productionAssociation(descriptor, 920_077L);
        SkyIslandFluvialVoxelQuantizationPlan terrain =
                SkyIslandFluvialVoxelQuantizationPlanner.plan(
                        association, candidate);
        SkyIslandWorldWaterProjectionQualificationPlan direct =
                SkyIslandWorldWaterProjectionQualificationPlanner.plan(terrain);

        SkyIslandWorldWaterHeadRefinementPlan a =
                SkyIslandWorldWaterHeadRefinementPlanner.plan(direct);
        SkyIslandWorldWaterHeadRefinementPlan b =
                SkyIslandWorldWaterHeadRefinementPlanner.plan(direct);

        assertEquals(a.components().size(), b.components().size());
        for (int i = 0; i < a.components().size(); i++) {
            var x = a.components().get(i);
            var y = b.components().get(i);
            assertEquals(x.terminalCellIndex(), y.terminalCellIndex());
            assertEquals(x.status(), y.status());
            assertEquals(x.blockers(), y.blockers());
            assertEquals(x.reaches().size(), y.reaches().size());
            for (int r = 0; r < x.reaches().size(); r++) {
                var xr = x.reaches().get(r);
                var yr = y.reaches().get(r);
                assertEquals(xr.status(), yr.status());
                assertEquals(xr.samples(), yr.samples());
                assertEquals(xr.diagnostic(), yr.diagnostic());
                if (xr.solve().isPresent()) {
                    assertTrue(yr.solve().isPresent());
                    assertEquals(
                            xr.solve().orElseThrow().status(),
                            yr.solve().orElseThrow().status());
                    assertEquals(
                            xr.solve().orElseThrow().objective(),
                            yr.solve().orElseThrow().objective(),
                            EPSILON);
                    assertEquals(
                            xr.solve().orElseThrow().primalResidual(),
                            yr.solve().orElseThrow().primalResidual(),
                            EPSILON);
                }
            }
        }
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
                                "f4f-test",
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
