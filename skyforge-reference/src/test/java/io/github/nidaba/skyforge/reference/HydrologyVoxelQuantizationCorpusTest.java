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
        System.out.println("F4_QUANTIZATION=" + a.lines()
                .filter(line -> line.startsWith("ordinary-77")
                        || line.startsWith("primary-287")
                        || line.startsWith("confluence-632")
                        || line.startsWith("lake-609"))
                .toList());

        assertTrue(a.contains(
                "ordinary-77,77,2,0,8969,4911,15849,7,0.999999918,0.000407162,0.000000000"));
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
