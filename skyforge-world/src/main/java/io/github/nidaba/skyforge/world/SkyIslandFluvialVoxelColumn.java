package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** One F4C removal-only quantized column derived from an F4B continuous world-space target. */
public record SkyIslandFluvialVoxelColumn(
        int worldX,
        int worldZ,
        SkyIslandProjectedFluvialTerrainSample projection,
        SkyIslandIntegerColumnSupport originalSupport,
        int targetMaximumSolidY,
        double quantizedUpperBoundaryWorldY,
        double undercutResidualWorld,
        int removedSolidBlocks) {

    private static final double EPSILON = 1.0e-9;

    public SkyIslandFluvialVoxelColumn {
        projection = Objects.requireNonNull(projection, "projection");
        originalSupport = Objects.requireNonNull(originalSupport, "originalSupport");
        if (targetMaximumSolidY < originalSupport.minimumSolidY()
                || targetMaximumSolidY > originalSupport.maximumSolidY()) {
            throw new IllegalArgumentException(
                    "F4C target solid maximum must remain inside original exact support");
        }
        if (removedSolidBlocks
                != originalSupport.maximumSolidY() - targetMaximumSolidY) {
            throw new IllegalArgumentException("removedSolidBlocks must match support delta");
        }
        if (removedSolidBlocks < 0) {
            throw new IllegalArgumentException("F4C cannot add solid blocks");
        }
        if (Math.abs(quantizedUpperBoundaryWorldY - (targetMaximumSolidY + 1.0))
                > EPSILON) {
            throw new IllegalArgumentException("quantized upper boundary must be maxSolidY + 1");
        }
        double expectedResidual =
                quantizedUpperBoundaryWorldY - projection.targetUpperSurfaceWorldY();
        if (Math.abs(expectedResidual - undercutResidualWorld) > EPSILON) {
            throw new IllegalArgumentException("undercut residual must match quantized-target");
        }
        if (undercutResidualWorld < -EPSILON || undercutResidualWorld >= 1.0 + EPSILON) {
            throw new IllegalArgumentException(
                    "ceiling quantization residual must remain in [0,1)");
        }
        if (quantizedUpperBoundaryWorldY + EPSILON
                < projection.targetUpperSurfaceWorldY()) {
            throw new IllegalArgumentException(
                    "F4C may never excavate below the qualified continuous target");
        }
    }

    public boolean mutatesTerrain() {
        return removedSolidBlocks > 0;
    }
}
