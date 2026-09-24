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
    void localizedInteriorDropsPreserveSelectionAndMoveOntoNaturalizedPaths() {
        SkyIslandDescriptor descriptor = descriptor(287L);
        var profiles = SkyIslandChannelProfilePlanner.plan(descriptor).profiles();
        var selected = SkyIslandChannelDropPlanner.plan(descriptor, profiles);
        var naturalized = SkyIslandNaturalizedChannelPlanner.plan(descriptor, profiles);
        var localized = SkyIslandChannelDropPlanner.localize(
                descriptor, selected, naturalized);

        assertEquals(selected.drops().size(), localized.drops().size());
        Map<String, SkyIslandChannelDrop> selectedByIdentity = new HashMap<>();
        for (SkyIslandChannelDrop drop : selected.drops()) {
            selectedByIdentity.put(
                    drop.kind() + ":" + drop.sourceCellIndex() + ":" + drop.downstreamCellIndex(),
                    drop);
        }

        for (SkyIslandChannelDrop drop : localized.drops()) {
            SkyIslandChannelDrop original = selectedByIdentity.get(
                    drop.kind() + ":" + drop.sourceCellIndex() + ":" + drop.downstreamCellIndex());
            assertTrue(original != null);
            assertEquals(original.dropPotential(), drop.dropPotential());
            assertEquals(original.dischargePotential(), drop.dischargePotential());
            assertEquals(original.persistencePotential(), drop.persistencePotential());
            assertEquals(original.plungePoolPotential(), drop.plungePoolPotential());
            if (drop.kind() == SkyIslandChannelDropKind.EDGE_FALL) {
                assertEquals(original.position(), drop.position());
                continue;
            }

            var path = naturalized.paths().stream()
                    .filter(candidate ->
                            candidate.profile().segment().sourceCellIndex() == drop.sourceCellIndex()
                                    && candidate.profile().segment().downstreamCellIndex()
                                            == drop.downstreamCellIndex())
                    .findFirst()
                    .orElseThrow();
            double distance = distanceToPath(drop.position(), path);
            assertTrue(distance <= 1.0e-9);
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

    private static double distanceToPath(
            SkyIslandLocalPosition position,
            SkyIslandNaturalizedChannelPath path) {
        double best = Double.POSITIVE_INFINITY;
        var points = path.points();
        for (int index = 1; index < points.size(); index++) {
            var a = points.get(index - 1);
            var b = points.get(index);
            double dx = b.x() - a.x();
            double dz = b.z() - a.z();
            double lengthSquared = dx * dx + dz * dz;
            if (lengthSquared <= 1.0e-12) {
                continue;
            }
            double t = Math.max(0.0, Math.min(
                    1.0,
                    ((position.x() - a.x()) * dx + (position.z() - a.z()) * dz)
                            / lengthSquared));
            double x = a.x() + t * dx;
            double z = a.z() + t * dz;
            best = Math.min(best, Math.hypot(position.x() - x, position.z() - z));
        }
        return best;
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(SkyIslandIdentity.of(SEED, 6L, 61L, key));
    }
}
