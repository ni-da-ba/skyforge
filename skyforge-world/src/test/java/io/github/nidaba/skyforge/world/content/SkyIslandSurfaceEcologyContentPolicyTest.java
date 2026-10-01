package io.github.nidaba.skyforge.world.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandEcologyRegime;
import io.github.nidaba.skyforge.world.SkyIslandEcologySample;
import io.github.nidaba.skyforge.world.SkyIslandLocalPosition;
import io.github.nidaba.skyforge.world.SkyIslandSurfaceSiteCapabilityCell;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

final class SkyIslandSurfaceEcologyContentPolicyTest {

    @Test
    void dryOrMissingHydrologicEvidencePreservesAcceptedAuth0003Regime() {
        SkyIslandEcologySample authored =
                new SkyIslandEcologySample(
                        SkyIslandEcologyRegime.OPEN_GRASSLAND, 0.7, 0.2, 0.8);

        assertEquals(
                SkyIslandEcologyRegime.OPEN_GRASSLAND,
                SkyIslandSurfaceEcologyContentPolicy.presentationRegime(
                        authored,
                        Optional.empty()));
        assertEquals(
                SkyIslandEcologyRegime.OPEN_GRASSLAND,
                SkyIslandSurfaceEcologyContentPolicy.presentationRegime(
                        authored,
                        Optional.of(cell(false, false, 0.0, 0.0, 0.0, 0.0))));
    }

    @Test
    void acceptedRawFreshwaterOrRiparianEvidenceRequestsWetlandPresentation() {
        SkyIslandEcologySample authored =
                new SkyIslandEcologySample(
                        SkyIslandEcologyRegime.DRY_SCRUB, 0.3, 0.1, 0.9);

        var riparian = cell(false, false, 0.0, 0.0, 0.25, 0.0);
        var margin = cell(false, false, 0.0, 0.35, 0.0, 0.0);
        var channel = cell(false, false, 0.0, 0.0, 0.0, 0.45);
        var retainedWater = cell(true, false, 0.40, 0.0, 0.0, 0.0);

        assertTrue(SkyIslandSurfaceEcologyContentPolicy.requestsWetlandPresentation(riparian));
        assertTrue(SkyIslandSurfaceEcologyContentPolicy.requestsWetlandPresentation(margin));
        assertTrue(SkyIslandSurfaceEcologyContentPolicy.requestsWetlandPresentation(channel));
        assertTrue(
                SkyIslandSurfaceEcologyContentPolicy.requestsWetlandPresentation(retainedWater));

        for (var evidence : java.util.List.of(riparian, margin, channel, retainedWater)) {
            assertEquals(
                    SkyIslandEcologyRegime.WETLAND,
                    SkyIslandSurfaceEcologyContentPolicy.presentationRegime(
                            authored,
                            Optional.of(evidence)));
        }
    }

    @Test
    void dryCellDoesNotAcquireWetlandMeaning() {
        assertFalse(SkyIslandSurfaceEcologyContentPolicy.requestsWetlandPresentation(
                cell(false, false, 0.0, 0.0, 0.0, 0.0)));
    }

    private static SkyIslandSurfaceSiteCapabilityCell cell(
            boolean retainedWaterbody,
            boolean shoreline,
            double waterDepth,
            double margin,
            double riparian,
            double channelDischarge) {
        return new SkyIslandSurfaceSiteCapabilityCell(
                0,
                new SkyIslandLocalPosition(0.0, 0.0),
                true,
                OptionalDouble.of(0.0),
                0.8,
                1.0,
                1.0,
                1.0,
                OptionalDouble.of(0.1),
                OptionalDouble.of(0.1),
                OptionalDouble.of(0.1),
                OptionalDouble.of(0.05),
                0.0,
                retainedWaterbody,
                shoreline,
                waterDepth,
                margin,
                riparian,
                channelDischarge,
                0.0);
    }
}
