package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandExteriorConnectedCaveVolumeField;
import org.junit.jupiter.api.Test;

final class SkyforgeTemporaryConnectedFixtureSearchTest {
    @Test
    void reportSmallestConnectedFixtureCandidate() {
        long worldSeed = 0x534B59464F524745L;
        long provinceKey = 8L;
        long clusterKey = 81L;
        long bestKey = 3670L;
        double bestRadius = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(worldSeed, provinceKey, clusterKey, bestKey)).nominalRadius();
        int bestConnections = 1;

        for (long key = 0L; key < 5000L; key++) {
            var descriptor = SkyIslandDescriptorGenerator.derive(
                    SkyIslandIdentity.of(worldSeed, provinceKey, clusterKey, key));
            if (descriptor.nominalRadius() >= bestRadius) {
                continue;
            }
            var field = SkyIslandExteriorConnectedCaveVolumeField.create(descriptor);
            int connections = field.exposureGeometry().connectionCount();
            if (connections > 0) {
                bestKey = key;
                bestRadius = descriptor.nominalRadius();
                bestConnections = connections;
            }
        }

        throw new AssertionError(
                "SF-IMP-0068 fixture candidate: key=" + bestKey
                        + ", radius=" + bestRadius
                        + ", connections=" + bestConnections);
    }
}
