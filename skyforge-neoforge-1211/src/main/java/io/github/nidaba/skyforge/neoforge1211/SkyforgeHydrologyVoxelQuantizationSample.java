package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandProjectedFluvialTerrainSample;
import java.util.Objects;

/**
 * One read-only F4C integer-column quantization of an already-admitted F4B world-surface sample.
 *
 * <p>The retained voxel column never extends below the continuous F4B target. Instead its top face
 * may remain less than one block above that target, which is ordinary one-sided voxel quantization.
 */
record SkyforgeHydrologyVoxelQuantizationSample(
        int worldX,
        int worldZ,
        SkyIslandProjectedFluvialTerrainSample projection,
        int originalMinimumY,
        int originalMaximumY,
        int targetMaximumY,
        int removedSolidVoxels,
        double discreteTopBoundaryWorldY,
        double quantizationErrorWorldUnits) {

    private static final double EPSILON = 1.0e-9;

    SkyforgeHydrologyVoxelQuantizationSample {
        projection = Objects.requireNonNull(projection, "projection");
        if (originalMaximumY < originalMinimumY) {
            throw new IllegalArgumentException("original exact support range is empty");
        }
        if (targetMaximumY < originalMinimumY || targetMaximumY > originalMaximumY) {
            throw new IllegalArgumentException(
                    "F4C target support must remain inside the original exact support range");
        }
        int expectedRemoved = originalMaximumY - targetMaximumY;
        if (removedSolidVoxels != expectedRemoved) {
            throw new IllegalArgumentException(
                    "removed voxel count must equal originalMaximumY-targetMaximumY");
        }
        if (!Double.isFinite(discreteTopBoundaryWorldY)
                || Math.abs(discreteTopBoundaryWorldY - (targetMaximumY + 1.0)) > EPSILON) {
            throw new IllegalArgumentException(
                    "discrete top boundary must be the retained top voxel's upper face");
        }
        if (!Double.isFinite(quantizationErrorWorldUnits)
                || quantizationErrorWorldUnits < -EPSILON
                || quantizationErrorWorldUnits >= 1.0 + EPSILON) {
            throw new IllegalArgumentException(
                    "F4C surface quantization error must be one-sided and strictly sub-block");
        }
        if (Math.abs(
                        quantizationErrorWorldUnits
                                - (discreteTopBoundaryWorldY
                                        - projection.targetUpperSurfaceWorldY()))
                > EPSILON) {
            throw new IllegalArgumentException(
                    "quantization error must equal discrete boundary minus F4B target");
        }
        if (projection.semanticSample().zone()
                        == io.github.nidaba.skyforge.world.SkyIslandQualifiedFluvialZone.UNAFFECTED
                && removedSolidVoxels != 0) {
            throw new IllegalArgumentException(
                    "F4C cannot mutate a column outside F4A/F4B hydrology authority");
        }
    }

    boolean hydrologyMutatesColumn() {
        return removedSolidVoxels > 0;
    }
}
