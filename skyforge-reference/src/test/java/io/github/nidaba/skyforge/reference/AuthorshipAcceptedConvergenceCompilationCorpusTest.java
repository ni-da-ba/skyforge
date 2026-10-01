package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuthorshipAcceptedConvergenceCompilationCorpusTest {
    @Test
    void generatesReviewCorpus() throws Exception {
        Path output =
                Path.of(
                        "build",
                        "evidence",
                        "authorship-accepted-convergence-compilation-v1");
        AuthorshipAcceptedConvergenceCompilationCorpusCli.main(
                new String[] {output.toString()});

        assertTrue(Files.isRegularFile(output.resolve("index.html")));
        assertTrue(Files.isRegularFile(output.resolve("atlas.png")));
        assertTrue(Files.isRegularFile(output.resolve("manifest.csv")));
        assertTrue(Files.isRegularFile(output.resolve("binding.csv")));
        assertTrue(Files.isRegularFile(output.resolve("attempts.csv")));

        String manifest = Files.readString(output.resolve("manifest.csv"));
        assertEquals(5, manifest.lines().count());
        assertEquals(
                "scenario,success,acceptedInput,preflightReproduced,primaryCompiles,"
                        + "compiledVolumes,certifiedVolumes,exactPlanIds,failureContains",
                manifest.lines().findFirst().orElseThrow());

        Map<String, String[]> rows = new HashMap<>();
        for (String line : manifest.lines().skip(1).toList()) {
            String[] columns = line.split(",", -1);
            assertEquals(9, columns.length);
            rows.put(columns[0], columns);
        }

        String[] accepted = rows.get("ACCEPTED_HANDOFF");
        assertEquals("true", accepted[1]);
        assertEquals("true", accepted[2]);
        assertEquals("true", accepted[3]);
        assertEquals("1", accepted[4]);
        assertEquals("1", accepted[5]);
        assertEquals("1", accepted[6]);
        assertEquals("true", accepted[7]);
        assertEquals("", accepted[8]);

        String[] nonAccepted = rows.get("NON_ACCEPTED_BLOCKED");
        assertEquals("false", nonAccepted[1]);
        assertEquals("false", nonAccepted[2]);
        assertEquals("0", nonAccepted[4]);
        assertTrue(nonAccepted[8].contains("ACCEPTED_ONE_PASS"));

        String[] mismatch = rows.get("REGISTRY_MISMATCH_BLOCKED");
        assertEquals("false", mismatch[1]);
        assertEquals("true", mismatch[2]);
        assertEquals("0", mismatch[4]);
        assertTrue(mismatch[8].contains("does not reproduce"));

        String[] primary = rows.get("PRIMARY_FAILURE_EXPLICIT");
        assertEquals("false", primary[1]);
        assertEquals("true", primary[2]);
        assertEquals("true", primary[3]);
        assertEquals("1", primary[4]);
        assertTrue(primary[8].contains("failed after accepted preflight reproduced"));

        String binding = Files.readString(output.resolve("binding.csv"));
        assertEquals(2, binding.lines().count());
        assertEquals(
                "freshPlanMembers,catalogVolumes,certifiedVolumes,exactPlanIds,"
                        + "preflightEqual,fullyCertified",
                binding.lines().findFirst().orElseThrow());
        String[] bindingRow =
                binding.lines().skip(1).findFirst().orElseThrow().split(",", -1);
        assertEquals(6, bindingRow.length);
        assertEquals("1", bindingRow[0]);
        assertEquals("1", bindingRow[1]);
        assertEquals("1", bindingRow[2]);
        assertEquals("true", bindingRow[3]);
        assertEquals("true", bindingRow[4]);
        assertEquals("true", bindingRow[5]);

        String attempts = Files.readString(output.resolve("attempts.csv"));
        assertEquals(2, attempts.lines().count());
        assertEquals(
                "explicitCompileCalls,totalPrimaryCompiles,firstAndSecondCatalogEqual,"
                        + "firstAndSecondCertificatesEqual",
                attempts.lines().findFirst().orElseThrow());
        String[] attemptRow =
                attempts.lines().skip(1).findFirst().orElseThrow().split(",", -1);
        assertEquals(4, attemptRow.length);
        assertEquals("2", attemptRow[0]);
        assertEquals("2", attemptRow[1]);
        assertEquals("true", attemptRow[2]);
        assertEquals("true", attemptRow[3]);
    }
}
