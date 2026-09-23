package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.world.SkyIslandVisibleHydrologicRealizationKind;
import io.github.nidaba.skyforge.world.SkyIslandVisibleHydrologicRealizationPlanner;
import org.junit.jupiter.api.Test;

final class SkyforgeHydrologyReferenceReviewFixtureTest {
    @Test
    void fixedReferenceFixtureExercisesTheRequiredVisualHydrology() {
        var fixture = SkyforgeHydrologyReferenceReviewFixture.create();
        var hydrology = SkyIslandVisibleHydrologicRealizationPlanner.plan(fixture.descriptor());

        assertEquals(
                SkyforgeHydrologyReferenceReviewFixture.ISLAND_KEY,
                fixture.descriptor().identity().islandKey());
        assertFalse(hydrology.channels().isEmpty());
        assertFalse(hydrology.retainedWater().isEmpty());
        assertTrue(hydrology.drops().stream().anyMatch(drop ->
                drop.kind() == SkyIslandVisibleHydrologicRealizationKind.EDGE_DISCHARGE));
        assertFalse(fixture.catalog().volumes().isEmpty());
        assertEquals(
                fixture.descriptor(),
                fixture.descriptorsByVolumeId().get(fixture.volume().id()));
    }
}
