package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class WaterWheelSourceLifecycleResourceTest {
    private static final Path PROJECT_DIRECTORY =
            Path.of(System.getProperty("skyforge.test.projectDirectory", ".")).toAbsolutePath().normalize();

    @Test
    void fixtureUsesExactRetainedCreateStackAndSourceInspectedWaterWheelContract() throws IOException {
        String build = Files.readString(PROJECT_DIRECTORY.resolve("build.gradle.kts"));
        String pins = Files.readString(PROJECT_DIRECTORY.resolve("wave-c1-mods.properties"));
        String source = fixtureSource();

        assertTrue(build.contains("compilerPlatformWaterWheelSourceLifecycleServer"));
        assertTrue(build.contains("sourceSet.set(waveC11Runtime)"));
        assertTrue(build.contains("run-compiler-platform-water-wheel-source-lifecycle"));
        assertTrue(pins.contains("minecraft.version=1.21.1"));
        assertTrue(pins.contains("neoforge.version=21.1.249"));
        assertTrue(pins.contains("create.version=6.0.10+mc1.21.1"));
        assertTrue(pins.contains("create.coordinate=maven.modrinth:LNytGWDc:UjX6dr61"));
        assertTrue(source.contains("create:water_wheel"));
        assertTrue(source.contains("create:shaft"));
        assertTrue(source.contains("WaterWheelBlockEntity"));
        assertTrue(source.contains("GeneratingKineticBlockEntity"));
        assertTrue(source.contains("facing\", \"east"));
        assertTrue(source.contains("axis\", \"x"));
    }

    @Test
    void lifecycleUsesRealFluidFlowAndOnlyEnvironmentalDisableRestore() throws IOException {
        String source = fixtureSource();

        assertTrue(source.contains("Blocks.WATER.defaultBlockState()"));
        assertTrue(source.contains("fluid.getFlow(level, pos)"));
        assertTrue(source.contains("hasActiveWaterFlow"));
        assertTrue(source.contains("flowingWaterCellRemoved=true"));
        assertTrue(source.contains("flowingWaterCellRestored=true"));
        assertTrue(source.contains("withProperty(Blocks.WATER.defaultBlockState(), \"level\", \"8\")"));
        assertTrue(source.contains("environmentalDisableObserved=true environmentalRecoveryObserved=true"));
        assertTrue(source.contains("getSpeed"));
        assertTrue(source.contains("hasSource"));
        assertTrue(source.contains("hasNetwork"));
        assertFalse(source.contains("CreativeMotor"));
        assertFalse(source.contains("setFlowScoreAndUpdate"));
        assertFalse(source.contains("determineAndApplyFlowScore"));
        assertFalse(source.contains("Sable"));
    }

    @Test
    void lifecycleAndOuterRunnerAreBoundedAndClassified() throws IOException {
        String source = fixtureSource();
        String failures = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeCompilerIntegrationFailure.java"));
        String runner = Files.readString(PROJECT_DIRECTORY.resolve(
                "../scripts/compiler-integration/run-exact-stack-fixture.sh").normalize());
        String workflow = Files.readString(PROJECT_DIRECTORY.resolve(
                "../.github/workflows/compiler-platform-water-wheel-source-lifecycle.yml").normalize());

        assertTrue(source.contains("ACTIVE_DEADLINE_TICKS"));
        assertTrue(source.contains("DISABLED_DEADLINE_TICKS"));
        assertTrue(source.contains("RECOVERY_DEADLINE_TICKS"));
        assertTrue(source.contains("FAIL_BLOCK_ENTITY_INIT"));
        assertTrue(source.contains("FAIL_NETWORK"));
        assertTrue(failures.contains("TIMEOUT_KINETIC_BUILD"));
        assertTrue(failures.contains("TIMEOUT_KINETIC_DISCONNECT"));
        assertTrue(failures.contains("TIMEOUT_KINETIC_REBUILD"));
        assertTrue(runner.contains("TIMEOUT_SERVER_BOOT"));
        assertTrue(runner.contains("TIMEOUT_FIXTURE_TERMINAL_STATE"));
        assertTrue(workflow.contains("l1-contract"));
        assertTrue(workflow.contains("l2-exact-stack"));
        assertTrue(workflow.contains("waveC11ResolvePinnedMods"));
    }

    @Test
    void capabilityIsNotPublishedAcceptedBeforeExactL2Evidence() throws IOException {
        String ledger = Files.readString(PROJECT_DIRECTORY.resolve(
                "../docs/agent-state/COMPILER_INTEGRATION_CAPABILITIES.json").normalize());
        assertFalse(ledger.contains("CREATE_WATER_WHEEL_SOURCE_LIFECYCLE"));
    }

    private static String fixtureSource() throws IOException {
        return Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeWaterWheelSourceLifecycleAcceptance.java"));
    }
}
