package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HydrologyNetworkAssemblyCorpusTest {
    @Test
    void networkAssemblyCorpusIsDeterministicAndExplicit() throws Exception {
        Path first = Path.of("build", "evidence", "hydrology-network-assembly-test-a");
        Path second = Path.of("build", "evidence", "hydrology-network-assembly-test-b");

        HydrologyNetworkAssemblyCorpusCli.main(new String[] {first.toString()});
        HydrologyNetworkAssemblyCorpusCli.main(new String[] {second.toString()});

        String terminalA = Files.readString(first.resolve("terminal-components.csv"));
        String terminalB = Files.readString(second.resolve("terminal-components.csv"));
        String reachA = Files.readString(first.resolve("reach-assemblies.csv"));
        String reachB = Files.readString(second.resolve("reach-assemblies.csv"));

        assertEquals(terminalA, terminalB);
        assertEquals(reachA, reachB);
        assertTrue(terminalA.contains("primary-287"));
        assertTrue(terminalA.contains("lake-609"));
        assertTrue(terminalA.contains("RETAINED_OPEN_WATER"));
        assertTrue(terminalA.contains("PHYSICAL_REJECTION"));
        assertTrue(reachA.contains("confluence-632"));
        assertTrue(Files.isRegularFile(first.resolve("README.txt")));
    }
}
