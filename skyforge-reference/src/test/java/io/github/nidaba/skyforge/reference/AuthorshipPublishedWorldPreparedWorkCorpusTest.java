package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuthorshipPublishedWorldPreparedWorkCorpusTest {
    @Test
    void generatesReviewCorpus() throws Exception {
        Path output =
                Path.of("build", "evidence", "authorship-published-world-prepared-work-v1");
        AuthorshipPublishedWorldPreparedWorkCorpusCli.main(new String[] {output.toString()});

        assertTrue(Files.isRegularFile(output.resolve("index.html")));
        assertTrue(Files.isRegularFile(output.resolve("atlas.png")));
        assertTrue(Files.isRegularFile(output.resolve("manifest.csv")));
        assertTrue(Files.isRegularFile(output.resolve("identity.csv")));
        assertTrue(Files.isRegularFile(output.resolve("proof.csv")));
        assertTrue(Files.isRegularFile(output.resolve("validation.csv")));

        String manifest = Files.readString(output.resolve("manifest.csv"));
        assertEquals(7, manifest.lines().count());
        assertEquals(
                "scenario,pass,workSequence,hitCount,status",
                manifest.lines().findFirst().orElseThrow());

        Map<String, String[]> rows = new HashMap<>();
        for (String line : manifest.lines().skip(1).toList()) {
            String[] columns = line.split(",", -1);
            assertEquals(5, columns.length);
            assertEquals("true", columns[1]);
            assertEquals("700", columns[2]);
            assertEquals("1", columns[3]);
            rows.put(columns[0], columns);
        }
        assertEquals(6, rows.size());
        assertEquals("CURRENT", rows.get("CURRENT_GATE")[4]);
        assertEquals("STALE", rows.get("STALE_BLOCKED")[4]);
        assertEquals("INACTIVE", rows.get("INACTIVE_BLOCKED")[4]);

        String identity = Files.readString(output.resolve("identity.csv"));
        assertEquals(2, identity.lines().count());
        String[] identityRow = identity.lines().skip(1).findFirst().orElseThrow().split(",", -1);
        assertEquals(4, identityRow.length);
        assertTrue(identityRow[0].startsWith("sfwork:v1:"));
        assertEquals("true", identityRow[1]);
        assertEquals("true", identityRow[2]);
        assertEquals("true", identityRow[3]);

        String proof = Files.readString(output.resolve("proof.csv"));
        assertEquals(2, proof.lines().count());
        assertEquals(
                "true,true,true,true,true",
                proof.lines().skip(1).findFirst().orElseThrow());

        String validation = Files.readString(output.resolve("validation.csv"));
        assertEquals(2, validation.lines().count());
        assertEquals(
                "CURRENT,STALE,INACTIVE,true,true",
                validation.lines().skip(1).findFirst().orElseThrow());
    }
}
