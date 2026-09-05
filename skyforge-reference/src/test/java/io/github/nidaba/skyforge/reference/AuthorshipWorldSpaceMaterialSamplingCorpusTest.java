package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AuthorshipWorldSpaceMaterialSamplingCorpusTest {
    @Test
    void generatesReviewCorpus() throws Exception {
        Path output =
                Path.of(
                        "build",
                        "evidence",
                        "authorship-world-space-material-sampling-v1");
        AuthorshipWorldSpaceMaterialSamplingCorpusCli.main(
                new String[] {output.toString()});

        assertTrue(Files.isRegularFile(output.resolve("index.html")));
        assertTrue(Files.isRegularFile(output.resolve("atlas.png")));
        assertTrue(Files.isRegularFile(output.resolve("manifest.csv")));

        String manifest = Files.readString(output.resolve("manifest.csv"));
        assertTrue(manifest.lines().count() >= 7);
        assertEquals(
                "role,islandKey,morphology,worldCenterX,worldCenterZ,"
                        + "mappableSamples,ownedSamples,authoredVoidSamples,"
                        + "materialSamples,applicationSamples,conditionedSamples,"
                        + "winnerMismatches,applicationKeyMismatches,"
                        + "localFrameMismatches,maxHorizontalError,maxDepthError,"
                        + "uniqueApplicationKeys",
                manifest.lines().findFirst().orElseThrow());

        int totalVoid = 0;
        int totalConditioned = 0;
        int totalUniqueKeys = 0;
        for (String line : manifest.lines().skip(1).toList()) {
            String[] columns = line.split(",", -1);
            assertEquals(17, columns.length);

            int mappable = Integer.parseInt(columns[5]);
            int material = Integer.parseInt(columns[8]);
            int applications = Integer.parseInt(columns[9]);
            int conditioned = Integer.parseInt(columns[10]);
            assertTrue(mappable > 0);
            assertEquals(material, applications);
            assertEquals("0", columns[11]);
            assertEquals("0", columns[12]);
            assertEquals("0", columns[13]);
            assertTrue(
                    Double.parseDouble(columns[14]) <= 1.0e-9,
                    "horizontal world/local round-trip drift exceeded AUTH-0047 tolerance: "
                            + columns[14]);
            assertTrue(
                    Double.parseDouble(columns[15]) <= 1.0e-9,
                    "column-relative depth drift exceeded AUTH-0047 tolerance: "
                            + columns[15]);
            assertTrue(Integer.parseInt(columns[16]) > 0);

            totalVoid += Integer.parseInt(columns[7]);
            totalConditioned += conditioned;
            totalUniqueKeys += Integer.parseInt(columns[16]);
        }

        assertTrue(totalVoid > 0);
        assertTrue(totalConditioned > 0);
        assertTrue(totalUniqueKeys > 6);
    }
}
