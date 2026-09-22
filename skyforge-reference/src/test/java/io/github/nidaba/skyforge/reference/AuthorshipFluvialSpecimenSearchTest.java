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

        System.out.println("DR70_AUDIT_SUMMARY_BEGIN");
        System.out.print(result.summaryText());
        System.out.println("DR70_AUDIT_SUMMARY_END");
        System.out.println("DR70_REVIEW_CORPUS_BEGIN");
        System.out.print(Files.readString(out.resolve("review-corpus.csv"), StandardCharsets.UTF_8));
        System.out.println("DR70_REVIEW_CORPUS_END");
    }
}
