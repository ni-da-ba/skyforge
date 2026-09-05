package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuthorshipCompiledWorldPublicationCorpusTest {
    @Test
    void generatesReviewCorpus() throws Exception {
        Path output =
                Path.of(
                        "build",
                        "evidence",
                        "authorship-compiled-world-publication-v1");
        AuthorshipCompiledWorldPublicationCorpusCli.main(new String[] {output.toString()});

        assertTrue(Files.isRegularFile(output.resolve("index.html")));
        assertTrue(Files.isRegularFile(output.resolve("atlas.png")));
        assertTrue(Files.isRegularFile(output.resolve("manifest.csv")));
        assertTrue(Files.isRegularFile(output.resolve("binding.csv")));
        assertTrue(Files.isRegularFile(output.resolve("version.csv")));
        assertTrue(Files.isRegularFile(output.resolve("regional.csv")));

        String manifest = Files.readString(output.resolve("manifest.csv"));
        assertEquals(7, manifest.lines().count());
        assertEquals(
                "scenario,pass,publicationToken,root,revision,volumeCount,certifiedCount",
                manifest.lines().findFirst().orElseThrow());

        Map<String, String[]> rows = new HashMap<>();
        for (String line : manifest.lines().skip(1).toList()) {
            String[] columns = line.split(",", -1);
            assertEquals(7, columns.length);
            assertEquals("true", columns[1]);
            assertEquals("1", columns[5]);
            assertEquals("1", columns[6]);
            rows.put(columns[0], columns);
        }
        assertEquals(6, rows.size());
        assertEquals("3", rows.get("PUBLICATION_GATE")[4]);
        assertEquals("4", rows.get("VERSION_AXIS")[4]);

        String binding = Files.readString(output.resolve("binding.csv"));
        assertEquals(2, binding.lines().count());
        assertEquals(
                "accepted,preflightAdmitted,catalogSame,certificatesSame,catalogIdentitySame,fullyCertified",
                binding.lines().findFirst().orElseThrow());
        assertEquals(
                "true,true,true,true,true,true",
                binding.lines().skip(1).findFirst().orElseThrow());

        String version = Files.readString(output.resolve("version.csv"));
        assertEquals(2, version.lines().count());
        String[] versionRow =
                version.lines().skip(1).findFirst().orElseThrow().split(",", -1);
        assertEquals(5, versionRow.length);
        assertNotEquals(versionRow[0], versionRow[1]);
        assertEquals("true", versionRow[2]);
        assertEquals("true", versionRow[3]);
        assertEquals("true", versionRow[4]);

        String regional = Files.readString(output.resolve("regional.csv"));
        assertEquals(2, regional.lines().count());
        String[] regionalRow =
                regional.lines().skip(1).findFirst().orElseThrow().split(",", -1);
        assertEquals(5, regionalRow.length);
        assertNotEquals(regionalRow[0], regionalRow[1]);
        assertEquals("true", regionalRow[2]);
        assertEquals("true", regionalRow[3]);
        assertEquals("true", regionalRow[4]);
    }
}
