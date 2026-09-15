package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class SteeringWheelClientOnSableLifecycleResourceTest {
    private static final Path PROJECT_DIRECTORY =
            Path.of(System.getProperty("skyforge.test.projectDirectory", "."))
                    .toAbsolutePath()
                    .normalize();

    @Test
    void fixtureComposesAcceptedSableGlueAndKineticSeams() throws IOException {
        String build = Files.readString(PROJECT_DIRECTORY.resolve("build.gradle.kts"));
        String pins = Files.readString(PROJECT_DIRECTORY.resolve("wave-c1-mods.properties"));
        String server = serverSource();

        assertTrue(build.contains("compilerPlatformSteeringWheelClientWorldPrepareServer"));
        assertTrue(build.contains("compilerPlatformSteeringWheelClient"));
        assertTrue(build.contains("sourceSet.set(waveC11Runtime)"));
        assertTrue(build.contains("--quickPlaySingleplayer"));
        assertTrue(pins.contains("minecraft.version=1.21.1"));
        assertTrue(pins.contains("neoforge.version=21.1.249"));
        assertTrue(server.contains("simulated:physics_assembler"));
        assertTrue(server.contains("simulated:steering_wheel"));
        assertTrue(server.contains("create:shaft"));
        assertTrue(server.contains("create:gearbox"));
        assertTrue(server.contains("SuperGlueEntity"));
    }

    @Test
    void clientUsesProductionInteractionPathWithoutSyntheticPacketShortcut() throws IOException {
        String client = clientSource();

        assertTrue(client.contains("minecraft.gameMode.useItemOn"));
        assertTrue(client.contains("invokeSimulatedMouseMove"));
        assertTrue(client.contains("invokeSimulatedUseRelease"));
        assertTrue(client.contains("HoldInteractionManager"));
        assertTrue(client.contains("SimulatedCommonClientEvents"));
        assertTrue(client.contains("projectOutOfClientRenderPose"));
        assertTrue(client.contains("STEERING_WHEEL_FLOOR"));
        assertTrue(client.contains("wheelShape.toAabbs()"));
        assertTrue(client.contains("lookingAtWheel.invoke"));
        assertTrue(client.contains("player.yRotO = yaw"));
        assertTrue(client.contains("player.xRotO = pitch"));
        assertTrue(client.contains("!angleInputHit"));
        assertFalse(client.contains("STEERING_WHEEL_VISUAL_X"));
        assertFalse(client.contains("STEERING_WHEEL_VISUAL_Y"));
        assertTrue(client.contains("getMethod(\"renderPose\", float.class)"));
        assertTrue(client.contains("getMethod(\"transformPosition\", Vec3.class)"));
        assertFalse(client.contains("projectOutOfSubLevel"));
        assertTrue(client.contains("CLIENT_SERVER_POSE_TOLERANCE_BLOCKS"));
        assertTrue(client.contains("expectedGlobalWheelCenter"));
        assertFalse(client.contains("new SteeringWheelPacket"));
        assertFalse(client.contains("startHolding("));
        assertFalse(client.contains("stopHolding("));
        assertFalse(client.contains("SteeringWheelPacket.handle"));
        assertFalse(client.contains("targetAngleToUpdate ="));
    }

    @Test
    void serverObservesRoundTripAndKineticSettleOnCanonicalBody() throws IOException {
        String server = serverSource();

        assertTrue(server.contains("getUniqueId"));
        assertTrue(server.contains("findCanonicalBody"));
        assertTrue(server.contains("currentPhysicsHandleValid=true"));
        assertTrue(server.contains("FAIL_CLIENT_INTERACTION"));
        assertTrue(server.contains("activePacketRoundTrip=true activeResponseSettled=true releasePacketRoundTrip=true"));
        assertTrue(server.contains("activeEndpointSpeed"));
        assertTrue(server.contains("settledEndpointSpeed"));
        assertTrue(server.contains("playerSableTrackingQualified=false"));
        assertTrue(server.contains("fixtureLivenessTicket=sable:command_forced(released)"));
        assertTrue(server.contains("testSetupPhysicsPinned=true"));
        assertFalse(server.contains("SteeringWheelPacket"));
    }

    @Test
    void workflowRequiresActualXvfbClientAndLedgerStartsFailClosed() throws IOException {
        String workflow = Files.readString(PROJECT_DIRECTORY.resolve(
                "../.github/workflows/compiler-platform-steering-wheel-client-on-sable.yml").normalize());
        String ledger = Files.readString(PROJECT_DIRECTORY.resolve(
                "../docs/agent-state/COMPILER_INTEGRATION_CAPABILITIES.json").normalize());
        int start = ledger.indexOf("\"STEERING_WHEEL_CLIENT_ON_SABLE_LIFECYCLE\"");
        assertTrue(start >= 0);
        String entry = ledger.substring(start);

        assertTrue(workflow.contains("xvfb-run -a"));
        assertTrue(workflow.contains("waveC11ResolvePinnedMods"));
        assertTrue(workflow.contains("runCompilerPlatformSteeringWheelClientWorldPrepareServer"));
        assertTrue(workflow.contains("runCompilerPlatformSteeringWheelClient"));
        assertTrue(entry.contains("\"status\": \"qualification_pending\""));
        assertTrue(entry.contains("\"latest_accepted_evidence\": null"));
        assertTrue(entry.contains("\"B\": false"));
        assertTrue(entry.contains("\"C\": false"));
    }

    private static String serverSource() throws IOException {
        return Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/"
                        + "SkyforgeSteeringWheelClientOnSableLifecycleAcceptance.java"));
    }

    private static String clientSource() throws IOException {
        return Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/"
                        + "SkyforgeSteeringWheelClientOnSableClientAcceptance.java"));
    }
}
