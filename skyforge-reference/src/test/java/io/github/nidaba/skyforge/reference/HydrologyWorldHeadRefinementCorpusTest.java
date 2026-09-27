package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HydrologyWorldHeadRefinementCorpusTest {
    @Test
    void worldHeadRefinementCorpusIsDeterministicAndNumericallyFailClosed() throws Exception {
        Path a = Path.of("build", "evidence", "hydrology-world-head-refinement-test-a");
        Path b = Path.of("build", "evidence", "hydrology-world-head-refinement-test-b");

        HydrologyWorldHeadRefinementCorpusCli.main(new String[] {a.toString()});
        HydrologyWorldHeadRefinementCorpusCli.main(new String[] {b.toString()});

        String summaryA = Files.readString(a.resolve("summary.csv"));
        String summaryB = Files.readString(b.resolve("summary.csv"));
        String componentsA = Files.readString(a.resolve("components.csv"));
        String componentsB = Files.readString(b.resolve("components.csv"));

        assertEquals(summaryA, summaryB);
        assertEquals(componentsA, componentsB);
        assertTrue(summaryA.contains("ordinary-77,77,2,"));
        assertTrue(summaryA.contains("primary-287,287,0,0,0"));
        assertTrue(summaryA.contains("confluence-632,632,0,0,0"));
        assertTrue(summaryA.contains("lake-609,609,0,0,0"));
        assertFalse(componentsA.contains("NUMERICAL_FAILURE"));
        assertTrue(Files.isRegularFile(a.resolve("README.txt")));
    }
}
