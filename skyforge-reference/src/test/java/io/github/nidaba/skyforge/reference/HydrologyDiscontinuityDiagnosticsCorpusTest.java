package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HydrologyDiscontinuityDiagnosticsCorpusTest {
    @Test
    void corpusIsDeterministicAndContainsPrimary() throws Exception {
        Path first = Path.of("build", "evidence", "hydrology-discontinuity-test-a");
        Path second = Path.of("build", "evidence", "hydrology-discontinuity-test-b");

        HydrologyDiscontinuityDiagnosticsCorpusCli.main(new String[] {first.toString()});
        HydrologyDiscontinuityDiagnosticsCorpusCli.main(new String[] {second.toString()});

        String a = Files.readString(first.resolve("manifest.csv"));
        String b = Files.readString(second.resolve("manifest.csv"));
        assertEquals(a, b);
        assertTrue(a.contains("primary-287"));
        assertTrue(a.contains("cascadeShare"));
    }
}
