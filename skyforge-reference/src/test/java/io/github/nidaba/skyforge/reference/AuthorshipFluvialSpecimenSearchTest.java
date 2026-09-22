package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AuthorshipFluvialSpecimenSearchTest {
    @Test
    void findsAndRendersStrongDeterministicCandidates() throws Exception {
        Path out = Path.of("build", "evidence", AuthorshipFluvialSpecimenSearchCli.EVIDENCE_ID);
        AuthorshipFluvialSpecimenSearchCli.main(new String[] {out.toString()});
        assertTrue(Files.isRegularFile(out.resolve("atlas.png")));
        assertTrue(Files.isRegularFile(out.resolve("candidates.csv")));
        assertTrue(Files.readString(out.resolve("candidates.csv")).lines().count() == 9);
    }

    @Test
    void emitsOneTimeDr70CrossSystemPopulationAudit() throws Exception {
        Path out = Path.of("build", "evidence", Dr70IslandPopulationAuditCli.EVIDENCE_ID);
        var result = Dr70IslandPopulationAuditCli.generate(
                out,
                Dr70IslandPopulationAuditCli.DEFAULT_POPULATION_COUNT,
                Dr70IslandPopulationAuditCli.DEFAULT_REVIEW_COUNT);
        assertEquals(4096, result.populationCount());
        assertEquals(100, result.reviewCount());

        String reviewKeys = result.reviewSelections().stream()
                .map(selection -> selection.bucket() + ":" + selection.row().islandKey())
                .collect(java.util.stream.Collectors.joining(","));
        String capture = result.summaryText() + "reviewKeys=" + reviewKeys;
        String encoded = java.util.Base64.getEncoder()
                .encodeToString(capture.getBytes(StandardCharsets.UTF_8));
        Path index = Path.of(
                "build",
                "evidence",
                AuthorshipFluvialSpecimenSearchCli.EVIDENCE_ID,
                "index.html");
        if (!Files.isRegularFile(index)) {
            AuthorshipFluvialSpecimenSearchCli.main(new String[] {index.getParent().toString()});
        }
        Files.writeString(
                index,
                "\n<!-- DR70_AUDIT_CAPTURE_BASE64:" + encoded + " -->\n",
                StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.APPEND);
    }
}
