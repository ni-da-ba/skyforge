package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HydrologyOrdinarySpanCorpusTest {
    @Test
    void ordinarySpanCorpusIsDeterministicAndExplicit() throws Exception {
        Path first = Path.of("build", "evidence", "hydrology-ordinary-span-test-a");
        Path second = Path.of("build", "evidence", "hydrology-ordinary-span-test-b");

        HydrologyOrdinarySpanCorpusCli.main(new String[] {first.toString()});
        HydrologyOrdinarySpanCorpusCli.main(new String[] {second.toString()});

        String a = Files.readString(first.resolve("manifest.csv"));
        String b = Files.readString(second.resolve("manifest.csv"));
        assertEquals(a, b);
        assertTrue(a.contains(
                "primary-287,287,1090,1758,0.000000000,0.212121212,ALLUVIAL,INFEASIBLE"));
        assertTrue(a.contains(
                "primary-287,287,1090,1758,0.242424242,0.333333333,ALLUVIAL,INFEASIBLE"));
        assertTrue(a.contains(
                "primary-287,287,1090,1758,0.454545455,0.818181818,ALLUVIAL,INFEASIBLE"));
        assertTrue(a.contains(
                "primary-287,287,1090,1758,0.969696970,1.000000000,ALLUVIAL,INFEASIBLE"));
        assertTrue(a.contains(
                "confluence-632,632,759,710,0.000000000,0.704136615,ALLUVIAL,SOLVED_QUALIFIED"));
        assertTrue(a.contains(
                "confluence-632,632,1088,710,0.000000000,0.975385479,ALLUVIAL,SOLVED_QUALIFIED"));
        assertTrue(a.contains(
                "stress-512,512,1631,1729,0.000000000,0.825864069,ALLUVIAL,SOLVED_QUALIFIED"));
        assertTrue(a.contains("lake-609"));
        assertTrue(a.contains("RETAINED_OPEN_WATER"));
        assertTrue(a.contains("BOUNDARY_DEFERRED"));
        assertTrue(Files.isRegularFile(first.resolve("README.txt")));
    }
}
