package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class SwivelControlChildLifecycleResourceTest {
    private static final Path PROJECT_DIRECTORY = Path.of(System.getProperty("skyforge.test.projectDirectory", "."))
            .toAbsolutePath().normalize();

    @Test
    void fixtureUsesExactStackAndRealSwivelKinetics() throws IOException {
        String build = Files.readString(PROJECT_DIRECTORY.resolve("build.gradle.kts"));
        String source = fixtureSource();
        assertTrue(build.contains("compilerPlatformSwivelControlChildLifecycleServer"));
        assertTrue(build.contains("sourceSet.set(waveC11Runtime)"));
        assertTrue(source.contains("simulated:swivel_bearing"));
        assertTrue(source.contains("simulated:white_symmetric_sail"));
        assertTrue(source.contains("create:cogwheel"));
        assertTrue(source.contains("create:creative_motor"));
        assertTrue(source.contains("getExtraKinetics"));
        assertTrue(source.contains("generatedSpeed"));
        assertTrue(source.contains("setValue"));
    }

    @Test
    void childAndCommandLifecycleAreBoundedAndReadOnlyAtTarget() throws IOException {
        String source = fixtureSource();
        assertTrue(source.contains("sable$getConnectionDependencies"));
        assertTrue(source.contains("removePrimaryAssemblyGlue"));
        assertTrue(source.contains("PRIMARY_ASSEMBLY_GLUE_RELEASED"));
        assertTrue(source.contains("signedDeflectionObserved=true"));
        assertTrue(source.contains("lockedHoldObserved=true"));
        assertTrue(source.contains("inverseNeutralReturnObserved=true"));
        assertTrue(source.contains("childResolutionPerPhase=true"));
        assertTrue(source.contains("directTargetMutation=false"));
        assertTrue(source.contains("getTargetAngleDegrees"));
        assertFalse(source.contains("setTargetAngle"));
        assertFalse(source.contains("targetAngleDegrees ="));
        assertFalse(source.contains("steering_wheel"));
        assertFalse(source.contains("PropellerBearing"));
        assertFalse(source.contains("PERSISTENCE_RELOAD"));
    }

    @Test
    void workflowAndLedgerRemainFailClosedUntilL2Acceptance() throws IOException {
        String workflow = Files.readString(PROJECT_DIRECTORY.resolve(
                "../.github/workflows/compiler-platform-swivel-control-child-lifecycle.yml").normalize());
        String ledger = Files.readString(PROJECT_DIRECTORY.resolve(
                "../docs/agent-state/COMPILER_INTEGRATION_CAPABILITIES.json").normalize());
        assertTrue(workflow.contains("l1-contract"));
        assertTrue(workflow.contains("l2-exact-stack"));
        assertTrue(workflow.contains("waveC11ResolvePinnedMods"));
        assertTrue(workflow.contains("COMPILER_PLATFORM_SWIVEL_CONTROL_CHILD_ON_SABLE_LIFECYCLE PASS"));
        int start = ledger.indexOf("\"SWIVEL_CONTROL_CHILD_ON_SABLE_LIFECYCLE\"");
        assertTrue(start >= 0);
        String entry = ledger.substring(start);
        assertTrue(entry.contains("\"status\": \"qualification_pending\""));
        assertTrue(entry.contains("\"B\": false"));
        assertTrue(entry.contains("\"C\": false"));
    }

    private static String fixtureSource() throws IOException {
        return Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeSwivelControlChildLifecycleAcceptance.java"));
    }
}
