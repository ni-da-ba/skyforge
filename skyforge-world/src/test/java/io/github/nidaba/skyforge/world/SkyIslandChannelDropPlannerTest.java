package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SkyIslandChannelDropPlannerTest {
    private static final long SEED = 0x534B59464F524745L;

    @Test
    void dropPlanIsDeterministicNormalizedAndPreservesEdgeOutflowCandidates() {
        SkyIslandDescriptor descriptor = descriptor(77L);
        SkyIslandChannelDropPlan first = SkyIslandChannelDropPlanner.plan(descriptor);
        SkyIslandChannelDropPlan second = SkyIslandChannelDropPlanner.plan(descriptor);
        SkyIslandHydrologicFeaturePlan features = SkyIslandHydrologicFeaturePlanner.plan(descriptor);
        SkyIslandChannelProfilePlan profiles = SkyIslandChannelProfilePlanner.plan(descriptor);
        Map<Integer, SkyIslandChannelProfile> profileBySource = new HashMap<>();
        profiles.profiles().forEach(profile -> profileBySource.put(profile.segment().sourceCellIndex(), profile));

        assertEquals(first, second);
        assertEquals(
                features.count(SkyIslandHydrologicFeatureKind.EDGE_WATERFALL),
                first.count(SkyIslandChannelDropKind.EDGE_FALL));
        assertFalse(first.drops().isEmpty());

        for (SkyIslandChannelDrop drop : first.drops()) {
            assertTrue(drop.dropPotential() >= 0.0 && drop.dropPotential() <= 1.0);
            assertTrue(drop.dischargePotential() >= 0.0 && drop.dischargePotential() <= 1.0);
            assertTrue(drop.persistencePotential() >= 0.0 && drop.persistencePotential() <= 1.0);
            assertTrue(drop.plungePoolPotential() >= 0.0 && drop.plungePoolPotential() <= 1.0);
            if (drop.kind() == SkyIslandChannelDropKind.EDGE_FALL) {
                assertEquals(-1, drop.downstreamCellIndex());
                assertEquals(0.0, drop.plungePoolPotential());
            } else {
                assertTrue(profileBySource.containsKey(drop.sourceCellIndex()));
                assertEquals(
                        profileBySource.get(drop.sourceCellIndex()).segment().downstreamCellIndex(),
                        drop.downstreamCellIndex());
            }
        }
    }

    @Test
    void representativeNetworksProduceSparseInteriorDropsAndEdgeFalls() {
        long interior = 0;
        long edges = 0;
        for (long key : new long[] {77L, 118L, 241L, 512L, 811L, 83L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandChannelProfilePlan profiles = SkyIslandChannelProfilePlanner.plan(descriptor);
            SkyIslandChannelDropPlan drops = SkyIslandChannelDropPlanner.plan(descriptor);
            long interiorCount = drops.count(SkyIslandChannelDropKind.CASCADE_STEP)
                    + drops.count(SkyIslandChannelDropKind.WATERFALL);
            assertTrue(interiorCount <= Math.max(1, (int) Math.ceil(profiles.profiles().size() * 0.08)));
            interior += interiorCount;
            edges += drops.count(SkyIslandChannelDropKind.EDGE_FALL);
        }
        assertTrue(interior > 0);
        assertTrue(edges > 0);
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(SkyIslandIdentity.of(SEED, 6L, 61L, key));
    }
}
