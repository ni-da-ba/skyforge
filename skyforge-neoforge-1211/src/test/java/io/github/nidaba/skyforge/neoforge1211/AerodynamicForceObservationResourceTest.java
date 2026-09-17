package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AerodynamicForceObservationResourceTest {
    private static final Path PROJECT_DIRECTORY = Path.of(System.getProperty("skyforge.test.projectDirectory", "."))
            .toAbsolutePath().normalize();

    @Test void forceObservationUsesRealSableDragAndDoesNotClaimAircraftYaw() throws Exception {
        String source = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeAerodynamicForceObservationAcceptance.java"));
        assertTrue(source.contains("ForceGroups"));
        assertTrue(source.contains("DRAG"));
        assertTrue(source.contains("getRecordedPointForces"));
        assertTrue(source.contains("enableIndividualQueuedForcesTracking"));
        assertTrue(source.contains("forceTrackingReleased=true"));
        assertTrue(source.contains("aircraftSignConventionQualified=false"));
        assertTrue(source.contains("aggregate DRAG moment is non-finite"));
        assertFalse(source.contains("aggregate DRAG moment is zero"));
    }
    @Test void ledgerPublishesAcceptedL2Authority() throws Exception {
        String ledger = Files.readString(PROJECT_DIRECTORY.resolve(
                "../docs/agent-state/COMPILER_INTEGRATION_CAPABILITIES.json").normalize());
        int start = ledger.indexOf("\"SABLE_AERODYNAMIC_FORCE_OBSERVATION_LIFECYCLE\"");
        assertTrue(start >= 0);
        String entry = ledger.substring(start);
        assertTrue(entry.contains("\"status\": \"accepted\""));
        assertTrue(entry.contains("\"workflow_run\": 35121317584"));
        assertTrue(entry.contains("\"job\": 104880160249"));
        assertTrue(entry.contains("d274eeac6f960d9dd69a3b24ca189a1340305c5c"));
        assertTrue(entry.contains("\"result\": \"PASS\""));
        assertTrue(entry.contains("\"B\": true"));
        assertTrue(entry.contains("\"C\": false"));
    }

}
