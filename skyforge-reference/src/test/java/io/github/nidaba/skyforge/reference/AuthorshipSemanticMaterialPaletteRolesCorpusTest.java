package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AuthorshipSemanticMaterialPaletteRolesCorpusTest {
    @Test
    void generatesReviewCorpus() throws Exception {
        Path output =
                Path.of("build", "evidence", "authorship-semantic-material-palette-roles-v1");
        AuthorshipSemanticMaterialPaletteRolesCorpusCli.main(
                new String[] {output.toString()});

        assertTrue(Files.isRegularFile(output.resolve("index.html")));
        assertTrue(Files.isRegularFile(output.resolve("atlas.png")));
        assertTrue(Files.isRegularFile(output.resolve("manifest.csv")));

        String manifest = Files.readString(output.resolve("manifest.csv"));
        assertTrue(manifest.lines().count() >= 7);
        String header = manifest.lines().findFirst().orElseThrow();
        assertTrue(header.equals(
                "role,islandKey,morphology,materialSamples,primaryMassive,primaryFabric,"
                        + "secondaryEligible,alterationEligible,waterEligible,mineralEligible,"
                        + "meanCandidateCount,meanSecondaryCeiling,meanAlterationCeiling,"
                        + "meanWaterCeiling,meanMineralCeiling"));
        for (String line : manifest.lines().skip(1).toList()) {
            assertTrue(line.split(",", -1).length == 15);
        }
    }
}
