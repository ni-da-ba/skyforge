package io.github.nidaba.skyforge.world;

import java.util.Optional;

/**
 * Backend-neutral source of authoritative physical upper/underside columns for one realized island.
 *
 * <p>Horizontal input remains in authored island-local coordinates. Implementations are responsible
 * for any translation/rotation needed to query their realized physical surfaces.
 */
public interface SkyIslandVerticalColumnField {
    /** Nominal authored horizontal radius represented by this physical realization. */
    double nominalRadius();

    /**
     * Returns the physical solid column at one island-local horizontal position.
     *
     * <p>An empty result means the realized upper and underside surfaces do not form a positive
     * physical island column at that horizontal position.
     */
    Optional<SkyIslandVerticalColumn> columnAt(SkyIslandLocalPosition position);
}
