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
        assertTrue(summaryA.contains(
                "ordinary-77,77,2,0,2,9005,2483,0.002619649,2.805964392,-5.419589927,2.365899859"));
        assertTrue(summaryA.contains(
                "primary-287,287,0,0,0,0,0,0.000000000,0.000000000,0.000000000,0.000000000"));
        assertTrue(summaryA.contains(
                "confluence-632,632,0,0,0,0,0,0.000000000,0.000000000,0.000000000,0.000000000"));
        assertTrue(summaryA.contains(
                "lake-609,609,0,0,0,0,0,0.000000000,0.000000000,0.000000000,0.000000000"));
        assertTrue(reachesA.contains(
                "ordinary-77,77,709,559,false,\"[UPHILL_HEAD]\",35,35,"
                        + "269.463026857,282.533331955,1,0.168878403,0.084049826,0.210443604"));
        assertTrue(reachesA.contains(
                "ordinary-77,77,1742,1842,false,\"[UPHILL_HEAD]\",24,24,"
                        + "273.172341988,281.340191248,2,0.034208932,0.017275969,0.246125042"));
        assertTrue(Files.isRegularFile(first.resolve("README.txt")));
    }
}
