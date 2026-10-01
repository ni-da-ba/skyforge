package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class SkyforgeDr60HumanReviewPacketTest {
    private static final Path PROJECT_DIRECTORY =
            Path.of(System.getProperty("skyforge.test.projectDirectory", "."))
                    .toAbsolutePath()
                    .normalize();

    @Test
    void packetIsCompleteAndUsesRepairedIntegratedEvidence() throws Exception {
        String packet = Files.readString(PROJECT_DIRECTORY.resolve("../DR60_HUMAN_REVIEW_PACKET.md"));

        assertTrue(packet.contains("P2_DRESSED_REGION_A"));
        assertTrue(packet.contains("44d25cf0f49d3803d5a99df687d0672bf8461b59"));
        assertTrue(packet.contains("35163438331"));
        assertTrue(packet.contains("10474981110"));
        assertTrue(packet.contains("dr50CanonicalCompletedStructures=0"));
        assertTrue(packet.contains("f69ae93eeda1196e"));
        assertTrue(packet.contains("9c788b382232c405"));
        assertTrue(packet.contains("## 7. Known limitations"));
        assertTrue(packet.contains("## 8. Intentionally unresolved human/product questions"));
        assertTrue(packet.contains("## 9. Nonblocking anomalies and evidence notes"));
        assertFalse(packet.contains("c61b945e0450800"));
        assertFalse(packet.contains("1901b269ae9384ad0c6bfe8a1ca839a20cee7d7a"));
    }

    @Test
    void manualReviewProfileReusesQualifiedWorldWithoutAcceptanceAutoExit() throws Exception {
        String build = Files.readString(PROJECT_DIRECTORY.resolve("build.gradle.kts"));
        int start = build.indexOf("create(\"dr60HumanReviewClient\")");
        assertTrue(start >= 0);
        int end = build.indexOf("\n        create(\"", start + 1);
        assertTrue(end > start);
        String profile = build.substring(start, end);

        assertTrue(profile.contains("run-dr50-auto-b"));
        assertTrue(profile.contains("--quickPlaySingleplayer"));
        assertTrue(profile.contains("productionComposedCaveReload"));
        assertTrue(profile.contains("dr40ProductionEcologyReload"));
        assertTrue(profile.contains("dr50IntegratedRegionReload"));
        assertTrue(profile.contains("productionComposedCaveExpectedResultFile"));
        assertFalse(profile.contains("acceptanceHarness"));
        assertFalse(profile.contains("acceptanceMode"));
        assertFalse(profile.contains("acceptanceCase"));
        assertFalse(profile.contains("acceptanceResultFile"));
    }
}
