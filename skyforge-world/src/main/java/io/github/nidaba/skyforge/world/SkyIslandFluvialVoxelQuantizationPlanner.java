package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Builds F4C removal-only ceiling-quantization evidence from one exact F4B association. */
public final class SkyIslandFluvialVoxelQuantizationPlanner {
    private SkyIslandFluvialVoxelQuantizationPlanner() {}

    public static SkyIslandFluvialVoxelQuantizationPlan plan(
            SkyIslandAuthoredRealizationAssociation association,
            SkyIslandComponentFluvialTerrainCandidatePlan candidatePlan) {
        Objects.requireNonNull(association, "association");
        Objects.requireNonNull(candidatePlan, "candidatePlan");
        if (!association.authoredDescriptor().equals(candidatePlan.descriptor())) {
            throw new IllegalArgumentException(
                    "F4C association and candidate descriptor must match");
        }

        SkyIslandComponentFluvialWorldSurfaceProjection projection =
                new SkyIslandComponentFluvialWorldSurfaceProjection(
                        association, candidatePlan);
        CompiledSkyIslandVolume volume =
                association.realizedVolume().compiledVolume();
        SkyIslandTerrainInterpreter terrain =
                new SkyIslandTerrainInterpreter(volume, SkyIslandTerrainProfile.reference());

        if (candidatePlan.terrainField().acceptedReaches().isEmpty()) {
            return new SkyIslandFluvialVoxelQuantizationPlan(
                    association, candidatePlan, List.of());
        }

        Bounds bounds = bounds(candidatePlan, volume);
        List<SkyIslandFluvialVoxelColumn> columns = new ArrayList<>();
        for (int worldX = bounds.minimumX(); worldX <= bounds.maximumX(); worldX++) {
            for (int worldZ = bounds.minimumZ(); worldZ <= bounds.maximumZ(); worldZ++) {
                double localX = worldX - volume.descriptor().centerX();
                double localZ = worldZ - volume.descriptor().centerZ();
                SkyIslandQualifiedFluvialSample semantic =
                        candidatePlan.terrainField().sampleDetailed(
                                new SkyIslandLocalPosition(localX, localZ));
                if (semantic.zone() == SkyIslandQualifiedFluvialZone.UNAFFECTED) {
                    continue;
                }

                SkyIslandProjectedFluvialTerrainSample sample =
                        projection.sampleWorld(worldX, worldZ);
                SkyIslandIntegerColumnSupport original =
                        SkyIslandIntegerColumnSupport.measure(terrain, worldX, worldZ)
                                .orElseThrow(() -> new IllegalStateException(
                                        "F4C authorized hydrology column has no compiled solid support"));

                int targetMaximumSolidY =
                        ceilToInt(sample.targetUpperSurfaceWorldY()) - 1;
                if (targetMaximumSolidY < original.minimumSolidY()) {
                    throw new IllegalStateException(
                            "F4C qualified hydrology target exhausts exact compiled support");
                }
                targetMaximumSolidY =
                        Math.min(targetMaximumSolidY, original.maximumSolidY());
                double quantizedUpper = targetMaximumSolidY + 1.0;
                double residual =
                        quantizedUpper - sample.targetUpperSurfaceWorldY();
                int removed =
                        original.maximumSolidY() - targetMaximumSolidY;
                columns.add(new SkyIslandFluvialVoxelColumn(
                        worldX,
                        worldZ,
                        sample,
                        original,
                        targetMaximumSolidY,
                        quantizedUpper,
                        residual,
                        removed));
            }
        }

        columns.sort(Comparator
                .comparingInt(SkyIslandFluvialVoxelColumn::worldX)
                .thenComparingInt(SkyIslandFluvialVoxelColumn::worldZ));
        return new SkyIslandFluvialVoxelQuantizationPlan(
                association, candidatePlan, columns);
    }

    private static Bounds bounds(
            SkyIslandComponentFluvialTerrainCandidatePlan candidate,
            CompiledSkyIslandVolume volume) {
        double minimumLocalX = Double.POSITIVE_INFINITY;
        double maximumLocalX = Double.NEGATIVE_INFINITY;
        double minimumLocalZ = Double.POSITIVE_INFINITY;
        double maximumLocalZ = Double.NEGATIVE_INFINITY;

        for (SkyIslandHydraulicReachGeometry reach :
                candidate.terrainField().acceptedReaches()) {
            double margin = reach.maximumBankfullHalfWidth() * 3.5 + 1.0;
            for (SkyIslandLocalPosition point : reach.centerline().points()) {
                minimumLocalX = Math.min(minimumLocalX, point.x() - margin);
                maximumLocalX = Math.max(maximumLocalX, point.x() + margin);
                minimumLocalZ = Math.min(minimumLocalZ, point.z() - margin);
                maximumLocalZ = Math.max(maximumLocalZ, point.z() + margin);
            }
        }
        return new Bounds(
                floorToInt(volume.descriptor().centerX() + minimumLocalX),
                ceilToInt(volume.descriptor().centerX() + maximumLocalX),
                floorToInt(volume.descriptor().centerZ() + minimumLocalZ),
                ceilToInt(volume.descriptor().centerZ() + maximumLocalZ));
    }

    private static int floorToInt(double value) {
        double v = Math.floor(value);
        if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("coordinate exceeds integer range");
        }
        return (int) v;
    }

    private static int ceilToInt(double value) {
        double v = Math.ceil(value);
        if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("coordinate exceeds integer range");
        }
        return (int) v;
    }

    private record Bounds(int minimumX, int maximumX, int minimumZ, int maximumZ) {}
}
