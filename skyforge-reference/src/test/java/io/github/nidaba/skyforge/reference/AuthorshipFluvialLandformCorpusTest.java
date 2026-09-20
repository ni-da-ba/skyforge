package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AuthorshipFluvialLandformCorpusTest {
    @Test
    void generatesDryLandformReviewCorpus() throws Exception {
        Path output = Path.of("build", "evidence", AuthorshipFluvialLandformCorpusCli.EVIDENCE_ID);
        AuthorshipFluvialLandformCorpusCli.main(new String[] {output.toString()});

        assertTrue(Files.isRegularFile(output.resolve("index.html")));
        assertTrue(Files.isRegularFile(output.resolve("atlas.png")));
        assertTrue(Files.isRegularFile(output.resolve("manifest.csv")));
        String manifest = Files.readString(output.resolve("manifest.csv"));
        assertTrue(manifest.lines().count() == 8);
        assertTrue(manifest.contains("dr70-1471"));
        assertTrue(manifest.contains("key-83-retained-basin"));
    }
}
