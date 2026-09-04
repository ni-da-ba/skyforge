package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AuthorshipMesoscaleMaterialDomainsCorpusTest {
    @Test
    void generatesReviewCorpus() throws Exception {
        Path output = Path.of("build", "evidence", "authorship-mesoscale-material-domains-v1");
        AuthorshipMesoscaleMaterialDomainsCorpusCli.main(new String[] {output.toString()});

        assertTrue(Files.isRegularFile(output.resolve("index.html")));
        assertTrue(Files.isRegularFile(output.resolve("atlas.png")));
        assertTrue(Files.isRegularFile(output.resolve("manifest.csv")));
        String manifest = Files.readString(output.resolve("manifest.csv"));
        assertTrue(manifest.lines().count() >= 7);
        String header = manifest.lines().findFirst().orElseThrow();
        assertTrue(header.equals(
                "role,islandKey,morphology,mineralCarriers,fabricCarriers,activeHostCells,"
                        + "alteredDomains,alteredCells,largestAltered,alteredCoverage,"
                        + "saturatedDomains,saturatedCells,largestSaturated,saturatedCoverage,"
                        + "mineralizedDomains,mineralizedCells,largestMineralized,mineralizedCoverage,"
                        + "fabricDomains,fabricCells,largestFabric,fabricCoverage"));
        for (String line : manifest.lines().skip(1).toList()) {
            assertTrue(line.split(",", -1).length == 22);
        }
    }
}
