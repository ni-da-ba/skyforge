package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuthorshipPublishedWorldCommitAcknowledgementCheckpointConsumptionCorpusTest {
    @Test
    void generatesReviewCorpus() throws Exception {
        Path output =
                Path.of(
                        "build",
                        "evidence",
                        "authorship-published-world-commit-acknowledgement-checkpoint-consumption-v1");
        AuthorshipPublishedWorldCommitAcknowledgementCheckpointConsumptionCorpusCli.main(
                new String[] {output.toString()});

        assertTrue(Files.isRegularFile(output.resolve("index.html")));
        assertTrue(Files.isRegularFile(output.resolve("atlas.png")));
        assertTrue(Files.isRegularFile(output.resolve("manifest.csv")));
        assertTrue(Files.isRegularFile(output.resolve("prepared.csv")));
        assertTrue(Files.isRegularFile(output.resolve("failures.csv")));

        String manifest = Files.readString(output.resolve("manifest.csv"));
        assertEquals(7, manifest.lines().count());
        assertEquals(
                "scenario,pass,status,preparationSequence",
                manifest.lines().findFirst().orElseThrow());

        Map<String, String[]> rows = new HashMap<>();
        for (String line : manifest.lines().skip(1).toList()) {
            String[] columns = line.split(",", -1);
            assertEquals(4, columns.length);
            assertEquals("true", columns[1]);
            assertEquals("2101", columns[3]);
            rows.put(columns[0], columns);
        }
        assertEquals(6, rows.size());
        assertEquals("CURRENT", rows.get("CURRENT_PREPARE")[2]);
        assertEquals("STALE", rows.get("STALE_EXECUTION_BLOCKED")[2]);
        assertEquals("INACTIVE", rows.get("INACTIVE_BLOCKED")[2]);

        String prepared = Files.readString(output.resolve("prepared.csv"));
        assertEquals(2, prepared.lines().count());
        String[] preparedRow =
                prepared.lines().skip(1).findFirst().orElseThrow().split(",", -1);
        assertEquals(6, preparedRow.length);
        assertTrue(preparedRow[0].startsWith("sfackcpprep:v1:"));
        assertTrue(preparedRow[1].startsWith("sfackcptarget:v1:"));
        assertEquals("1", preparedRow[2]);
        assertEquals("CURRENT", preparedRow[3]);
        assertEquals("STALE", preparedRow[4]);
        assertEquals("INACTIVE", preparedRow[5]);

        String failures = Files.readString(output.resolve("failures.csv"));
        assertEquals(2, failures.lines().count());
        assertEquals(
                "true,true,true,true,true",
                failures.lines().skip(1).findFirst().orElseThrow());
    }
}
