package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class AuthorshipPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionCorpusTest {
    @Test
    void generatesReviewCorpus() throws Exception {
        Path output =
                Path.of(
                        "build",
                        "evidence",
                        "authorship-published-world-commit-acknowledgement-checkpoint-consumption-outcome-checkpoint-consumption-v1");
        AuthorshipPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionCorpusCli
                .main(new String[] {output.toString()});

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
            assertEquals("3301", columns[3]);
            rows.put(columns[0], columns);
        }
        assertEquals("CURRENT", rows.get("CURRENT_PREPARE")[2]);
        assertEquals("STALE", rows.get("STALE_EXECUTION_BLOCKED")[2]);
        assertEquals("INACTIVE", rows.get("INACTIVE_BLOCKED")[2]);

        String prepared = Files.readString(output.resolve("prepared.csv"));
        String[] row = prepared.lines().skip(1).findFirst().orElseThrow().split(",", -1);
        assertEquals(6, row.length);
        assertTrue(row[0].startsWith("sfackcpoutprep:v1:"));
        assertTrue(row[1].startsWith("sfackcpouttarget:v1:"));
        assertEquals("1", row[2]);
        assertEquals("CURRENT", row[3]);
        assertEquals("STALE", row[4]);
        assertEquals("INACTIVE", row[5]);

        String failures = Files.readString(output.resolve("failures.csv"));
        assertEquals(
                "true,true,true,true,true",
                failures.lines().skip(1).findFirst().orElseThrow());
    }
}
