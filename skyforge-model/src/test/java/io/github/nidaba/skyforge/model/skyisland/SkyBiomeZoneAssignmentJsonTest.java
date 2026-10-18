package io.github.nidaba.skyforge.model.skyisland;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

final class SkyBiomeZoneAssignmentJsonTest {
    private final SkyBiomeZoneAssignmentJson json = new SkyBiomeZoneAssignmentJson();

    @Test
    void writesStableVersionedZoneIdentityAndSkyBiomeId() {
        SkyBiomeZoneAssignment assignment = SkyBiomeZoneAssignment.of(
                SkyBiomeZoneIdentity.of(0x534B59464F524745L, 0x1020304050607080L),
                SkyBiomeCatalog.CLOUD_SHELF_ID);

        assertEquals(
                "{\"schemaVersion\":1,\"zoneIdentity\":{\"schemaVersion\":1,"
                        + "\"worldSeed\":\"0x534b59464f524745\","
                        + "\"zoneKey\":\"0x1020304050607080\"},"
                        + "\"skyBiomeId\":\"skyforge:cloud_shelf\"}\n",
                json.writeString(assignment));
        assertEquals(
                json.writeString(assignment),
                new String(json.write(assignment), StandardCharsets.UTF_8));
    }

    @Test
    void acceptsOpaqueKeysAndRejectsUnsupportedSchemas() {
        assertEquals(
                SkyBiomeZoneIdentity.of(-1L, Long.MIN_VALUE),
                SkyBiomeZoneIdentity.of(-1L, Long.MIN_VALUE));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyBiomeZoneIdentity(2, 1L, 2L));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyBiomeZoneAssignment(
                        2, SkyBiomeZoneIdentity.of(1L, 2L), SkyBiomeCatalog.OPEN_SKY_ID));
    }
}
