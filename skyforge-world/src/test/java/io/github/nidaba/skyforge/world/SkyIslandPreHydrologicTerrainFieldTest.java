package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import org.junit.jupiter.api.Test;

class SkyIslandPreHydrologicTerrainFieldTest {
    private static final long SEED = 0x534B59464F524745L;

    @Test
    void preHydrologicFieldIsExactlyTheAuthoredElevationBeforeLegacyHydrologyResponse() {
        for (long key : new long[] {77L, 118L, 287L, 632L, 811L}) {
            SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                    SkyIslandIdentity.of(SEED, 8L, 81L, key));
            SkyIslandPreHydrologicTerrainField field =
                    SkyIslandPreHydrologicTerrainField.create(descriptor);
            SkyIslandSemanticField expected =
                    SkyIslandSemanticFieldSet.create(descriptor).elevationTendency();
            double radius = descriptor.nominalRadius();

            for (int z = 0; z <= 16; z++) {
                for (int x = 0; x <= 16; x++) {
                    SkyIslandLocalPosition position = new SkyIslandLocalPosition(
                            -radius + 2.0 * radius * x / 16.0,
                            -radius + 2.0 * radius * z / 16.0);
                    assertEquals(expected.sample(position), field.sample(position), 0.0);
                }
            }
        }
    }
}
