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
    void exactReviewAssociationHasOneWorldStageAcrossEachWetCrossSection() throws Exception {
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
                    if (side == 0) {
                        assertEquals(256.0 + reach.samples().get(i).bedElevationPotential()
                                        * descriptor.reliefBudget(),
                                terrain.targetUpperSurfaceWorldY(), 1.0e-9,
                                "hydraulic bed must equal the realized centerline bed");
                    }
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
        String summary = "ASSOCIATED WORLD FRAME PASS: sections=" + sections
                + " maxTransverseStageSpan=" + maximumSpan
                + " authorizedColumns=" + quantized.authorizedColumns().size()
                + "\n" + connectivityReport(quantized);
        var directory = java.nio.file.Path.of("build", "evidence", "hydrology-join-diagnostics");
        java.nio.file.Files.createDirectories(directory);
        java.nio.file.Files.writeString(directory.resolve("associated-summary.txt"), summary);
        System.out.println(summary);
    }


    /** Exact face-connectivity measurement of the water bands the runtime is permitted to write. */
    private static String connectivityReport(SkyIslandFluvialVoxelQuantizationPlan plan) {
        var bands = new java.util.HashMap<Long, int[]>();
        int clipped = 0;
        int subVoxel = 0;
        for (var column : plan.authorizedColumns()) {
            var sample = column.projection();
            var semantic = sample.semanticSample();
            if (!semantic.wet()) {
                continue;
            }
            double stage = sample.originalUpperSurfaceWorldY()
                    + (semantic.waterSurfacePotential() - semantic.originalTerrainPotential())
                            * plan.candidatePlan().descriptor().reliefBudget();
            int top = (int) Math.ceil(stage) - 1;
            if (top > column.originalSupport().maximumSolidY()) {
                clipped++;
            }
            top = Math.min(top, column.originalSupport().maximumSolidY());
            int bottom = column.targetMaximumSolidY() + 1;
            if (top < bottom) {
                subVoxel++;
                continue;
            }
            bands.put(key(column.worldX(), column.worldZ()), new int[] {bottom, top});
        }
        assertEquals(0, clipped, "solved stage must fit exact owned support without backend clipping");
        var labels = new java.util.HashMap<Long, Integer>();
        int components = 0;
        for (long start : bands.keySet()) {
            if (labels.containsKey(start)) {
                continue;
            }
            int label = ++components;
            var queue = new java.util.ArrayDeque<Long>();
            labels.put(start, label);
            queue.add(start);
            while (!queue.isEmpty()) {
                long current = queue.removeFirst();
                int x = (int) (current >> 32);
                int z = (int) current;
                int[] a = bands.get(current);
                for (int[] direction : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    long neighbor = key(x + direction[0], z + direction[1]);
                    int[] b = bands.get(neighbor);
                    if (b != null && !labels.containsKey(neighbor)
                            && Math.max(a[0], b[0]) <= Math.min(a[1], b[1])) {
                        labels.put(neighbor, label);
                        queue.add(neighbor);
                    }
                }
            }
        }
        assertEquals(plan.candidatePlan().terrainField().acceptedReaches().size(), components,
                "each ordinary river must form one face-connected water body");
        StringBuilder result = new StringBuilder("INTEGER WATER ACCEPTANCE: components=")
                .append(components).append(" columns=").append(bands.size())
                .append(" supportClipped=").append(clipped)
                .append(" subVoxel=").append(subVoxel).append('\n');
        for (var reach : plan.candidatePlan().terrainField().acceptedReaches()) {
            var routeLabels = new java.util.HashSet<Integer>();
            int missing = 0;
            for (var point : reach.centerline().points()) {
                Integer label = labels.get(key((int) Math.round(point.x()), (int) Math.round(point.z())));
                if (label == null) {
                    missing++;
                } else {
                    routeLabels.add(label);
                }
            }
            assertEquals(0, missing, "all rounded centerline samples require water");
            assertEquals(1, routeLabels.size(), "centerline must remain in one water component");
            var semantic = reach.geomorphicRoute().semanticReach();
            result.append(semantic.startCellIndex()).append("->").append(semantic.endCellIndex())
                    .append(" centerlineComponents=").append(routeLabels.size())
                    .append(" missingCenterlineSamples=").append(missing).append('\n');
        }
        return result.toString();
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ Integer.toUnsignedLong(z);
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
