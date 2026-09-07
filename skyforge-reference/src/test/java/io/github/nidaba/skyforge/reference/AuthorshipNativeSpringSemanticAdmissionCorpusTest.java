package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class AuthorshipNativeSpringSemanticAdmissionCorpusTest {

    @Test
    void generatesNativeSpringAdmissionProofCorpus() throws Exception {
        Path output = Path.of(
                "build",
                "evidence",
                AuthorshipNativeSpringSemanticAdmissionCorpusCli.EVIDENCE_ID);

        AuthorshipNativeSpringSemanticAdmissionCorpusCli.main(
                new String[] {output.toString()});

        assertTrue(Files.isRegularFile(output.resolve("index.html")));
        assertTrue(Files.isRegularFile(output.resolve("atlas.png")));
        assertTrue(Files.isRegularFile(output.resolve("manifest.csv")));
        assertTrue(Files.isRegularFile(output.resolve("positions.csv")));

        String manifest = Files.readString(output.resolve("manifest.csv"));
        assertEquals(6, manifest.lines().count());

        Map<String, String[]> rows = new HashMap<>();
        for (String line : manifest.lines().skip(1).toList()) {
            String[] columns = line.split(",", -1);
            assertEquals(9, columns.length);
            rows.put(columns[0], columns);
        }

        assertEquals("true", rows.get("aquifer-cave-water")[2]);
        assertEquals(
                "ADMITTED_AQUIFER_CAVE_WATER",
                rows.get("aquifer-cave-water")[3]);

        assertEquals("false", rows.get("same-site-molten")[2]);
        assertEquals(
                "MISSING_GEOTHERMAL_SEMANTICS",
                rows.get("same-site-molten")[3]);

        assertEquals(
                "NOT_AUTHORED_CAVE_INTERIOR",
                rows.get("owned-non-cave-water")[3]);
        assertEquals(
                "NO_AQUIFER_SUPPORT",
                rows.get("cave-no-aquifer-water")[3]);
        assertEquals(
                "OUTSIDE_AUTHORED_ISLAND",
                rows.get("outside-island-water")[3]);

        String[] admitted = rows.get("aquifer-cave-water");
        assertNotEquals("NONE", admitted[4]);
        assertTrue(Integer.parseInt(admitted[5]) >= 0);
        assertTrue(Integer.parseInt(admitted[6]) >= 0);
        assertTrue(Integer.parseInt(admitted[7]) >= 0);
        assertTrue(Double.parseDouble(admitted[8]) > 0.0);

        assertEquals(6, Files.readString(output.resolve("positions.csv")).lines().count());
    }
}
