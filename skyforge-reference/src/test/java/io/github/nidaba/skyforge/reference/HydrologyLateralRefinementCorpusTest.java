package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HydrologyLateralRefinementCorpusTest {
    @Test
    void lateralRefinementCorpusIsDeterministicAndNeverDeepens() throws Exception {
        Path a = Path.of("build", "evidence", "hydrology-lateral-refinement-test-a");
        Path b = Path.of("build", "evidence", "hydrology-lateral-refinement-test-b");

        HydrologyLateralRefinementCorpusCli.main(new String[] {a.toString()});
        HydrologyLateralRefinementCorpusCli.main(new String[] {b.toString()});

        String summaryA = Files.readString(a.resolve("summary.csv"));
        String summaryB = Files.readString(b.resolve("summary.csv"));
        String reachesA = Files.readString(a.resolve("reaches.csv"));
        String reachesB = Files.readString(b.resolve("reaches.csv"));

        assertEquals(summaryA, summaryB);
        assertEquals(reachesA, reachesB);
        String[] ordinary = summaryA.lines()
                .filter(line -> line.startsWith("ordinary-77,77,"))
                .findFirst()
                .orElseThrow()
                .split(",");
        assertEquals("0", ordinary[2]);
        assertEquals("2", ordinary[3]);
        assertEquals("0", ordinary[4]);
        assertEquals(0, Integer.parseInt(ordinary[8]));
        assertEquals(0.0, Double.parseDouble(ordinary[9]), 0.0);
        assertEquals(0.0, Double.parseDouble(ordinary[10]), 0.0);
        assertTrue(reachesA.contains("709,559,false,"));
        assertTrue(reachesA.contains("1742,1842,false,"));
        assertTrue(Files.isRegularFile(a.resolve("README.txt")));
    }
}
