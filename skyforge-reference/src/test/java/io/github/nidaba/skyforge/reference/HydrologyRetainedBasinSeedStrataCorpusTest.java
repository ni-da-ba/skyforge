package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.util.Arrays;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HydrologyRetainedBasinSeedStrataCorpusTest {
    @Test
    void fixedDiscoveryCorpusIsOptInAndHasStableDimensions() throws Exception {
        assertEquals(6144,
                HydrologyRetainedBasinSeedStrataCorpusCli.EXPECTED_SPECIMEN_COUNT);
        if (!"true".equalsIgnoreCase(System.getenv("SKYFORGE_RUN_BASIN_DISCOVERY"))) {
            return;
        }

        Path out = Path.of("build", "evidence",
                HydrologyRetainedBasinSeedStrataCorpusCli.EVIDENCE_ID);
        HydrologyRetainedBasinSeedStrataCorpusCli.main(new String[] {out.toString()});

        var specimenLines = Files.readAllLines(out.resolve("specimens.csv"));
        var candidateLines = Files.readAllLines(out.resolve("candidates.csv"));
        var semanticLines = Files.readAllLines(out.resolve("semantics.csv"));
        String summary = Files.readString(out.resolve("summary.csv"));
        String readme = Files.readString(out.resolve("README.txt"));

        assertEquals(6145, specimenLines.size(), "header plus 6144 fixed identities");
        assertEquals(12, specimenLines.get(0).split(",", -1).length);
        Set<String> identities = new HashSet<>();
        for (String line : specimenLines.subList(1, specimenLines.size())) {
            String[] fields = line.split(",", -1);
            identities.add(String.join(",", fields[0], fields[1], fields[2],
                    fields[3], fields[4], fields[5]));
        }
        assertEquals(6144, identities.size());
        String[] candidateHeader = candidateLines.get(0).split(",", -1);
        int matchedIndex = Arrays.asList(candidateHeader).indexOf("matched_terminal_reaches");
        int minimumOffsetIndex =
                Arrays.asList(candidateHeader).indexOf("min_channel_datum_offset_world");
        int maximumOffsetIndex =
                Arrays.asList(candidateHeader).indexOf("max_channel_datum_offset_world");
        int mismatchIndex =
                Arrays.asList(candidateHeader).indexOf("max_channel_datum_mismatch_world");
        for (String line : candidateLines.subList(1, candidateLines.size())) {
            String[] fields = line.split(",", -1);
            int matched = Integer.parseInt(fields[matchedIndex]);
            double minimum = Double.parseDouble(fields[minimumOffsetIndex]);
            double maximum = Double.parseDouble(fields[maximumOffsetIndex]);
            double mismatch = Double.parseDouble(fields[mismatchIndex]);
            if (matched == 0) {
                assertEquals(0.0, minimum);
                assertEquals(0.0, maximum);
                assertEquals(0.0, mismatch);
            } else {
                assertTrue(minimum <= maximum);
                assertEquals(mismatch, Math.max(Math.abs(minimum), Math.abs(maximum)), 1.0e-8);
            }
        }
        String lake609 = candidateLines.stream().skip(1)
                .filter(line -> {
                    String[] fields = line.split(",", -1);
                    return fields[0].equals("seed-skyforge")
                            && fields[2].equals("reference-8-81")
                            && fields[5].equals("609")
                            && fields[7].equals("lake");
                })
                .findFirst()
                .orElseThrow(() -> new AssertionError("fixed lake-609 candidate must be present"));
        String[] lakeFields = lake609.split(",", -1);
        double minimumLakeOffset = Double.parseDouble(lakeFields[minimumOffsetIndex]);
        double maximumLakeOffset = Double.parseDouble(lakeFields[maximumOffsetIndex]);
        double lakeMismatch = Double.parseDouble(lakeFields[mismatchIndex]);
        assertEquals(1, Integer.parseInt(lakeFields[matchedIndex]));
        assertTrue(Math.abs(minimumLakeOffset) > 0.0,
                "the fixed exact terminal must expose its transition direction");
        assertEquals(minimumLakeOffset, maximumLakeOffset, 1.0e-9);
        assertEquals(lakeMismatch, Math.abs(minimumLakeOffset), 1.0e-8);
        assertEquals(15, semanticLines.get(0).split(",", -1).length);
        assertTrue(semanticLines.size() > 1, "retained-sink semantic classifications are recorded");
        assertTrue(summary.contains("stratum_seed-min/reference-8-81,1024"));
        assertTrue(summary.contains("stratum_seed-min/reference-6-61,1024"));
        assertTrue(summary.contains("stratum_seed-zero/reference-8-81,1024"));
        assertTrue(summary.contains("stratum_seed-zero/reference-6-61,1024"));
        assertTrue(summary.contains("stratum_seed-skyforge/reference-8-81,1024"));
        assertTrue(summary.contains("stratum_seed-skyforge/reference-6-61,1024"));
        assertTrue(summary.contains("sample_design,3 seed strata x 2 fixed namespaces x 1024 keys"));
        assertTrue(summary.contains("prevalence_estimate,NOT_CLAIMED"));
        assertTrue(summary.contains("production_qualification,NOT_GRANTED"));
        assertTrue(readme.contains("not a prevalence estimate"));
        assertTrue(readme.contains("marching-squares shoreline perimeter"));
        assertTrue(readme.contains("signed terminal"));
        assertTrue(Files.isRegularFile(out.resolve("geometry-failures.csv")));
    }
}
