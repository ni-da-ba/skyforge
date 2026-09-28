package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HydrologyGeomorphicQualificationCorpusTest {
    @Test
    void qualificationCorpusIsDeterministicAndUsesC3RidgeEvidence() throws Exception {
        Path first = Path.of("build", "evidence", "hydrology-qualification-test-a");
        Path second = Path.of("build", "evidence", "hydrology-qualification-test-b");

        HydrologyGeomorphicQualificationCorpusCli.main(new String[] {first.toString()});
        HydrologyGeomorphicQualificationCorpusCli.main(new String[] {second.toString()});

        String a = Files.readString(first.resolve("qualification-manifest.csv"));
        String b = Files.readString(second.resolve("qualification-manifest.csv"));
        assertEquals(a, b);
        assertTrue(a.contains("ridgeLengthFraction"));
        assertTrue(a.contains("primary-287,287,1090,1758,"));
        assertTrue(a.contains("control-241,241,671,479,"));
        assertTrue(a.contains("control-118,118,700,451,"));
        assertTrue(a.contains("stress-512,512,461,261,"));
        assertTrue(a.contains("pure-incised-2084,2084,700,600,"));
        assertTrue(a.contains("pure-incised-2093,2093,708,559,"));
        assertTrue(Files.isRegularFile(first.resolve("README.txt")));
    }
}
