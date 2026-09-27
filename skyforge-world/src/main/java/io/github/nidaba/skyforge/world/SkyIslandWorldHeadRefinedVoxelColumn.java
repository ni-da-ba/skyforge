package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** One F4H re-quantized column whose removal interval must be a subset of accepted F4C authority. */
public record SkyIslandWorldHeadRefinedVoxelColumn(
        SkyIslandFluvialVoxelColumn directColumn,
        SkyIslandProjectedFluvialTerrainSample refinedProjection,
        int refinedTargetMaximumSolidY,
        double refinedQuantizedUpperBoundaryWorldY,
        double refinedUndercutResidualWorld,
        int refinedRemovedSolidBlocks) {

    private static final double EPSILON = 1.0e-9;

    public SkyIslandWorldHeadRefinedVoxelColumn {
        directColumn = Objects.requireNonNull(directColumn, "directColumn");
        refinedProjection = Objects.requireNonNull(refinedProjection, "refinedProjection");
        if (refinedTargetMaximumSolidY < directColumn.targetMaximumSolidY()) {
            throw new IllegalArgumentException(
                    "F4H may not deepen a column below accepted F4C target");
        }
        if (refinedTargetMaximumSolidY > directColumn.originalSupport().maximumSolidY()) {
            throw new IllegalArgumentException(
                    "F4H target maximum may not exceed original compiled support");
        }
        if (refinedRemovedSolidBlocks
                != directColumn.originalSupport().maximumSolidY()
                        - refinedTargetMaximumSolidY) {
            throw new IllegalArgumentException(
                    "F4H removal count must match refined support interval");
        }
        if (refinedRemovedSolidBlocks < 0
                || refinedRemovedSolidBlocks > directColumn.removedSolidBlocks()) {
            throw new IllegalArgumentException(
                    "F4H removal count must be a subset of accepted F4C removal count");
        }
        if (Math.abs(
                        refinedQuantizedUpperBoundaryWorldY
                                - (refinedTargetMaximumSolidY + 1.0))
                > EPSILON) {
            throw new IllegalArgumentException(
                    "F4H quantized upper boundary must equal max solid Y + 1");
        }
        double expectedResidual =
                refinedQuantizedUpperBoundaryWorldY
                        - refinedProjection.targetUpperSurfaceWorldY();
        if (Math.abs(expectedResidual - refinedUndercutResidualWorld) > EPSILON) {
            throw new IllegalArgumentException(
                    "F4H residual must match quantized boundary minus refined continuous target");
        }
        if (refinedUndercutResidualWorld < -EPSILON
                || refinedUndercutResidualWorld >= 1.0 + EPSILON) {
            throw new IllegalArgumentException(
                    "F4H ceiling-quantization residual must remain in [0,1)");
        }
        if (refinedProjection.targetUpperSurfaceWorldY() + EPSILON
                < directColumn.projection().targetUpperSurfaceWorldY()) {
            throw new IllegalArgumentException(
                    "F4H continuous target may not be deeper than accepted F4B/F4C target");
        }
    }

    public int returnedSolidBlocks() {
        return directColumn.removedSolidBlocks() - refinedRemovedSolidBlocks;
    }

    public boolean mutatesTerrain() {
        return refinedRemovedSolidBlocks > 0;
    }
}
