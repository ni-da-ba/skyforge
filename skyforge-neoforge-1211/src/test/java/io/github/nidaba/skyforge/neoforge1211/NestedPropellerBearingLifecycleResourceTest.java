package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class NestedPropellerBearingLifecycleResourceTest {
    private static final Path PROJECT_DIRECTORY =
            Path.of(System.getProperty("skyforge.test.projectDirectory", "."))
                    .toAbsolutePath()
                    .normalize();

    @Test
    void fixtureComposesAcceptedPrimaryKineticAndGlueSeams() throws IOException {
        String build = Files.readString(PROJECT_DIRECTORY.resolve("build.gradle.kts"));
        String pins = Files.readString(PROJECT_DIRECTORY.resolve("wave-c1-mods.properties"));
        String source = fixtureSource();

        assertTrue(build.contains("compilerPlatformNestedPropellerBearingLifecycleServer"));
        assertTrue(build.contains("sourceSet.set(waveC11Runtime)"));
        assertTrue(pins.contains("minecraft.version=1.21.1"));
        assertTrue(pins.contains("neoforge.version=21.1.249"));
        assertTrue(source.contains("aeronautics:propeller_bearing"));
        assertTrue(source.contains("simulated:white_symmetric_sail"));
        assertTrue(source.contains("create:creative_motor"));
        assertTrue(source.contains("create:shaft"));
        assertTrue(source.contains("simulated:physics_assembler"));
        assertTrue(source.contains("SuperGlueEntity"));
    }

    @Test
    void groundFlattenAndFreshNestedChildAreAllRequired() throws IOException {
        String source = fixtureSource();

        assertTrue(source.contains("getMovedContraption"));
        assertTrue(source.contains("getSailBlocks"));
        assertTrue(source.contains("groundChildId"));
        assertTrue(source.contains("nestedChildId"));
        assertTrue(source.contains("staleGroundChildRetained=false"));
        assertTrue(source.contains("childTeardownRestored=true"));
        assertTrue(source.contains("requireSourceChildRemoved"));
        assertTrue(source.contains("requireMovedChildRemoved"));
        assertTrue(source.contains("canonicalBodyResolutionPerPhase=true"));
        assertTrue(source.contains("blockEntityResolutionPerPoll=true"));
        assertTrue(source.contains("getUniqueId"));
        assertFalse(source.contains("publicMethod(container, \"getSubLevel\""));
    }

    @Test
    void controllerBoundaryAndTimeoutsRemainBounded() throws IOException {
        String source = fixtureSource();
        String failures = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeCompilerIntegrationFailure.java"));
        String workflow = Files.readString(PROJECT_DIRECTORY.resolve(
                        "../.github/workflows/compiler-platform-nested-propeller-bearing-lifecycle.yml").normalize());

        assertTrue(source.contains("requireGlueBoundary"));
        assertTrue(source.contains("fixture glue crosses Propeller Bearing controller boundary"));
        assertTrue(source.contains("FAIL_CHILD_ASSEMBLY"));
        assertTrue(source.contains("TIMEOUT_CHILD_ASSEMBLY"));
        assertTrue(failures.contains("TIMEOUT_CHILD_ASSEMBLY"));
        assertTrue(workflow.contains("l1-contract"));
        assertTrue(workflow.contains("l2-exact-stack"));
        assertTrue(workflow.contains("waveC11ResolvePinnedMods"));
        assertFalse(source.contains("steering_wheel"));
        assertFalse(source.contains("swivel_bearing"));
        assertFalse(source.contains("seat"));
        assertFalse(source.contains("PERSISTENCE_RELOAD"));
    }

    @Test
    void ledgerStartsFailClosedPendingExactStackAcceptance() throws IOException {
        String ledger = Files.readString(PROJECT_DIRECTORY.resolve(
                        "../docs/agent-state/COMPILER_INTEGRATION_CAPABILITIES.json").normalize());
        int start = ledger.indexOf("\"NESTED_PROPELLER_BEARING_LIFECYCLE\"");
        assertTrue(start >= 0);
        String entry = ledger.substring(start);

        assertTrue(entry.contains("\"status\": \"qualification_pending\""));
        assertTrue(entry.contains("\"latest_accepted_evidence\": null"));
        assertTrue(entry.contains("\"B\": false"));
        assertTrue(entry.contains("\"C\": false"));
    }

    private static String fixtureSource() throws IOException {
        return Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/"
                        + "SkyforgeNestedPropellerBearingLifecycleAcceptance.java"));
    }
}
