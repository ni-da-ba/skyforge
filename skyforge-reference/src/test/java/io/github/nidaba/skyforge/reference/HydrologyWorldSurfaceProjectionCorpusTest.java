package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HydrologyWorldSurfaceProjectionCorpusTest {
    @Test
    void worldSurfaceProjectionCorpusIsDeterministicAndFailClosed() throws Exception {
        Path first = Path.of("build", "evidence", "hydrology-world-projection-test-a");
        Path second = Path.of("build", "evidence", "hydrology-world-projection-test-b");

        HydrologyWorldSurfaceProjectionCorpusCli.main(new String[] {first.toString()});
        HydrologyWorldSurfaceProjectionCorpusCli.main(new String[] {second.toString()});

        String summaryA = Files.readString(first.resolve("summary.csv"));
        String summaryB = Files.readString(second.resolve("summary.csv"));
        String reachesA = Files.readString(first.resolve("reaches.csv"));
        String reachesB = Files.readString(second.resolve("reaches.csv"));

        assertEquals(summaryA, summaryB);
        assertEquals(reachesA, reachesB);
        String ordinary77SummaryRows = summaryA.lines()
                .filter(line -> line.startsWith("ordinary-77,77,"))
                .toList()
                .toString();
        assertTrue(
                summaryA.lines().anyMatch(line -> line.startsWith("ordinary-77,77,2,")),
                ordinary77SummaryRows);
        assertTrue(summaryA.contains(
                "primary-287,287,0,0,0,0.000000000,0.000000000,0.000000000,0.000000000"));
        assertTrue(summaryA.contains(
                "lake-609,609,0,0,0,0.000000000,0.000000000,0.000000000,0.000000000"));
        String ordinary77ReachRows = reachesA.lines()
                .filter(line -> line.startsWith("ordinary-77,77,709,559,")
                        || line.startsWith("ordinary-77,77,1742,1842,"))
                .toList()
                .toString();
        assertTrue(ordinary77ReachRows.contains("709,559"));
        assertTrue(ordinary77ReachRows.contains("1742,1842"));

        assertTrue(Files.isRegularFile(first.resolve("README.txt")));
    }
}
