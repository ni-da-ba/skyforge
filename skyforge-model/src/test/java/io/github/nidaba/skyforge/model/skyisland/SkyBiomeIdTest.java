package io.github.nidaba.skyforge.model.skyisland;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class SkyBiomeIdTest {
    @Test
    void parsesAndPersistsCanonicalBackendNeutralIdentity() {
        SkyBiomeId id = SkyBiomeId.parse("skyforge:open_sky/windward");

        assertEquals("skyforge", id.namespace());
        assertEquals("open_sky/windward", id.path());
        assertEquals("skyforge:open_sky/windward", id.value());
        assertEquals(id.value(), id.toString());
        assertEquals(id, SkyBiomeId.parse(id.value()));
    }

    @Test
    void ordersIdsByCanonicalNamespaceAndPath() {
        assertEquals(
                -1,
                SkyBiomeId.parse("skyforge:alpine").compareTo(SkyBiomeId.parse("skyforge:temperate")));
    }

    @Test
    void rejectsMalformedAndNonCanonicalIdentities() {
        assertThrows(IllegalArgumentException.class, () -> SkyBiomeId.parse("open_sky"));
        assertThrows(IllegalArgumentException.class, () -> SkyBiomeId.parse(":open_sky"));
        assertThrows(IllegalArgumentException.class, () -> SkyBiomeId.parse("skyforge:"));
        assertThrows(IllegalArgumentException.class, () -> SkyBiomeId.parse("skyforge:open:sky"));
        assertThrows(IllegalArgumentException.class, () -> SkyBiomeId.parse("Skyforge:open_sky"));
        assertThrows(IllegalArgumentException.class, () -> SkyBiomeId.parse("skyforge:open sky"));
        assertThrows(IllegalArgumentException.class, () -> SkyBiomeId.parse("skyforge:open//sky"));
    }
}
