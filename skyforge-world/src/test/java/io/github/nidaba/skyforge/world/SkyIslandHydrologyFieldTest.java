package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import org.junit.jupiter.api.Test;

class SkyIslandHydrologyFieldTest {
    private static SkyIslandDescriptor descriptor(long islandKey) {
        return SkyIslandDescriptorGenerator.derive(SkyIslandIdentity.of(0x534B59464F524745L, 4L, 41L, islandKey));
    }

    @Test
    void repeatedSamplingIsDeterministicAndNormalized() {
        SkyIslandHydrologyField field = SkyIslandHydrologyField.create(descriptor(17L));
        SkyIslandLocalPosition position = new SkyIslandLocalPosition(12.5, -21.0);
        SkyIslandHydrologySample first = field.sample(position);
        assertEquals(first, field.sample(position));
        assertTrue(first.runoffPotential() >= 0.0 && first.runoffPotential() <= 1.0);
        assertTrue(first.retentionPotential() >= 0.0 && first.retentionPotential() <= 1.0);
        assertTrue(first.drainagePotential() >= 0.0 && first.drainagePotential() <= 1.0);
        assertTrue(first.outflowPotential() >= 0.0 && first.outflowPotential() <= 1.0);
        assertTrue(Math.hypot(first.flowX(), first.flowZ()) <= 1.0000001);
    }

    @Test
    void outsideIslandCarriesNoHydrologicalPlan() {
        SkyIslandDescriptor descriptor = descriptor(23L);
        SkyIslandHydrologySample sample = SkyIslandHydrologyField.create(descriptor)
                .sample(new SkyIslandLocalPosition(descriptor.nominalRadius() * 1.2, 0.0));
        assertEquals(0.0, sample.runoffPotential());
        assertEquals(0.0, sample.retentionPotential());
        assertEquals(0.0, sample.drainagePotential());
        assertEquals(0.0, sample.outflowPotential());
    }

    @Test
    void generatedCorpusContainsDrainageRetentionAndEdgeOutflowSignals() {
        boolean drainage = false;
        boolean retention = false;
        boolean outflow = false;
        for (long key = 0; key < 128; key++) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandHydrologyField field = SkyIslandHydrologyField.create(descriptor);
            double r = descriptor.nominalRadius();
            for (double z = -0.9; z <= 0.9; z += 0.3) {
                for (double x = -0.9; x <= 0.9; x += 0.3) {
                    SkyIslandHydrologySample sample = field.sample(new SkyIslandLocalPosition(x * r, z * r));
                    drainage |= sample.drainagePotential() > 0.35;
                    retention |= sample.retentionPotential() > 0.35;
                    outflow |= sample.outflowPotential() > 0.15;
                }
            }
        }
        assertTrue(drainage, "expected meaningful drainage potential");
        assertTrue(retention, "expected meaningful retention potential");
        assertTrue(outflow, "expected edge outflow potential");
    }
}
