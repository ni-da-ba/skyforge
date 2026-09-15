package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class CreateKineticNetworkLifecycleResourceTest {
    private static final Path PROJECT_DIRECTORY =
            Path.of(System.getProperty("skyforge.test.projectDirectory", "."))
                    .toAbsolutePath()
                    .normalize();

    @Test
    void fixtureUsesExactRetainedCreateStackAndInspectedTopology() throws IOException {
        String build = Files.readString(PROJECT_DIRECTORY.resolve("build.gradle.kts"));
        String pins = Files.readString(PROJECT_DIRECTORY.resolve("wave-c1-mods.properties"));
        String source = fixtureSource();

        assertTrue(build.contains("compilerPlatformCreateKineticNetworkLifecycleServer"));
        assertTrue(build.contains("sourceSet.set(waveC11Runtime)"));
        assertTrue(build.contains("run-compiler-platform-create-kinetic-network-lifecycle"));
        assertTrue(pins.contains("minecraft.version=1.21.1"));
        assertTrue(pins.contains("neoforge.version=21.1.249"));
        assertTrue(pins.contains("create.coordinate=maven.modrinth:LNytGWDc:UjX6dr61"));
        assertTrue(source.contains("create:creative_motor"));
        assertTrue(source.contains("create:shaft"));
        assertTrue(source.contains("create:gearbox"));
        assertTrue(source.contains("CreativeMotorBlockEntity"));
        assertTrue(source.contains("GearboxBlockEntity"));
        assertTrue(source.contains("KineticBlockEntity"));
    }

    @Test
    void lifecycleObservesRealSpeedAndBuildDisconnectRebuild() throws IOException {
        String source = fixtureSource();

        assertTrue(source.contains("getSpeed"));
        assertTrue(source.contains("getTheoreticalSpeed"));
        assertTrue(source.contains("hasSource"));
        assertTrue(source.contains("hasNetwork"));
        assertTrue(source.contains("INITIAL_NETWORK"));
        assertTrue(source.contains("DISCONNECTED"));
        assertTrue(source.contains("shaftRemoved=true"));
        assertTrue(source.contains("shaftRestored=true"));
        assertTrue(source.contains("severedObserved=true rebuiltObserved=true"));
        assertFalse(source.contains("sable"));
        assertFalse(source.contains("SuperGlue"));
        assertFalse(source.contains("PhysicsAssembler"));
    }

    @Test
    void lifecycleAndOuterRunnerAreBoundedAndClassified() throws IOException {
        String source = fixtureSource();
        String failures = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeCompilerIntegrationFailure.java"));
        String runner = Files.readString(PROJECT_DIRECTORY.resolve(
                        "../scripts/compiler-integration/run-exact-stack-fixture.sh")
                .normalize());
        String workflow = Files.readString(PROJECT_DIRECTORY.resolve(
                        "../.github/workflows/compiler-platform-create-kinetic-network-lifecycle.yml")
                .normalize());

        assertTrue(source.contains("INIT_DEADLINE_TICKS"));
        assertTrue(source.contains("DISCONNECT_DEADLINE_TICKS"));
        assertTrue(source.contains("REBUILD_DEADLINE_TICKS"));
        assertTrue(source.contains("FAIL_BLOCK_ENTITY_INIT"));
        assertTrue(source.contains("FAIL_KINETIC_REBUILD"));
        assertTrue(failures.contains("TIMEOUT_KINETIC_BUILD"));
        assertTrue(failures.contains("TIMEOUT_KINETIC_DISCONNECT"));
        assertTrue(failures.contains("TIMEOUT_KINETIC_REBUILD"));
        assertTrue(runner.contains("TIMEOUT_SERVER_BOOT"));
        assertTrue(runner.contains("TIMEOUT_FIXTURE_TERMINAL_STATE"));
        assertTrue(runner.contains("FAIL_RUNTIME_EXIT"));
        assertTrue(workflow.contains("l1-contract"));
        assertTrue(workflow.contains("l2-exact-stack"));
        assertTrue(workflow.contains("waveC11ResolvePinnedMods"));
    }

    @Test
    void capabilityLedgerStartsFailClosedForAgentC() throws IOException {
        String ledger = Files.readString(PROJECT_DIRECTORY.resolve(
                        "../docs/agent-state/COMPILER_INTEGRATION_CAPABILITIES.json")
                .normalize());

        assertTrue(ledger.contains("CREATE_KINETIC_NETWORK_LIFECYCLE"));
        assertTrue(ledger.contains("qualification_pending"));
        assertTrue(ledger.contains("production_authority_for_agents"));
    }

    private static String fixtureSource() throws IOException {
        return Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/"
                        + "SkyforgeCreateKineticNetworkLifecycleAcceptance.java"));
    }
}
