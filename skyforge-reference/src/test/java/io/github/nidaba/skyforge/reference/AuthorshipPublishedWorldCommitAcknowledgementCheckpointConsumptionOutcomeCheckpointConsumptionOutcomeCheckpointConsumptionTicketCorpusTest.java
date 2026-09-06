package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class AuthorshipPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTicketCorpusTest {
    @Test
    void generatesReviewCorpus() throws Exception {
        Path output =
                Path.of(
                        "build",
                        "evidence",
                        "authorship-published-world-commit-acknowledgement-checkpoint-consumption-outcome-checkpoint-consumption-outcome-checkpoint-consumption-ticket-v1");
        AuthorshipPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTicketCorpusCli
                .main(new String[] {output.toString()});

        assertTrue(Files.isRegularFile(output.resolve("index.html")));
        assertTrue(Files.isRegularFile(output.resolve("atlas.png")));
        assertTrue(Files.isRegularFile(output.resolve("manifest.csv")));
        assertTrue(Files.isRegularFile(output.resolve("ticket.csv")));
        assertTrue(Files.isRegularFile(output.resolve("failures.csv")));

        String manifest = Files.readString(output.resolve("manifest.csv"));
        assertEquals(7, manifest.lines().count());
        Map<String, String[]> rows = new HashMap<>();
        for (String line : manifest.lines().skip(1).toList()) {
            String[] columns = line.split(",", -1);
            assertEquals("true", columns[1]);
            rows.put(columns[0], columns);
        }
        assertEquals("CURRENT", rows.get("CURRENT_ADMISSION")[3]);
        assertEquals("STALE", rows.get("STALE_BLOCKED")[3]);
        assertEquals("INACTIVE", rows.get("INACTIVE_BLOCKED")[3]);

        String ticket = Files.readString(output.resolve("ticket.csv"));
        String[] ticketRow = ticket.lines().skip(1).findFirst().orElseThrow().split(",", -1);
        assertEquals(5, ticketRow.length);
        assertTrue(ticketRow[0].startsWith("sfackcpoutcpoutticket:v1:"));
        assertTrue(ticketRow[1].startsWith("sfackcpoutcpoutprep:v1:"));
        assertTrue(ticketRow[2].startsWith("sfackcpoutcpouttarget:v1:"));
        assertEquals("1", ticketRow[3]);
        assertEquals("true", ticketRow[4]);

        String failures = Files.readString(output.resolve("failures.csv"));
        assertEquals(
                "true,true,true,true",
                failures.lines().skip(1).findFirst().orElseThrow());
    }
}
