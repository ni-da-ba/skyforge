package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HydrologyGeomorphicDiagnosticsCorpusTest {
    @Test
    void diagnosticsCorpusIsDeterministicAndContainsFixedProvingGrounds() throws Exception {
        Path first = Path.of("build", "evidence", "hydrology-diagnostics-test-a");
        Path second = Path.of("build", "evidence", "hydrology-diagnostics-test-b");

        HydrologyGeomorphicDiagnosticsCorpusCli.main(new String[] {first.toString()});
        HydrologyGeomorphicDiagnosticsCorpusCli.main(new String[] {second.toString()});

        String a = Files.readString(first.resolve("manifest.csv"));
        String b = Files.readString(second.resolve("manifest.csv"));
        assertEquals(a, b);
        assertTrue(a.contains("primary-287"));
        assertTrue(a.contains("confluence-632"));
        assertTrue(a.contains("retained-83"));
        assertTrue(a.lines().count() == 9);
        assertTrue(a.contains("maxLateralRecoveryGrade"));
        assertTrue(a.contains("maxContainmentDeficitWorld"));
        assertTrue(a.contains("maxDepthToBankfullWidth"));
        assertTrue(a.contains("maxReliefToValleyWidth"));
        assertTrue(a.contains("maxExcavationBurden"));
        assertTrue(a.contains("maxExcavationVolume"));
        assertTrue(a.contains("maxCurvatureWidthRatio"));
        assertTrue(a.contains("maxLongitudinalGradeWorld"));
        assertTrue(Files.isRegularFile(first.resolve("README.txt")));
    }
}
