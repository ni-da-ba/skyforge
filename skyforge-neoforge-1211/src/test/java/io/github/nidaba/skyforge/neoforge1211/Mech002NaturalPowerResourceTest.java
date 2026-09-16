package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class Mech002NaturalPowerResourceTest {
    private static final Path PROJECT_DIRECTORY =
            Path.of(System.getProperty("skyforge.test.projectDirectory", "."))
                    .toAbsolutePath()
                    .normalize();

    @Test
    void semanticSpecIsTargetNeutralWhilePlanCarriesAcceptedEnvironmentalLowering() throws IOException {
        String spec = Files.readString(PROJECT_DIRECTORY.resolve(
                "../tools/asset_compiler/specimens/mech_002_waterwheel_airflow_bench.json").normalize());
        String plan = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/resources/data/skyforge/mechanisms/mech_002_waterwheel_airflow_bench.json"));

        assertFalse(spec.contains("create:"));
        assertFalse(spec.contains("minecraft:water"));
        assertTrue(spec.contains("\"renewable_stationary_source\""));
        assertTrue(spec.contains("\"bounded_falling_water\""));
        assertTrue(plan.contains("\"requiredPlatformCapability\": \"CREATE_WATER_WHEEL_SOURCE_LIFECYCLE\""));
        assertTrue(plan.contains("\"sourcePolicy\": \"qualified_environmental_source_candidate_not_geography_canon\""));
        assertTrue(plan.contains("\"name\": \"create:water_wheel\""));
        assertTrue(plan.contains("\"name\": \"minecraft:water\""));
        assertTrue(plan.contains("\"level\": \"8\""));
        assertTrue(plan.contains("\"name\": \"create:shaft\""));
        assertTrue(plan.contains("\"name\": \"create:encased_fan\""));
        assertTrue(plan.contains("5e78c14a2632d75255e18c06da261b8365a9ffe5623c0c371b7b54184cdc697d"));
        Path structure = PROJECT_DIRECTORY.resolve(
                "src/development/resources/data/skyforge/structure/mech_002_waterwheel_airflow_bench.nbt");
        assertTrue(Files.exists(structure));
        assertTrue(Files.size(structure) > 0L);
    }

    @Test
    void runtimeConsumesCompilerArtifactsAndUsesEnvironmentalCellAsOnlyControl() throws IOException {
        String source = fixtureSource();

        assertTrue(source.contains("/data/skyforge/mechanisms/mech_002_waterwheel_airflow_bench.json"));
        assertTrue(source.contains("WaterWheelBlockEntity"));
        assertTrue(source.contains("EncasedFanBlockEntity"));
        assertTrue(source.contains("KineticBlockEntity"));
        assertTrue(source.contains("getSpeed"));
        assertTrue(source.contains("hasSource"));
        assertTrue(source.contains("hasNetwork"));
        assertTrue(source.contains("flowVector"));
        assertTrue(source.contains("environmentalControlRemoved=true"));
        assertTrue(source.contains("environmentalControlRestored=true"));
        assertTrue(source.contains("level.setBlock(flowPos, Blocks.AIR.defaultBlockState(), 3)"));
        assertTrue(source.contains("level.setBlock(flowPos, flowRestoreState, 3)"));
        assertTrue(source.contains("place template "));
        assertTrue(source.contains("skyforge:mech_002_waterwheel_airflow_bench"));
        assertTrue(source.contains("structure-template placement mismatch"));
        assertTrue(source.contains("requireClearance"));
        assertFalse(source.contains("Sable"));
        assertFalse(source.contains("PhysicsAssembler"));
    }

    @Test
    void workflowIsBoundedAndConsumesPlatform009Authority() throws IOException {
        String source = fixtureSource();
        String workflow = Files.readString(PROJECT_DIRECTORY.resolve(
                "../.github/workflows/mech-002-natural-workshop-power.yml").normalize());
        String ledger = Files.readString(PROJECT_DIRECTORY.resolve(
                "../docs/agent-state/COMPILER_INTEGRATION_CAPABILITIES.json").normalize());
        String build = Files.readString(PROJECT_DIRECTORY.resolve("build.gradle.kts"));

        assertTrue(source.contains("ACTIVE_DEADLINE_TICKS"));
        assertTrue(source.contains("DISABLED_DEADLINE_TICKS"));
        assertTrue(source.contains("RECOVERY_DEADLINE_TICKS"));
        assertTrue(source.contains("TIMEOUT_KINETIC_BUILD"));
        assertTrue(source.contains("TIMEOUT_KINETIC_DISCONNECT"));
        assertTrue(source.contains("TIMEOUT_KINETIC_REBUILD"));
        assertTrue(workflow.contains("l1-static-contract"));
        assertTrue(workflow.contains("l3-exact-stack"));
        assertTrue(workflow.contains("runMech002NaturalPowerServer"));
        assertTrue(workflow.contains("MECH_002_NATURAL_WORKSHOP_POWER PASS"));
        assertTrue(workflow.contains("cmp build/mech-002/resolved.json"));
        assertTrue(workflow.contains("cmp build/mech-002/mech_002_waterwheel_airflow_bench.nbt"));
        assertTrue(build.contains("mech002NaturalPowerServer"));
        assertTrue(build.contains("mech002NaturalPowerClient"));
        assertTrue(build.contains("sourceSet.set(waveC11Runtime)"));
        assertTrue(ledger.contains("\"CREATE_WATER_WHEEL_SOURCE_LIFECYCLE\""));
        assertTrue(ledger.contains("\"production_authority_for_agents\""));
        assertTrue(ledger.contains("\"C\": true"));
    }

    private static String fixtureSource() throws IOException {
        return Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/"
                        + "SkyforgeMech002NaturalPowerAcceptance.java"));
    }
}
