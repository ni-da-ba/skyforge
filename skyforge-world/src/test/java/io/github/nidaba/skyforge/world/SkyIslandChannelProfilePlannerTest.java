package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import org.junit.jupiter.api.Test;

class SkyIslandChannelProfilePlannerTest {
    private static final long SEED = 0x534B59464F524745L;

    @Test
    void profilesAreDeterministicAndPreserveAcceptedChannelIdentity() {
        SkyIslandDescriptor descriptor = descriptor(77L);
        SkyIslandChannelNetworkPlan network = SkyIslandChannelNetworkPlanner.plan(descriptor);
        SkyIslandChannelProfilePlan first = SkyIslandChannelProfilePlanner.plan(descriptor);
        SkyIslandChannelProfilePlan second = SkyIslandChannelProfilePlanner.plan(descriptor);

        assertEquals(first, second);
        assertEquals(network.segments().size(), first.profiles().size());
        assertFalse(first.profiles().isEmpty());

        for (int i = 0; i < first.profiles().size(); i++) {
            SkyIslandChannelProfile profile = first.profiles().get(i);
            assertEquals(network.segments().get(i), profile.segment());
            assertNormalized(profile.gradientPotential());
            assertNormalized(profile.streamPowerPotential());
            assertNormalized(profile.bankfullWidthPotential());
            assertNormalized(profile.depthPotential());
            assertNormalized(profile.incisionPotential());
            assertTrue(profile.bankfullWidthPotential() > 0.0);
            assertTrue(profile.depthPotential() > 0.0);

            if (profile.kind() == SkyIslandChannelProfileKind.CASCADE) {
                assertTrue(profile.gradientPotential() >= 0.60 - 1.0e-12);
                assertTrue(profile.streamPowerPotential() >= 0.45 - 1.0e-12);
            } else if (profile.kind() == SkyIslandChannelProfileKind.INCISED) {
                assertTrue(profile.incisionPotential() >= 0.58 - 1.0e-12);
            }
        }
    }

    @Test
    void representativeNetworksProduceBoundedProfiles() {
        for (long key : new long[] {77L, 118L, 241L, 512L, 811L, 83L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandChannelNetworkPlan network = SkyIslandChannelNetworkPlanner.plan(descriptor);
            SkyIslandChannelProfilePlan profiles = SkyIslandChannelProfilePlanner.plan(descriptor);

            assertFalse(network.segments().isEmpty());
            assertEquals(network.segments().size(), profiles.profiles().size());
            assertNormalized(profiles.maxWidthPotential());
            assertNormalized(profiles.maxDepthPotential());
            assertNormalized(profiles.maxIncisionPotential());
            assertNormalized(profiles.maxGradientPotential());
        }
    }

    private static void assertNormalized(double value) {
        assertTrue(Double.isFinite(value));
        assertTrue(value >= 0.0 && value <= 1.0);
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(SkyIslandIdentity.of(SEED, 6L, 61L, key));
    }
}
