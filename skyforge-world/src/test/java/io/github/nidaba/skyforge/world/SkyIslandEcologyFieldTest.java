package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandEcologyRegime;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

final class SkyIslandEcologyFieldTest {
    @Test
    void repeatedSamplingIsDeterministicAndNormalized() {
        SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(0x534B59464F524745L, 2L, 17L, 66L));
        SkyIslandEcologyField ecology = SkyIslandEcologyField.create(descriptor);
        SkyIslandLocalPosition position = new SkyIslandLocalPosition(
                descriptor.nominalRadius() * 0.18,
                descriptor.nominalRadius() * -0.27);

        SkyIslandEcologySample first = ecology.sample(position);
        SkyIslandEcologySample second = ecology.sample(position);
        assertEquals(first, second);
        assertNormalized(first.vegetationPotential());
        assertNormalized(first.saturationPotential());
        assertNormalized(first.thermalSuitability());
    }

    @Test
    void representativeAuthoredIslandsProduceMultipleEcologicalRegimes() {
        EnumSet<SkyIslandEcologyRegime> regimes = EnumSet.noneOf(SkyIslandEcologyRegime.class);
        for (long islandKey = 0; islandKey < 128; islandKey++) {
            SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                    SkyIslandIdentity.of(0x534B59464F524745L, 3L, 29L, islandKey));
            SkyIslandEcologyField ecology = SkyIslandEcologyField.create(descriptor);
            double radius = descriptor.nominalRadius();
            for (int z = -4; z <= 4; z++) {
                for (int x = -4; x <= 4; x++) {
                    regimes.add(ecology.sample(new SkyIslandLocalPosition(
                            radius * x / 5.0,
                            radius * z / 5.0)).regime());
                }
            }
        }
        assertTrue(regimes.size() >= 6, "expected broad ecology diversity but got " + regimes);
    }

    @Test
    void outsideIslandIsSemanticallyBarren() {
        SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(11L, 12L, 13L, 14L));
        SkyIslandEcologyField ecology = SkyIslandEcologyField.create(descriptor);
        assertEquals(
                SkyIslandEcologyRegime.COLD_BARREN,
                ecology.sample(new SkyIslandLocalPosition(
                        descriptor.nominalRadius() * 1.2,
                        0.0)).regime());
    }

    private static void assertNormalized(double value) {
        assertTrue(value >= 0.0 && value <= 1.0);
    }
}
