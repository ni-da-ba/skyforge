package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HydrologyGameScaleCalibrationSweepTest {
    @Test
    void fixedControlSweepIsDeterministicAndKeepsKey700HeldOut() throws Exception {
        Path first = Path.of("build", "evidence", "hydrology-game-scale-sweep-test-a");
        Path second = Path.of("build", "evidence", "hydrology-game-scale-sweep-test-b");
        HydrologyGameScaleCalibrationSweepCli.main(new String[] {first.toString()});
        HydrologyGameScaleCalibrationSweepCli.main(new String[] {second.toString()});

        String a = Files.readString(first.resolve("ordinary-span-sweep.csv"));
        String b = Files.readString(second.resolve("ordinary-span-sweep.csv"));
        assertEquals(a, b);
        assertEquals(82, a.lines().count());
        assertTrue(a.contains("accepted-77,false,6,61,77"));
        assertTrue(a.contains("rejected-287,false,8,81,287"));
        assertTrue(a.contains("heldout-700,true,8,81,700"));
        assertTrue(Files.readString(first.resolve("README.txt")).contains(
                "Key 700 is not used to choose parameters."));
    }
}
