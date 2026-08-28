package io.github.nidaba.skyforge.model.skyisland;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Objects;

/** Canonical JSON serialization for semantic sky-island volume descriptors. */
public final class SkyIslandVolumeDescriptorJson {
    /** Serializes one descriptor as deterministic UTF-8 JSON followed by one newline. */
    public byte[] write(SkyIslandVolumeDescriptor descriptor) {
        return writeString(descriptor).getBytes(StandardCharsets.UTF_8);
    }

    /** Serializes one descriptor as deterministic JSON followed by one newline. */
    public String writeString(SkyIslandVolumeDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        StringBuilder json = new StringBuilder();
        appendCommon(json, descriptor);
        if (descriptor.schemaVersion() == SkyIslandVolumeDescriptor.SCHEMA_VERSION_1) {
            appendHexMember(json, "signalAmplitude", descriptor.signalAmplitude());
            appendHexMember(json, "signalScale", descriptor.signalScale());
        } else if (descriptor.schemaVersion() == SkyIslandVolumeDescriptor.SCHEMA_VERSION_2) {
            json.append(",\"morphologyFamily\":\"")
                    .append(descriptor.morphologyFamily().identifier()).append('"');
            appendHexMember(json, "detailAmplitude", descriptor.detailAmplitude());
            appendHexMember(json, "detailScale", descriptor.detailScale());
            appendHexMember(
                    json,
                    "secondaryMorphologyAmplitude",
                    descriptor.secondaryMorphologyAmplitude());
        } else {
            throw new IllegalArgumentException(
                    "unsupported sky-island volume descriptor schema: " + descriptor.schemaVersion());
        }
        return json.append("}\n").toString();
    }

    private static void appendCommon(StringBuilder json, SkyIslandVolumeDescriptor descriptor) {
        json.append("{\"schemaVersion\":").append(descriptor.schemaVersion());
        json.append(",\"seed\":\"0x")
                .append(HexFormat.of().toHexDigits(descriptor.seed())).append('"');
        appendHexMember(json, "centerX", descriptor.centerX());
        appendHexMember(json, "centerZ", descriptor.centerZ());
        appendHexMember(json, "suspensionElevation", descriptor.suspensionElevation());
        appendHexMember(json, "nominalRadius", descriptor.nominalRadius());
        appendHexMember(json, "upperElevation", descriptor.upperElevation());
        appendHexMember(json, "undersideDepth", descriptor.undersideDepth());
        appendHexMember(json, "coastalFalloff", descriptor.coastalFalloff());
        appendHexMember(json, "ridgeAzimuth", descriptor.ridgeAzimuth());
        appendHexMember(json, "ridgeStrength", descriptor.ridgeStrength());
        appendHexMember(json, "undersideTaper", descriptor.undersideTaper());
        appendHexMember(json, "undersideAsymmetry", descriptor.undersideAsymmetry());
    }

    private static void appendHexMember(StringBuilder json, String name, double value) {
        json.append(",\"").append(name).append("\":\"")
                .append(Double.toHexString(value)).append('"');
    }
}
