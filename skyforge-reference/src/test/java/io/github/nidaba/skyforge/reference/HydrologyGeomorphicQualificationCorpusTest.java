package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HydrologyGeomorphicQualificationCorpusTest {
    @Test
    void qualificationCorpusIsDeterministicAndRetainsFixedSpecimens() throws Exception {
        Path first = Path.of("build", "evidence", "hydrology-qualification-test-a");
        Path second = Path.of("build", "evidence", "hydrology-qualification-test-b");

        HydrologyGeomorphicQualificationCorpusCli.main(new String[] {first.toString()});
        HydrologyGeomorphicQualificationCorpusCli.main(new String[] {second.toString()});

        String a = Files.readString(first.resolve("manifest.csv"));
        String b = Files.readString(second.resolve("manifest.csv"));
        assertEquals(a, b);
        assertTrue(a.contains("primary-287"));
        assertTrue(a.contains("confluence-632"));
        assertTrue(a.contains("retained-83"));
        assertTrue(a.lines().count() == 9);
        assertTrue(Files.isRegularFile(first.resolve("README.txt")));
    }
}
