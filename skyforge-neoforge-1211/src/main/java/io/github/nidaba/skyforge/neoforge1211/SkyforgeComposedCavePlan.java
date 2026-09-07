package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandExteriorConnectedCaveVolumeField;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import java.util.Objects;

/**
 * Immutable production plan for one exact-volume composed cave realization.
 *
 * <p>The plan deliberately contains only backend-neutral physical volume identity/geometry and the
 * accepted AUTH-0030 exterior-connected authored cave field. Minecraft biome identity remains
 * owned by {@link SkyforgeNativeSurfacePopulationStage} and is resolved from that existing plan at
 * service time.
 */
record SkyforgeComposedCavePlan(
        SkyIslandWorldVolume volume,
        SkyIslandExteriorConnectedCaveVolumeField authoredField) {

    SkyforgeComposedCavePlan {
        Objects.requireNonNull(volume, "volume");
        Objects.requireNonNull(authoredField, "authoredField");
    }
}
