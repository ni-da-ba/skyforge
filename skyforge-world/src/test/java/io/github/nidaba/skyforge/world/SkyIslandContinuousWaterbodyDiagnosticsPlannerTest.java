package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkyIslandContinuousWaterbodyDiagnosticsPlannerTest {
    private static final long SEED = 0x534B59464F524745L;

    @Test
    void basinDiagnosticsAreDeterministicAndFiniteAcrossControls() {
        for (long key : new long[] {83L, 287L, 512L, 632L, 811L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            List<SkyIslandContinuousWaterbodyDiagnostics> first =
                    SkyIslandContinuousWaterbodyDiagnosticsPlanner.measure(descriptor);
            List<SkyIslandContinuousWaterbodyDiagnostics> second =
                    SkyIslandContinuousWaterbodyDiagnosticsPlanner.measure(descriptor);
            assertEquals(first, second);
            for (SkyIslandContinuousWaterbodyDiagnostics d : first) {
                assertTrue(d.equivalentDiameter() > 0.0);
                assertTrue(Double.isFinite(d.maximumDepthWorldUnits()));
                assertTrue(Double.isFinite(d.depthToEquivalentDiameterRatio()));
                assertTrue(Double.isFinite(d.maximumShorelineGrade()));
                assertTrue(Double.isFinite(d.spillHeadroomWorldUnits()));
                assertTrue(d.matchedTerminalReachCount() >= 0);
                assertTrue(Double.isFinite(d.maximumChannelDatumMismatchWorldUnits()));
                assertTrue(d.maximumChannelDatumMismatchWorldUnits() >= 0.0);
            }
        }
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 6L, 61L, key));
    }
}
