package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HydrologyWorldWaterProjectionCorpusTest {
    @Test
    void worldWaterProjectionCorpusIsDeterministicAndFailsClosedOnUphillHead()
            throws Exception {
        Path first = Path.of("build", "evidence", "hydrology-world-water-test-a");
        Path second = Path.of("build", "evidence", "hydrology-world-water-test-b");

        HydrologyWorldWaterProjectionCorpusCli.main(new String[] {first.toString()});
        HydrologyWorldWaterProjectionCorpusCli.main(new String[] {second.toString()});

        String summaryA = Files.readString(first.resolve("summary.csv"));
        String summaryB = Files.readString(second.resolve("summary.csv"));
        String reachesA = Files.readString(first.resolve("reaches.csv"));
        String reachesB = Files.readString(second.resolve("reaches.csv"));

        assertEquals(summaryA, summaryB);
        assertEquals(reachesA, reachesB);
        String[] ordinary = summaryA.lines()
                .filter(line -> line.startsWith("ordinary-77,77,"))
                .findFirst()
                .orElseThrow()
                .split(",");
        assertEquals("2", ordinary[2]);
        assertEquals("2", ordinary[3]);
        assertEquals("0", ordinary[4]);
        assertTrue(Integer.parseInt(ordinary[5]) > 0);
        assertTrue(Integer.parseInt(ordinary[6]) > 0);
        assertTrue(Double.parseDouble(ordinary[7]) > 0.0);
        assertTrue(summaryA.contains(
                "primary-287,287,0,0,0,0,0,0.000000000,0.000000000,0.000000000,0.000000000"));
        assertTrue(summaryA.contains(
                "confluence-632,632,0,0,0,0,0,0.000000000,0.000000000,0.000000000,0.000000000"));
        assertTrue(summaryA.contains(
                "lake-609,609,0,0,0,0,0,0.000000000,0.000000000,0.000000000,0.000000000"));
        var ordinaryReaches = reachesA.lines()
                .filter(line -> line.startsWith("ordinary-77,77,"))
                .map(line -> line.split(","))
                .toList();
        assertEquals(2, ordinaryReaches.size());
        for (String[] reach : ordinaryReaches) {
            assertEquals("true", reach[4]);
            assertEquals("\"[]\"", reach[5]);
            assertTrue(Integer.parseInt(reach[7]) > 0);
            assertEquals("0", reach[10]);
            assertEquals("0.000000000", reach[11]);
        }

        assertTrue(Files.isRegularFile(first.resolve("README.txt")));
    }
}
