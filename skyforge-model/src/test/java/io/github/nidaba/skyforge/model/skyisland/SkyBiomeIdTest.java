package io.github.nidaba.skyforge.model.skyisland;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class SkyBiomeIdTest {
    @Test
    void parsesAndPersistsCanonicalBackendNeutralIdentity() {
        SkyBiomeId id = SkyBiomeId.parse("skyforge:sample/alpha");

        assertEquals("skyforge", id.namespace());
        assertEquals("sample/alpha", id.path());
        assertEquals("skyforge:sample/alpha", id.value());
        assertEquals(id.value(), id.toString());
        assertEquals(id, SkyBiomeId.parse(id.value()));
    }

    @Test
    void ordersIdsByCanonicalNamespaceAndPath() {
        assertEquals(
                -1,
                SkyBiomeId.parse("skyforge:a").compareTo(SkyBiomeId.parse("skyforge:b")));
    }

    @Test
    void rejectsMalformedAndNonCanonicalIdentities() {
        assertThrows(IllegalArgumentException.class, () -> SkyBiomeId.parse("sample"));
        assertThrows(IllegalArgumentException.class, () -> SkyBiomeId.parse(":sample"));
        assertThrows(IllegalArgumentException.class, () -> SkyBiomeId.parse("skyforge:"));
        assertThrows(IllegalArgumentException.class, () -> SkyBiomeId.parse("skyforge:open:sky"));
        assertThrows(IllegalArgumentException.class, () -> SkyBiomeId.parse("Skyforge:sample"));
        assertThrows(IllegalArgumentException.class, () -> SkyBiomeId.parse("skyforge:open sky"));
        assertThrows(IllegalArgumentException.class, () -> SkyBiomeId.parse("skyforge:open//sky"));
    }
}
