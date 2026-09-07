package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuthorshipPublishedWorldCommitAcknowledgementCheckpointCorpusTest {
    @Test
    void generatesReviewCorpus() throws Exception {
        Path output =
                Path.of(
                        "build",
                        "evidence",
                        "authorship-published-world-commit-acknowledgement-checkpoint-v1");
        AuthorshipPublishedWorldCommitAcknowledgementCheckpointCorpusCli.main(
                new String[] {output.toString()});

        assertTrue(Files.isRegularFile(output.resolve("index.html")));
        assertTrue(Files.isRegularFile(output.resolve("atlas.png")));
        assertTrue(Files.isRegularFile(output.resolve("manifest.csv")));
        assertTrue(Files.isRegularFile(output.resolve("checkpoint.csv")));
        assertTrue(Files.isRegularFile(output.resolve("failures.csv")));

        String manifest = Files.readString(output.resolve("manifest.csv"));
        assertEquals(7, manifest.lines().count());
        assertEquals(
                "scenario,pass,checkpointRevision,size",
                manifest.lines().findFirst().orElseThrow());

        Map<String, String[]> rows = new HashMap<>();
        for (String line : manifest.lines().skip(1).toList()) {
            String[] columns = line.split(",", -1);
            assertEquals(4, columns.length);
            assertEquals("true", columns[1]);
            rows.put(columns[0], columns);
        }
        assertEquals(6, rows.size());
        assertEquals("2", rows.get("EXACT_CHECKPOINT")[3]);
        assertEquals("0", rows.get("EMPTY_CHECKPOINT")[3]);

        String checkpoint = Files.readString(output.resolve("checkpoint.csv"));
        assertEquals(2, checkpoint.lines().count());
        String[] checkpointRow =
                checkpoint.lines().skip(1).findFirst().orElseThrow().split(",", -1);
        assertEquals(5, checkpointRow.length);
        assertTrue(checkpointRow[0].startsWith("sfackcp:v1:"));
        assertEquals("2", checkpointRow[1]);
        assertEquals("true", checkpointRow[2]);
        assertEquals("true", checkpointRow[3]);
        assertEquals("true", checkpointRow[4]);

        String failures = Files.readString(output.resolve("failures.csv"));
        assertEquals(2, failures.lines().count());
        assertEquals(
                "true,true,true",
                failures.lines().skip(1).findFirst().orElseThrow());
    }
}
