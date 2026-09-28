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
        System.out.println(a);
        assertEquals(a, b);
        assertTrue(a.contains("specimen,islandKey,reachEndCell,status"), a);

        assertTrue(Files.isRegularFile(first.resolve("README.txt")));
    }
}
