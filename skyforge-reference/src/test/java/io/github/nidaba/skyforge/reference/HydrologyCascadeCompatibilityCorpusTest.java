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
        assertTrue(a.contains("primary-287,287,1090,1758,7,8,1,SOLVED"));
        assertTrue(a.contains("primary-287,287,1090,1758,11,15,4,SOLVED"));
        assertTrue(a.contains("primary-287,287,1090,1758,27,32,5,SOLVED"));
        assertTrue(a.contains("legacy-control-649,649,710,995,4,5,1,INFEASIBLE"));
        assertTrue(a.contains("legacy-control-649,649,995,2095,16,20,4,SOLVED"));
        assertTrue(a.contains("retained-83,83,895,595,1,5,4,SOLVED"));
        assertTrue(a.contains("stress-512,512,406,306,0,2,2,BOUNDARY_COUPLED"));
        assertTrue(Files.isRegularFile(first.resolve("README.txt")));
    }
}
