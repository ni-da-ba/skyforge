package io.github.nidaba.skyforge.world.content;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandEcologyRegime;
import io.github.nidaba.skyforge.world.SkyIslandEcologySample;
import io.github.nidaba.skyforge.world.SkyIslandSurfaceSiteCapabilityCell;
import java.util.Objects;
import java.util.Optional;

/**
 * Content-owned presentation policy over accepted authored ecology and AUTH-0096 hydrologic
 * evidence.
 *
 * <p>This policy selects backend-neutral ecological presentation meaning only. It does not name a
 * Minecraft biome, interpolate hydrology, or define population density.
 */
public final class SkyIslandSurfaceEcologyContentPolicy {

    private SkyIslandSurfaceEcologyContentPolicy() {}

    /**
     * Returns the ecological regime requested for visible surface presentation.
     *
     * <p>The accepted AUTH-0003 regime is preserved unless exact AUTH-0096 evidence already carries
     * freshwater or riparian context. The wetland override intentionally preserves the existing
     * DR-40 nonzero-evidence behavior; it introduces no new numeric cutoff.
     */
    public static SkyIslandEcologyRegime presentationRegime(
            SkyIslandEcologySample authoredEcology,
            Optional<SkyIslandSurfaceSiteCapabilityCell> surfaceEvidence) {
        Objects.requireNonNull(authoredEcology, "authoredEcology");
        surfaceEvidence = Objects.requireNonNull(surfaceEvidence, "surfaceEvidence");

        if (surfaceEvidence
                .filter(SkyIslandSurfaceEcologyContentPolicy::requestsWetlandPresentation)
                .isPresent()) {
            return SkyIslandEcologyRegime.WETLAND;
        }
        return authoredEcology.regime();
    }

    /**
     * Returns whether one exact AUTH-0096 cell carries accepted freshwater/riparian evidence for
     * wetland presentation.
     *
     * <p>Every term is an accepted raw AUTH-0096 value. This method does not combine them into a new
     * wetness scalar or apply an interpolation/tolerance policy.
     */
    public static boolean requestsWetlandPresentation(
            SkyIslandSurfaceSiteCapabilityCell cell) {
        Objects.requireNonNull(cell, "cell");
        return cell.retainedWaterbody()
                || cell.shoreline()
                || cell.waterDepthPotential() > 0.0
                || cell.waterbodyMarginPotential() > 0.0
                || cell.riparianPotential() > 0.0
                || cell.channelRelativeDischarge() > 0.0;
    }
}
