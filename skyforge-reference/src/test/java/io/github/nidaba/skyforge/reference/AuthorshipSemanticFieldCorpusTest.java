package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class AuthorshipSemanticFieldCorpusTest {
    @Test
    void generatesReviewableSemanticFieldCorpus() throws Exception {
        Path output = Path.of("build", "evidence", "authorship-semantic-fields-v1");
        AuthorshipSemanticFieldCorpusCli.main(new String[] {output.toString()});

        assertTrue(Files.isRegularFile(output.resolve("overview.png")));
        assertTrue(Files.isRegularFile(output.resolve("elevation.png")));
        assertTrue(Files.isRegularFile(output.resolve("temperature.png")));
        assertTrue(Files.isRegularFile(output.resolve("moisture.png")));
        assertTrue(Files.isRegularFile(output.resolve("exposure.png")));
        assertTrue(Files.isRegularFile(output.resolve("interiority.png")));
        assertTrue(Files.isRegularFile(output.resolve("descriptor.json")));
        assertTrue(Files.isRegularFile(output.resolve("stats.csv")));
        assertTrue(Files.isRegularFile(output.resolve("index.html")));
    }
}
