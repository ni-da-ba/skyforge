package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkyIslandHydraulicDiscontinuityDiagnosticsPlannerTest {
    private static final long SEED = 0x534B59464F524745L;

    @Test
    void diagnosticsAreDeterministicAndConserveDropBounds() {
        for (long key : new long[] {287L, 649L, 811L, 512L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            List<SkyIslandHydraulicDiscontinuityDiagnostics> first =
                    SkyIslandHydraulicDiscontinuityDiagnosticsPlanner.measure(descriptor);
            List<SkyIslandHydraulicDiscontinuityDiagnostics> second =
                    SkyIslandHydraulicDiscontinuityDiagnosticsPlanner.measure(descriptor);
            assertEquals(first, second);
            assertFalse(first.isEmpty());
            for (SkyIslandHydraulicDiscontinuityDiagnostics d : first) {
                assertTrue(d.cascadeShareOfDownhillDrop() >= 0.0);
                assertTrue(d.cascadeShareOfDownhillDrop() <= 1.0 + 1.0e-10);
                assertTrue(d.maximumSingleSegmentDropPotential()
                        <= d.accumulatedDownhillDropPotential() + 1.0e-10);
            }
        }
    }

    @Test
    void primaryRejectedReachExposesItsInternalProfileStructure() {
        List<SkyIslandHydraulicDiscontinuityDiagnostics> diagnostics =
                SkyIslandHydraulicDiscontinuityDiagnosticsPlanner.measure(descriptor(287L));
        SkyIslandHydraulicDiscontinuityDiagnostics primary = diagnostics.stream()
                .filter(d -> d.reach().startCellIndex() == 1090
                        && d.reach().endCellIndex() == 1758)
                .findFirst()
                .orElseThrow();

        assertEquals(33, primary.reach().coarseSegmentCount());
        assertTrue(
                primary.profileTransitionCount() > 0 || primary.cascadeProfileCount() > 0,
                "mixed primary reach must expose transition/cascade structure for retry analysis");
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 8L, 81L, key));
    }
}
