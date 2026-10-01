package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandExteriorConnectedCaveVolumeField;
import io.github.nidaba.skyforge.world.SkyIslandLocalPosition;
import io.github.nidaba.skyforge.world.SkyIslandSubsurfacePosition;
import org.junit.jupiter.api.Test;

final class SkyforgeExteriorConnectedCaveSpatialIndexTest {
    private static final long WORLD_SEED = 0x534B59464F524745L;

    @Test
    void canonicalAuth0030PositiveSamplesAreNeverCulled() {
        var descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(WORLD_SEED, 8L, 81L, 3670L));
        var field = SkyIslandExteriorConnectedCaveVolumeField.create(descriptor);
        var index = SkyforgeExteriorConnectedCaveSpatialIndex.create(field);

        int positiveSamples = 0;
        int radius = (int) Math.ceil(descriptor.nominalRadius());
        for (int x = -radius; x <= radius; x += 12) {
            for (int z = -radius; z <= radius; z += 12) {
                var slice = index.slice(x, x, z, z);
                for (int depthStep = 0; depthStep <= 20; depthStep++) {
                    double depth = depthStep / 20.0;
                    var position = new SkyIslandSubsurfacePosition(
                            new SkyIslandLocalPosition(x, z),
                            depth);
                    if (!field.contains(position)) {
                        continue;
                    }
                    positiveSamples++;
                    assertTrue(
                            slice.mayContainPositive(position),
                            () -> "conservative AUTH-0030 broad phase culled positive sample " + position);
                }
            }
        }

        assertTrue(positiveSamples > 0, "canonical AUTH-0030 representative must exercise the broad phase");
    }

    @Test
    void distantHorizontalSliceHasNoCandidatePrimitiveBounds() {
        var descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(WORLD_SEED, 8L, 81L, 3670L));
        var field = SkyIslandExteriorConnectedCaveVolumeField.create(descriptor);
        var index = SkyforgeExteriorConnectedCaveSpatialIndex.create(field);

        var distant = index.slice(10_000.0, 10_016.0, 10_000.0, 10_016.0);
        assertTrue(distant.candidatePrimitiveBounds() == 0);
    }
}
