package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandMorphologyFamily;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.recipes.skyisland.SemanticSkyIslandVolumeRecipe;
import org.junit.jupiter.api.Test;

class SkyIslandComponentFluvialWorldSurfaceProjectionTest {
    private static final long SEED = 0x534B59464F524745L;
    private static final double EPSILON = 1.0e-9;

    @Test
    void ordinary77ProjectsOnlyTheQualifiedDeltaIntoCompiledWorldSpace() {
        SkyIslandDescriptor descriptor = descriptor(8L, 81L, 77L);
        SkyIslandComponentFluvialTerrainCandidatePlan candidate =
                SkyIslandComponentFluvialTerrainCandidatePlanner.plan(descriptor);
        CompiledSkyIslandVolume volume =
                compiled(descriptor, 910_077L, 140.0, -96.0, descriptor.nominalRadius());
        SkyIslandComponentFluvialWorldSurfaceProjection projection =
                new SkyIslandComponentFluvialWorldSurfaceProjection(
                        descriptor, candidate, volume);

        int affected = 0;
        for (SkyIslandHydraulicReachGeometry reach :
                candidate.terrainField().acceptedReaches()) {
            for (SkyIslandLocalPosition local : reach.centerline().points()) {
                double worldX = volume.descriptor().centerX() + local.x();
                double worldZ = volume.descriptor().centerZ() + local.z();
                SkyIslandProjectedFluvialTerrainSample sample =
                        projection.sampleWorld(worldX, worldZ);

                assertEquals(local.x(), sample.localPosition().x(), EPSILON);
                assertEquals(local.z(), sample.localPosition().z(), EPSILON);
                assertEquals(
                        sample.semanticSample().terrainDeltaPotential()
                                * descriptor.reliefBudget(),
                        sample.terrainDeltaWorldUnits(),
                        EPSILON);
                assertEquals(
                        sample.originalUpperSurfaceWorldY()
                                + sample.terrainDeltaWorldUnits(),
                        sample.targetUpperSurfaceWorldY(),
                        EPSILON);
                assertTrue(
                        sample.targetColumnThicknessWorldUnits() > 0.0);
                assertTrue(sample.terrainDeltaWorldUnits() <= EPSILON);
                if (sample.terrainDeltaWorldUnits() < -EPSILON) {
                    affected++;
                }
            }
        }

        assertEquals(59, affected);
    }

    @Test
    void translatingTheCompiledVolumeDoesNotChangeSemanticHydrologyDelta() {
        SkyIslandDescriptor descriptor = descriptor(8L, 81L, 77L);
        SkyIslandComponentFluvialTerrainCandidatePlan candidate =
                SkyIslandComponentFluvialTerrainCandidatePlanner.plan(descriptor);
        CompiledSkyIslandVolume origin =
                compiled(descriptor, 920_077L, 0.0, 0.0, descriptor.nominalRadius());
        CompiledSkyIslandVolume translated =
                compiled(descriptor, 920_077L, 320.0, -176.0, descriptor.nominalRadius());

        SkyIslandLocalPosition local =
                candidate.terrainField()
                        .acceptedReaches()
                        .getFirst()
                        .centerline()
                        .points()
                        .get(7);

        SkyIslandProjectedFluvialTerrainSample a =
                new SkyIslandComponentFluvialWorldSurfaceProjection(
                                descriptor, candidate, origin)
                        .sampleWorld(local.x(), local.z());
        SkyIslandProjectedFluvialTerrainSample b =
                new SkyIslandComponentFluvialWorldSurfaceProjection(
                                descriptor, candidate, translated)
                        .sampleWorld(
                                translated.descriptor().centerX() + local.x(),
                                translated.descriptor().centerZ() + local.z());

        assertEquivalentSemanticSample(a.semanticSample(), b.semanticSample());
        assertEquals(a.terrainDeltaWorldUnits(), b.terrainDeltaWorldUnits(), EPSILON);
    }

    @Test
    void physicalPlacementWithInsufficientLocalThicknessFailsClosed() {
        SkyIslandDescriptor descriptor = descriptor(8L, 81L, 77L);
        SkyIslandComponentFluvialTerrainCandidatePlan candidate =
                SkyIslandComponentFluvialTerrainCandidatePlanner.plan(descriptor);
        CompiledSkyIslandVolume volume =
                compiled(descriptor, 910_077L, 96.0, -64.0, descriptor.nominalRadius());
        SkyIslandComponentFluvialWorldSurfaceProjection projection =
                new SkyIslandComponentFluvialWorldSurfaceProjection(
                        descriptor, candidate, volume);

        boolean rejected = false;
        outer:
        for (SkyIslandHydraulicReachGeometry reach :
                candidate.terrainField().acceptedReaches()) {
            for (SkyIslandLocalPosition local : reach.centerline().points()) {
                try {
                    projection.sampleWorld(
                            volume.descriptor().centerX() + local.x(),
                            volume.descriptor().centerZ() + local.z());
                } catch (IllegalArgumentException expected) {
                    rejected = true;
                    break outer;
                }
            }
        }

        assertTrue(rejected);
    }

    @Test
    void rejectedPrimary287ProjectsExactlyZeroDelta() {
        SkyIslandDescriptor descriptor = descriptor(8L, 81L, 287L);
        SkyIslandComponentFluvialTerrainCandidatePlan candidate =
                SkyIslandComponentFluvialTerrainCandidatePlanner.plan(descriptor);
        CompiledSkyIslandVolume volume =
                compiled(descriptor, 910_287L, 0.0, 0.0, descriptor.nominalRadius());
        SkyIslandHydraulicReachSkeleton reach =
                SkyIslandHydraulicGeometrySkeletonPlanner.plan(descriptor)
                        .reaches()
                        .getFirst();
        SkyIslandLocalPosition local =
                reach.centerline().points().get(reach.centerline().points().size() / 2);

        SkyIslandProjectedFluvialTerrainSample sample =
                new SkyIslandComponentFluvialWorldSurfaceProjection(
                                descriptor, candidate, volume)
                        .sampleWorld(local.x(), local.z());

        assertEquals(0.0, sample.terrainDeltaWorldUnits(), EPSILON);
        assertEquals(
                sample.originalUpperSurfaceWorldY(),
                sample.targetUpperSurfaceWorldY(),
                EPSILON);
    }

    @Test
    void projectionFailsClosedWhenAuthorizedCutWouldCollapseCompiledColumn() {
        SkyIslandDescriptor descriptor = descriptor(8L, 81L, 77L);
        SkyIslandComponentFluvialTerrainCandidatePlan candidate =
                SkyIslandComponentFluvialTerrainCandidatePlanner.plan(descriptor);
        SkyIslandVolumeDescriptor physical =
                SkyIslandVolumeDescriptor.schema2(
                        935_077L,
                        0.0,
                        0.0,
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
                        0.0);
        CompiledSkyIslandVolume thin =
                new SemanticSkyIslandVolumeRecipe().compile(physical);
        SkyIslandComponentFluvialWorldSurfaceProjection projection =
                new SkyIslandComponentFluvialWorldSurfaceProjection(
                        descriptor, candidate, thin);
        SkyIslandLocalPosition local =
                candidate.terrainField()
                        .acceptedReaches()
                        .getFirst()
                        .centerline()
                        .points()
                        .getFirst();

        assertThrows(
                IllegalArgumentException.class,
                () -> projection.sampleWorld(local.x(), local.z()));
    }

    @Test
    void projectionRejectsMismatchedPhysicalScaleOrMorphology() {
        SkyIslandDescriptor descriptor = descriptor(8L, 81L, 77L);
        SkyIslandComponentFluvialTerrainCandidatePlan candidate =
                SkyIslandComponentFluvialTerrainCandidatePlanner.plan(descriptor);

        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandComponentFluvialWorldSurfaceProjection(
                        descriptor,
                        candidate,
                        compiled(
                                descriptor,
                                930_077L,
                                0.0,
                                0.0,
                                descriptor.nominalRadius() + 1.0)));

        SkyIslandMorphologyFamily alternate =
                descriptor.morphologyFamily() == SkyIslandMorphologyFamily.MASSIF
                        ? SkyIslandMorphologyFamily.TABLELAND
                        : SkyIslandMorphologyFamily.MASSIF;
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandComponentFluvialWorldSurfaceProjection(
                        descriptor,
                        candidate,
                        compiled(
                                descriptor,
                                930_078L,
                                0.0,
                                0.0,
                                descriptor.nominalRadius(),
                                alternate)));
    }

    private static void assertEquivalentSemanticSample(
            SkyIslandQualifiedFluvialSample a,
            SkyIslandQualifiedFluvialSample b) {
        assertEquals(a.originalTerrainPotential(), b.originalTerrainPotential(), EPSILON);
        assertEquals(a.targetTerrainPotential(), b.targetTerrainPotential(), EPSILON);
        assertEquals(a.terrainDeltaPotential(), b.terrainDeltaPotential(), EPSILON);
        assertEquals(a.wet(), b.wet());
        assertEquals(a.waterSurfacePotential(), b.waterSurfacePotential(), EPSILON);
        assertEquals(a.zone(), b.zone());
        assertEquals(a.provenance(), b.provenance());
    }

    private static CompiledSkyIslandVolume compiled(
            SkyIslandDescriptor descriptor,
            long geometrySeed,
            double centerX,
            double centerZ,
            double radius) {
        return compiled(
                descriptor,
                geometrySeed,
                centerX,
                centerZ,
                radius,
                descriptor.morphologyFamily());
    }

    private static CompiledSkyIslandVolume compiled(
            SkyIslandDescriptor descriptor,
            long geometrySeed,
            double centerX,
            double centerZ,
            double radius,
            SkyIslandMorphologyFamily morphologyFamily) {
        SkyIslandVolumeDescriptor physical =
                SkyIslandVolumeDescriptor.schema2(
                        geometrySeed,
                        centerX,
                        centerZ,
                        220.0,
                        radius,
                        58.0,
                        82.0,
                        Math.min(54.0, radius * 0.18),
                        0.0,
                        0.24,
                        0.62,
                        0.0,
                        morphologyFamily,
                        0.10,
                        28.0,
                        0.18);
        return new SemanticSkyIslandVolumeRecipe().compile(physical);
    }

    private static SkyIslandDescriptor descriptor(long province, long cluster, long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, province, cluster, key));
    }
}
