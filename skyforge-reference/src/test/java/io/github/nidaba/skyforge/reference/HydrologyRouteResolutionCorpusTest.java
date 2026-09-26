package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HydrologyRouteResolutionCorpusTest {
    @Test
    void routeResolutionCorpusIsDeterministicAndNonempty() throws Exception {
        Path first = Path.of("build", "evidence", "hydrology-route-resolution-test-a");
        Path second = Path.of("build", "evidence", "hydrology-route-resolution-test-b");

        HydrologyRouteResolutionCorpusCli.main(new String[] {first.toString()});
        HydrologyRouteResolutionCorpusCli.main(new String[] {second.toString()});

        String a = Files.readString(first.resolve("manifest.csv"));
        String b = Files.readString(second.resolve("manifest.csv"));
        assertEquals(a, b);
        assertTrue(a.contains("ordinary-77-a"));
        assertTrue(a.contains("ordinary-77-b"));
        assertTrue(a.contains("coarseObjective"));
        assertTrue(a.contains("mediumFinePolylineDistance"));
        assertTrue(a.lines().count() == 3);
    }
}
