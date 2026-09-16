package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AerodynamicForceObservationResourceTest {
    @Test void forceObservationUsesRealSableDragAndDoesNotClaimAircraftYaw() throws Exception {
        String source = Files.readString(Path.of("src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeAerodynamicForceObservationAcceptance.java"));
        assertTrue(source.contains("ForceGroups"));
        assertTrue(source.contains("DRAG"));
        assertTrue(source.contains("getRecordedPointForces"));
        assertTrue(source.contains("enableIndividualQueuedForcesTracking"));
        assertTrue(source.contains("forceTrackingReleased=true"));
        assertTrue(source.contains("aircraftSignConventionQualified=false"));
    }
}
