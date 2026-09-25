package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import org.junit.jupiter.api.Test;

class SkyIslandContinuousWaterbodyPlannerTest {
    private static final long SEED = 0x534B59464F524745L;
    private static final double EPSILON = 1.0e-10;

    @Test
    void continuousBasinCandidatesAreDeterministicAndRespectSpillAuthority() {
        for (long key : new long[] {83L, 287L, 512L, 632L, 811L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandContinuousWaterbodyPlan first =
                    SkyIslandContinuousWaterbodyPlanner.plan(descriptor);
            SkyIslandContinuousWaterbodyPlan second =
                    SkyIslandContinuousWaterbodyPlanner.plan(descriptor);
            assertEquals(first, second);

            for (SkyIslandContinuousWaterbodyBasin basin : first.basins()) {
                assertTrue(basin.waterSurfacePotential()
                        <= basin.spillSurfacePotential() + EPSILON);
                assertTrue(basin.connectedSampleCount() > 0);
                assertTrue(basin.approximateArea() > 0.0);
                assertTrue(basin.maximumDepthPotential() >= 0.0);
                assertFalse(
                        basin.sourceCandidate().kind() == SkyIslandWaterbodyKind.WETLAND,
                        "wetlands must remain deferred saturated-margin semantics");
            }
            for (SkyIslandWaterbodyCandidate wetland : first.deferredWetlands()) {
                assertEquals(SkyIslandWaterbodyKind.WETLAND, wetland.kind());
            }
        }
    }

    @Test
    void retainedControlProducesFineTerrainDerivedShorelineCrossings() {
        SkyIslandContinuousWaterbodyPlan plan =
                SkyIslandContinuousWaterbodyPlanner.plan(descriptor(83L));
        assertFalse(plan.basins().isEmpty(), "retained-water control should exercise open water");

        SkyIslandContinuousWaterbodyBasin basin = plan.basins().getFirst();
        assertFalse(
                basin.shorelineCrossings().isEmpty(),
                "continuous open-water basin should expose interpolated contour crossings");

        double coarseSpacing = SkyIslandWatershedPlanner.plan(descriptor(83L)).spacing();
        assertTrue(basin.sampleSpacing() < coarseSpacing);
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 6L, 61L, key));
    }
}
