package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HydrologyComponentTerrainCandidateCorpusTest {
    @Test
    void componentTerrainCandidateCorpusIsDeterministicAndFailClosed() throws Exception {
        Path first = Path.of("build", "evidence", "hydrology-component-terrain-test-a");
        Path second = Path.of("build", "evidence", "hydrology-component-terrain-test-b");

        HydrologyComponentTerrainCandidateCorpusCli.main(
                new String[] {first.toString()});
        HydrologyComponentTerrainCandidateCorpusCli.main(
                new String[] {second.toString()});

        String componentsA = Files.readString(first.resolve("component-candidates.csv"));
        String componentsB = Files.readString(second.resolve("component-candidates.csv"));
        String reachesA = Files.readString(first.resolve("realized-reaches.csv"));
        String reachesB = Files.readString(second.resolve("realized-reaches.csv"));

        assertEquals(componentsA, componentsB);
        assertEquals(reachesA, reachesB);
        assertTrue(componentsA.contains(
                "ordinary-77,77,559,QUALIFIED,REALIZED,"));
        assertTrue(componentsA.contains(
                "ordinary-77,77,1842,QUALIFIED,REALIZED,"));
        assertTrue(componentsA.contains(
                "primary-287,287,1758,PHYSICAL_REJECTION,EXCLUDED,"));
        assertTrue(componentsA.contains(
                "lake-609,609,1397,PHYSICAL_REJECTION,EXCLUDED,"));
        assertTrue(reachesA.contains("ordinary-77,77,559,709,559,"));
        assertTrue(reachesA.contains("ordinary-77,77,1842,1742,1842,"));
        assertTrue(Files.isRegularFile(first.resolve("README.txt")));
    }
}
