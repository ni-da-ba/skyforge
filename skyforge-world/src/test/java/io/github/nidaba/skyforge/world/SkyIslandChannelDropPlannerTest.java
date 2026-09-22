package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SkyIslandChannelDropPlannerTest {
    private static final long SEED = 0x534B59464F524745L;

    @Test
    void dropPlanIsDeterministicNormalizedAndKeepsOnlyRoutedEdgeOutflows() {
        SkyIslandDescriptor descriptor = descriptor(77L);
        SkyIslandChannelDropPlan first = SkyIslandChannelDropPlanner.plan(descriptor);
        SkyIslandChannelDropPlan second = SkyIslandChannelDropPlanner.plan(descriptor);
        SkyIslandHydrologicFeaturePlan features = SkyIslandHydrologicFeaturePlanner.plan(descriptor);
        SkyIslandChannelProfilePlan profiles = SkyIslandChannelProfilePlanner.plan(descriptor);
        Map<Integer, SkyIslandChannelProfile> profileBySource = new HashMap<>();
        profiles.profiles().forEach(profile -> profileBySource.put(profile.segment().sourceCellIndex(), profile));

        assertEquals(first, second);
        assertTrue(
                first.count(SkyIslandChannelDropKind.EDGE_FALL)
                        <= features.count(SkyIslandHydrologicFeatureKind.EDGE_WATERFALL));
        assertFalse(first.drops().isEmpty());
        Set<Integer> routedSourceCells = profiles.profiles().stream()
                .map(profile -> profile.segment().sourceCellIndex())
                .collect(java.util.stream.Collectors.toSet());
        Set<Integer> routedTerminalCells = profiles.profiles().stream()
                .map(profile -> profile.segment().downstreamCellIndex())
                .filter(index -> !routedSourceCells.contains(index))
                .collect(java.util.stream.Collectors.toSet());

        for (SkyIslandChannelDrop drop : first.drops()) {
            assertTrue(drop.dropPotential() >= 0.0 && drop.dropPotential() <= 1.0);
            assertTrue(drop.dischargePotential() >= 0.0 && drop.dischargePotential() <= 1.0);
            assertTrue(drop.persistencePotential() >= 0.0 && drop.persistencePotential() <= 1.0);
            assertTrue(drop.plungePoolPotential() >= 0.0 && drop.plungePoolPotential() <= 1.0);
            if (drop.kind() == SkyIslandChannelDropKind.EDGE_FALL) {
                assertEquals(-1, drop.downstreamCellIndex());
                assertEquals(0.0, drop.plungePoolPotential());
                assertTrue(routedTerminalCells.contains(drop.sourceCellIndex()),
                        "visible edge discharge must terminate a retained routed channel");
            } else {
                assertTrue(profileBySource.containsKey(drop.sourceCellIndex()));
                assertEquals(
                        profileBySource.get(drop.sourceCellIndex()).segment().downstreamCellIndex(),
                        drop.downstreamCellIndex());
            }
        }
    }

    @Test
    void representativeNetworksProduceSparseSeparatedInteriorDropsAndEdgeFalls() {
        long interior = 0;
        long edges = 0;
        for (long key : new long[] {77L, 118L, 241L, 512L, 811L, 83L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandChannelProfilePlan profiles = SkyIslandChannelProfilePlanner.plan(descriptor);
            SkyIslandChannelDropPlan drops = SkyIslandChannelDropPlanner.plan(descriptor);
            List<SkyIslandChannelDrop> interiorDrops = new ArrayList<>();
            for (SkyIslandChannelDrop drop : drops.drops()) {
                if (drop.kind() != SkyIslandChannelDropKind.EDGE_FALL) {
                    interiorDrops.add(drop);
                }
            }
            assertTrue(interiorDrops.size() <= Math.max(1, (int) Math.ceil(profiles.profiles().size() * 0.08)));
            double minimumSeparation = descriptor.nominalRadius() * 0.08;
            for (int i = 0; i < interiorDrops.size(); i++) {
                for (int j = i + 1; j < interiorDrops.size(); j++) {
                    SkyIslandLocalPosition a = interiorDrops.get(i).position();
                    SkyIslandLocalPosition b = interiorDrops.get(j).position();
                    double dx = a.x() - b.x();
                    double dz = a.z() - b.z();
                    assertTrue(dx * dx + dz * dz >= minimumSeparation * minimumSeparation - 1.0e-10);
                }
            }
            interior += interiorDrops.size();
            edges += drops.count(SkyIslandChannelDropKind.EDGE_FALL);
        }
        assertTrue(interior > 0);
        assertTrue(edges > 0);
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(SkyIslandIdentity.of(SEED, 6L, 61L, key));
    }
}
