package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class AircraftSteeringYawSourceResourceTest {
    private static final Path PROJECT_DIRECTORY = Path.of(System.getProperty("skyforge.test.projectDirectory", "."))
            .toAbsolutePath().normalize();

    @Test
    void productionStaticContractReferencesAcceptedPlatformAuthorityWithoutCloningClientFixture() throws IOException {
        String profile = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeAircraftSteeringYawSourceProfile.java"));
        String lowerer = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeAircraftSteeringYawSourceLowerer.java"));
        String ledger = Files.readString(PROJECT_DIRECTORY.resolve(
                "../docs/agent-state/COMPILER_INTEGRATION_CAPABILITIES.json").normalize());
        int start = ledger.indexOf("\"STEERING_WHEEL_CLIENT_ON_SABLE_LIFECYCLE\"");
        assertTrue(start >= 0);
        String entry = ledger.substring(start, Math.min(ledger.length(), start + 9000));

        assertTrue(entry.contains("\"status\": \"accepted\""));
        assertTrue(entry.contains("\"verification_level\": \"L2\""));
        assertTrue(entry.contains("\"B\": true"));
        assertTrue(profile.contains("simulated:steering_wheel"));
        assertTrue(profile.contains("on_floor\", \"false"));
        assertTrue(profile.contains("50443d00afa06e0982b45f40cd686f7ecf978132"));
        assertTrue(profile.contains("hasShaftTowards"));
        assertTrue(profile.contains("RPM=16"));
        assertTrue(lowerer.contains("sourceRudderActuationAuthorityDigestSha256"));
        assertTrue(lowerer.contains("sourceRudderNeutralReturnAuthorityDigestSha256"));
        assertFalse(lowerer.contains("MultiPlayerGameMode"));
        assertFalse(lowerer.contains("SteeringWheelPacket"));
        assertFalse(lowerer.contains("updateTargetAngle("));
    }
}
