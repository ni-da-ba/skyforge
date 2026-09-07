package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuthorshipOneShotConvergenceCorpusTest {
    @Test
    void generatesReviewCorpus() throws Exception {
        Path output =
                Path.of(
                        "build",
                        "evidence",
                        "authorship-one-shot-convergence-v1");
        AuthorshipOneShotConvergenceCorpusCli.main(
                new String[] {output.toString()});

        assertTrue(Files.isRegularFile(output.resolve("index.html")));
        assertTrue(Files.isRegularFile(output.resolve("atlas.png")));
        assertTrue(Files.isRegularFile(output.resolve("manifest.csv")));
        assertTrue(Files.isRegularFile(output.resolve("attempts.csv")));

        String manifest = Files.readString(output.resolve("manifest.csv"));
        assertEquals(5, manifest.lines().count());
        assertEquals(
                "scenario,outcome,plannerAttempts,plannerFailure,freshPlan,"
                        + "freshSynthesis,freshPreflight,freshFullySynthesized,"
                        + "freshUncertifiedMembers,undersizedHorizontal,undersizedVertical,"
                        + "undersizedGroup,accepted",
                manifest.lines().findFirst().orElseThrow());

        Map<String, String[]> rows = new HashMap<>();
        for (String line : manifest.lines().skip(1).toList()) {
            String[] columns = line.split(",", -1);
            assertEquals(13, columns.length);
            rows.put(columns[0], columns);
        }

        String[] accepted = rows.get("ACCEPTED_ONE_PASS");
        assertEquals("ACCEPTED_ONE_PASS", accepted[1]);
        assertEquals("1", accepted[2]);
        assertEquals("false", accepted[3]);
        assertEquals("true", accepted[4]);
        assertEquals("true", accepted[5]);
        assertEquals("true", accepted[6]);
        assertEquals("true", accepted[7]);
        assertEquals("0", accepted[8]);
        assertEquals("0", accepted[9]);
        assertEquals("0", accepted[10]);
        assertEquals("0", accepted[11]);
        assertEquals("true", accepted[12]);

        String[] planner = rows.get("PLANNER_REJECTED");
        assertEquals("PLANNER_REJECTED", planner[1]);
        assertEquals("1", planner[2]);
        assertEquals("true", planner[3]);
        assertEquals("false", planner[4]);
        assertEquals("false", planner[5]);
        assertEquals("false", planner[6]);
        assertEquals("", planner[7]);
        assertEquals("false", planner[12]);

        String[] incomplete = rows.get("FRESH_SYNTHESIS_INCOMPLETE");
        assertEquals("FRESH_SYNTHESIS_INCOMPLETE", incomplete[1]);
        assertEquals("1", incomplete[2]);
        assertEquals("true", incomplete[4]);
        assertEquals("true", incomplete[5]);
        assertEquals("false", incomplete[6]);
        assertEquals("false", incomplete[7]);
        assertTrue(Integer.parseInt(incomplete[8]) > 0);
        assertEquals("false", incomplete[12]);

        String[] rejected = rows.get("FRESH_RESERVATION_REJECTED");
        assertEquals("FRESH_RESERVATION_REJECTED", rejected[1]);
        assertEquals("1", rejected[2]);
        assertEquals("true", rejected[4]);
        assertEquals("true", rejected[5]);
        assertEquals("true", rejected[6]);
        assertEquals("true", rejected[7]);
        assertEquals("0", rejected[8]);
        assertTrue(Integer.parseInt(rejected[9]) > 0);
        assertEquals("false", rejected[12]);

        String attempts = Files.readString(output.resolve("attempts.csv"));
        assertEquals(2, attempts.lines().count());
        assertEquals(
                "explicitCallerExecutions,reportsEqual,firstPlannerAttempts,"
                        + "secondPlannerAttempts,firstOutcome,secondOutcome",
                attempts.lines().findFirst().orElseThrow());
        String[] summary =
                attempts.lines().skip(1).findFirst().orElseThrow().split(",", -1);
        assertEquals(6, summary.length);
        assertEquals("2", summary[0]);
        assertEquals("true", summary[1]);
        assertEquals("1", summary[2]);
        assertEquals("1", summary[3]);
        assertEquals("ACCEPTED_ONE_PASS", summary[4]);
        assertEquals("ACCEPTED_ONE_PASS", summary[5]);
    }
}
