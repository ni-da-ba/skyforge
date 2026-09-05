package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuthorshipReservationPreflightCorpusTest {
    @Test
    void generatesReviewCorpus() throws Exception {
        Path output =
                Path.of("build", "evidence", "authorship-reservation-preflight-v1");
        AuthorshipReservationPreflightCorpusCli.main(
                new String[] {output.toString()});

        assertTrue(Files.isRegularFile(output.resolve("index.html")));
        assertTrue(Files.isRegularFile(output.resolve("atlas.png")));
        assertTrue(Files.isRegularFile(output.resolve("manifest.csv")));

        String manifest = Files.readString(output.resolve("manifest.csv"));
        assertEquals(7, manifest.lines().count());
        assertEquals(
                "scenario,admitted,certified,descriptorSeed,reservedHorizontal,"
                        + "requiredHorizontal,reservedBelow,requiredBelow,reservedAbove,"
                        + "requiredAbove,reservedGroup,requiredGroup,consumedReservationDefect",
                manifest.lines().findFirst().orElseThrow());

        Map<String, String[]> rows = new HashMap<>();
        for (String line : manifest.lines().skip(1).toList()) {
            String[] columns = line.split(",", -1);
            assertEquals(13, columns.length);
            rows.put(columns[0], columns);
        }

        assertEquals("true", rows.get("ADMITTED_BUILTIN")[1]);
        assertEquals("true", rows.get("ADMITTED_BUILTIN")[2]);
        assertEquals("false", rows.get("ADMITTED_BUILTIN")[12]);

        assertEquals("false", rows.get("HORIZONTAL_GROUP_UNDERSIZED")[1]);
        assertEquals("true", rows.get("HORIZONTAL_GROUP_UNDERSIZED")[2]);
        assertEquals("true", rows.get("HORIZONTAL_GROUP_UNDERSIZED")[12]);
        assertTrue(
                Double.parseDouble(rows.get("HORIZONTAL_GROUP_UNDERSIZED")[5])
                        > Double.parseDouble(rows.get("HORIZONTAL_GROUP_UNDERSIZED")[4]));

        assertEquals("false", rows.get("VERTICAL_UNDERSIZED")[1]);
        assertEquals("true", rows.get("VERTICAL_UNDERSIZED")[12]);

        assertEquals("false", rows.get("INTERIOR_BLEND_UNCERTIFIED")[1]);
        assertEquals("false", rows.get("INTERIOR_BLEND_UNCERTIFIED")[2]);
        assertEquals("", rows.get("INTERIOR_BLEND_UNCERTIFIED")[5]);
        assertEquals("false", rows.get("INTERIOR_BLEND_UNCERTIFIED")[12]);

        String[] seedA = rows.get("SEED_A_LARGE");
        String[] seedB = rows.get("SEED_B_SMALL");
        assertEquals("false", seedA[1]);
        assertEquals("true", seedB[1]);
        assertTrue(!seedA[3].equals(seedB[3]));
        assertEquals("300.000000", seedA[5]);
        assertEquals("120.000000", seedB[5]);
    }
}
