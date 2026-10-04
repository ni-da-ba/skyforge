package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HydrologyGameScaleCalibrationSweepTest {
    @Test
    void fixedControlSweepEmitsOrderedDiagnosticsAndKeepsKey700HeldOut() throws Exception {
        Path output = Path.of("build", "evidence", "hydrology-game-scale-sweep-test");
        HydrologyGameScaleCalibrationSweepCli.main(new String[] {output.toString()});

        String summary = Files.readString(output.resolve("ordinary-span-sweep.csv"));
        assertEquals(82, summary.lines().count());
        assertTrue(summary.contains("accepted-77,false,6,61,77"));
        assertTrue(summary.contains("rejected-287,false,8,81,287"));
        assertTrue(summary.contains("heldout-700,true,8,81,700"));
        String diagnostics = Files.readString(output.resolve("ordinary-span-diagnostics.txt"));
        assertTrue(diagnostics.startsWith(
                "control|heldOut|key|reach|parentStations|parameterSet|outcome|details"));
        var diagnosticRows = diagnostics.lines().skip(1).toList();
        assertEquals(diagnosticRows.stream().sorted().toList(), diagnosticRows);
        assertTrue(diagnostics.contains("accepted-77|false|77|"));
        assertTrue(diagnostics.contains("rejected-287|false|287|"));
        assertTrue(diagnostics.contains("heldout-700|true|700|"));
        assertTrue(diagnostics.contains("bedSlopeDownstreamRange="));
        assertTrue(Files.readString(output.resolve("README.txt")).contains(
                "Key 700 is not used to choose parameters."));
    }
}
