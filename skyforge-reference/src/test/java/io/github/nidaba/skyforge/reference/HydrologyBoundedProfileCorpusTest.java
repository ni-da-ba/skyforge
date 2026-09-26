package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HydrologyBoundedProfileCorpusTest {
    @Test
    void corpusIsDeterministicAndSeparatesSolveFromQualification() throws Exception {
        Path first = Path.of("build", "evidence", "hydrology-bounded-profile-test-a");
        Path second = Path.of("build", "evidence", "hydrology-bounded-profile-test-b");

        HydrologyBoundedProfileCorpusCli.main(new String[] {first.toString()});
        HydrologyBoundedProfileCorpusCli.main(new String[] {second.toString()});

        String a = Files.readString(first.resolve("manifest.csv"));
        String b = Files.readString(second.resolve("manifest.csv"));
        String convergenceA = Files.readString(first.resolve("convergence-manifest.csv"));
        String convergenceB = Files.readString(second.resolve("convergence-manifest.csv"));
        String terminalFateA = Files.readString(first.resolve("terminal-fate-manifest.csv"));
        String terminalFateB = Files.readString(second.resolve("terminal-fate-manifest.csv"));
        assertEquals(a, b);
        assertEquals(convergenceA, convergenceB);
        assertEquals(terminalFateA, terminalFateB);
        assertTrue(a.contains("primary-287"));
        assertTrue(a.contains("solverStatus"));
        assertTrue(a.contains("d2Accepted"));
        assertTrue(a.contains("TRANSITION_DEFERRED"));
        assertTrue(convergenceA.contains("coarseObjectivePerLength"));
        assertTrue(convergenceA.contains("mediumObjectivePerLength"));
        assertTrue(convergenceA.contains("fineObjectivePerLength"));
        assertTrue(convergenceA.contains("fineHeadDependentD2Pass"));
        assertTrue(
                convergenceA.lines().count() > 1,
                "F2C evidence must contain at least one solved reach at three resolutions");
        assertTrue(
                a.contains("SOLVED_QUALIFIED") || a.contains("SOLVED_REJECTED"),
                "F2C profile evidence must contain at least one actual bounded-QP solve");
        assertTrue(terminalFateA.contains("fate"));
        assertTrue(terminalFateA.contains("watershedPath"));
    }
}
