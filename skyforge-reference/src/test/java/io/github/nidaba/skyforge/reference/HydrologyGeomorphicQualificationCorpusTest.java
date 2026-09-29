package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.util.List;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HydrologyGeomorphicQualificationCorpusTest {
    @Test
    void qualificationCorpusIsDeterministicAndUsesC3RidgeEvidence() throws Exception {
        Path first = Path.of("build", "evidence", "hydrology-qualification-test-a");
        Path second = Path.of("build", "evidence", "hydrology-qualification-test-b");

        HydrologyGeomorphicQualificationCorpusCli.main(new String[] {first.toString()});
        HydrologyGeomorphicQualificationCorpusCli.main(new String[] {second.toString()});

        String a = Files.readString(first.resolve("qualification-manifest.csv"));
        String b = Files.readString(second.resolve("qualification-manifest.csv"));
        assertEquals(a, b);
        assertTrue(a.contains("ridgeLengthFraction"));
        List<String> expectedRows = List.of(
                "primary-287,287,1090,1758,false,CENTERLINE_LOWERING|LATERAL_RECOVERY_GRADE|BANK_CONTAINMENT|RELIEF_TO_VALLEY_WIDTH|EXCAVATION_BURDEN,0.101411175",
                "control-241,241,671,479,false,RIDGE_OCCUPANCY,0.375000000",
                "control-118,118,700,451,true,,0.318667086",
                "stress-512,512,461,261,true,,0.375000000",
                "pure-incised-2084,2084,700,600,true,,0.000000000",
                "pure-incised-2093,2093,708,559,false,BANK_CONTAINMENT,0.000000000");
        List<String> missingRows = expectedRows.stream()
                .filter(row -> !a.contains(row))
                .toList();
        assertTrue(
                missingRows.isEmpty(),
                () -> "hydrology qualification corpus changed; missing="
                        + missingRows
                        + "; actual manifest:\\n"
                        + a);
        assertTrue(Files.isRegularFile(first.resolve("README.txt")));
    }
}
