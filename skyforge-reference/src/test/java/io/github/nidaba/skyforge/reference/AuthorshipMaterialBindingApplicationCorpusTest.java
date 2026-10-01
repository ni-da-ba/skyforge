package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AuthorshipMaterialBindingApplicationCorpusTest {
    @Test
    void generatesReviewCorpus() throws Exception {
        Path output =
                Path.of(
                        "build",
                        "evidence",
                        "authorship-material-binding-application-v1");
        AuthorshipMaterialBindingApplicationCorpusCli.main(
                new String[] {output.toString()});

        assertTrue(Files.isRegularFile(output.resolve("index.html")));
        assertTrue(Files.isRegularFile(output.resolve("atlas.png")));
        assertTrue(Files.isRegularFile(output.resolve("manifest.csv")));

        String manifest = Files.readString(output.resolve("manifest.csv"));
        assertTrue(manifest.lines().count() >= 7);
        assertEquals(
                "role,islandKey,morphology,materialSamples,appliedSamples,"
                        + "voidSamples,voidApplications,missingBindings,reuseViolations,"
                        + "repeatMismatches,uniqueWinnerBindings,uniqueReferenceMaterials,"
                        + "conditionedApplications",
                manifest.lines().findFirst().orElseThrow());

        int conditionedApplications = 0;
        int distinctMaterialCountSum = 0;
        for (String line : manifest.lines().skip(1).toList()) {
            String[] columns = line.split(",", -1);
            assertEquals(13, columns.length);
            assertEquals(columns[3], columns[4]);
            assertEquals("0", columns[6]);
            assertEquals("0", columns[7]);
            assertEquals("0", columns[8]);
            assertEquals("0", columns[9]);
            assertTrue(Integer.parseInt(columns[10]) > 0);
            assertTrue(Integer.parseInt(columns[11]) > 0);
            distinctMaterialCountSum += Integer.parseInt(columns[11]);
            conditionedApplications += Integer.parseInt(columns[12]);
        }

        assertTrue(distinctMaterialCountSum > 6);
        assertTrue(conditionedApplications > 0);
    }
}
