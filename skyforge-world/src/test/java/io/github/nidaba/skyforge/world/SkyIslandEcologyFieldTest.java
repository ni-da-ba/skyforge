package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void explicitCurrentInputsPreserveAcceptedEcologyExactly() {
        for (long islandKey = 0; islandKey < 16; islandKey++) {
            SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                    SkyIslandIdentity.of(0x534B59464F524745L, 7L, 43L, islandKey));
            SkyIslandEcologyField compatibility = SkyIslandEcologyField.create(descriptor);
            SkyIslandEcologyField explicit = SkyIslandEcologyField.create(
                    SkyIslandEcologyInputFieldSet.fromCurrentSemantics(descriptor));
            double radius = descriptor.nominalRadius();

            for (int z = -3; z <= 3; z++) {
                for (int x = -3; x <= 3; x++) {
                    SkyIslandLocalPosition position = new SkyIslandLocalPosition(
                            radius * x / 4.0,
                            radius * z / 4.0);
                    assertEquals(compatibility.sample(position), explicit.sample(position));
                }
            }
        }
    }

    @Test
    void explicitEnvironmentalFieldsDriveEcologicalResponse() {
        SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(0x534B59464F524745L, 5L, 31L, 8L));

        SkyIslandEcologyInputFieldSet dryInputs = new SkyIslandEcologyInputFieldSet(
                descriptor,
                constant(1.0),
                constant(0.58),
                constant(0.15),
                constant(0.25),
                constant(0.20),
                0.10,
                0.80);
        SkyIslandEcologyInputFieldSet wetInputs = new SkyIslandEcologyInputFieldSet(
                descriptor,
                constant(1.0),
                constant(0.58),
                constant(0.85),
                constant(0.25),
                constant(0.20),
                0.90,
                0.80);

        SkyIslandLocalPosition origin = new SkyIslandLocalPosition(0.0, 0.0);
        SkyIslandEcologySample dry = SkyIslandEcologyField.create(dryInputs).sample(origin);
        SkyIslandEcologySample wet = SkyIslandEcologyField.create(wetInputs).sample(origin);

        assertTrue(wet.saturationPotential() > dry.saturationPotential());
        assertTrue(wet.vegetationPotential() > dry.vegetationPotential());
    }

    @Test
    void invalidExplicitEnvironmentalFieldFailsClosed() {
        SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(11L, 12L, 13L, 14L));
        SkyIslandEcologyInputFieldSet invalid = new SkyIslandEcologyInputFieldSet(
                descriptor,
                constant(1.0),
                ignored -> Double.NaN,
                constant(0.5),
                constant(0.5),
                constant(0.5),
                0.5,
                0.5);

        SkyIslandEcologyField ecology = SkyIslandEcologyField.create(invalid);
        assertThrows(
                IllegalArgumentException.class,
                () -> ecology.sample(new SkyIslandLocalPosition(0.0, 0.0)));
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

    private static SkyIslandSemanticField constant(double value) {
        return ignored -> value;
    }

    private static void assertNormalized(double value) {
        assertTrue(value >= 0.0 && value <= 1.0);
    }
}
