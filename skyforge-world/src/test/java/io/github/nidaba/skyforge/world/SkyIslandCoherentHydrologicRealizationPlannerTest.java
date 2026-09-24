package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SkyIslandCoherentHydrologicRealizationPlannerTest {
    private static final long SEED = 0x534B59464F524745L;
    private static final double EPSILON = 1.0e-10;

    @Test
    void genuinelyUnprunedNetworksRemainExactlyEquivalentDownstream() {
        boolean exercised = false;
        for (long key : new long[] {77L, 118L, 241L, 83L, 287L, 649L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandCoherentHydrologicRealizationPlan coherent =
                    SkyIslandCoherentHydrologicRealizationPlanner.plan(descriptor);
            int rawReachCount = SkyIslandChannelProfilePlanner.plan(descriptor).profiles().size();
            if (coherent.channels().retainedReachCount() != rawReachCount) {
                continue;
            }
            exercised = true;

            SkyIslandRiparianCorridorPlan riparian =
                    SkyIslandRiparianCorridorPlanner.plan(descriptor);
            SkyIslandNaturalizedChannelPlan naturalized =
                    SkyIslandNaturalizedChannelPlanner.plan(descriptor);
            SkyIslandChannelDropPlan localizedDrops =
                    SkyIslandChannelDropPlanner.localize(
                            descriptor,
                            SkyIslandChannelDropPlanner.plan(descriptor),
                            naturalized);
            SkyIslandHydrologicTerrainInfluencePlan localizedInfluence =
                    SkyIslandHydrologicTerrainInfluencePlanner.plan(
                            descriptor,
                            coherent.channels().profiles(),
                            riparian,
                            localizedDrops);
            SkyIslandHydrologicTerrainSurfacePlan localizedSurface =
                    SkyIslandHydrologicTerrainSurfacePlanner.plan(
                            descriptor,
                            localizedInfluence,
                            riparian);

            assertEquals(riparian, coherent.riparian());
            assertEquals(localizedDrops, coherent.drops());
            assertEquals(localizedInfluence, coherent.terrainInfluence());
            assertEquals(localizedSurface, coherent.terrainSurface());
            assertEquals(naturalized, coherent.naturalizedChannels());
        }
        assertTrue(exercised, "representative corpus must retain at least one genuinely unpruned network");
    }

    @Test
    void prunedRakeNetworksLoseOnlyChannelDerivedDownstreamSupport() {
        for (long key : new long[] {512L, 811L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandCoherentHydrologicRealizationPlan coherent =
                    SkyIslandCoherentHydrologicRealizationPlanner.plan(descriptor);
            SkyIslandHydrologicTerrainSurfacePlan rawSurface =
                    SkyIslandHydrologicTerrainSurfacePlanner.plan(descriptor);

            assertEquals(
                    coherent.channels().retainedReachCount(),
                    coherent.naturalizedChannels().paths().size());
            assertTrue(coherent.riparian().cellCount()
                    <= SkyIslandRiparianCorridorPlanner.plan(descriptor).cellCount());
            assertNotEquals(rawSurface, coherent.terrainSurface());

            Set<Integer> retainedSources = coherent.channels().profiles().stream()
                    .map(profile -> profile.segment().sourceCellIndex())
                    .collect(Collectors.toSet());
            for (SkyIslandRiparianCell cell : coherent.riparian().cells()) {
                assertTrue(retainedSources.contains(cell.channelSourceCellIndex()));
            }
            for (SkyIslandChannelDrop drop : coherent.drops().drops()) {
                if (drop.kind() != SkyIslandChannelDropKind.EDGE_FALL) {
                    assertTrue(retainedSources.contains(drop.sourceCellIndex()));
                }
            }
        }
    }

    @Test
    void coherentContinuousFieldReproducesItsCoarseSurfaceAnchorsExactly() {
        for (long key : new long[] {512L, 811L}) {
            SkyIslandCoherentHydrologicRealizationPlan coherent =
                    SkyIslandCoherentHydrologicRealizationPlanner.plan(descriptor(key));
            SkyIslandContinuousHydrologicTerrainField field = coherent.continuousTerrain();

            for (SkyIslandHydrologicTerrainSurfaceCell cell : coherent.terrainSurface().cells()) {
                assertEquals(
                        cell.adjustedElevationPotential(),
                        field.sample(cell.position()),
                        EPSILON);
            }
        }
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(SkyIslandIdentity.of(SEED, 6L, 61L, key));
    }
}
