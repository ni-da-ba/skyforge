package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuthorshipPublishedWorldViewCorpusTest {
    @Test
    void generatesReviewCorpus() throws Exception {
        Path output =
                Path.of(
                        "build",
                        "evidence",
                        "authorship-published-world-view-v1");
        AuthorshipPublishedWorldViewCorpusCli.main(new String[] {output.toString()});

        assertTrue(Files.isRegularFile(output.resolve("index.html")));
        assertTrue(Files.isRegularFile(output.resolve("atlas.png")));
        assertTrue(Files.isRegularFile(output.resolve("manifest.csv")));
        assertTrue(Files.isRegularFile(output.resolve("view.csv")));
        assertTrue(Files.isRegularFile(output.resolve("query.csv")));
        assertTrue(Files.isRegularFile(output.resolve("replacement.csv")));
        assertTrue(Files.isRegularFile(output.resolve("failures.csv")));

        String manifest = Files.readString(output.resolve("manifest.csv"));
        assertEquals(7, manifest.lines().count());
        assertEquals(
                "scenario,pass,publicationCount,volumeCount,detail",
                manifest.lines().findFirst().orElseThrow());

        Map<String, String[]> rows = new HashMap<>();
        for (String line : manifest.lines().skip(1).toList()) {
            String[] columns = line.split(",", -1);
            assertEquals(5, columns.length);
            assertEquals("true", columns[1]);
            rows.put(columns[0], columns);
        }
        assertEquals(6, rows.size());
        assertEquals("2", rows.get("CANONICAL_VIEW")[2]);
        assertEquals("2", rows.get("QUERY_BOUNDS_VS_SUPPORT")[3]);
        assertEquals("0", rows.get("DUPLICATE_ROOT_BLOCKED")[2]);
        assertEquals("0", rows.get("SUPPORT_OVERLAP_BLOCKED")[3]);

        String view = Files.readString(output.resolve("view.csv"));
        assertEquals(2, view.lines().count());
        String[] viewRow =
                view.lines().skip(1).findFirst().orElseThrow().split(",", -1);
        assertEquals(5, viewRow.length);
        assertNotEquals(viewRow[0], viewRow[1]);
        assertEquals("true", viewRow[2]);
        assertEquals("false", viewRow[3]);
        assertEquals("true", viewRow[4]);

        String query = Files.readString(output.resolve("query.csv"));
        assertEquals(2, query.lines().count());
        String[] queryRow =
                query.lines().skip(1).findFirst().orElseThrow().split(",", -1);
        assertEquals(4, queryRow.length);
        assertEquals("1", queryRow[0]);
        assertTrue(queryRow[1].startsWith("sfpub:v1:"));
        assertTrue(queryRow[2].contains("/auth59/"));
        assertEquals("true", queryRow[3]);

        String replacement = Files.readString(output.resolve("replacement.csv"));
        assertEquals(2, replacement.lines().count());
        String[] replacementRow =
                replacement.lines().skip(1).findFirst().orElseThrow().split(",", -1);
        assertEquals(6, replacementRow.length);
        assertEquals("1", replacementRow[0]);
        assertEquals("2", replacementRow[1]);
        assertEquals("true", replacementRow[2]);
        assertEquals("true", replacementRow[3]);
        assertEquals("true", replacementRow[4]);
        assertEquals("true", replacementRow[5]);

        String failures = Files.readString(output.resolve("failures.csv"));
        assertEquals(2, failures.lines().count());
        assertEquals(
                "true,true",
                failures.lines().skip(1).findFirst().orElseThrow());
    }
}
