package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class AuthorshipEcologyCorpusTest {
    @Test
    void generatesReviewAtlasDuringVerification() throws Exception {
        Path output = Path.of("build", "evidence", "authorship-ecology-v1");
        AuthorshipEcologyCorpusCli.main(new String[] {output.toString()});
        assertTrue(Files.isRegularFile(output.resolve("index.html")));
        assertTrue(Files.isRegularFile(output.resolve("atlas.png")));
        assertTrue(Files.isRegularFile(output.resolve("manifest.csv")));
    }
}
