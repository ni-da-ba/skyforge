package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuthorshipPublishedWorldCommitAcknowledgementCheckpointConsumptionTicketCorpusTest {
    @Test
    void generatesReviewCorpus() throws Exception {
        Path output =
                Path.of(
                        "build",
                        "evidence",
                        "authorship-published-world-commit-acknowledgement-checkpoint-consumption-ticket-v1");
        AuthorshipPublishedWorldCommitAcknowledgementCheckpointConsumptionTicketCorpusCli.main(
                new String[] {output.toString()});

        assertTrue(Files.isRegularFile(output.resolve("index.html")));
        assertTrue(Files.isRegularFile(output.resolve("atlas.png")));
        assertTrue(Files.isRegularFile(output.resolve("manifest.csv")));
        assertTrue(Files.isRegularFile(output.resolve("ticket.csv")));
        assertTrue(Files.isRegularFile(output.resolve("failures.csv")));

        String manifest = Files.readString(output.resolve("manifest.csv"));
        assertEquals(7, manifest.lines().count());
        assertEquals(
                "scenario,pass,ticketSequence,status",
                manifest.lines().findFirst().orElseThrow());

        Map<String, String[]> rows = new HashMap<>();
        for (String line : manifest.lines().skip(1).toList()) {
            String[] columns = line.split(",", -1);
            assertEquals(4, columns.length);
            assertEquals("true", columns[1]);
            assertEquals("2301", columns[2]);
            rows.put(columns[0], columns);
        }
        assertEquals(6, rows.size());
        assertEquals("CURRENT", rows.get("CURRENT_ADMISSION")[3]);
        assertEquals("STALE", rows.get("STALE_BLOCKED")[3]);
        assertEquals("INACTIVE", rows.get("INACTIVE_BLOCKED")[3]);
        assertEquals("ADMITTED", rows.get("NO_IO_OUTCOME")[3]);

        String ticket = Files.readString(output.resolve("ticket.csv"));
        assertEquals(2, ticket.lines().count());
        String[] ticketRow = ticket.lines().skip(1).findFirst().orElseThrow().split(",", -1);
        assertEquals(5, ticketRow.length);
        assertTrue(ticketRow[0].startsWith("sfackcpticket:v1:"));
        assertTrue(ticketRow[1].startsWith("sfackcpprep:v1:"));
        assertTrue(ticketRow[2].startsWith("sfackcptarget:v1:"));
        assertEquals("1", ticketRow[3]);
        assertEquals("true", ticketRow[4]);

        String failures = Files.readString(output.resolve("failures.csv"));
        assertEquals(2, failures.lines().count());
        assertEquals(
                "true,true,true,true",
                failures.lines().skip(1).findFirst().orElseThrow());
    }
}
