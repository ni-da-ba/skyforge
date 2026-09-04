package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AuthorshipSemanticMaterialFamiliesCorpusTest {
    @Test
    void generatesReviewCorpus() throws Exception {
        Path output = Path.of("build", "evidence", "authorship-semantic-material-families-v1");
        AuthorshipSemanticMaterialFamiliesCorpusCli.main(new String[] {output.toString()});

        assertTrue(Files.isRegularFile(output.resolve("index.html")));
        assertTrue(Files.isRegularFile(output.resolve("atlas.png")));
        assertTrue(Files.isRegularFile(output.resolve("manifest.csv")));
        String manifest = Files.readString(output.resolve("manifest.csv"));
        assertTrue(manifest.lines().count() >= 7);
        String header = manifest.lines().findFirst().orElseThrow();
        assertTrue(header.equals(
                "role,islandKey,morphology,activeHostCells,"
                        + "massiveExpressed,massiveMean,massivePeak,"
                        + "fabricExpressed,fabricMean,fabricPeak,"
                        + "alteredExpressed,alteredMean,alteredPeak,"
                        + "waterExpressed,waterMean,waterPeak,"
                        + "mineralExpressed,mineralMean,mineralPeak"));
        for (String line : manifest.lines().skip(1).toList()) {
            assertTrue(line.split(",", -1).length == 19);
        }
    }
}
