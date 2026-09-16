package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class PlayerTrackingOnSableLifecycleResourceTest {
    private static final Path PROJECT_DIRECTORY =
            Path.of(System.getProperty("skyforge.test.projectDirectory", "."))
                    .toAbsolutePath()
                    .normalize();

    @Test
    void fixtureReusesRealSeatSetupAndPinsExactSableTrackingSource() throws IOException {
        String build = Files.readString(PROJECT_DIRECTORY.resolve("build.gradle.kts"));
        String pins = Files.readString(PROJECT_DIRECTORY.resolve("wave-c1-mods.properties"));
        String server = serverSource();

        assertTrue(build.contains("compilerPlatformPlayerTrackingClientWorldPrepareServer"));
        assertTrue(build.contains("compilerPlatformPlayerTrackingClient"));
        assertTrue(build.contains("sourceSet.set(waveC11Runtime)"));
        assertTrue(pins.contains("minecraft.version=1.21.1"));
        assertTrue(pins.contains("neoforge.version=21.1.249"));
        assertTrue(server.contains("create:brown_seat"));
        assertTrue(server.contains("simulated:physics_assembler"));
        assertTrue(server.contains("6966d2928340de7631abcecf8549904b877df0a8"));
        assertTrue(server.contains("getTrackingSubLevel"));
        assertTrue(server.contains("client_sublevel_collision_then_movement_packet"));
        assertTrue(server.contains("requireSynchronousSimulatedTraversal();"));
        assertTrue(server.contains("SimAssemblyHelper.assembleFromSingleBlock(..., includeStart=true, includeEncasingGlue=true)"));
        assertTrue(server.contains("getMethod(\"searchMovedStructure\", Level.class, BlockPos.class)"));
        assertTrue(server.contains("getBlocks"));
        assertTrue(server.contains("getGlues"));
        assertTrue(server.contains("SIMULATED_TRAVERSAL_READY glueId="));
        assertTrue(server.contains("synchronousPreAssemblyProbe=true"));
        assertTrue(server.contains("FIXTURE_CHUNK_TICKET_DISTANCE = 3"));
        assertTrue(server.contains("skyforge_platform_012"));
        assertTrue(server.indexOf("addFixtureChunkTicket();") < server.indexOf("beforeIds = currentSubLevelIds();"));
    }

    @Test
    void trackingAndMeasurementUseNaturalInputWithoutHarnessTrackingOrPlayerMutation() throws IOException {
        String client = clientSource();
        String server = serverSource();
        String measurement = client.substring(client.indexOf("private static void awaitNaturalTracking"),
                client.indexOf("private static void complete"));

        assertTrue(client.contains("minecraft.options.keyJump.setDown(true)"));
        assertTrue(client.contains("minecraft.gameMode.useItemOn"));
        assertTrue(client.contains("minecraft.options.keyShift.setDown(true)"));
        assertTrue(measurement.contains("clientTrackingSubLevel"));
        assertTrue(measurement.contains("submitClientTranslationBaseline"));
        assertTrue(measurement.contains("submitClientTranslationResult"));
        assertFalse(measurement.contains("setPos("));
        assertFalse(measurement.contains("teleportTo("));
        assertFalse(measurement.contains("setDeltaMovement("));
        assertFalse(client.contains("sable$setTrackingSubLevel("));
        assertFalse(server.contains("sable$setTrackingSubLevel("));
        assertFalse(client.contains("startRiding("));
        assertFalse(client.contains("stopRiding("));
    }

    @Test
    void serverRequiresPersistentTrackingAndBoundedInheritedTranslation() throws IOException {
        String server = serverSource();

        assertTrue(server.contains("TIMEOUT_TRACKING"));
        assertTrue(server.contains("TIMEOUT_PARENT_TRANSLATION"));
        assertTrue(server.contains("FAIL_PASSENGER_TRACKING"));
        assertTrue(server.contains("TRANSLATION_VELOCITY_METERS_PER_SECOND = 2.0"));
        assertTrue(server.contains("MINIMUM_PARENT_TRANSLATION_BLOCKS = 0.15"));
        assertTrue(server.contains("PLAYER_DELTA_TOLERANCE_BLOCKS = 0.08"));
        assertTrue(server.contains("bodyId.equals(clientTrackingId)"));
        assertTrue(server.contains("bodyId.equals(serverTrackingId)"));
        assertTrue(server.contains("playerSableTrackingQualified=true inheritedParentTranslationQualified=true"));
        assertTrue(server.contains("fixtureLivenessTicket=sable:command_forced(released)"));
        assertTrue(server.contains("fixtureChunkTicket=skyforge_platform_012(released)"));
        assertTrue(server.contains("primary seat assembly source cleanup did not settle before deadline"));
        assertTrue(server.contains("if (sourceNonAir != 0)"));
        assertFalse(server.contains("valid synchronous post-assembly state"));
    }

    @Test
    void workflowRunsActualClientAndCapabilityRemainsUnpublishedBeforeL2Acceptance() throws IOException {
        String workflow = Files.readString(PROJECT_DIRECTORY.resolve(
                "../.github/workflows/compiler-platform-player-tracking-on-sable.yml").normalize());
        String ledger = Files.readString(PROJECT_DIRECTORY.resolve(
                "../docs/agent-state/COMPILER_INTEGRATION_CAPABILITIES.json").normalize());

        assertTrue(workflow.contains("xvfb-run -a"));
        assertTrue(workflow.contains("waveC11ResolvePinnedMods"));
        assertTrue(workflow.contains("runCompilerPlatformPlayerTrackingClientWorldPrepareServer"));
        assertTrue(workflow.contains("runCompilerPlatformPlayerTrackingClient"));
        assertTrue(workflow.contains("naturalTrackingAcquired=true"));
        assertTrue(workflow.contains("harnessTrackingSetterInvoked=false"));
        assertTrue(workflow.contains("harnessPlayerMutationDuringMeasurement=false"));
        assertTrue(workflow.contains("inheritedParentTranslationQualified=true"));
        assertFalse(ledger.contains("\"PLAYER_TRACKING_ON_SABLE_LIFECYCLE\""));
    }

    private static String serverSource() throws IOException {
        return Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/"
                        + "SkyforgePlayerTrackingOnSableLifecycleAcceptance.java"));
    }

    private static String clientSource() throws IOException {
        return Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/"
                        + "SkyforgePlayerTrackingOnSableClientAcceptance.java"));
    }
}
