package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AuthorshipMaterialResolutionDecisionCorpusTest {
    @Test
    void generatesReviewCorpus() throws Exception {
        Path output =
                Path.of(
                        "build",
                        "evidence",
                        "authorship-material-resolution-decision-v1");
        AuthorshipMaterialResolutionDecisionCorpusCli.main(
                new String[] {output.toString()});

        assertTrue(Files.isRegularFile(output.resolve("index.html")));
        assertTrue(Files.isRegularFile(output.resolve("atlas.png")));
        assertTrue(Files.isRegularFile(output.resolve("manifest.csv")));

        String manifest = Files.readString(output.resolve("manifest.csv"));
        assertTrue(manifest.lines().count() >= 7);
        String header = manifest.lines().findFirst().orElseThrow();
        assertEquals(
                "role,islandKey,morphology,materialSamples,resolvedUses,"
                        + "semanticWinnerUses,backendTieBreakUses,maxCompatibleCandidates,"
                        + "maxTopSemanticTieCount,uniqueRequests,unstableRequests,"
                        + "meanSelectedMinHeadroom",
                header);

        int backendTieBreakUses = 0;
        for (String line : manifest.lines().skip(1).toList()) {
            String[] columns = line.split(",", -1);
            assertEquals(12, columns.length);
            assertEquals("0", columns[10]);
            backendTieBreakUses += Integer.parseInt(columns[6]);
        }
        assertTrue(backendTieBreakUses > 0);
    }
}
