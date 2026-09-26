package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HydrologyConfluenceCompatibilityCorpusTest {
    @Test
    void confluenceCompatibilityCorpusIsDeterministicAndExplicit() throws Exception {
        Path first = Path.of("build", "evidence", "hydrology-confluence-compatibility-test-a");
        Path second = Path.of("build", "evidence", "hydrology-confluence-compatibility-test-b");

        HydrologyConfluenceCompatibilityCorpusCli.main(new String[] {first.toString()});
        HydrologyConfluenceCompatibilityCorpusCli.main(new String[] {second.toString()});

        String a = Files.readString(first.resolve("manifest.csv"));
        String b = Files.readString(second.resolve("manifest.csv"));
        assertEquals(a, b);
        assertTrue(a.contains("control-241,241,671,SOLVED"));
        assertTrue(a.contains("confluence-632,632,710,SOLVED"));
        assertTrue(a.contains("stress-512,512,1729,SOLVED"));
        assertTrue(a.contains("retained-83,83,895,INFEASIBLE"));
        assertTrue(a.contains("legacy-control-649,649,995,CASCADE_COUPLED"));
        assertTrue(a.contains("control-77,77,897,CASCADE_COUPLED"));
        assertTrue(Files.isRegularFile(first.resolve("README.txt")));
    }
}
