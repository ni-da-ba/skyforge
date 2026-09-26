package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HydrologyCascadeCompatibilityCorpusTest {
    @Test
    void cascadeCompatibilityCorpusIsDeterministic() throws Exception {
        Path first = Path.of("build", "evidence", "hydrology-cascade-compatibility-test-a");
        Path second = Path.of("build", "evidence", "hydrology-cascade-compatibility-test-b");

        HydrologyCascadeCompatibilityCorpusCli.main(new String[] {first.toString()});
        HydrologyCascadeCompatibilityCorpusCli.main(new String[] {second.toString()});

        String a = Files.readString(first.resolve("manifest.csv"));
        String b = Files.readString(second.resolve("manifest.csv"));
        assertEquals(a, b);
        assertTrue(a.contains("primary-287"));
        assertTrue(a.contains("authoredMaxDropWorld"));
        assertTrue(a.contains("SOLVED"));
        assertTrue(Files.isRegularFile(first.resolve("README.txt")));
    }
}
