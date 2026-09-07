package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class AuthorshipPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBindingCorpusTest {
    @Test
    void generatesReviewCorpus() throws Exception {
        Path output =
                Path.of(
                        "build",
                        "evidence",
                        "authorship-published-world-commit-acknowledgement-checkpoint-consumption-outcome-checkpoint-consumption-outcome-checkpoint-binding-v1");
        AuthorshipPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBindingCorpusCli
                .main(new String[] {output.toString()});

        assertTrue(Files.isRegularFile(output.resolve("index.html")));
        assertTrue(Files.isRegularFile(output.resolve("atlas.png")));
        assertTrue(Files.isRegularFile(output.resolve("manifest.csv")));
        assertTrue(Files.isRegularFile(output.resolve("binding.csv")));
        assertTrue(Files.isRegularFile(output.resolve("failures.csv")));

        String manifest = Files.readString(output.resolve("manifest.csv"));
        assertEquals(7, manifest.lines().count());
        assertEquals(
                "scenario,pass,status,checkpointRevision",
                manifest.lines().findFirst().orElseThrow());

        Map<String, String[]> rows = new HashMap<>();
        for (String line : manifest.lines().skip(1).toList()) {
            String[] columns = line.split(",", -1);
            assertEquals(4, columns.length);
            assertEquals("true", columns[1]);
            rows.put(columns[0], columns);
        }
        assertEquals("CURRENT", rows.get("CURRENT_BINDING")[2]);
        assertEquals("STALE", rows.get("STALE_NO_REFRESH")[2]);
        assertEquals("INACTIVE", rows.get("INACTIVE_DISTINCT")[2]);

        String binding = Files.readString(output.resolve("binding.csv"));
        String[] bindingRow =
                binding.lines().skip(1).findFirst().orElseThrow().split(",", -1);
        assertEquals(5, bindingRow.length);
        assertTrue(bindingRow[0].startsWith("sfackcpoutcpoutbinding:v1:"));
        assertEquals("CURRENT", bindingRow[1]);
        assertEquals("STALE", bindingRow[2]);
        assertEquals("INACTIVE", bindingRow[3]);
        assertEquals("true", bindingRow[4]);

        String failures = Files.readString(output.resolve("failures.csv"));
        assertEquals(
                "true,true,true,true,true",
                failures.lines().skip(1).findFirst().orElseThrow());
    }
}
