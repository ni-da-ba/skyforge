package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HydrologyDiscontinuityDiagnosticsCorpusTest {
    @TempDir Path temp;

    @Test
    void corpusIsDeterministicAndContainsPrimary() throws Exception {
        Path a = temp.resolve("a");
        Path b = temp.resolve("b");
        HydrologyDiscontinuityDiagnosticsCorpusCli.main(new String[] {a.toString()});
        HydrologyDiscontinuityDiagnosticsCorpusCli.main(new String[] {b.toString()});
        String first = Files.readString(a.resolve("manifest.csv"));
        String second = Files.readString(b.resolve("manifest.csv"));
        assertEquals(first, second);
        assertTrue(first.contains("primary-287"));
        assertTrue(first.contains("cascadeShare"));
    }
}
