package io.github.nidaba.skyforge.model.skyisland;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class SkyIslandDescriptorTest {
    @Test
    void identityIsHierarchicalStableAndPlacementFree() {
        SkyIslandIdentity identity = SkyIslandIdentity.of(
                Long.MIN_VALUE,
                Long.MAX_VALUE,
                -17L,
                42L);

        assertAll(
                () -> assertEquals(SkyIslandIdentity.SCHEMA_VERSION, identity.schemaVersion()),
                () -> assertEquals(Long.MIN_VALUE, identity.worldSeed()),
                () -> assertEquals(Long.MAX_VALUE, identity.provinceKey()),
                () -> assertEquals(-17L, identity.clusterKey()),
                () -> assertEquals(42L, identity.islandKey()),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new SkyIslandIdentity(2, 1L, 2L, 3L, 4L)));
    }

    @Test
    void validatesSemanticRangesWithoutBackendPlacementState() {
        SkyIslandDescriptor descriptor = descriptor();

        assertAll(
                () -> assertEquals(SkyIslandDescriptor.SCHEMA_VERSION, descriptor.schemaVersion()),
                () -> assertEquals(SkyIslandMorphologyFamily.TABLELAND, descriptor.morphologyFamily()),
                () -> assertEquals(256.0, descriptor.nominalRadius()),
                () -> assertEquals(96.0, descriptor.reliefBudget()),
                () -> assertDoesNotThrow(() -> new SkyIslandDescriptor(
                        1,
                        SkyIslandIdentity.of(1L, 2L, 3L, 4L),
                        Long.MIN_VALUE,
                        SkyIslandMorphologyFamily.MASSIF,
                        1.0,
                        1.0,
                        0.0,
                        1.0,
                        0.0,
                        1.0,
                        0.0,
                        1.0,
                        0.0,
                        1.0)));
    }

    @Test
    void rejectsInvalidScaleAndNormalizedSemanticValues() {
        SkyIslandIdentity identity = SkyIslandIdentity.of(1L, 2L, 3L, 4L);

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new SkyIslandDescriptor(
                        1, identity, 5L, SkyIslandMorphologyFamily.MASSIF,
                        0.0, 1.0, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5)),
                () -> assertThrows(IllegalArgumentException.class, () -> new SkyIslandDescriptor(
                        1, identity, 5L, SkyIslandMorphologyFamily.MASSIF,
                        1.0, Double.NaN, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5)),
                () -> assertThrows(IllegalArgumentException.class, () -> new SkyIslandDescriptor(
                        1, identity, 5L, SkyIslandMorphologyFamily.MASSIF,
                        1.0, 1.0, -0.01, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5)),
                () -> assertThrows(IllegalArgumentException.class, () -> new SkyIslandDescriptor(
                        1, identity, 5L, SkyIslandMorphologyFamily.MASSIF,
                        1.0, 1.0, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 1.01)));
    }

    @Test
    void canonicalJsonIsDeterministicInspectableAndContainsNoPlacementCoordinates() {
        SkyIslandDescriptorJson json = new SkyIslandDescriptorJson();
        String first = json.writeString(descriptor());
        String second = json.writeString(descriptor());

        assertAll(
                () -> assertEquals(first, second),
                () -> assertTrue(first.startsWith("{\"schemaVersion\":1,\"identity\":")),
                () -> assertTrue(first.contains("\"morphologyFamily\":\"tableland\"")),
                () -> assertTrue(first.contains("\"hydrologicalPotential\"")),
                () -> assertTrue(first.contains("\"ecologicalPotential\"")),
                () -> assertFalse(first.contains("centerX")),
                () -> assertFalse(first.contains("centerZ")),
                () -> assertFalse(first.contains("suspensionElevation")),
                () -> assertTrue(first.endsWith("}\n")));
    }

    private static SkyIslandDescriptor descriptor() {
        return new SkyIslandDescriptor(
                1,
                SkyIslandIdentity.of(11L, 12L, 13L, 14L),
                15L,
                SkyIslandMorphologyFamily.TABLELAND,
                256.0,
                96.0,
                0.8,
                0.3,
                0.4,
                0.7,
                0.6,
                0.2,
                0.75,
                0.65);
    }
}
