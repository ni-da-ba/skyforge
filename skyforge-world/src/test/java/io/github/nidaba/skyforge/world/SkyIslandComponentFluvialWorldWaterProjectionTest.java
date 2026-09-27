package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.recipes.skyisland.SemanticSkyIslandVolumeRecipe;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkyIslandComponentFluvialWorldWaterProjectionTest {
    private static final long SEED = 0x534B59464F524745L;
    private static final long REALIZATION_ROOT = 0x5245414C495A4552L;
    private static final double EPSILON = 1.0e-9;

    @Test
    void ordinary77PreservesAcceptedHydraulicDepthAtEveryCenterlineSample() {
        SkyIslandDescriptor descriptor = descriptor(8L, 81L, 77L);
        SkyIslandComponentFluvialTerrainCandidatePlan candidate =
                SkyIslandComponentFluvialTerrainCandidatePlanner.plan(descriptor);
        SkyIslandAuthoredRealizationAssociation association =
                productionAssociation(descriptor, 910_077L);
        SkyIslandComponentFluvialWorldWaterProjection projection =
                new SkyIslandComponentFluvialWorldWaterProjection(
                        association, candidate);

        int wet = 0;
        for (SkyIslandHydraulicReachGeometry reach :
                candidate.terrainField().acceptedReaches()) {
            for (SkyIslandLocalPosition local : reach.centerline().points()) {
                double worldX =
                        association.realizedVolume().compiledVolume().descriptor().centerX()
                                + local.x();
                double worldZ =
                        association.realizedVolume().compiledVolume().descriptor().centerZ()
                                + local.z();
                SkyIslandProjectedFluvialWaterSample sample =
                        projection.sampleWorld(worldX, worldZ);
                assertTrue(sample.wet());
                wet++;

                double expectedDepth =
                        (sample.terrainProjection()
                                                .semanticSample()
                                                .waterSurfacePotential()
                                        - sample.terrainProjection()
                                                .semanticSample()
                                                .targetTerrainPotential())
                                * descriptor.reliefBudget();
                assertEquals(expectedDepth, sample.waterDepthWorldUnits(), EPSILON);
                assertEquals(
                        sample.terrainProjection().targetUpperSurfaceWorldY()
                                + sample.waterDepthWorldUnits(),
                        sample.waterSurfaceWorldY().orElseThrow(),
                        EPSILON);
            }
        }
        assertEquals(59, wet);
    }

    @Test
    void semanticWaterDepthIsIndependentOfHorizontalPhysicalPlacement() {
        SkyIslandDescriptor descriptor = descriptor(8L, 81L, 77L);
        SkyIslandComponentFluvialTerrainCandidatePlan candidate =
                SkyIslandComponentFluvialTerrainCandidatePlanner.plan(descriptor);
        SkyIslandAuthoredRealizationAssociation a =
                productionAssociation(descriptor, 920_077L, 0.0, 0.0);
        SkyIslandAuthoredRealizationAssociation b =
                productionAssociation(descriptor, 920_077L, 640.0, -384.0);
        SkyIslandLocalPosition local =
                candidate.terrainField().acceptedReaches().getFirst()
                        .centerline().points().get(7);

        SkyIslandProjectedFluvialWaterSample x =
                new SkyIslandComponentFluvialWorldWaterProjection(a, candidate)
                        .sampleWorld(
                                a.realizedVolume().compiledVolume().descriptor().centerX()
                                        + local.x(),
                                a.realizedVolume().compiledVolume().descriptor().centerZ()
                                        + local.z());
        SkyIslandProjectedFluvialWaterSample y =
                new SkyIslandComponentFluvialWorldWaterProjection(b, candidate)
                        .sampleWorld(
                                b.realizedVolume().compiledVolume().descriptor().centerX()
                                        + local.x(),
                                b.realizedVolume().compiledVolume().descriptor().centerZ()
                                        + local.z());

        assertTrue(x.wet());
        assertTrue(y.wet());
        assertEquals(x.waterDepthWorldUnits(), y.waterDepthWorldUnits(), EPSILON);
    }

    @Test
    void rejectedOrDeferredControlsPublishNoWaterAuthority() {
        for (long key : List.of(287L, 632L, 609L)) {
            SkyIslandDescriptor descriptor = descriptor(8L, 81L, key);
            SkyIslandComponentFluvialTerrainCandidatePlan candidate =
                    SkyIslandComponentFluvialTerrainCandidatePlanner.plan(descriptor);
            assertTrue(candidate.terrainField().acceptedReaches().isEmpty());

            SkyIslandAuthoredRealizationAssociation association =
                    productionAssociation(descriptor, 910_000L + key);
            SkyIslandProjectedFluvialWaterSample sample =
                    new SkyIslandComponentFluvialWorldWaterProjection(
                                    association, candidate)
                            .sampleWorld(
                                    association.realizedVolume().compiledVolume()
                                            .descriptor().centerX(),
                                    association.realizedVolume().compiledVolume()
                                            .descriptor().centerZ());
            assertFalse(sample.wet());
            assertTrue(sample.waterSurfaceWorldY().isEmpty());
            assertEquals(0.0, sample.waterDepthWorldUnits(), EPSILON);
        }
    }

    private static SkyIslandAuthoredRealizationAssociation productionAssociation(
            SkyIslandDescriptor descriptor,
            long geometrySeed) {
        return productionAssociation(descriptor, geometrySeed, 1200.0, -900.0);
    }

    private static SkyIslandAuthoredRealizationAssociation productionAssociation(
            SkyIslandDescriptor descriptor,
            long geometrySeed,
            double centerX,
            double centerZ) {
        double radius = descriptor.nominalRadius();
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
                                "f4e-water-test",
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
