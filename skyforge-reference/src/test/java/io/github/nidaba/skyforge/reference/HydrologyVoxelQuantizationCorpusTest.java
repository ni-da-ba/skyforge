package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HydrologyVoxelQuantizationCorpusTest {
    @Test
    void voxelQuantizationCorpusIsDeterministicAndFailClosed() throws Exception {
        Path first = Path.of("build", "evidence", "hydrology-voxel-quantization-test-a");
        Path second = Path.of("build", "evidence", "hydrology-voxel-quantization-test-b");

        HydrologyVoxelQuantizationCorpusCli.main(new String[] {first.toString()});
        HydrologyVoxelQuantizationCorpusCli.main(new String[] {second.toString()});

        String a = Files.readString(first.resolve("summary.csv"));
        String b = Files.readString(second.resolve("summary.csv"));
        assertEquals(a, b);
        assertTrue(a.contains("ordinary-77,77,8969,"));
        assertTrue(a.contains("primary-287,287,0,0,0,0,0.000000000,0.000000000,0.000000000"));
        assertTrue(a.contains("confluence-632,632,0,0,0,0,0.000000000,0.000000000,0.000000000"));
        assertTrue(a.contains("lake-609,609,0,0,0,0,0.000000000,0.000000000,0.000000000"));
        assertTrue(Files.isRegularFile(first.resolve("README.txt")));
    }
}
