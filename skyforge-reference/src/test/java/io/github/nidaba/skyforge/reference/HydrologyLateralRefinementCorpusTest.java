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
        assertTrue(summaryA.contains("ordinary-77,77,1,2,0,8969,"), summaryA);
        assertTrue(summaryA.contains(",0,0.168878403,"));
        assertTrue(reachesA.contains("709,559,true,0.168878403,"));
        assertTrue(reachesA.contains("1742,1842,false,0.000000000,"));
        assertTrue(Files.isRegularFile(a.resolve("README.txt")));
    }
}
