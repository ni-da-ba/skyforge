package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuthorshipImmutableReplanProposalCorpusTest {
    @Test
    void generatesReviewCorpus() throws Exception {
        Path output =
                Path.of(
                        "build",
                        "evidence",
                        "authorship-immutable-replan-proposal-v1");
        AuthorshipImmutableReplanProposalCorpusCli.main(
                new String[] {output.toString()});

        assertTrue(Files.isRegularFile(output.resolve("index.html")));
        assertTrue(Files.isRegularFile(output.resolve("atlas.png")));
        assertTrue(Files.isRegularFile(output.resolve("manifest.csv")));
        assertTrue(Files.isRegularFile(output.resolve("boundary.csv")));

        String manifest = Files.readString(output.resolve("manifest.csv"));
        assertEquals(7, manifest.lines().count());
        assertEquals(
                "scenario,complete,candidatePresent,freshReplanRequired,"
                        + "originalHorizontal,proofHorizontal,authorHorizontalMargin,"
                        + "proposedHorizontal,originalLayoutSpacing,proofLayoutSpacing,"
                        + "layoutAuthorMargin,proposedLayoutSpacing,originalGroupRadius,"
                        + "proofGroupRadius,groupAuthorMargin,dependentGroupFloor,"
                        + "proposedGroupRadius,originalBelow,proofBelow,belowMargin,"
                        + "proposedBelow,originalAbove,proofAbove,aboveMargin,proposedAbove",
                manifest.lines().findFirst().orElseThrow());

        Map<String, String[]> rows = new HashMap<>();
        for (String line : manifest.lines().skip(1).toList()) {
            String[] columns = line.split(",", -1);
            assertEquals(25, columns.length);
            rows.put(columns[0], columns);
        }

        String[] noChange = rows.get("NO_CHANGE");
        assertEquals("true", noChange[1]);
        assertEquals("true", noChange[2]);
        assertEquals("false", noChange[3]);
        assertEquals(noChange[4], noChange[7]);
        assertEquals(noChange[8], noChange[11]);
        assertEquals(noChange[12], noChange[16]);

        String[] proof = rows.get("PROOF_RAISES_HORIZONTAL");
        assertEquals("true", proof[1]);
        assertEquals("true", proof[2]);
        assertEquals("true", proof[3]);
        assertTrue(Double.parseDouble(proof[5]) > Double.parseDouble(proof[4]));
        assertTrue(Double.parseDouble(proof[7]) > Double.parseDouble(proof[4]));
        assertTrue(Double.parseDouble(proof[11]) > Double.parseDouble(proof[8]));
        assertTrue(Double.parseDouble(proof[16]) >= Double.parseDouble(proof[15]));

        String[] margin = rows.get("AUTHOR_MARGIN");
        assertEquals("10.000000", margin[6]);
        assertEquals("20.000000", margin[14]);
        assertEquals("5.000000", margin[19]);
        assertEquals("7.000000", margin[23]);
        assertTrue(Double.parseDouble(margin[7]) > Double.parseDouble(margin[4]));

        String[] vertical = rows.get("VERTICAL_ONLY");
        assertEquals("false", vertical[3]);
        assertEquals(vertical[4], vertical[7]);
        assertTrue(Double.parseDouble(vertical[20]) > Double.parseDouble(vertical[17]));

        String[] incomplete = rows.get("INCOMPLETE_UNCERTIFIED");
        assertEquals("false", incomplete[1]);
        assertEquals("false", incomplete[2]);
        assertEquals("", incomplete[5]);
        assertEquals("", incomplete[13]);
        assertEquals("", incomplete[18]);
        assertEquals("", incomplete[22]);

        String boundary = Files.readString(output.resolve("boundary.csv"));
        assertEquals(2, boundary.lines().count());
        assertEquals(
                "candidateBuiltWithoutExecution,explicitFreshPlanSucceeded,"
                        + "oldExactPlanGroupProof,proposalProvisionalGroupRadius,"
                        + "freshExactPlanGroupProof,freshPreflightAdmitted,"
                        + "originalObservedSpacing,freshObservedSpacing",
                boundary.lines().findFirst().orElseThrow());
        String[] summary =
                boundary.lines().skip(1).findFirst().orElseThrow().split(",", -1);
        assertEquals(8, summary.length);
        assertEquals("true", summary[0]);
        assertEquals("true", summary[1]);
        assertEquals("true", summary[5]);
        assertTrue(!summary[2].equals(summary[4]));
        assertTrue(!summary[6].equals(summary[7]));
    }
}
