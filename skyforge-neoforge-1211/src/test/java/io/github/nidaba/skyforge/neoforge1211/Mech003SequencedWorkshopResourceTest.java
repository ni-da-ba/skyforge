package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class Mech003SequencedWorkshopResourceTest {
    private static final Path PROJECT_DIRECTORY =
            Path.of(System.getProperty("skyforge.test.projectDirectory", "."))
                    .toAbsolutePath()
                    .normalize();

    @Test
    void semanticSpecIsTargetNeutralWhilePlanCarriesAcceptedBeltlessLowering() throws IOException {
        String spec = Files.readString(PROJECT_DIRECTORY.resolve(
                "../tools/asset_compiler/specimens/mech_003_portable_engine_workshop.json").normalize());
        String plan = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/resources/data/skyforge/mechanisms/mech_003_portable_engine_workshop.json"));

        assertFalse(spec.contains("create:"));
        assertFalse(spec.contains("simulated:"));
        assertTrue(spec.contains("\"manual_sequenced_processing\""));
        assertTrue(spec.contains("\"world_item_manual_handoff\""));
        assertTrue(plan.contains("\"requiredPlatformCapability\": \"CREATE_WORLD_ITEM_CUT_PRESS_LIFECYCLE\""));
        assertTrue(plan.contains("\"name\": \"create:mechanical_saw\""));
        assertTrue(plan.contains("\"name\": \"create:mechanical_press\""));
        assertTrue(plan.contains("\"mode\": \"world_item_restage\""));
        assertTrue(plan.contains("\"terminalResultPolicy\": \"live_weighted_result_pool\""));
        assertTrue(plan.contains("\"singleAttemptSuccessRequired\": false"));
        assertTrue(plan.contains("a34c3135f77a4d5ac1f84751b4bd625747599c96c4b2f8e390e0498a54e962c0"));
        assertFalse(plan.contains("\"name\": \"create:depot\""));
        assertFalse(plan.contains("\"name\": \"create:mechanical_arm\""));
        Path structure = PROJECT_DIRECTORY.resolve(
                "src/development/resources/data/skyforge/structure/mech_003_portable_engine_workshop.nbt");
        assertTrue(Files.exists(structure));
        assertTrue(Files.size(structure) > 0L);
    }

    @Test
    void runtimeConsumesCompilerArtifactAndProvesLiveEightLoopWeightedRecipe() throws IOException {
        String source = fixtureSource();

        assertTrue(source.contains("/data/skyforge/mechanisms/mech_003_portable_engine_workshop.json"));
        assertTrue(source.contains("skyforge:mech_003_portable_engine_workshop"));
        assertTrue(source.contains("place template "));
        assertTrue(source.contains("simulated:sequenced_assembly/engine_assembly"));
        assertTrue(source.contains("create:iron_sheet"));
        assertTrue(source.contains("simulated:incomplete_engine_assembly"));
        assertTrue(source.contains("EXPECTED_LOOPS = 8"));
        assertTrue(source.contains("EXPECTED_TOTAL_STEPS"));
        assertTrue(source.contains("getOutputChance"));
        assertTrue(source.contains("resultPool"));
        assertTrue(source.contains("engineAssemblyChance="));
        assertTrue(source.contains("singleAttemptEngineSuccessRequired=false"));
        assertTrue(source.contains("PRESS_RETRACTION"));
        assertTrue(source.contains("PRESS_RETRACTED"));
        assertTrue(source.contains("PRESS_RETRACTION_DEADLINE_TICKS"));
        assertTrue(source.contains("resultBelongsToLivePool=true"));
        assertTrue(source.contains("CUT_PASS step="));
        assertTrue(source.contains("PRESS_PASS step="));
        assertTrue(source.contains("world_item_restage"));
        assertTrue(source.contains("requireNoForbiddenTransport"));
        assertTrue(source.contains("requireClearance"));
        assertFalse(source.contains("Sable"));
        assertFalse(source.contains("PhysicsAssembler"));
    }

    @Test
    void workflowIsBoundedAndConsumesPlatform015Authority() throws IOException {
        String workflow = Files.readString(PROJECT_DIRECTORY.resolve(
                "../.github/workflows/mech-003-sequenced-workshop.yml").normalize());
        String ledger = Files.readString(PROJECT_DIRECTORY.resolve(
                "../docs/agent-state/COMPILER_INTEGRATION_CAPABILITIES.json").normalize());
        String build = Files.readString(PROJECT_DIRECTORY.resolve("build.gradle.kts"));

        assertTrue(workflow.contains("l1-static-contract"));
        assertTrue(workflow.contains("l3-exact-stack"));
        assertTrue(workflow.contains("runMech003SequencedWorkshopServer"));
        assertTrue(workflow.contains("MECH_003_SEQUENCED_WORKSHOP PASS"));
        assertTrue(workflow.contains("cmp build/mech-003/resolved.json"));
        assertTrue(workflow.contains("cmp build/mech-003/mech_003_portable_engine_workshop.nbt"));
        assertTrue(build.contains("mech003SequencedWorkshopServer"));
        assertTrue(build.contains("mech003SequencedWorkshopClient"));
        assertTrue(build.contains("sourceSet.set(waveC11Runtime)"));
        assertTrue(ledger.contains("\"CREATE_WORLD_ITEM_CUT_PRESS_LIFECYCLE\""));
        assertTrue(ledger.contains("\"C\": true"));
    }

    private static String fixtureSource() throws IOException {
        return Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/"
                        + "SkyforgeMech003SequencedWorkshopAcceptance.java"));
    }
}
