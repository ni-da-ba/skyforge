package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuthorshipProviderSpecWorldSupportCorpusTest {
    @Test
    void generatesReviewCorpus() throws Exception {
        Path output =
                Path.of(
                        "build",
                        "evidence",
                        "authorship-provider-spec-world-support-v1");
        AuthorshipProviderSpecWorldSupportCorpusCli.main(
                new String[] {output.toString()});

        assertTrue(Files.isRegularFile(output.resolve("index.html")));
        assertTrue(Files.isRegularFile(output.resolve("atlas.png")));
        assertTrue(Files.isRegularFile(output.resolve("manifest.csv")));
        assertTrue(Files.isRegularFile(output.resolve("bundle.csv")));

        String manifest = Files.readString(output.resolve("manifest.csv"));
        assertEquals(6, manifest.lines().count());
        assertEquals(
                "scenario,specKind,certified,certificateKind,queryContainsSupport,"
                        + "sampledColumns,queryContainmentViolations,"
                        + "supportContainmentViolations,queryHorizontalRadius,"
                        + "supportHorizontalRadius,queryBelow,queryAbove,"
                        + "supportBelow,supportAbove",
                manifest.lines().findFirst().orElseThrow());

        Map<String, String[]> rows = new HashMap<>();
        for (String line : manifest.lines().skip(1).toList()) {
            String[] columns = line.split(",", -1);
            assertEquals(14, columns.length);
            rows.put(columns[0], columns);
        }
        assertEquals(5, rows.size());

        for (String scenario :
                new String[] {
                    "DIRECT_MASSIF",
                    "DIRECT_SPINE",
                    "ENDPOINT_FIRST",
                    "ENDPOINT_SECOND"
                }) {
            String[] row = rows.get(scenario);
            assertEquals("true", row[2]);
            assertTrue(!row[3].isEmpty());
            assertEquals("true", row[4]);
            assertTrue(Integer.parseInt(row[5]) > 0);
            assertEquals("0", row[6]);
            assertEquals("0", row[7]);
            assertTrue(Double.parseDouble(row[9]) < Double.parseDouble(row[8]));
            assertTrue(Double.parseDouble(row[12]) < Double.parseDouble(row[10]));
            assertTrue(Double.parseDouble(row[13]) < Double.parseDouble(row[11]));
        }

        String[] interior = rows.get("INTERIOR_BLEND");
        assertEquals("BLEND_INTERIOR", interior[1]);
        assertEquals("false", interior[2]);
        assertEquals("", interior[3]);
        assertEquals("false", interior[4]);
        assertTrue(Integer.parseInt(interior[5]) > 0);
        assertEquals("0", interior[6]);
        assertEquals("-1", interior[7]);
        assertEquals("", interior[9]);
        assertEquals("", interior[12]);
        assertEquals("", interior[13]);

        String bundle = Files.readString(output.resolve("bundle.csv"));
        assertEquals(2, bundle.lines().count());
        assertEquals(
                "volumeCount,certifiedCount,uncertifiedCount,fullyCertified,"
                        + "ordinaryCatalogEqual,legacyReservationRejected",
                bundle.lines().findFirst().orElseThrow());
        String[] summary =
                bundle.lines().skip(1).findFirst().orElseThrow().split(",", -1);
        assertEquals(6, summary.length);
        assertEquals("5", summary[0]);
        assertEquals("4", summary[1]);
        assertEquals("1", summary[2]);
        assertEquals("false", summary[3]);
        assertEquals("true", summary[4]);
        assertEquals("true", summary[5]);
    }
}
