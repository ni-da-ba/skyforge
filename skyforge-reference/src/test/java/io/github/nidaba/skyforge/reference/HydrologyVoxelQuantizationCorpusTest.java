package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
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

        String[] ordinary = row(a, "ordinary-77");
        assertEquals("2", ordinary[2]);
        assertEquals("0", ordinary[3]);
        assertTrue(Integer.parseInt(ordinary[4]) > 0);
        assertTrue(Integer.parseInt(ordinary[5]) > 0);
        assertTrue(Long.parseLong(ordinary[6]) > 0L);
        assertTrue(Double.parseDouble(ordinary[8]) >= 0.0);
        assertTrue(Double.parseDouble(ordinary[8]) < 1.0);
        assertTrue(Double.parseDouble(ordinary[9]) >= 0.0);
        assertEquals("0.000000000", ordinary[10]);
        assertTrue(a.contains(
                "ordinary-77-weak,77,0,2,0,0,0,0,0.000000000,0.000000000,0.000000000"));

        for (String name : new String[] {"primary-287", "confluence-632", "lake-609"}) {
            String[] control = row(a, name);
            assertEquals("0", control[2]);
            assertEquals("0", control[3]);
            assertEquals("0", control[4]);
            assertEquals("0", control[5]);
            assertEquals("0", control[6]);
            assertEquals("0.000000000", control[10]);
        }
        assertTrue(Files.isRegularFile(first.resolve("README.txt")));
    }

    private static String[] row(String csv, String specimen) {
        return Arrays.stream(csv.split("\\R"))
                .filter(line -> line.startsWith(specimen + ","))
                .findFirst()
                .orElseThrow()
                .split(",");
    }
}
