package io.github.nidaba.skyforge.reference.evidence;

import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.reference.sampling.OccupancyVolumeGrid;
import io.github.nidaba.skyforge.reference.sampling.ScalarGrid;
import io.github.nidaba.skyforge.reference.sampling.ScalarVolumeGrid;
import java.util.Objects;

/** Complete numerical evidence derived from one compiled suspended-volume specimen. */
public record SuspendedVolumeEvidence(
        CompiledSkyIslandVolume compiledVolume,
        ScalarVolumeGrid density,
        OccupancyVolumeGrid occupancy,
        ScalarGrid upperSurface,
        ScalarGrid undersideSurface,
        ScalarGrid suspensionDensity,
        VolumeSlice eastWest,
        VolumeSlice northSouth,
        VolumeMetrics metrics) {
    /** Validates that every artifact represents the same declared spatial domain. */
    public SuspendedVolumeEvidence {
        Objects.requireNonNull(compiledVolume, "compiledVolume");
        Objects.requireNonNull(density, "density");
        Objects.requireNonNull(occupancy, "occupancy");
        Objects.requireNonNull(upperSurface, "upperSurface");
        Objects.requireNonNull(undersideSurface, "undersideSurface");
        Objects.requireNonNull(suspensionDensity, "suspensionDensity");
        Objects.requireNonNull(eastWest, "eastWest");
        Objects.requireNonNull(northSouth, "northSouth");
        Objects.requireNonNull(metrics, "metrics");
        if (!density.specification().equals(occupancy.specification())) {
            throw new IllegalArgumentException("density and occupancy specifications differ");
        }
        if (!upperSurface.specification().equals(undersideSurface.specification())
                || !upperSurface.specification().equals(suspensionDensity.specification())) {
            throw new IllegalArgumentException("horizontal evidence specifications differ");
        }
    }
}
