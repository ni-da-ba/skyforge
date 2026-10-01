package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuthorshipMultiIslandMaterialCompositionCorpusTest {
    @Test
    void generatesReviewCorpus() throws Exception {
        Path output =
                Path.of(
                        "build",
                        "evidence",
                        "authorship-multi-island-material-composition-v1");
        AuthorshipMultiIslandMaterialCompositionCorpusCli.main(
                new String[] {output.toString()});

        assertTrue(Files.isRegularFile(output.resolve("index.html")));
        assertTrue(Files.isRegularFile(output.resolve("atlas.png")));
        assertTrue(Files.isRegularFile(output.resolve("manifest.csv")));

        String manifest = Files.readString(output.resolve("manifest.csv"));
        assertEquals(7, manifest.lines().count());
        assertEquals(
                "scenario,status,conservativeCandidates,physicalOccupants,authoredOwners,"
                        + "samplePresent,materialPresent,authoredVoid,applicationPresent,"
                        + "providerCalls,uniqueIslandKey",
                manifest.lines().findFirst().orElseThrow());

        Map<String, String[]> rows = new HashMap<>();
        for (String line : manifest.lines().skip(1).toList()) {
            String[] columns = line.split(",", -1);
            assertEquals(11, columns.length);
            rows.put(columns[0], columns);
        }

        assertEquals(6, rows.size());

        String[] material = rows.get("UNIQUE_MATERIAL");
        assertEquals("UNIQUE", material[1]);
        assertEquals("1", material[4]);
        assertEquals("true", material[5]);
        assertEquals("true", material[6]);
        assertEquals("false", material[7]);
        assertEquals("true", material[8]);
        assertTrue(Integer.parseInt(material[9]) > 0);
        assertTrue(!material[10].isEmpty());

        String[] authoredVoid = rows.get("UNIQUE_VOID");
        assertEquals("UNIQUE", authoredVoid[1]);
        assertEquals("true", authoredVoid[5]);
        assertEquals("false", authoredVoid[6]);
        assertEquals("true", authoredVoid[7]);
        assertEquals("false", authoredVoid[8]);
        assertEquals("0", authoredVoid[9]);

        String[] empty = rows.get("EMPTY_SKY");
        assertEquals("NONE", empty[1]);
        assertEquals("false", empty[5]);
        assertEquals("false", empty[8]);
        assertEquals("0", empty[9]);
        assertEquals("", empty[10]);

        String[] ambiguous = rows.get("AMBIGUOUS_OVERLAP");
        assertEquals("AMBIGUOUS", ambiguous[1]);
        assertEquals("2", ambiguous[4]);
        assertEquals("false", ambiguous[5]);
        assertEquals("false", ambiguous[8]);
        assertEquals("0", ambiguous[9]);
        assertEquals("", ambiguous[10]);

        String[] lower = rows.get("STACK_LOWER");
        String[] upper = rows.get("STACK_UPPER");
        assertEquals("UNIQUE", lower[1]);
        assertEquals("UNIQUE", upper[1]);
        assertEquals("1", lower[4]);
        assertEquals("1", upper[4]);
        assertTrue(!lower[10].isEmpty());
        assertTrue(!upper[10].isEmpty());
        assertTrue(!lower[10].equals(upper[10]));
    }
}
