package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
