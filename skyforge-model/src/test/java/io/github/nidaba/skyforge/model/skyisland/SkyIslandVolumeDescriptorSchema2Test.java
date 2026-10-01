package io.github.nidaba.skyforge.model.skyisland;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class SkyIslandVolumeDescriptorSchema2Test {
    @Test
    void legacyConstructorRemainsSchemaOneAndCouplesHistoricalAmplitude() {
        SkyIslandVolumeDescriptor descriptor = new SkyIslandVolumeDescriptor(
                1, 7L, 0.0, 0.0, 256.0, 256.0, 96.0, 128.0, 64.0,
                Math.PI / 6.0, 0.65, 0.60, 0.25, 0.4, 32.0);

        assertAll(
                () -> assertEquals(SkyIslandVolumeDescriptor.SCHEMA_VERSION_1, descriptor.schemaVersion()),
                () -> assertNull(descriptor.morphologyFamily()),
                () -> assertFalse(descriptor.hasSemanticMorphologyFamily()),
                () -> assertEquals(0.4, descriptor.detailAmplitude()),
                () -> assertEquals(0.4, descriptor.secondaryMorphologyAmplitude()),
                () -> assertEquals(32.0, descriptor.detailScale()));
    }

    @Test
    void schemaTwoOwnsFamilyAndIndependentMorphologyAmplitudes() {
        SkyIslandVolumeDescriptor descriptor = descriptor(
                SkyIslandMorphologyFamily.BASIN, 0.25, 0.75);

        assertAll(
                () -> assertEquals(SkyIslandVolumeDescriptor.SCHEMA_VERSION_2, descriptor.schemaVersion()),
                () -> assertEquals(SkyIslandMorphologyFamily.BASIN, descriptor.morphologyFamily()),
                () -> assertTrue(descriptor.hasSemanticMorphologyFamily()),
                () -> assertEquals(0.25, descriptor.detailAmplitude()),
                () -> assertEquals(0.75, descriptor.secondaryMorphologyAmplitude()));
    }

    @Test
    void schemaTwoRejectsMissingFamilyAndOutOfRangeSecondaryAmplitude() {
        assertAll(
                () -> assertThrows(NullPointerException.class, () -> SkyIslandVolumeDescriptor.schema2(
                        0L, 0.0, 0.0, 256.0, 256.0, 96.0, 128.0, 64.0,
                        0.0, 0.65, 0.60, 0.25, null, 0.5, 32.0, 0.5)),
                () -> assertThrows(IllegalArgumentException.class, () -> new SkyIslandVolumeDescriptor(
                        2, 0L, 0.0, 0.0, 256.0, 256.0, 96.0, 128.0, 64.0,
                        0.0, 0.65, 0.60, 0.25, 0.5, 32.0,
                        SkyIslandMorphologyFamily.MASSIF, 1.01)));
    }

    @Test
    void builtInFamilyIdentifiersRoundTripExactly() {
        for (SkyIslandMorphologyFamily family : SkyIslandMorphologyFamily.values()) {
            assertEquals(family, SkyIslandMorphologyFamily.fromIdentifier(family.identifier()));
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> SkyIslandMorphologyFamily.fromIdentifier("user-defined-wacky-island"));
    }

    private static SkyIslandVolumeDescriptor descriptor(
            SkyIslandMorphologyFamily family,
            double detail,
            double secondary) {
        return SkyIslandVolumeDescriptor.schema2(
                0x534b59464f524745L,
                0.0,
                0.0,
                256.0,
                256.0,
                96.0,
                128.0,
                64.0,
                Math.PI / 6.0,
                0.65,
                0.60,
                0.25,
                family,
                detail,
                32.0,
                secondary);
    }
}
