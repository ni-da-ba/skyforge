package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AuthorshipMultiIslandOwnershipCorpusTest {
    @Test
    void generatesReviewCorpus() throws Exception {
        Path output =
                Path.of(
                        "build",
                        "evidence",
                        "authorship-multi-island-ownership-v1");
        AuthorshipMultiIslandOwnershipCorpusCli.main(
                new String[] {output.toString()});

        assertTrue(Files.isRegularFile(output.resolve("index.html")));
        assertTrue(Files.isRegularFile(output.resolve("atlas.png")));
        assertTrue(Files.isRegularFile(output.resolve("manifest.csv")));

        String manifest = Files.readString(output.resolve("manifest.csv"));
        assertEquals(2, manifest.lines().count());
        assertEquals(
                "sampledPoints,multiConservativePoints,multiPhysicalPoints,"
                        + "multipleConservativeUniquePoints,multipleConservativeEmptyPoints,"
                        + "uniqueOwnedPoints,ambiguousOwnedPoints,physicalUnownedPoints,"
                        + "twoPhysicalOneNativePoints,stackedCrossContaminationViolations,"
                        + "orderDependenceViolations",
                manifest.lines().findFirst().orElseThrow());

        String[] columns =
                manifest.lines().skip(1).findFirst().orElseThrow().split(",", -1);
        assertEquals(11, columns.length);

        assertTrue(Integer.parseInt(columns[0]) > 0);
        assertTrue(Integer.parseInt(columns[1]) > 0);
        assertTrue(Integer.parseInt(columns[2]) > 0);
        assertTrue(Integer.parseInt(columns[3]) > 0);
        assertTrue(Integer.parseInt(columns[4]) > 0);
        assertTrue(Integer.parseInt(columns[5]) > 0);
        assertTrue(Integer.parseInt(columns[6]) > 0);
        assertTrue(Integer.parseInt(columns[7]) > 0);
        assertTrue(Integer.parseInt(columns[8]) > 0);
        assertEquals("0", columns[9]);
        assertEquals("0", columns[10]);
    }
}
