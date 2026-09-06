package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class AuthorshipPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementCorpusTest {
    @Test
    void generatesReviewCorpus() throws Exception {
        Path output =
                Path.of(
                        "build",
                        "evidence",
                        "authorship-published-world-commit-acknowledgement-checkpoint-consumption-outcome-checkpoint-consumption-acknowledgement-v1");
        AuthorshipPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementCorpusCli
                .main(new String[] {output.toString()});

        assertTrue(Files.isRegularFile(output.resolve("index.html")));
        assertTrue(Files.isRegularFile(output.resolve("atlas.png")));
        assertTrue(Files.isRegularFile(output.resolve("manifest.csv")));
        assertTrue(Files.isRegularFile(output.resolve("acknowledgement.csv")));
        assertTrue(Files.isRegularFile(output.resolve("failures.csv")));

        String manifest = Files.readString(output.resolve("manifest.csv"));
        assertEquals(7, manifest.lines().count());
        Map<String, String[]> rows = new HashMap<>();
        for (String line : manifest.lines().skip(1).toList()) {
            String[] columns = line.split(",", -1);
            assertEquals(4, columns.length);
            assertEquals("true", columns[1]);
            rows.put(columns[0], columns);
        }
        assertEquals("SUCCEEDED", rows.get("SUCCESS_ATTESTED")[3]);
        assertEquals("FAILED", rows.get("FAILURE_ATTESTED")[3]);

        String acknowledgement = Files.readString(output.resolve("acknowledgement.csv"));
        String[] row =
                acknowledgement.lines().skip(1).findFirst().orElseThrow().split(",", -1);
        assertEquals(6, row.length);
        assertTrue(row[0].startsWith("sfackcpoutack:v1:"));
        assertTrue(row[1].startsWith("sfackcpoutticket:v1:"));
        assertTrue(row[2].startsWith("sfackcpouttarget:v1:"));
        assertEquals("SUCCEEDED", row[3]);
        assertEquals("FAILED", row[4]);
        assertEquals("true", row[5]);

        String failures = Files.readString(output.resolve("failures.csv"));
        assertEquals(
                "true,true,true,true",
                failures.lines().skip(1).findFirst().orElseThrow());
    }
}
