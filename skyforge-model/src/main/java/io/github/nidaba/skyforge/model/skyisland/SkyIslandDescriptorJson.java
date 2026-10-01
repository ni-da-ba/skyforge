package io.github.nidaba.skyforge.model.skyisland;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Objects;

/** Canonical deterministic JSON diagnostics for authored sky-island descriptors. */
public final class SkyIslandDescriptorJson {
    /** Serializes one descriptor as deterministic UTF-8 JSON followed by one newline. */
    public byte[] write(SkyIslandDescriptor descriptor) {
        return writeString(descriptor).getBytes(StandardCharsets.UTF_8);
    }

    /** Serializes one descriptor as deterministic JSON followed by one newline. */
    public String writeString(SkyIslandDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        SkyIslandIdentity identity = descriptor.identity();

        StringBuilder json = new StringBuilder();
        json.append("{\"schemaVersion\":").append(descriptor.schemaVersion());
        json.append(",\"identity\":{");
        json.append("\"schemaVersion\":").append(identity.schemaVersion());
        appendHexLongMember(json, "worldSeed", identity.worldSeed());
        appendHexLongMember(json, "provinceKey", identity.provinceKey());
        appendHexLongMember(json, "clusterKey", identity.clusterKey());
        appendHexLongMember(json, "islandKey", identity.islandKey());
        json.append('}');
        appendHexLongMember(json, "authorshipSeed", descriptor.authorshipSeed());
        json.append(",\"morphologyFamily\":\"")
                .append(descriptor.morphologyFamily().identifier())
                .append('"');
        appendHexDoubleMember(json, "nominalRadius", descriptor.nominalRadius());
        appendHexDoubleMember(json, "reliefBudget", descriptor.reliefBudget());
        appendHexDoubleMember(json, "rockCompetence", descriptor.rockCompetence());
        appendHexDoubleMember(json, "permeability", descriptor.permeability());
        appendHexDoubleMember(json, "temperatureTendency", descriptor.temperatureTendency());
        appendHexDoubleMember(json, "moistureTendency", descriptor.moistureTendency());
        appendHexDoubleMember(json, "exposureTendency", descriptor.exposureTendency());
        appendHexDoubleMember(json, "erosionMaturity", descriptor.erosionMaturity());
        appendHexDoubleMember(json, "hydrologicalPotential", descriptor.hydrologicalPotential());
        appendHexDoubleMember(json, "ecologicalPotential", descriptor.ecologicalPotential());
        return json.append("}\n").toString();
    }

    private static void appendHexLongMember(StringBuilder json, String name, long value) {
        json.append(",\"").append(name).append("\":\"0x")
                .append(HexFormat.of().toHexDigits(value)).append('"');
    }

    private static void appendHexDoubleMember(StringBuilder json, String name, double value) {
        json.append(",\"").append(name).append("\":\"")
                .append(Double.toHexString(value)).append('"');
    }
}
