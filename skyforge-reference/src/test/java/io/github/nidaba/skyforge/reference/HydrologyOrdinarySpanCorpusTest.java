package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HydrologyOrdinarySpanCorpusTest {
    @Test
    void ordinarySpanCorpusIsDeterministicAndExplicit() throws Exception {
        Path first = Path.of("build", "evidence", "hydrology-ordinary-span-test-a");
        Path second = Path.of("build", "evidence", "hydrology-ordinary-span-test-b");

        HydrologyOrdinarySpanCorpusCli.main(new String[] {first.toString()});
        HydrologyOrdinarySpanCorpusCli.main(new String[] {second.toString()});

        String a = Files.readString(first.resolve("manifest.csv"));
        String b = Files.readString(second.resolve("manifest.csv"));
        assertEquals(a, b);
        assertTrue(a.contains("primary-287"));
        assertTrue(a.contains("confluence-632"));
        assertTrue(a.contains("retained-83"));
        assertTrue(a.contains("BOUNDARY_DEFERRED"));
        assertTrue(a.contains("SOLVED_"));
        assertTrue(Files.isRegularFile(first.resolve("README.txt")));
    }
}
