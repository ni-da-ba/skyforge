package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AuthorshipAuthoredRealizationAssociationCorpusTest {
    @Test
    void generatesReviewCorpus() throws Exception {
        Path output =
                Path.of(
                        "build",
                        "evidence",
                        "authorship-authored-realization-association-v1");
        AuthorshipAuthoredRealizationAssociationCorpusCli.main(
                new String[] {output.toString()});

        assertTrue(Files.isRegularFile(output.resolve("index.html")));
        assertTrue(Files.isRegularFile(output.resolve("atlas.png")));
        assertTrue(Files.isRegularFile(output.resolve("manifest.csv")));

        String manifest = Files.readString(output.resolve("manifest.csv"));
        assertTrue(manifest.lines().count() >= 7);
        assertEquals(
                "role,islandKey,authoredWorld,provinceKey,clusterKey,"
                        + "authoredMorphology,authoredRadius,"
                        + "realizationRoot,groupOrdinal,memberOrdinal,geometrySeed,"
                        + "worldCenterX,worldCenterZ,realizedMorphology,realizedRadius,"
                        + "authoredOwnedSamples,realizedColumnSamples,overlapSamples,"
                        + "authoredOnlySamples,realizedOnlySamples,associationToken",
                manifest.lines().findFirst().orElseThrow());

        Set<String> tokens = new HashSet<>();
        for (String line : manifest.lines().skip(1).toList()) {
            String[] columns = line.split(",", -1);
            assertEquals(21, columns.length);

            assertNotEquals(columns[2], columns[7]);
            assertEquals(columns[5], columns[13]);
            assertEquals(columns[6], columns[14]);

            int authoredOwned = Integer.parseInt(columns[15]);
            int realizedColumns = Integer.parseInt(columns[16]);
            int overlap = Integer.parseInt(columns[17]);
            int authoredOnly = Integer.parseInt(columns[18]);
            int realizedOnly = Integer.parseInt(columns[19]);

            assertTrue(authoredOwned > 0);
            assertTrue(realizedColumns > 0);
            assertTrue(overlap > 0);
            assertEquals(authoredOwned, overlap + authoredOnly);
            assertEquals(realizedColumns, overlap + realizedOnly);
            assertTrue(tokens.add(columns[20]));
            assertTrue(columns[20].startsWith("sfassoc:v1:"));
        }
        assertEquals(6, tokens.size());
    }
}
