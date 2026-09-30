package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class HydrologyRefinedVoxelQuantizationCorpusTest {
    @Test
    void refinedVoxelQuantizationIsDeterministicAndNeverExceedsBaselineRemoval()
            throws Exception {
        Path first = Path.of("build", "evidence", "hydrology-refined-voxel-quantization-test-a");
        Path second = Path.of("build", "evidence", "hydrology-refined-voxel-quantization-test-b");

        HydrologyRefinedVoxelQuantizationCorpusCli.main(new String[] {first.toString()});
        HydrologyRefinedVoxelQuantizationCorpusCli.main(new String[] {second.toString()});

        String summaryA = Files.readString(first.resolve("summary.csv"));
        String summaryB = Files.readString(second.resolve("summary.csv"));
        String reachesA = Files.readString(first.resolve("reaches.csv"));
        String reachesB = Files.readString(second.resolve("reaches.csv"));
        assertEquals(summaryA, summaryB);
        assertEquals(reachesA, reachesB);

        String[] row = Arrays.stream(summaryA.split("\\R"))
                .filter(line -> line.startsWith("ordinary-77,"))
                .findFirst()
                .orElseThrow()
                .split(",");
        assertEquals("77", row[1]);
        int refinedReaches = Integer.parseInt(row[2]);
        int totalReaches = Integer.parseInt(row[3]);
        int postD2Rejected = Integer.parseInt(row[4]);
        int baselineColumns = Integer.parseInt(row[5]);
        int refinedColumns = Integer.parseInt(row[6]);
        long baselineRemovedBlocks = Long.parseLong(row[7]);
        long refinedRemovedBlocks = Long.parseLong(row[8]);
        int quantizedShallower = Integer.parseInt(row[9]);
        int quantizedUnchanged = Integer.parseInt(row[10]);
        int quantizedDeeper = Integer.parseInt(row[11]);
        int continuousShallower = Integer.parseInt(row[12]);
        int continuousDeeper = Integer.parseInt(row[14]);
        double maxRecoveryWorld = Double.parseDouble(row[15]);
        double meanRecoveryWorld = Double.parseDouble(row[16]);
        double minimumResidual = Double.parseDouble(row[17]);
        double maximumResidual = Double.parseDouble(row[18]);

        assertTrue(refinedReaches > 0);
        assertTrue(refinedReaches <= totalReaches);
        assertEquals(0, postD2Rejected);
        assertTrue(baselineColumns > 0);
        assertEquals(baselineColumns, refinedColumns);
        assertTrue(baselineRemovedBlocks >= 0);
        assertTrue(refinedRemovedBlocks >= 0);
        assertTrue(refinedRemovedBlocks <= baselineRemovedBlocks);
        assertEquals(0, quantizedDeeper);
        assertEquals(0, continuousDeeper);
        assertEquals(refinedColumns, quantizedShallower + quantizedUnchanged);
        assertTrue(continuousShallower > 0);
        assertTrue(maxRecoveryWorld > 0.0);
        assertTrue(meanRecoveryWorld >= 0.0);
        assertTrue(meanRecoveryWorld <= maxRecoveryWorld);
        assertTrue(minimumResidual >= 0.0);
        assertTrue(maximumResidual < 1.0);
        assertTrue(Files.readString(first.resolve("README.txt")).contains("evidence only"));
    }
}
