package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.SemanticSkyIslandVolumeRecipe;
import org.junit.jupiter.api.Test;

class SkyIslandAssociatedHydraulicTerrainTest {
    @Test
    void exactReviewAssociationHasOneWorldStageAcrossEachWetCrossSection() {
        var descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(0x534B59464F524745L, 8L, 81L, 77L));
        var association = association(descriptor, 0.0);
        var candidate = SkyIslandComponentFluvialTerrainCandidatePlanner.planAssociated(association);
        assertEquals(association, candidate.worldAssociation().orElseThrow());
        assertEquals(2, candidate.terrainField().acceptedReaches().size());
        assertTrue(candidate.postRealizationQualifications().stream().allMatch(
                SkyIslandGeomorphicReachQualification::accepted));
        var projection = new SkyIslandComponentFluvialWorldWaterProjection(association, candidate);
        double maximumSpan = 0.0;
        int sections = 0;
        for (var reach : candidate.terrainField().acceptedReaches()) {
            var points = reach.centerline().points();
            for (int i = 1; i + 1 < points.size(); i++) {
                var point = points.get(i);
                double dx = points.get(i + 1).x() - points.get(i - 1).x();
                double dz = points.get(i + 1).z() - points.get(i - 1).z();
                double length = Math.hypot(dx, dz);
                double offset = reach.samples().get(i).bankfullHalfWidth() * 0.25;
                double minimum = Double.POSITIVE_INFINITY;
                double maximum = Double.NEGATIVE_INFINITY;
                for (int side : new int[] {-1, 0, 1}) {
                    var sample = projection.sampleWorld(
                            point.x() - side * dz / length * offset,
                            point.z() + side * dx / length * offset);
                    assertTrue(sample.wet(), "review inner cross section must remain wet");
                    var terrain = sample.terrainProjection();
                    var semantic = terrain.semanticSample();
                    double stage = sample.waterSurfaceWorldY().orElseThrow();
                    assertEquals(256.0 + semantic.waterSurfacePotential() * descriptor.reliefBudget(),
                            stage, 1.0e-9);
                    assertEquals(256.0 + semantic.targetTerrainPotential() * descriptor.reliefBudget(),
                            terrain.targetUpperSurfaceWorldY(), 1.0e-9);
                    assertTrue(terrain.terrainDeltaWorldUnits() <= 1.0e-9);
                    assertTrue(terrain.targetColumnThicknessWorldUnits() > 0.0);
                    assertTrue(sample.waterDepthWorldUnits() > 0.0);
                    minimum = Math.min(minimum, stage);
                    maximum = Math.max(maximum, stage);
                }
                maximumSpan = Math.max(maximumSpan, maximum - minimum);
                assertEquals(0.0, maximum - minimum, 1.0e-9);
                sections++;
            }
        }
        assertEquals(55, sections);
        var quantized = SkyIslandFluvialVoxelQuantizationPlanner.plan(association, candidate);
        assertTrue(quantized.rejectedComponents().isEmpty());
        assertTrue(!quantized.authorizedColumns().isEmpty());
        var qualified = SkyIslandWorldWaterProjectionQualificationPlanner.plan(quantized);
        assertEquals(2, qualified.qualifiedComponents().size());
        assertTrue(qualified.refinementRequiredComponents().isEmpty());
        var other = association(descriptor, 640.0);
        assertThrows(IllegalArgumentException.class,
                () -> new SkyIslandComponentFluvialWorldWaterProjection(other, candidate));
        assertThrows(IllegalArgumentException.class,
                () -> SkyIslandFluvialVoxelQuantizationPlanner.plan(other, candidate));
        var legacy = SkyIslandComponentFluvialTerrainCandidatePlanner.plan(descriptor);
        assertTrue(legacy.worldAssociation().isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> new SkyIslandComponentFluvialWorldSurfaceProjection(
                        association, candidate, legacy.terrainField()));
        System.out.println("ASSOCIATED WORLD FRAME PASS: sections=" + sections
                + " maxTransverseStageSpan=" + maximumSpan
                + " authorizedColumns=" + quantized.authorizedColumns().size());
    }

    private static SkyIslandAuthoredRealizationAssociation association(
            SkyIslandDescriptor descriptor, double centerX) {
        double radius = descriptor.nominalRadius();
        var physical = SkyIslandVolumeDescriptor.schema2(
                910_077L, centerX, 0.0, 256.0, radius, 72.0, 104.0,
                Math.min(32.0, radius), 0.43, 0.62, 0.57, 0.18,
                descriptor.morphologyFamily(), 0.22, 38.0, 0.31);
        var volume = new SkyIslandWorldVolume(
                new SkyIslandWorldVolumeId(0x5245414C495A4552L,
                        "associated-hydraulic-test", 0, 0, 910_077L),
                new WorldBounds(centerX - radius, centerX + radius,
                        64.0, 448.0, -radius, radius),
                new SemanticSkyIslandVolumeRecipe().compile(physical));
        return SkyIslandAuthoredRealizationAssociation.of(descriptor, volume);
    }
}
