package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.Optional;

/**
 * Exact backend-neutral binding of ecology assembly evidence to accepted AUTH-0096 local
 * surface/hydrologic evidence.
 *
 * <p>This record performs no ecological interpretation of hydrology. It preserves the accepted
 * AUTH-0096 grid, values, association identity, and canonical order exactly.
 */
public record SkyIslandCommunityHydrologicEvidence(
        SkyIslandCommunityAssemblyEvidence assemblyEvidence,
        SkyIslandSurfaceSiteCapabilityProfile surfaceSiteProfile) {

    public SkyIslandCommunityHydrologicEvidence {
        assemblyEvidence = Objects.requireNonNull(assemblyEvidence, "assemblyEvidence");
        surfaceSiteProfile = Objects.requireNonNull(surfaceSiteProfile, "surfaceSiteProfile");

        if (!assemblyEvidence.association().equals(surfaceSiteProfile.association())) {
            throw new IllegalArgumentException(
                    "ecology assembly and hydrologic evidence must use the exact association");
        }
        if (!assemblyEvidence.association()
                .authoredDescriptor()
                .equals(surfaceSiteProfile.association().authoredDescriptor())) {
            throw new IllegalArgumentException(
                    "ecology assembly and hydrologic evidence must use the exact authored descriptor");
        }
        if (!assemblyEvidence.realizedVolumeId()
                .equals(surfaceSiteProfile.association().realizedVolumeId())) {
            throw new IllegalArgumentException(
                    "ecology assembly and hydrologic evidence must use the exact realized volume");
        }
    }

    public SkyIslandAuthoredRealizationAssociation association() {
        return assemblyEvidence.association();
    }

    /**
     * Returns the original AUTH-0096 cell for one exact watershed cell index.
     *
     * <p>No interpolation or nearest-cell policy is applied.
     */
    public SkyIslandSurfaceSiteCapabilityCell cell(int watershedCellIndex) {
        if (watershedCellIndex < 0) {
            throw new IndexOutOfBoundsException(
                    "watershedCellIndex must be non-negative: " + watershedCellIndex);
        }
        return surfaceSiteProfile.cells().stream()
                .filter(cell -> cell.watershedCellIndex() == watershedCellIndex)
                .findFirst()
                .orElseThrow(() -> new IndexOutOfBoundsException(
                        "watershedCellIndex is not present in accepted AUTH-0096 evidence: "
                                + watershedCellIndex));
    }

    /**
     * Returns the original AUTH-0096 cell only when its position exactly equals the requested
     * island-local position.
     *
     * <p>No interpolation, tolerance, or nearest-cell fallback is applied.
     */
    public Optional<SkyIslandSurfaceSiteCapabilityCell> cellAt(
            SkyIslandLocalPosition position) {
        Objects.requireNonNull(position, "position");
        return surfaceSiteProfile.cells().stream()
                .filter(cell -> cell.position().equals(position))
                .findFirst();
    }
}
