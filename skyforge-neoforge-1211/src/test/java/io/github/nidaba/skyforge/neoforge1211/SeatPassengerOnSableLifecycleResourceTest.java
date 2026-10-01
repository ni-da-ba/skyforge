package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class SeatPassengerOnSableLifecycleResourceTest {
    private static final Path PROJECT_DIRECTORY =
            Path.of(System.getProperty("skyforge.test.projectDirectory", "."))
                    .toAbsolutePath()
                    .normalize();

    @Test
    void fixtureUsesExactCreateSeatOnLiveSableBody() throws IOException {
        String build = Files.readString(PROJECT_DIRECTORY.resolve("build.gradle.kts"));
        String pins = Files.readString(PROJECT_DIRECTORY.resolve("wave-c1-mods.properties"));
        String server = serverSource();

        assertTrue(build.contains("compilerPlatformSeatPassengerClientWorldPrepareServer"));
        assertTrue(build.contains("compilerPlatformSeatPassengerClient"));
        assertTrue(build.contains("sourceSet.set(waveC11Runtime)"));
        assertTrue(build.contains("--quickPlaySingleplayer"));
        assertTrue(pins.contains("minecraft.version=1.21.1"));
        assertTrue(pins.contains("neoforge.version=21.1.249"));
        assertTrue(server.contains("create:brown_seat"));
        assertTrue(server.contains("simulated:physics_assembler"));
        assertTrue(server.contains("ac0c444d9828da3453ae8cc65338e8de063286fb"));
        assertTrue(server.contains("SeatBlock"));
        assertTrue(server.contains("SeatEntity"));
        assertTrue(server.contains("findCanonicalBody"));
        assertTrue(server.contains("findCurrentPhysicsHandle"));
        assertTrue(server.contains("fixtureLivenessTicket=sable:command_forced(released)"));
    }

    @Test
    void clientUsesOrdinarySeatInteractionAndCrouchWithoutSyntheticMounting() throws IOException {
        String client = clientSource();

        assertTrue(client.contains("minecraft.gameMode.useItemOn"));
        assertTrue(client.contains("minecraft.options.keyShift.setDown(true)"));
        assertTrue(client.contains("SeatEntity"));
        assertTrue(client.contains("serverSeatId.equals(vehicle.getUUID())"));
        assertTrue(client.contains("projectOutOfClientRenderPose"));
        assertTrue(client.contains("CLIENT_SERVER_POSE_TOLERANCE_BLOCKS"));
        assertFalse(client.contains("startRiding("));
        assertFalse(client.contains("stopRiding("));
        assertFalse(client.contains("ejectPassengers("));
        assertFalse(client.contains("setVehicle("));
    }

    @Test
    void serverRequiresMountDismountAndSeatEntityCleanup() throws IOException {
        String server = serverSource();

        assertTrue(server.contains("TIMEOUT_SEAT_MOUNT"));
        assertTrue(server.contains("TIMEOUT_SEAT_DISMOUNT"));
        assertTrue(server.contains("TIMEOUT_SEAT_ENTITY_CLEANUP"));
        assertTrue(server.contains("FAIL_PASSENGER_TRACKING"));
        assertTrue(server.contains("seatMountObserved=true seatDismountObserved=true"));
        assertTrue(server.contains("seatEntityCleanupObserved=true"));
        assertTrue(server.contains("samePersistentSableUuid=true"));
        assertTrue(server.contains("playerSableTrackingQualified=false"));
        assertFalse(server.contains("startRiding("));
        assertFalse(server.contains("stopRiding("));
    }

    @Test
    void workflowRunsActualClientAndLedgerPublishesAcceptedAgentBAuthority() throws IOException {
        String workflow = Files.readString(PROJECT_DIRECTORY.resolve(
                "../.github/workflows/compiler-platform-seat-passenger-on-sable.yml").normalize());
        String ledger = Files.readString(PROJECT_DIRECTORY.resolve(
                "../docs/agent-state/COMPILER_INTEGRATION_CAPABILITIES.json").normalize());

        assertTrue(workflow.contains("xvfb-run -a"));
        assertTrue(workflow.contains("waveC11ResolvePinnedMods"));
        assertTrue(workflow.contains("runCompilerPlatformSeatPassengerClientWorldPrepareServer"));
        assertTrue(workflow.contains("runCompilerPlatformSeatPassengerClient"));
        assertTrue(workflow.contains("seatMountClientServerAgreement=true"));
        assertTrue(workflow.contains("seatDismountClientServerAgreement=true"));
        assertTrue(workflow.contains("seatEntityCleanupObserved=true"));

        int start = ledger.indexOf("\"CREATE_SEAT_PASSENGER_ON_SABLE_LIFECYCLE\"");
        assertTrue(start >= 0);
        String entry = ledger.substring(start);
        assertTrue(entry.contains("\"status\": \"accepted\""));
        assertTrue(entry.contains("\"workflow_run\": 35054474890"));
        assertTrue(entry.contains("\"job\": 104661898706"));
        assertTrue(entry.contains("\"commit\": \"17a80b21ae148e8457ee65f255e6b4a5bfd52746\""));
        assertTrue(entry.contains("MultiPlayerGameMode.useItemOn"));
        assertTrue(entry.contains("\"B\": true"));
        assertTrue(entry.contains("\"C\": false"));
    }

    private static String serverSource() throws IOException {
        return Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/"
                        + "SkyforgeSeatPassengerOnSableLifecycleAcceptance.java"));
    }

    private static String clientSource() throws IOException {
        return Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/"
                        + "SkyforgeSeatPassengerOnSableClientAcceptance.java"));
    }
}
