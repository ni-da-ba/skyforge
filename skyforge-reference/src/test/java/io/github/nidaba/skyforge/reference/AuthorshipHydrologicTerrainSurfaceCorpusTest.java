package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AuthorshipHydrologicTerrainSurfaceCorpusTest {
    @Test
    void generatesReviewCorpus() throws Exception {
        Path output = Path.of("build", "evidence", "authorship-hydrologic-terrain-surface-v1");
        AuthorshipHydrologicTerrainSurfaceCorpusCli.main(new String[] {output.toString()});
        assertTrue(Files.isRegularFile(output.resolve("index.html")));
        assertTrue(Files.isRegularFile(output.resolve("atlas.png")));
        assertTrue(Files.isRegularFile(output.resolve("manifest.csv")));
        assertTrue(Files.isRegularFile(output.resolve("cells.csv")));
        assertTrue(Files.readString(output.resolve("manifest.csv")).lines().count() >= 7);
        assertTrue(Files.readString(output.resolve("cells.csv")).lines().count() > 1);
    }
}
