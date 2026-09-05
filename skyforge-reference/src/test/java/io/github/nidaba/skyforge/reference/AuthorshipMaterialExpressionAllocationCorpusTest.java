package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AuthorshipMaterialExpressionAllocationCorpusTest {
    @Test
    void generatesReviewCorpus() throws Exception {
        Path output =
                Path.of(
                        "build",
                        "evidence",
                        "authorship-material-expression-allocation-v1");
        AuthorshipMaterialExpressionAllocationCorpusCli.main(
                new String[] {output.toString()});

        assertTrue(Files.isRegularFile(output.resolve("index.html")));
        assertTrue(Files.isRegularFile(output.resolve("atlas.png")));
        assertTrue(Files.isRegularFile(output.resolve("manifest.csv")));

        String manifest = Files.readString(output.resolve("manifest.csv"));
        assertTrue(manifest.lines().count() >= 7);
        assertEquals(
                "role,islandKey,morphology,materialSamples,primaryMean,primaryMin,"
                        + "secondaryMean,secondaryMax,alterationMean,alterationMax,"
                        + "waterMean,waterMax,mineralMean,mineralMax,"
                        + "conditionedOverlapSamples,matrixBudgetViolations,"
                        + "ceilingViolations,uniqueDecisions",
                manifest.lines().findFirst().orElseThrow());

        int overlapSamples = 0;
        for (String line : manifest.lines().skip(1).toList()) {
            String[] columns = line.split(",", -1);
            assertEquals(18, columns.length);
            assertEquals("0", columns[15]);
            assertEquals("0", columns[16]);
            overlapSamples += Integer.parseInt(columns[14]);
        }
        assertTrue(overlapSamples > 0);
    }
}
