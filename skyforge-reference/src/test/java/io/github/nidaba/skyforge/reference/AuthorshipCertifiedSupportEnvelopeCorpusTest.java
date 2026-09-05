package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AuthorshipCertifiedSupportEnvelopeCorpusTest {
    @Test
    void generatesReviewCorpus() throws Exception {
        Path output =
                Path.of(
                        "build",
                        "evidence",
                        "authorship-certified-support-envelope-v1");
        AuthorshipCertifiedSupportEnvelopeCorpusCli.main(
                new String[] {output.toString()});

        assertTrue(Files.isRegularFile(output.resolve("index.html")));
        assertTrue(Files.isRegularFile(output.resolve("atlas.png")));
        assertTrue(Files.isRegularFile(output.resolve("manifest.csv")));
        assertTrue(Files.isRegularFile(output.resolve("stack.csv")));

        String manifest = Files.readString(output.resolve("manifest.csv"));
        assertEquals(6, manifest.lines().count());
        assertEquals(
                "family,queryMinimumY,queryMaximumY,supportMinimumY,supportMaximumY,"
                        + "sampledMinimumY,sampledMaximumY,sampledColumns,"
                        + "containmentViolations,querySpan,supportSpan",
                manifest.lines().findFirst().orElseThrow());

        for (String line : manifest.lines().skip(1).toList()) {
            String[] columns = line.split(",", -1);
            assertEquals(11, columns.length);
            double supportMinimum = Double.parseDouble(columns[3]);
            double supportMaximum = Double.parseDouble(columns[4]);
            double sampledMinimum = Double.parseDouble(columns[5]);
            double sampledMaximum = Double.parseDouble(columns[6]);
            int sampledColumns = Integer.parseInt(columns[7]);
            int violations = Integer.parseInt(columns[8]);
            double querySpan = Double.parseDouble(columns[9]);
            double supportSpan = Double.parseDouble(columns[10]);

            assertTrue(sampledColumns > 0);
            assertEquals(0, violations);
            assertTrue(sampledMinimum >= supportMinimum - 1.0e-6);
            assertTrue(sampledMaximum <= supportMaximum + 1.0e-6);
            assertTrue(supportSpan < querySpan);
        }

        String stack = Files.readString(output.resolve("stack.csv"));
        assertEquals(2, stack.lines().count());
        assertEquals(
                "queryBoundsIntersect,broadStatus,certifiedStatus,proofVerticalGap,"
                        + "minimumRequiredGap,lowerSupportMaximumY,upperSupportMinimumY",
                stack.lines().findFirst().orElseThrow());
        String[] columns =
                stack.lines().skip(1).findFirst().orElseThrow().split(",", -1);
        assertEquals(7, columns.length);
        assertEquals("true", columns[0]);
        assertEquals("REJECTED_STACK_REQUIREMENT", columns[1]);
        assertEquals("CERTIFIED_STACKED", columns[2]);
        assertTrue(Double.parseDouble(columns[3]) >= Double.parseDouble(columns[4]));
        assertTrue(Double.parseDouble(columns[6]) > Double.parseDouble(columns[5]));
    }
}
