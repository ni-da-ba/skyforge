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
        assertTrue(terminalA.contains(
                "primary-287,287,1758,1758,EDGE_OUTLET,PHYSICAL_REJECTION,1,"));
        assertTrue(terminalA.contains(
                "ordinary-77,77,559,559,EDGE_OUTLET,QUALIFIED,1,"));
        assertTrue(terminalA.contains(
                "ordinary-77,77,1842,1842,EDGE_OUTLET,QUALIFIED,1,"));
        assertTrue(terminalA.contains(
                "confluence-632,632,225,225,EDGE_OUTLET,TRANSITION_DEFERRED,3,"));
        assertTrue(terminalA.contains(
                "lake-609,609,1397,1397,RETAINED_OPEN_WATER,PHYSICAL_REJECTION,3,"));
        assertTrue(reachA.contains(
                "ordinary-77,77,709,559,QUALIFIED,1,0,"));
        assertTrue(reachA.contains(
                "ordinary-77,77,1742,1842,QUALIFIED,1,0,"));
        assertTrue(reachA.contains(
                "confluence-632,632,759,710,QUALIFIED,1,0,"));
        assertTrue(reachA.contains(
                "confluence-632,632,710,225,TRANSITION_DEFERRED,1,1,"));
        assertTrue(Files.isRegularFile(first.resolve("README.txt")));
    }
}
