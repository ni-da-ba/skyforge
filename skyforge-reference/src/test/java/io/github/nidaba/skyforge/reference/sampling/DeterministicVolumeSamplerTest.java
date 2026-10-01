package io.github.nidaba.skyforge.reference.sampling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.kernel.field.ScalarField3;
import org.junit.jupiter.api.Test;

final class DeterministicVolumeSamplerTest {
    private static final VolumeGridSpec GRID =
            new VolumeGridSpec(-3.0, 5.0, -2.0, 6.0, -4.0, 4.0, 9, 7, 11);

    @Test
    void everyScheduleProducesIdenticalCanonicalBinary64Samples() {
        ScalarField3 field = coordinate -> Math.fma(
                coordinate.x(), coordinate.x(),
                Math.fma(3.0, coordinate.y(), -2.0 * coordinate.z()));
        DeterministicVolumeSampler sampler = new DeterministicVolumeSampler();
        ScalarVolumeGrid forward = sampler.sample(field, GRID, SamplingOrder.FORWARD);

        for (SamplingOrder order : SamplingOrder.values()) {
            assertTrue(forward.rawValuesEqual(sampler.sample(field, GRID, order)), order.name());
        }
    }

    @Test
    void classificationUsesStrictPositiveInsideAndPreservesCanonicalLayout() {
        ScalarField3 field = coordinate -> coordinate.y();
        ScalarVolumeGrid density = new DeterministicVolumeSampler()
                .sample(field, GRID, SamplingOrder.PERMUTED);
        OccupancyVolumeGrid occupancy = OccupancyVolumeGrid.fromDensity(density);

        assertEquals(GRID, occupancy.specification());
        assertEquals(9 * 11 * 5, occupancy.solidSampleCount());
        assertTrue(!occupancy.isSolidAt(0, 1, 0));
        assertTrue(occupancy.isSolidAt(0, 2, 0));
    }

    @Test
    void rejectsNonFiniteFinalDensity() {
        ScalarField3 field = coordinate -> coordinate.x() == GRID.minimumX()
                ? Double.POSITIVE_INFINITY
                : 0.0;
        DeterministicVolumeSampler sampler = new DeterministicVolumeSampler();
        assertThrows(
                IllegalArgumentException.class,
                () -> sampler.sample(field, GRID, SamplingOrder.FORWARD));
    }
}
