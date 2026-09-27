package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationAssociation;
import io.github.nidaba.skyforge.world.SkyIslandComponentFluvialTerrainCandidatePlan;
import io.github.nidaba.skyforge.world.SkyIslandComponentFluvialWorldSurfaceProjection;
import io.github.nidaba.skyforge.world.SkyIslandQualifiedFluvialZone;
import io.github.nidaba.skyforge.world.SkyIslandTerrainInterpreter;
import io.github.nidaba.skyforge.world.SkyIslandTerrainProfile;
import java.util.Objects;
import java.util.Optional;

/**
 * F4C read-only voxel quantizer for an already-qualified F4B authored-realization association.
 *
 * <p>This class does not inspect or mutate Minecraft chunks. It has no water, material, connectivity,
 * carrier, bank-fill, or reconciliation policy. It can only truncate the original exact compiled
 * solid range at the integer cap implied by the F4B continuous upper-surface target.
 */
final class SkyforgeHydrologyVoxelQuantizer {
    private static final double EPSILON = 1.0e-9;

    private final SkyIslandAuthoredRealizationAssociation association;
    private final SkyIslandComponentFluvialTerrainCandidatePlan candidatePlan;
    private final SkyIslandComponentFluvialWorldSurfaceProjection projection;
    private final SkyIslandTerrainInterpreter interpreter;

    SkyforgeHydrologyVoxelQuantizer(
            SkyIslandAuthoredRealizationAssociation association,
            SkyIslandComponentFluvialTerrainCandidatePlan candidatePlan) {
        this.association = Objects.requireNonNull(association, "association");
        this.candidatePlan = Objects.requireNonNull(candidatePlan, "candidatePlan");
        if (!association.authoredDescriptor().equals(candidatePlan.descriptor())) {
            throw new IllegalArgumentException(
                    "F4C candidate must belong to the exact AUTH-0046 association descriptor");
        }
        this.projection =
                new SkyIslandComponentFluvialWorldSurfaceProjection(
                        association, candidatePlan);
        this.interpreter =
                new SkyIslandTerrainInterpreter(
                        association.realizedVolume().compiledVolume(),
                        SkyIslandTerrainProfile.reference());
    }

    SkyIslandAuthoredRealizationAssociation association() {
        return association;
    }

    SkyIslandComponentFluvialTerrainCandidatePlan candidatePlan() {
        return candidatePlan;
    }

    Optional<SkyforgeHydrologyVoxelQuantizationSample> sample(int worldX, int worldZ) {
        var projected = projection.sampleWorld(worldX, worldZ);
        Optional<SkyforgeExactVoxelSupportBounds.ColumnRange> original =
                SkyforgeExactVoxelSupportBounds.integerSolidRange(
                        interpreter, worldX, worldZ);

        if (original.isEmpty()) {
            if (projected.semanticSample().zone()
                            != SkyIslandQualifiedFluvialZone.UNAFFECTED
                    || Math.abs(projected.terrainDeltaWorldUnits()) > EPSILON) {
                throw new IllegalStateException(
                        "F4C hydrology authority addresses a column with no original exact voxel support");
            }
            return Optional.empty();
        }

        var range = original.orElseThrow();
        int targetCap = Math.subtractExact(
                ceilToInt(projected.targetUpperSurfaceWorldY()), 1);
        int targetMaximumY = Math.min(range.maximumY(), targetCap);
        if (targetMaximumY < range.minimumY()) {
            throw new IllegalStateException(
                    "F4C quantization would erase the entire original compiled column");
        }

        int removed = range.maximumY() - targetMaximumY;
        double discreteTopBoundary = targetMaximumY + 1.0;
        double quantizationError =
                discreteTopBoundary - projected.targetUpperSurfaceWorldY();
        if (quantizationError < -EPSILON || quantizationError >= 1.0 + EPSILON) {
            throw new IllegalStateException(
                    "F4C quantization exceeded the ordinary one-block surface error envelope");
        }
        if (projected.semanticSample().zone() == SkyIslandQualifiedFluvialZone.UNAFFECTED
                && removed != 0) {
            throw new IllegalStateException(
                    "F4C attempted hydrology mutation outside F4A/F4B authority");
        }

        return Optional.of(
                new SkyforgeHydrologyVoxelQuantizationSample(
                        worldX,
                        worldZ,
                        projected,
                        range.minimumY(),
                        range.maximumY(),
                        targetMaximumY,
                        removed,
                        discreteTopBoundary,
                        quantizationError));
    }

    private static int ceilToInt(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("voxel target must be finite");
        }
        double ceiled = Math.ceil(value);
        if (ceiled < Integer.MIN_VALUE || ceiled > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("voxel target exceeds integer range: " + value);
        }
        return (int) ceiled;
    }
}
