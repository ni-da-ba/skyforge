package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class SkyforgeProductionComposedCaveIntegratedTopologySourceTest {
    private static final Path PROJECT_DIRECTORY = Path.of(
                    System.getProperty("skyforge.test.projectDirectory", "."))
            .toAbsolutePath()
            .normalize();

    @Test
    void dr50AllowsDownstreamInteriorOccupancyWithoutWeakeningStandaloneCaveProof() throws IOException {
        String source = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeNeoForge1211ProductionComposedCaveDevRuntime.java"));

        assertTrue(source.contains("SkyforgeDr50IntegratedRegionEvidence.enabled()"));
        assertTrue(source.contains("finalEvidence.finalAuthoredAir() <= authoredPositive"));
        assertTrue(source.contains(": finalEvidence.finalAuthoredAir() == authoredPositive"));
        assertTrue(source.contains("authoredDownstreamOccupied"));
    }
}
