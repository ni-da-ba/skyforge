package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuthorshipReservationRequirementSynthesisCorpusTest {
    @Test
    void generatesReviewCorpus() throws Exception {
        Path output =
                Path.of(
                        "build",
                        "evidence",
                        "authorship-reservation-requirement-synthesis-v1");
        AuthorshipReservationRequirementSynthesisCorpusCli.main(
                new String[] {output.toString()});

        assertTrue(Files.isRegularFile(output.resolve("index.html")));
        assertTrue(Files.isRegularFile(output.resolve("atlas.png")));
        assertTrue(Files.isRegularFile(output.resolve("manifest.csv")));
        assertTrue(Files.isRegularFile(output.resolve("replan.csv")));

        String manifest = Files.readString(output.resolve("manifest.csv"));
        assertEquals(9, manifest.lines().count());
        assertEquals(
                "scenario,fullySynthesized,requiresFreshReplan,descriptorSeed,"
                        + "currentHorizontal,requiredHorizontal,currentGroup,requiredGroup,"
                        + "currentBelow,requiredBelow,currentAbove,requiredAbove",
                manifest.lines().findFirst().orElseThrow());

        Map<String, String[]> rows = new HashMap<>();
        for (String line : manifest.lines().skip(1).toList()) {
            String[] columns = line.split(",", -1);
            assertEquals(12, columns.length);
            rows.put(columns[0], columns);
        }

        assertEquals("true", rows.get("ADEQUATE_CURRENT")[1]);
        assertEquals("false", rows.get("ADEQUATE_CURRENT")[2]);
        assertTrue(
                Double.parseDouble(rows.get("ADEQUATE_CURRENT")[5])
                        <= Double.parseDouble(rows.get("ADEQUATE_CURRENT")[4]));

        assertEquals("true", rows.get("SYNTHESIZE_HORIZONTAL_GROUP")[1]);
        assertEquals("true", rows.get("SYNTHESIZE_HORIZONTAL_GROUP")[2]);
        assertTrue(
                Double.parseDouble(rows.get("SYNTHESIZE_HORIZONTAL_GROUP")[5])
                        > Double.parseDouble(rows.get("SYNTHESIZE_HORIZONTAL_GROUP")[4]));
        assertTrue(
                Double.parseDouble(rows.get("SYNTHESIZE_HORIZONTAL_GROUP")[7])
                        > Double.parseDouble(rows.get("SYNTHESIZE_HORIZONTAL_GROUP")[6]));

        assertEquals("true", rows.get("SYNTHESIZE_VERTICAL")[1]);
        assertTrue(
                Double.parseDouble(rows.get("SYNTHESIZE_VERTICAL")[9])
                        > Double.parseDouble(rows.get("SYNTHESIZE_VERTICAL")[8]));

        assertEquals("false", rows.get("UNCERTIFIED_INCOMPLETE")[1]);
        assertEquals("", rows.get("UNCERTIFIED_INCOMPLETE")[5]);
        assertEquals("", rows.get("UNCERTIFIED_INCOMPLETE")[7]);
        assertEquals("", rows.get("UNCERTIFIED_INCOMPLETE")[9]);
        assertEquals("", rows.get("UNCERTIFIED_INCOMPLETE")[11]);

        String[] seedA = rows.get("SEED_A_LARGE");
        String[] seedB = rows.get("SEED_B_SMALL");
        assertTrue(!seedA[3].equals(seedB[3]));
        assertTrue(Double.parseDouble(seedA[5]) > Double.parseDouble(seedB[5]));
        assertEquals("true", seedA[2]);
        assertEquals("false", seedB[2]);

        String replan = Files.readString(output.resolve("replan.csv"));
        assertEquals(2, replan.lines().count());
        assertEquals(
                "sameRootSeed,originalRequiredGroupRadius,freshRequiredGroupRadius,"
                        + "originalObservedCenterSpacing,freshObservedCenterSpacing,"
                        + "requirementsDiffer",
                replan.lines().findFirst().orElseThrow());
        String[] summary =
                replan.lines().skip(1).findFirst().orElseThrow().split(",", -1);
        assertEquals(6, summary.length);
        assertEquals("true", summary[0]);
        assertEquals("true", summary[5]);
        assertTrue(!summary[1].equals(summary[2]));
        assertTrue(!summary[3].equals(summary[4]));
    }
}
