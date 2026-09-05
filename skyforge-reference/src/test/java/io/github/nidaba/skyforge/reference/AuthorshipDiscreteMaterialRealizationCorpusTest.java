package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AuthorshipDiscreteMaterialRealizationCorpusTest {
    @Test
    void generatesReviewCorpus() throws Exception {
        Path output =
                Path.of(
                        "build",
                        "evidence",
                        "authorship-discrete-material-realization-v1");
        AuthorshipDiscreteMaterialRealizationCorpusCli.main(
                new String[] {output.toString()});

        assertTrue(Files.isRegularFile(output.resolve("index.html")));
        assertTrue(Files.isRegularFile(output.resolve("atlas.png")));
        assertTrue(Files.isRegularFile(output.resolve("manifest.csv")));

        String manifest = Files.readString(output.resolve("manifest.csv"));
        assertTrue(manifest.lines().count() >= 7);
        assertEquals(
                "role,islandKey,morphology,materialSamples,"
                        + "primaryStructuralWins,secondaryStructuralWins,"
                        + "primaryFinalWins,secondaryFinalWins,alterationWins,"
                        + "waterWins,mineralWins,conditionedWinnerSamples,"
                        + "multiActiveConditionedSamples,determinismMismatches,"
                        + "uniqueWinnerBindings,horizontalNeighborTransitions",
                manifest.lines().findFirst().orElseThrow());

        int conditionedWins = 0;
        for (String line : manifest.lines().skip(1).toList()) {
            String[] columns = line.split(",", -1);
            assertEquals(16, columns.length);
            assertEquals("0", columns[13]);
            conditionedWins += Integer.parseInt(columns[11]);
        }
        assertTrue(conditionedWins > 0);
    }
}
