package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuthorshipPublishedWorldSnapshotActivationCorpusTest {
    @Test
    void generatesReviewCorpus() throws Exception {
        Path output =
                Path.of(
                        "build",
                        "evidence",
                        "authorship-published-world-snapshot-activation-v1");
        AuthorshipPublishedWorldSnapshotActivationCorpusCli.main(
                new String[] {output.toString()});

        assertTrue(Files.isRegularFile(output.resolve("index.html")));
        assertTrue(Files.isRegularFile(output.resolve("atlas.png")));
        assertTrue(Files.isRegularFile(output.resolve("manifest.csv")));
        assertTrue(Files.isRegularFile(output.resolve("binding.csv")));
        assertTrue(Files.isRegularFile(output.resolve("activation.csv")));
        assertTrue(Files.isRegularFile(output.resolve("replacement.csv")));
        assertTrue(Files.isRegularFile(output.resolve("version.csv")));
        assertTrue(Files.isRegularFile(output.resolve("failures.csv")));

        String manifest = Files.readString(output.resolve("manifest.csv"));
        assertEquals(7, manifest.lines().count());
        assertEquals(
                "scenario,pass,snapshotRevision,publicationCount,volumeCount",
                manifest.lines().findFirst().orElseThrow());

        Map<String, String[]> rows = new HashMap<>();
        for (String line : manifest.lines().skip(1).toList()) {
            String[] columns = line.split(",", -1);
            assertEquals(5, columns.length);
            assertEquals("true", columns[1]);
            rows.put(columns[0], columns);
        }
        assertEquals(6, rows.size());
        assertEquals("7", rows.get("SNAPSHOT_BINDING")[2]);
        assertEquals("10", rows.get("INITIAL_ACTIVATION")[2]);
        assertEquals("21", rows.get("MONOTONIC_REPLACEMENT")[2]);
        assertEquals("2", rows.get("CANONICAL_IDENTITY")[3]);

        String binding = Files.readString(output.resolve("binding.csv"));
        assertEquals(2, binding.lines().count());
        String[] bindingRow =
                binding.lines().skip(1).findFirst().orElseThrow().split(",", -1);
        assertEquals(4, bindingRow.length);
        assertTrue(bindingRow[0].startsWith("sfviewsnap:v1:"));
        assertEquals("true", bindingRow[1]);
        assertEquals("true", bindingRow[2]);
        assertEquals("true", bindingRow[3]);

        String activation = Files.readString(output.resolve("activation.csv"));
        assertEquals(2, activation.lines().count());
        assertEquals(
                "true,true,true,true",
                activation.lines().skip(1).findFirst().orElseThrow());

        String replacement = Files.readString(output.resolve("replacement.csv"));
        assertEquals(2, replacement.lines().count());
        assertEquals(
                "20,21,1,2,true,true,true,true",
                replacement.lines().skip(1).findFirst().orElseThrow());

        String version = Files.readString(output.resolve("version.csv"));
        assertEquals(2, version.lines().count());
        assertEquals(
                "true,true,true",
                version.lines().skip(1).findFirst().orElseThrow());

        String failures = Files.readString(output.resolve("failures.csv"));
        assertEquals(2, failures.lines().count());
        assertEquals(
                "true,true,true,true,true",
                failures.lines().skip(1).findFirst().orElseThrow());
    }
}
