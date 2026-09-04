package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AuthorshipCoherentChannelSkeletonCorpusTest {
    @Test
    void generatesReviewCorpus() throws Exception {
        Path output = Path.of("build", "evidence", "authorship-coherent-channel-skeleton-v1");
        AuthorshipCoherentChannelSkeletonCorpusCli.main(new String[] {output.toString()});

        assertTrue(Files.isRegularFile(output.resolve("index.html")));
        assertTrue(Files.isRegularFile(output.resolve("atlas.png")));
        assertTrue(Files.isRegularFile(output.resolve("manifest.csv")));
        assertTrue(Files.isRegularFile(output.resolve("components.csv")));
        assertTrue(Files.readString(output.resolve("manifest.csv")).lines().count() >= 7);
        assertTrue(Files.readString(output.resolve("components.csv")).lines().count() > 1);
    }
}
