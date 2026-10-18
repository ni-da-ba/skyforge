package io.github.nidaba.skyforge.model.skyisland;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Objects;

/** Canonical deterministic JSON representation for a SkyBiome zone assignment. */
public final class SkyBiomeZoneAssignmentJson {
    /** Serializes one assignment as deterministic UTF-8 JSON followed by one newline. */
    public byte[] write(SkyBiomeZoneAssignment assignment) {
        return writeString(assignment).getBytes(StandardCharsets.UTF_8);
    }

    /** Serializes one assignment with stable hexadecimal world and zone keys. */
    public String writeString(SkyBiomeZoneAssignment assignment) {
        Objects.requireNonNull(assignment, "assignment");
        SkyBiomeZoneIdentity identity = assignment.zoneIdentity();
        return "{\"schemaVersion\":" + assignment.schemaVersion()
                + ",\"zoneIdentity\":{\"schemaVersion\":" + identity.schemaVersion()
                + ",\"worldSeed\":\"0x" + HexFormat.of().toHexDigits(identity.worldSeed())
                + "\",\"zoneKey\":\"0x" + HexFormat.of().toHexDigits(identity.zoneKey())
                + "\"},\"skyBiomeId\":\"" + assignment.skyBiomeId().value() + "\"}\n";
    }
}
