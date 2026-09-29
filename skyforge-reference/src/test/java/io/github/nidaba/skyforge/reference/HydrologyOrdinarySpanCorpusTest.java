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
        assertManifestContains(a,
                "primary-287,287,1090,1758,0.000000000,0.212121212,ALLUVIAL,INFEASIBLE");
        assertManifestContains(a,
                "primary-287,287,1090,1758,0.242424242,0.333333333,ALLUVIAL,INFEASIBLE");
        assertManifestContains(a,
                "primary-287,287,1090,1758,0.454545455,0.818181818,ALLUVIAL,INFEASIBLE");
        assertManifestContains(a,
                "primary-287,287,1090,1758,0.969696970,1.000000000,ALLUVIAL,INFEASIBLE");
        assertManifestContains(a,
                "confluence-632,632,759,710,0.000000000,0.704136615,ALLUVIAL,SOLVED_QUALIFIED");
        assertManifestContains(a,
                "confluence-632,632,1088,710,0.000000000,0.975384176,ALLUVIAL,SOLVED_QUALIFIED");
        assertManifestContains(a,
                "stress-512,512,1631,1729,0.000000000,0.825864069,ALLUVIAL,SOLVED_QUALIFIED");
        assertManifestContains(a, "lake-609");
        assertManifestContains(a, "RETAINED_OPEN_WATER");
        assertManifestContains(a, "BOUNDARY_DEFERRED");
        assertTrue(Files.isRegularFile(first.resolve("README.txt")));
    }

    private static void assertManifestContains(String manifest, String expected) {
        assertTrue(
                manifest.contains(expected),
                () -> "missing expected manifest fragment: " + expected
                        + "\nActual manifest:\n" + manifest);
    }
}
