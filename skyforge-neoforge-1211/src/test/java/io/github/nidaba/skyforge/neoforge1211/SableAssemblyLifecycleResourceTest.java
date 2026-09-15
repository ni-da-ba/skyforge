package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class SableAssemblyLifecycleResourceTest {
    private static final Path PROJECT_DIRECTORY =
            Path.of(System.getProperty("skyforge.test.projectDirectory", "."))
                    .toAbsolutePath()
                    .normalize();

    @Test
    void fixtureUsesExactAcceptedStackAndRealPhysicsAssembler() throws IOException {
        String build = Files.readString(PROJECT_DIRECTORY.resolve("build.gradle.kts"));
        String pins = Files.readString(PROJECT_DIRECTORY.resolve("wave-c1-mods.properties"));
        String source = fixtureSource();

        assertTrue(build.contains("compilerPlatformSableAssemblyLifecycleServer"));
        assertTrue(build.contains("sourceSet.set(waveC11Runtime)"));
        assertTrue(build.contains("run-compiler-platform-sable-assembly-lifecycle"));
        assertTrue(pins.contains("minecraft.version=1.21.1"));
        assertTrue(pins.contains("neoforge.version=21.1.249"));
        assertTrue(pins.contains("create.coordinate=maven.modrinth:LNytGWDc:UjX6dr61"));
        assertTrue(pins.contains("sable.coordinate=maven.modrinth:T9PomCSv:U678xqle"));
        assertTrue(pins.contains("aeronautics.coordinate=maven.modrinth:oWaK0Q19:44pLdPGg"));
        assertTrue(source.contains("simulated\", \"physics_assembler"));
        assertTrue(source.contains("assembleOrDisassemble"));
        assertTrue(source.contains("PhysicsAssemblerBlockEntity"));
        assertTrue(source.contains("SuperGlueEntity"));
        assertTrue(source.contains("GLUE_MIN"));
        assertTrue(source.contains("GLUE_MAX"));
        assertFalse(source.contains("assembleFromSingleBlock"));
    }

    @Test
    void fixtureTreatsPersistentUuidAndCanonicalLiveBodyAsAuthority() throws IOException {
        String source = fixtureSource();

        assertTrue(source.contains("getUniqueId"));
        assertTrue(source.contains("getAllSubLevels"));
        assertTrue(source.contains("findCanonicalBody(bodyId)"));
        assertFalse(source.contains("publicMethod(container, \"getSubLevel\""));
        assertTrue(source.contains("getPhysicsHandle"));
        assertTrue(source.contains("isValid"));
        assertTrue(source.contains("requireCanonicalBody(bodyId)"));
        assertTrue(source.contains("COMMAND_FORCED"));
        assertTrue(source.contains("removeFixtureForceLoadTicket"));
        assertTrue(source.contains("canonicalResolutionPerTick=true"));
        assertTrue(source.contains("staleHandleRetained=false"));
        assertFalse(source.contains("SkyforgeAircraftCompiler"));
        assertFalse(source.contains("propeller_bearing"));
    }

    @Test
    void lifecycleAndOuterRunnerAreBoundedAndClassified() throws IOException {
        String source = fixtureSource();
        String runner = Files.readString(PROJECT_DIRECTORY.resolve(
                        "../scripts/compiler-integration/run-exact-stack-fixture.sh")
                .normalize());
        String workflow = Files.readString(PROJECT_DIRECTORY.resolve(
                        "../.github/workflows/compiler-platform-sable-assembly-lifecycle.yml")
                .normalize());

        assertTrue(source.contains("TIMEOUT_ASSEMBLY_REGISTRATION"));
        assertTrue(source.contains("TIMEOUT_PHYSICS_INITIALIZATION"));
        assertTrue(source.contains("TIMEOUT_PHYSICS_PROGRESSION"));
        assertTrue(source.contains("assemblyRegistrationObservedSynchronously"));
        assertTrue(source.contains("sourceNonAirAfterAssembly"));
        assertTrue(runner.contains("TIMEOUT_SERVER_BOOT"));
        assertTrue(runner.contains("TIMEOUT_FIXTURE_TERMINAL_STATE"));
        assertTrue(runner.contains("FAIL_RUNTIME_EXIT"));
        assertTrue(workflow.contains("l1-contract"));
        assertTrue(workflow.contains("l2-exact-stack"));
        assertTrue(workflow.contains("waveC11ResolvePinnedMods"));
    }

    @Test
    void capabilityLedgerIsMachineReadableConsumerAuthority() throws IOException {
        String ledger = Files.readString(PROJECT_DIRECTORY.resolve(
                        "../docs/agent-state/COMPILER_INTEGRATION_CAPABILITIES.json")
                .normalize());

        assertTrue(ledger.contains("skyforge.compiler-integration-capabilities.v1"));
        assertTrue(ledger.contains("SABLE_PRIMARY_ASSEMBLY_LIFECYCLE"));
        assertTrue(ledger.contains("C11_FLIGHT_EXACT_2026-09-05"));
        assertTrue(ledger.contains("latest_accepted_evidence"));
        assertTrue(ledger.contains("production_authority_for_agents"));
    }

    private static String fixtureSource() throws IOException {
        return Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/"
                        + "SkyforgeSableAssemblyLifecycleAcceptance.java"));
    }
}
