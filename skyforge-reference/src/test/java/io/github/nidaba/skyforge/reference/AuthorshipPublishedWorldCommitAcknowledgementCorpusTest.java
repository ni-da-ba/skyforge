package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuthorshipPublishedWorldCommitAcknowledgementCorpusTest {
    @Test
    void generatesReviewCorpus() throws Exception {
        Path output =
                Path.of(
                        "build",
                        "evidence",
                        "authorship-published-world-commit-acknowledgement-v1");
        AuthorshipPublishedWorldCommitAcknowledgementCorpusCli.main(
                new String[] {output.toString()});

        assertTrue(Files.isRegularFile(output.resolve("index.html")));
        assertTrue(Files.isRegularFile(output.resolve("atlas.png")));
        assertTrue(Files.isRegularFile(output.resolve("manifest.csv")));
        assertTrue(Files.isRegularFile(output.resolve("acknowledgement.csv")));
        assertTrue(Files.isRegularFile(output.resolve("failures.csv")));

        String manifest = Files.readString(output.resolve("manifest.csv"));
        assertEquals(7, manifest.lines().count());
        assertEquals(
                "scenario,pass,ackSequence,outcome",
                manifest.lines().findFirst().orElseThrow());

        Map<String, String[]> rows = new HashMap<>();
        for (String line : manifest.lines().skip(1).toList()) {
            String[] columns = line.split(",", -1);
            assertEquals(4, columns.length);
            assertEquals("true", columns[1]);
            rows.put(columns[0], columns);
        }
        assertEquals(6, rows.size());
        assertEquals("SUCCEEDED", rows.get("SUCCESS_ATTESTED")[3]);
        assertEquals("FAILED", rows.get("FAILURE_ATTESTED")[3]);

        String acknowledgement = Files.readString(output.resolve("acknowledgement.csv"));
        assertEquals(2, acknowledgement.lines().count());
        String[] acknowledgementRow =
                acknowledgement.lines().skip(1).findFirst().orElseThrow().split(",", -1);
        assertEquals(5, acknowledgementRow.length);
        assertTrue(acknowledgementRow[0].startsWith("sfack:v1:"));
        assertEquals("true", acknowledgementRow[1]);
        assertEquals("SUCCEEDED", acknowledgementRow[2]);
        assertEquals("FAILED", acknowledgementRow[3]);
        assertEquals("true", acknowledgementRow[4]);

        String failures = Files.readString(output.resolve("failures.csv"));
        assertEquals(2, failures.lines().count());
        assertEquals(
                "true,true,true,true",
                failures.lines().skip(1).findFirst().orElseThrow());
    }
}
