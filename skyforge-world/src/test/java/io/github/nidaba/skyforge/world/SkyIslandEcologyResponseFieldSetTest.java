package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import org.junit.jupiter.api.Test;

final class SkyIslandEcologyResponseFieldSetTest {
    @Test
    void responseLayerExactlyMatchesAcceptedCompatibilityEcology() {
        for (long islandKey = 0L; islandKey < 24L; islandKey++) {
            SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                    SkyIslandIdentity.of(0x534B59464F524745L, 9L, 47L, islandKey));
            SkyIslandEcologyField ecology = SkyIslandEcologyField.create(descriptor);
            SkyIslandEcologyResponseFieldSet responses =
                    SkyIslandEcologyResponseFieldSet.create(descriptor);
            double radius = descriptor.nominalRadius();

            for (int z = -4; z <= 4; z++) {
                for (int x = -4; x <= 4; x++) {
                    SkyIslandLocalPosition position = new SkyIslandLocalPosition(
                            radius * x / 5.0,
                            radius * z / 5.0);
                    SkyIslandEcologySample accepted = ecology.sample(position);
                    SkyIslandEcologyResponseSample direct = responses.sample(position);

                    assertEquals(
                            Double.doubleToLongBits(accepted.vegetationPotential()),
                            Double.doubleToLongBits(direct.vegetationPotential()));
                    assertEquals(
                            Double.doubleToLongBits(accepted.saturationPotential()),
                            Double.doubleToLongBits(direct.saturationPotential()));
                    assertEquals(
                            Double.doubleToLongBits(accepted.thermalSuitability()),
                            Double.doubleToLongBits(direct.thermalSuitability()));
                }
            }
        }
    }

    @Test
    void individualResponseFieldsExposeTheSameDeterministicValues() {
        SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(1194L, 9L, 47L, 2L));
        SkyIslandEcologyResponseFieldSet responses =
                SkyIslandEcologyResponseFieldSet.create(descriptor);
        SkyIslandLocalPosition position = new SkyIslandLocalPosition(
                descriptor.nominalRadius() * 0.21,
                descriptor.nominalRadius() * -0.17);

        SkyIslandEcologyResponseSample sample = responses.sample(position);
        assertEquals(
                Double.doubleToLongBits(sample.vegetationPotential()),
                Double.doubleToLongBits(responses.vegetationPotential().sample(position)));
        assertEquals(
                Double.doubleToLongBits(sample.saturationPotential()),
                Double.doubleToLongBits(responses.saturationPotential().sample(position)));
        assertEquals(
                Double.doubleToLongBits(sample.thermalSuitability()),
                Double.doubleToLongBits(responses.thermalSuitability().sample(position)));
        assertEquals(sample, responses.sample(position));
    }

    @Test
    void invalidInjectedSourceFieldFailsClosedBeforeResponseEmission() {
        SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(1194L, 9L, 47L, 3L));
        SkyIslandEcologyInputFieldSet inputs = new SkyIslandEcologyInputFieldSet(
                descriptor,
                constant(1.0),
                constant(0.58),
                constant(1.01),
                constant(0.25),
                constant(0.20),
                0.50,
                0.50);

        SkyIslandEcologyResponseFieldSet responses =
                SkyIslandEcologyResponseFieldSet.create(inputs);
        assertThrows(
                IllegalArgumentException.class,
                () -> responses.sample(new SkyIslandLocalPosition(0.0, 0.0)));
    }

    private static SkyIslandSemanticField constant(double value) {
        return ignored -> value;
    }
}
