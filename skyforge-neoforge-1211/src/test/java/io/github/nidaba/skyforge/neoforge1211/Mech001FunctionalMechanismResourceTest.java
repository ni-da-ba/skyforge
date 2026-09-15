package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class Mech001FunctionalMechanismResourceTest {
    private static final Path PROJECT_DIRECTORY =
            Path.of(System.getProperty("skyforge.test.projectDirectory", "."))
                    .toAbsolutePath()
                    .normalize();

    @Test
    void semanticSpecStaysTargetNeutralWhileCompiledPlanIsExact() throws IOException {
        String spec = Files.readString(PROJECT_DIRECTORY.resolve(
                "../tools/asset_compiler/specimens/mech_001_airflow_bench.json").normalize());
        String plan = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/resources/data/skyforge/mechanisms/mech_001_airflow_bench.json"));

        assertFalse(spec.contains("create:"));
        assertTrue(spec.contains("\"qualified_kinetic_source\""));
        assertTrue(spec.contains("\"endpointRole\": \"airflow\""));
        assertTrue(plan.contains("\"requiredPlatformCapability\": \"CREATE_KINETIC_NETWORK_LIFECYCLE\""));
        assertTrue(plan.contains("\"sourcePolicy\": \"qualified_test_source_not_gameplay_canon\""));
        assertTrue(plan.contains("\"name\": \"create:creative_motor\""));
        assertTrue(plan.contains("\"name\": \"create:shaft\""));
        assertTrue(plan.contains("\"name\": \"create:encased_fan\""));
        assertTrue(plan.contains("da905478f5f3e42e197bd0ef1a38baba848c8ce65c2a911d7153743597098ce1"));
    }

    @Test
    void runtimeConsumesCompiledPlanAndUsesRealCreateKineticState() throws IOException {
        String source = fixtureSource();

        assertTrue(source.contains("/data/skyforge/mechanisms/mech_001_airflow_bench.json"));
        assertTrue(source.contains("CreativeMotorBlockEntity"));
        assertTrue(source.contains("EncasedFanBlockEntity"));
        assertTrue(source.contains("KineticBlockEntity"));
        assertTrue(source.contains("getSpeed"));
        assertTrue(source.contains("getTheoreticalSpeed"));
        assertTrue(source.contains("hasSource"));
        assertTrue(source.contains("hasNetwork"));
        assertTrue(source.contains("relayRemoved=true"));
        assertTrue(source.contains("relayRestored=true"));
        assertTrue(source.contains("requireClearance"));
        assertFalse(source.contains("Sable"));
        assertFalse(source.contains("PhysicsAssembler"));
    }

    @Test
    void runtimeAndWorkflowRemainBoundedAndConsumePlatform002() throws IOException {
        String source = fixtureSource();
        String workflow = Files.readString(PROJECT_DIRECTORY.resolve(
                "../.github/workflows/mech-001-functional-mechanism.yml").normalize());
        String ledger = Files.readString(PROJECT_DIRECTORY.resolve(
                "../docs/agent-state/COMPILER_INTEGRATION_CAPABILITIES.json").normalize());

        assertTrue(source.contains("INIT_DEADLINE_TICKS"));
        assertTrue(source.contains("DISCONNECT_DEADLINE_TICKS"));
        assertTrue(source.contains("REBUILD_DEADLINE_TICKS"));
        assertTrue(source.contains("TIMEOUT_KINETIC_BUILD"));
        assertTrue(source.contains("TIMEOUT_KINETIC_DISCONNECT"));
        assertTrue(source.contains("TIMEOUT_KINETIC_REBUILD"));
        assertTrue(workflow.contains("l1-static-contract"));
        assertTrue(workflow.contains("l3-exact-stack"));
        assertTrue(workflow.contains("runCompilerPlatformCreateKineticNetworkLifecycleServer"));
        assertTrue(workflow.contains("skyforge.dev.mech001FunctionalMechanism=true"));
        assertTrue(workflow.contains("cmp build/mech-001/resolved.json"));
        assertTrue(ledger.contains("\"CREATE_KINETIC_NETWORK_LIFECYCLE\""));
        assertTrue(ledger.contains("\"C\": true"));
    }

    private static String fixtureSource() throws IOException {
        return Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/"
                        + "SkyforgeMech001FunctionalMechanismAcceptance.java"));
    }
}
