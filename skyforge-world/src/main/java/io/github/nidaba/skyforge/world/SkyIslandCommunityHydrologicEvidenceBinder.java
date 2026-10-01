package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * Deterministic identity/provenance binder from ecology assembly evidence to accepted AUTH-0096
 * local surface/hydrologic evidence.
 *
 * <p>The binder applies no wetness, riparian, water-distance, habitat, or occupancy transform.
 */
public final class SkyIslandCommunityHydrologicEvidenceBinder {

    /** Binds two already-accepted evidence sources for the exact same association. */
    public SkyIslandCommunityHydrologicEvidence bind(
            SkyIslandCommunityAssemblyEvidence assemblyEvidence,
            SkyIslandSurfaceSiteCapabilityProfile surfaceSiteProfile) {
        Objects.requireNonNull(assemblyEvidence, "assemblyEvidence");
        Objects.requireNonNull(surfaceSiteProfile, "surfaceSiteProfile");
        return new SkyIslandCommunityHydrologicEvidence(
                assemblyEvidence,
                surfaceSiteProfile);
    }
}
