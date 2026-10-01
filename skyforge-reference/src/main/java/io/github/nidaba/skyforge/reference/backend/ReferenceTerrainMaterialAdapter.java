package io.github.nidaba.skyforge.reference.backend;

import io.github.nidaba.skyforge.world.SkyIslandTerrainSampleContext;
import java.util.Objects;

/**
 * Reference-only consumer of the minimal Skyforge sample context.
 *
 * <p>The adapter demonstrates that backend-owned environmental input can alter concrete
 * representation without altering Skyforge terrain meaning or occupancy.
 */
public final class ReferenceTerrainMaterialAdapter {

    public ReferenceBackendMaterial materialFor(
            SkyIslandTerrainSampleContext sample,
            ReferenceBackendEnvironment environment) {
        Objects.requireNonNull(sample, "sample");
        Objects.requireNonNull(environment, "environment");

        return switch (sample.semantic()) {
            case AIR -> ReferenceBackendMaterial.AIR;
            case SURFACE_MANTLE -> environment == ReferenceBackendEnvironment.FROZEN
                    ? ReferenceBackendMaterial.FROZEN_SURFACE
                    : ReferenceBackendMaterial.GREEN_SURFACE;
            case EDGE_SHELL, UNDERSIDE_SHELL -> ReferenceBackendMaterial.EXPOSED_SHELL;
            case SHALLOW_INTERIOR, DEEP_MASS -> ReferenceBackendMaterial.STRUCTURAL_ROCK;
        };
    }
}
