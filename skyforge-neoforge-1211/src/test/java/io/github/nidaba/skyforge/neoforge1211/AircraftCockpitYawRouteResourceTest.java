package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class AircraftCockpitYawRouteResourceTest {
    private static final Path PROJECT_DIRECTORY = Path.of(System.getProperty("skyforge.test.projectDirectory", ".")).toAbsolutePath().normalize();

    @Test
    void productionRouteRetainsExactSourceProvenanceAndNoRuntimeShortcut() throws IOException {
        String profile = readMain("SkyforgeAircraftCockpitYawRouteProfile.java");
        String lowerer = readMain("SkyforgeAircraftCockpitYawRouteLowerer.java");
        String ir = readMain("SkyforgeAircraftCockpitYawRouteIR.java");
        assertTrue(profile.contains("ac0c444d9828da3453ae8cc65338e8de063286fb"));
        assertTrue(profile.contains("RotationPropagator"));
        assertTrue(profile.contains("simulated:steering_wheel"));
        assertTrue(profile.contains("create:gearbox"));
        assertTrue(profile.contains("create:shaft"));
        assertTrue(profile.contains("create:cogwheel"));
        assertTrue(lowerer.contains("gearboxModifier"));
        assertTrue(lowerer.contains("sourcePilotStationDigestSha256"));
        assertTrue(lowerer.contains("sourceYawControlDigestSha256"));
        assertTrue(ir.contains("sourceSteeringYawSourceDigestSha256"));
        assertTrue(lowerer.contains("cockpit route glue domain crosses forbidden child or air-gap boundary"));
        assertTrue(ir.contains("cockpitRouteRuntimeProbeReady"));
        assertFalse(lowerer.contains("SteeringWheelPacket"));
        assertFalse(lowerer.contains("targetAngleToUpdate"));
    }

    private static String readMain(String name) throws IOException {
        return Files.readString(PROJECT_DIRECTORY.resolve("src/main/java/io/github/nidaba/skyforge/neoforge1211/" + name));
    }
}
