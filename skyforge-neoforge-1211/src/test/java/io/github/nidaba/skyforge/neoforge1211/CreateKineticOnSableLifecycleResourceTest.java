package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class CreateKineticOnSableLifecycleResourceTest {
    private static final Path PROJECT_DIRECTORY =
            Path.of(System.getProperty("skyforge.test.projectDirectory", "."))
                    .toAbsolutePath()
                    .normalize();

    @Test
    void fixtureComposesAcceptedSableAndCreateSeamsOnExactStack() throws IOException {
        String build = Files.readString(PROJECT_DIRECTORY.resolve("build.gradle.kts"));
        String pins = Files.readString(PROJECT_DIRECTORY.resolve("wave-c1-mods.properties"));
        String source = fixtureSource();

        assertTrue(build.contains("compilerPlatformCreateKineticOnSableLifecycleServer"));
        assertTrue(build.contains("sourceSet.set(waveC11Runtime)"));
        assertTrue(pins.contains("minecraft.version=1.21.1"));
        assertTrue(pins.contains("neoforge.version=21.1.249"));
        assertTrue(source.contains("simulated:physics_assembler"));
        assertTrue(source.contains("create:creative_motor"));
        assertTrue(source.contains("create:shaft"));
        assertTrue(source.contains("create:gearbox"));
        assertTrue(source.contains("assembleOrDisassemble"));
        assertTrue(source.contains("SuperGlueEntity"));
        assertFalse(source.contains("propeller_bearing"));
        assertFalse(source.contains("steering_wheel"));
    }

    @Test
    void persistentUuidAndCanonicalPlotOwnAllMovingKineticObservations() throws IOException {
        String source = fixtureSource();

        assertTrue(source.contains("getUniqueId"));
        assertTrue(source.contains("findCanonicalBody(bodyId)"));
        assertTrue(source.contains("requireCanonicalPlotOwnership"));
        assertTrue(source.contains("getPlot"));
        assertTrue(source.contains("movedOffset(listedBody, centerOfMass)"));
        assertTrue(source.contains("requireExpectedMovedBlockEntity"));
        assertTrue(source.contains("blockEntityResolutionPerPoll=true"));
        assertTrue(source.contains("currentPhysicsHandleValid=true"));
        assertTrue(source.contains("COMMAND_FORCED"));
        assertFalse(source.contains("publicMethod(container, \"getSubLevel\""));
    }

    @Test
    void movingNetworkBuildDisconnectAndRebuildAreBoundedAndClassified() throws IOException {
        String source = fixtureSource();
        String runner = Files.readString(PROJECT_DIRECTORY.resolve(
                        "../scripts/compiler-integration/run-exact-stack-fixture.sh").normalize());
        String workflow = Files.readString(PROJECT_DIRECTORY.resolve(
                        "../.github/workflows/compiler-platform-create-kinetic-on-sable-lifecycle.yml").normalize());

        assertTrue(source.contains("TIMEOUT_KINETIC_BUILD"));
        assertTrue(source.contains("TIMEOUT_KINETIC_DISCONNECT"));
        assertTrue(source.contains("TIMEOUT_KINETIC_REBUILD"));
        assertTrue(source.contains("FAIL_BLOCK_ENTITY_INIT"));
        assertTrue(source.contains("FAIL_KINETIC_REBUILD"));
        assertTrue(source.contains("movedShaftRemoved=true"));
        assertTrue(source.contains("movedShaftRestored=true"));
        assertTrue(runner.contains("TIMEOUT_SERVER_BOOT"));
        assertTrue(runner.contains("TIMEOUT_FIXTURE_TERMINAL_STATE"));
        assertTrue(workflow.contains("l1-contract"));
        assertTrue(workflow.contains("l2-exact-stack"));
        assertTrue(workflow.contains("waveC11ResolvePinnedMods"));
    }

    @Test
    void ledgerStartsFailClosedUntilExactStackAcceptance() throws IOException {
        String ledger = Files.readString(PROJECT_DIRECTORY.resolve(
                        "../docs/agent-state/COMPILER_INTEGRATION_CAPABILITIES.json").normalize());

        assertTrue(ledger.contains("CREATE_KINETIC_ON_SABLE_LIFECYCLE"));
        assertTrue(ledger.contains("\"status\": \"qualification_pending\""));
        assertTrue(ledger.contains("production_authority_for_agents"));
    }

    private static String fixtureSource() throws IOException {
        return Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/"
                        + "SkyforgeCreateKineticOnSableLifecycleAcceptance.java"));
    }
}
