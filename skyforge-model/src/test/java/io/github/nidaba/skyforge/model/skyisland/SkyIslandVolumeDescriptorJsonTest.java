package io.github.nidaba.skyforge.model.skyisland;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class SkyIslandVolumeDescriptorJsonTest {
    private final SkyIslandVolumeDescriptorJson codec = new SkyIslandVolumeDescriptorJson();

    @Test
    void schemaOneEncodingRemainsByteCompatibleWithAcceptedEvidenceFormat() {
        SkyIslandVolumeDescriptor descriptor = new SkyIslandVolumeDescriptor(
                1, 0L, 0.0, 0.0, 256.0, 256.0, 96.0, 128.0, 64.0,
                0.0, 0.5, 0.5, 0.0, 0.0, 32.0);

        assertEquals(
                "{\"schemaVersion\":1,\"seed\":\"0x0000000000000000\""
                        + ",\"centerX\":\"0x0.0p0\""
                        + ",\"centerZ\":\"0x0.0p0\""
                        + ",\"suspensionElevation\":\"0x1.0p8\""
                        + ",\"nominalRadius\":\"0x1.0p8\""
                        + ",\"upperElevation\":\"0x1.8p6\""
                        + ",\"undersideDepth\":\"0x1.0p7\""
                        + ",\"coastalFalloff\":\"0x1.0p6\""
                        + ",\"ridgeAzimuth\":\"0x0.0p0\""
                        + ",\"ridgeStrength\":\"0x1.0p-1\""
                        + ",\"undersideTaper\":\"0x1.0p-1\""
                        + ",\"undersideAsymmetry\":\"0x0.0p0\""
                        + ",\"signalAmplitude\":\"0x0.0p0\""
                        + ",\"signalScale\":\"0x1.0p5\"}\n",
                codec.writeString(descriptor));
    }

    @Test
    void schemaTwoUsesSemanticMorphologyNames() {
        SkyIslandVolumeDescriptor descriptor = SkyIslandVolumeDescriptor.schema2(
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
                SkyIslandMorphologyFamily.TABLELAND,
                0.25,
                32.0,
                0.75);

        String json = codec.writeString(descriptor);
        assertTrue(json.contains("\"schemaVersion\":2"));
        assertTrue(json.contains("\"morphologyFamily\":\"tableland\""));
        assertTrue(json.contains("\"detailAmplitude\":\"0x1.0p-2\""));
        assertTrue(json.contains("\"detailScale\":\"0x1.0p5\""));
        assertTrue(json.contains("\"secondaryMorphologyAmplitude\":\"0x1.8p-1\""));
        assertFalse(json.contains("\"signalAmplitude\""));
        assertFalse(json.contains("\"signalScale\""));
    }
}
