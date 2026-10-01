package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import io.github.nidaba.skyforge.world.TerrainBoxObservation;
import io.github.nidaba.skyforge.world.WorldBounds;
import java.util.Objects;

/**
 * Proof that one complete integer native-piece bounding box lies at or below one exact Skyforge
 * island underside.
 *
 * <p>This is deliberately evidence rather than a structure-admission decision. A later milestone
 * may decide whether a particular resolved native start can be rejected from this fact. SF-IMP-0049
 * does not modify generation behavior.
 */
record MinecraftStructurePieceUndersideSeparationEvidence(
        SkyIslandWorldVolumeId supportingVolumeId,
        WorldBounds pieceBounds,
        TerrainBoxObservation observation) {

    MinecraftStructurePieceUndersideSeparationEvidence {
        Objects.requireNonNull(supportingVolumeId, "supportingVolumeId");
        Objects.requireNonNull(pieceBounds, "pieceBounds");
        Objects.requireNonNull(observation, "observation");
        if (!supportingVolumeId.equals(observation.observedVolumeId())) {
            throw new IllegalArgumentException("separation evidence volume differs from observed volume");
        }
        if (!observation.allSamplesAtOrBelowUndersideSurface()) {
            throw new IllegalArgumentException("separation evidence requires every sampled coordinate below underside");
        }
    }
}
