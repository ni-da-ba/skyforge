package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AuthorshipWaterbodyPlanningCorpusTest {
    @Test
    void generatesReviewCorpus() throws Exception {
        Path output = Path.of("build", "evidence", "authorship-waterbodies-v1");
        AuthorshipWaterbodyPlanningCorpusCli.main(new String[] {output.toString()});
        assertTrue(Files.isRegularFile(output.resolve("index.html")));
        assertTrue(Files.isRegularFile(output.resolve("atlas.png")));
        assertTrue(Files.isRegularFile(output.resolve("manifest.csv")));
        assertTrue(Files.readString(output.resolve("manifest.csv")).lines().count() >= 7);
    }
}
