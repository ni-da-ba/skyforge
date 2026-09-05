package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuthorshipOverlapAdmissionCorpusTest {
    @Test
    void generatesReviewCorpus() throws Exception {
        Path output =
                Path.of("build", "evidence", "authorship-overlap-admission-v1");
        AuthorshipOverlapAdmissionCorpusCli.main(new String[] {output.toString()});

        assertTrue(Files.isRegularFile(output.resolve("index.html")));
        assertTrue(Files.isRegularFile(output.resolve("atlas.png")));
        assertTrue(Files.isRegularFile(output.resolve("manifest.csv")));

        String manifest = Files.readString(output.resolve("manifest.csv"));
        assertEquals(8, manifest.lines().count());
        assertEquals(
                "scenario,mode,status,admitted,boundsIntersect,supportDisjoint,"
                        + "verticalGap,minimumVerticalSeparation,witnessPresent",
                manifest.lines().findFirst().orElseThrow());

        Map<String, String[]> rows = new HashMap<>();
        for (String line : manifest.lines().skip(1).toList()) {
            String[] columns = line.split(",", -1);
            assertEquals(9, columns.length);
            rows.put(columns[0], columns);
        }
        assertEquals(7, rows.size());

        assertRow(
                rows.get("DISJOINT_BOUNDS"),
                "SEPARATE",
                "CERTIFIED_SEPARATE",
                "true",
                "false",
                "true",
                "false");

        assertRow(
                rows.get("OVERLAP_BOUNDS_SUPPORT_DISJOINT"),
                "SEPARATE",
                "CERTIFIED_SEPARATE",
                "true",
                "true",
                "true",
                "false");

        String[] stack = rows.get("STACK_CERTIFIED");
        assertRow(
                stack,
                "STACKED",
                "CERTIFIED_STACKED",
                "true",
                "false",
                "false",
                "false");
        assertTrue(Double.parseDouble(stack[6]) >= Double.parseDouble(stack[7]));

        assertRow(
                rows.get("STACK_BROAD_UNCERTIFIED"),
                "STACKED",
                "REJECTED_STACK_REQUIREMENT",
                "false",
                "true",
                "false",
                "false");

        assertRow(
                rows.get("STRICT_UNCERTIFIED"),
                "SEPARATE",
                "REJECTED_UNCERTIFIED_SEPARATION",
                "false",
                "true",
                "false",
                "false");

        assertRow(
                rows.get("STRICT_TRUE_OVERLAP"),
                "SEPARATE",
                "REJECTED_WITNESSED_OVERLAP",
                "false",
                "true",
                "false",
                "true");

        assertRow(
                rows.get("COMPOSE_TRUE_OVERLAP"),
                "COMPOSE",
                "ACCEPTED_EXPLICIT_COMPOSITION",
                "true",
                "true",
                "false",
                "true");
    }

    private static void assertRow(
            String[] row,
            String mode,
            String status,
            String admitted,
            String boundsIntersect,
            String supportDisjoint,
            String witnessPresent) {
        assertEquals(mode, row[1]);
        assertEquals(status, row[2]);
        assertEquals(admitted, row[3]);
        assertEquals(boundsIntersect, row[4]);
        assertEquals(supportDisjoint, row[5]);
        assertEquals(witnessPresent, row[8]);
    }
}
