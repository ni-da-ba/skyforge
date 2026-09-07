package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuthorshipPublishedWorldSnapshotBindingCorpusTest {
    @Test
    void generatesReviewCorpus() throws Exception {
        Path output =
                Path.of(
                        "build",
                        "evidence",
                        "authorship-published-world-snapshot-binding-v1");
        AuthorshipPublishedWorldSnapshotBindingCorpusCli.main(
                new String[] {output.toString()});

        assertTrue(Files.isRegularFile(output.resolve("index.html")));
        assertTrue(Files.isRegularFile(output.resolve("atlas.png")));
        assertTrue(Files.isRegularFile(output.resolve("manifest.csv")));
        assertTrue(Files.isRegularFile(output.resolve("capture.csv")));
        assertTrue(Files.isRegularFile(output.resolve("validation.csv")));
        assertTrue(Files.isRegularFile(output.resolve("query.csv")));
        assertTrue(Files.isRegularFile(output.resolve("failures.csv")));

        String manifest = Files.readString(output.resolve("manifest.csv"));
        assertEquals(7, manifest.lines().count());
        assertEquals(
                "scenario,pass,boundRevision,currentRevision,status",
                manifest.lines().findFirst().orElseThrow());

        Map<String, String[]> rows = new HashMap<>();
        for (String line : manifest.lines().skip(1).toList()) {
            String[] columns = line.split(",", -1);
            assertEquals(5, columns.length);
            assertEquals("true", columns[1]);
            assertEquals("70", columns[2]);
            assertEquals("71", columns[3]);
            rows.put(columns[0], columns);
        }
        assertEquals(6, rows.size());
        assertEquals("CURRENT", rows.get("CURRENT_VALIDATION")[4]);
        assertEquals("STALE", rows.get("STALE_NO_REFRESH")[4]);
        assertEquals("INACTIVE", rows.get("INACTIVE_DISTINCT")[4]);

        String capture = Files.readString(output.resolve("capture.csv"));
        assertEquals(2, capture.lines().count());
        String[] captureRow =
                capture.lines().skip(1).findFirst().orElseThrow().split(",", -1);
        assertEquals(4, captureRow.length);
        assertTrue(captureRow[0].startsWith("sfbinding:v1:sfviewsnap:v1:"));
        assertTrue(captureRow[1].startsWith("sfviewsnap:v1:"));
        assertEquals("true", captureRow[2]);
        assertEquals("true", captureRow[3]);

        String validation = Files.readString(output.resolve("validation.csv"));
        assertEquals(2, validation.lines().count());
        assertEquals(
                "CURRENT,true,STALE,true,INACTIVE,true",
                validation.lines().skip(1).findFirst().orElseThrow());

        String query = Files.readString(output.resolve("query.csv"));
        assertEquals(2, query.lines().count());
        String[] queryRow =
                query.lines().skip(1).findFirst().orElseThrow().split(",", -1);
        assertEquals(4, queryRow.length);
        assertTrue(queryRow[0].startsWith("sfpub:v1:"));
        assertTrue(queryRow[1].startsWith("sfpub:v1:"));
        assertEquals("true", queryRow[2]);
        assertEquals("true", queryRow[3]);

        String failures = Files.readString(output.resolve("failures.csv"));
        assertEquals(2, failures.lines().count());
        assertEquals(
                "true,true,true,true,true,true",
                failures.lines().skip(1).findFirst().orElseThrow());
    }
}
