package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class SuperGlueAssemblyDomainResourceTest {
    private static final Path PROJECT_DIRECTORY =
            Path.of(System.getProperty("skyforge.test.projectDirectory", "."))
                    .toAbsolutePath()
                    .normalize();

    @Test
    void fixtureUsesExactStackAndOneCausalGlueDomain() throws IOException {
        String build = Files.readString(PROJECT_DIRECTORY.resolve("build.gradle.kts"));
        String pins = Files.readString(PROJECT_DIRECTORY.resolve("wave-c1-mods.properties"));
        String source = fixtureSource();

        assertTrue(build.contains("compilerPlatformSuperGlueAssemblyDomainServer"));
        assertTrue(build.contains("sourceSet.set(waveC11Runtime)"));
        assertTrue(pins.contains("minecraft.version=1.21.1"));
        assertTrue(pins.contains("neoforge.version=21.1.249"));
        assertTrue(source.contains("ResourceLocation.fromNamespaceAndPath(\"simulated\", \"physics_assembler\")"));
        assertTrue(source.contains("Blocks.SLIME_BLOCK"));
        assertTrue(source.contains("Blocks.HONEY_BLOCK"));
        assertTrue(source.contains("canStickTo"));
        assertTrue(source.contains("SuperGlueEntity"));
        assertTrue(source.contains("exactlyOneIntentionalGlueDomain=true"));
        assertFalse(source.contains("creative_motor"));
        assertFalse(source.contains("propeller_bearing"));
        assertFalse(source.contains("steering_wheel"));
    }

    @Test
    void positiveAndNegativeAssemblyEffectsAreBothRequired() throws IOException {
        String source = fixtureSource();

        assertTrue(source.contains("\"glued\""));
        assertTrue(source.contains("\"unglued-control\""));
        assertTrue(source.contains("secondaryParentRemaining"));
        assertTrue(source.contains("secondaryTransferred"));
        assertTrue(source.contains("requireMovedGlue"));
        assertTrue(source.contains("sourceGlueCount"));
        assertTrue(source.contains("movedGlueDomains"));
        assertTrue(source.contains("containedFixtureCells != 2"));
        assertTrue(source.contains("slimeHoneyOrdinaryAdhesion=false"));
        assertTrue(source.contains("assembleOrDisassemble"));
    }

    @Test
    void failuresAndOuterWaitsRemainClassifiedAndBounded() throws IOException {
        String failures = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeCompilerIntegrationFailure.java"));
        String runner = Files.readString(PROJECT_DIRECTORY.resolve(
                        "../scripts/compiler-integration/run-exact-stack-fixture.sh").normalize());
        String workflow = Files.readString(PROJECT_DIRECTORY.resolve(
                        "../.github/workflows/compiler-platform-super-glue-assembly-domain.yml").normalize());

        assertTrue(failures.contains("FAIL_GLUE_REGISTRATION"));
        assertTrue(failures.contains("TIMEOUT_GLUE_REGISTRATION"));
        assertTrue(runner.contains("TIMEOUT_SERVER_BOOT"));
        assertTrue(runner.contains("TIMEOUT_FIXTURE_TERMINAL_STATE"));
        assertTrue(workflow.contains("l1-contract"));
        assertTrue(workflow.contains("l2-exact-stack"));
        assertTrue(workflow.contains("waveC11ResolvePinnedMods"));
    }

    @Test
    void ledgerStartsFailClosedPendingExactStackAcceptance() throws IOException {
        String ledger = Files.readString(PROJECT_DIRECTORY.resolve(
                        "../docs/agent-state/COMPILER_INTEGRATION_CAPABILITIES.json").normalize());
        int start = ledger.indexOf("\"SUPER_GLUE_ASSEMBLY_DOMAIN_LIFECYCLE\"");
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
                        + "SkyforgeSuperGlueAssemblyDomainAcceptance.java"));
    }
}
