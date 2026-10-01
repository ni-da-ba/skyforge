package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import org.junit.jupiter.api.Test;

final class SkyIslandSemanticFieldSetTest {
    @Test
    void repeatedEvaluationIsDeterministicAndNormalized() {
        SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(101L, 202L, 303L, 404L));
        SkyIslandSemanticFieldSet fields = SkyIslandSemanticFieldSet.create(descriptor);
        SkyIslandLocalPosition position = new SkyIslandLocalPosition(37.25, -91.5);

        assertField(fields.interiority(), position);
        assertField(fields.elevationTendency(), position);
        assertField(fields.temperature(), position);
        assertField(fields.moisture(), position);
        assertField(fields.exposure(), position);
    }

    @Test
    void boundaryInfluenceFallsToZeroOutsideNominalRadius() {
        SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(11L, 22L, 33L, 44L));
        SkyIslandSemanticFieldSet fields = SkyIslandSemanticFieldSet.create(descriptor);
        double radius = descriptor.nominalRadius();

        assertEquals(1.0, fields.interiority().sample(new SkyIslandLocalPosition(0.0, 0.0)));
        assertEquals(0.0, fields.interiority().sample(new SkyIslandLocalPosition(radius, 0.0)));
        assertEquals(0.0, fields.interiority().sample(new SkyIslandLocalPosition(radius * 1.25, 0.0)));
        assertEquals(0.0, fields.elevationTendency().sample(new SkyIslandLocalPosition(radius * 1.25, 0.0)));
    }

    @Test
    void environmentalFieldsVaryCoherentlyAcrossOneIsland() {
        SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(1234L, 5678L, 9012L, 3456L));
        SkyIslandSemanticFieldSet fields = SkyIslandSemanticFieldSet.create(descriptor);
        double radius = descriptor.nominalRadius();

        SkyIslandLocalPosition north = new SkyIslandLocalPosition(0.0, radius * 0.45);
        SkyIslandLocalPosition south = new SkyIslandLocalPosition(0.0, -radius * 0.45);
        SkyIslandLocalPosition west = new SkyIslandLocalPosition(-radius * 0.45, 0.0);
        SkyIslandLocalPosition east = new SkyIslandLocalPosition(radius * 0.45, 0.0);

        assertNotEquals(fields.temperature().sample(north), fields.temperature().sample(south));
        assertNotEquals(fields.moisture().sample(west), fields.moisture().sample(east));
        assertNotEquals(fields.exposure().sample(west), fields.exposure().sample(east));
    }

    @Test
    void nearbySamplesChangeMoreSmoothlyThanDistantSamplesAcrossCorpus() {
        SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(0x515151L, 7L, 8L, 9L));
        SkyIslandSemanticFieldSet fields = SkyIslandSemanticFieldSet.create(descriptor);
        double radius = descriptor.nominalRadius();
        double nearDelta = radius * 0.002;
        double farDelta = radius * 0.35;

        double nearTotal = 0.0;
        double farTotal = 0.0;
        int samples = 0;
        for (int ix = -6; ix <= 6; ix++) {
            for (int iz = -6; iz <= 6; iz++) {
                double x = radius * ix / 20.0;
                double z = radius * iz / 20.0;
                SkyIslandLocalPosition base = new SkyIslandLocalPosition(x, z);
                SkyIslandLocalPosition near = new SkyIslandLocalPosition(x + nearDelta, z);
                SkyIslandLocalPosition far = new SkyIslandLocalPosition(x + farDelta, z);
                nearTotal += Math.abs(fields.moisture().sample(base) - fields.moisture().sample(near));
                farTotal += Math.abs(fields.moisture().sample(base) - fields.moisture().sample(far));
                samples++;
            }
        }

        assertTrue(nearTotal / samples < farTotal / samples);
    }

    @Test
    void backendPlacementDoesNotEnterEvaluationContract() {
        SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(88L, 77L, 66L, 55L));
        SkyIslandSemanticFieldSet first = SkyIslandSemanticFieldSet.create(descriptor);
        SkyIslandSemanticFieldSet second = SkyIslandSemanticFieldSet.create(descriptor);
        SkyIslandLocalPosition position = new SkyIslandLocalPosition(12.0, 34.0);

        assertEquals(first.elevationTendency().sample(position), second.elevationTendency().sample(position));
        assertEquals(first.temperature().sample(position), second.temperature().sample(position));
        assertEquals(first.moisture().sample(position), second.moisture().sample(position));
        assertEquals(first.exposure().sample(position), second.exposure().sample(position));
        assertEquals(first.interiority().sample(position), second.interiority().sample(position));
    }

    private static void assertField(SkyIslandSemanticField field, SkyIslandLocalPosition position) {
        double first = field.sample(position);
        double second = field.sample(position);
        assertEquals(first, second);
        assertTrue(Double.isFinite(first));
        assertTrue(first >= 0.0 && first <= 1.0);
    }
}
