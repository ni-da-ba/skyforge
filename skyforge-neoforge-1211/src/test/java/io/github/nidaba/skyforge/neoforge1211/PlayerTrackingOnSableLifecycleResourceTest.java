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
        assertTrue(server.contains("6966d2928340de7631abcecf8549904b877df0a8"));
        assertTrue(server.contains("dev.ryanhcode.sable.api.SubLevelAssemblyHelper"));
        assertTrue(server.contains("assembleBlocks"));
        assertTrue(server.contains("qualificationSetup=sable:SubLevelAssemblyHelper.assembleBlocks"));
        assertTrue(server.contains("EXPLICIT_FIXTURE_CELLS = 9"));
        assertFalse(server.contains("simulated:physics_assembler"));
        assertFalse(server.contains("SuperGlueEntity"));
        assertFalse(server.contains("SimAssemblyHelper"));
        assertTrue(server.contains("getTrackingSubLevel"));
        String trackingMixinClass = "Class.forName(\"dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension\")";
        int trackingMixinResolution = server.indexOf(trackingMixinClass);
        assertTrue(trackingMixinResolution > server.indexOf("private static UUID trackingSubLevelId"));
        assertTrue(trackingMixinResolution == server.lastIndexOf(trackingMixinClass));
        assertTrue(server.contains("client_sublevel_collision_then_movement_packet"));
        assertFalse(server.contains("addRegionTicket("));
        assertFalse(server.contains("skyforge_platform_012"));
        assertTrue(server.contains("level.getChunk(0, 0);"));
    }

    @Test
    void trackingAndMeasurementUseNaturalInputWithoutHarnessTrackingOrPlayerMutation() throws IOException {
        String client = clientSource();
        String server = serverSource();
        String bridge = bridgeSource();
        String measurement = client.substring(client.indexOf("private static void awaitNaturalTracking"),
                client.indexOf("private static void complete"));

        assertTrue(client.contains("minecraft.options.keyJump.setDown(true)"));
        assertTrue(client.contains("minecraft.gameMode.useItemOn"));
        assertTrue(client.contains("minecraft.options.keyShift.setDown(true)"));
        assertTrue(client.contains("publishMountDiagnostic"));
        assertTrue(client.contains("gate=client-tick-entry"));
        assertTrue(client.contains("gate=client-top-readiness"));
        assertTrue(client.contains("gate=server-player-positioned"));
        assertTrue(client.contains("gate=sublevel-ready"));
        assertTrue(client.contains("gate=seat-block"));
        assertTrue(client.contains("gate=pose-convergence"));
        assertTrue(client.contains("gate=seat-use-attempt"));
        assertTrue(server.contains("clientMountDiagnostic={"));
        assertTrue(bridge.contains("mountDiagnostic"));
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
        assertTrue(server.contains("explicit Sable qualification assembly did not synchronously transfer all fixture cells"));
        assertTrue(server.contains("sourceNonAir != 0"));
        assertTrue(server.contains("explicitFixtureCells="));
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

    private static String bridgeSource() throws IOException {
        return Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/"
                        + "SkyforgePlayerTrackingOnSableBridge.java"));
    }

    private static String clientSource() throws IOException {
        return Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/"
                        + "SkyforgePlayerTrackingOnSableClientAcceptance.java"));
    }
}
