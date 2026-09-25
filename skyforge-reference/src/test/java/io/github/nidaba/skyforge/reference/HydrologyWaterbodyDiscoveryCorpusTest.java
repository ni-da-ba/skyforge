package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HydrologyWaterbodyDiscoveryCorpusTest {
    @Test
    void openWaterDiscoveryIsDeterministicAndFindsCalibrationCandidates() throws Exception {
        Path first = Path.of("build", "evidence", "hydrology-waterbody-discovery-test-a");
        Path second = Path.of("build", "evidence", "hydrology-waterbody-discovery-test-b");

        HydrologyWaterbodyDiscoveryCorpusCli.main(new String[] {first.toString()});
        HydrologyWaterbodyDiscoveryCorpusCli.main(new String[] {second.toString()});

        String a = Files.readString(first.resolve("discovery.csv"));
        String b = Files.readString(second.resolve("discovery.csv"));
        assertEquals(a, b);
        assertTrue(a.contains("#summary"));
    }
}
