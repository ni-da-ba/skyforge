package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HydrologyRetainedBasinSeedStrataCorpusTest {
    @Test
    void fixedDiscoveryCorpusIsOptInAndHasStableDimensions() throws Exception {
        assertEquals(768,
                HydrologyRetainedBasinSeedStrataCorpusCli.EXPECTED_SPECIMEN_COUNT);
        if (!"true".equalsIgnoreCase(System.getenv("SKYFORGE_RUN_BASIN_DISCOVERY"))) {
            return;
        }

        Path out = Path.of("build", "evidence",
                HydrologyRetainedBasinSeedStrataCorpusCli.EVIDENCE_ID);
        HydrologyRetainedBasinSeedStrataCorpusCli.main(new String[] {out.toString()});

        var specimenLines = Files.readAllLines(out.resolve("specimens.csv"));
        var candidateLines = Files.readAllLines(out.resolve("candidates.csv"));
        String summary = Files.readString(out.resolve("summary.csv"));
        String readme = Files.readString(out.resolve("README.txt"));

        assertEquals(769, specimenLines.size(), "header plus 768 fixed identities");
        assertEquals(12, specimenLines.get(0).split(",", -1).length);
        Set<String> identities = new HashSet<>();
        for (String line : specimenLines.subList(1, specimenLines.size())) {
            String[] fields = line.split(",", -1);
            identities.add(String.join(",", fields[0], fields[1], fields[2],
                    fields[3], fields[4], fields[5]));
        }
        assertEquals(768, identities.size());
        assertEquals(24, candidateLines.get(0).split(",", -1).length);
        assertTrue(summary.contains("stratum_seed-min/reference-8-81,128"));
        assertTrue(summary.contains("stratum_seed-zero/reference-6-61,128"));
        assertTrue(summary.contains("stratum_seed-skyforge/reference-8-81,128"));
        assertTrue(summary.contains("sample_design,3 seed strata x 2 fixed namespaces x 128 keys"));
        assertTrue(summary.contains("prevalence_estimate,NOT_CLAIMED"));
        assertTrue(summary.contains("production_qualification,NOT_GRANTED"));
        assertTrue(readme.contains("not a prevalence estimate"));
        assertTrue(Files.isRegularFile(out.resolve("geometry-failures.csv")));
    }
}
