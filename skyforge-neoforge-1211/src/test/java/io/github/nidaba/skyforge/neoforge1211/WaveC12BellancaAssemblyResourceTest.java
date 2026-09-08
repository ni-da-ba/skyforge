package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class WaveC12BellancaAssemblyResourceTest {
    private static final Path PROJECT_DIRECTORY =
            Path.of(System.getProperty("skyforge.test.projectDirectory", "."))
                    .toAbsolutePath()
                    .normalize();

    @Test
    void c12UsesAcceptedC11FlightOnlyRuntime() throws IOException {
        String build = Files.readString(PROJECT_DIRECTORY.resolve("build.gradle.kts"));

        assertTrue(build.contains("waveC12BellancaAssemblyMassServer"));
        assertTrue(build.contains("sourceSet.set(waveC11Runtime)"));
        assertTrue(build.contains("run-wave-c12-bellanca-assembly-mass"));
        assertTrue(build.contains("skyforge.dev.waveC12BellancaAssemblyMass"));
    }

    @Test
    void fixtureUsesRealGluePhysicsAssemblerAndSableMassData() throws IOException {
        String source = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/"
                        + "SkyforgeWaveC12BellancaAssemblyAcceptance.java"));

        assertTrue(source.contains("EXPECTED_MAIN_BODY_BLOCKS = 105"));
        assertTrue(source.contains("com.simibubi.create.content.contraptions.glue.SuperGlueEntity"));
        assertTrue(source.contains("assembleOrDisassemble"));
        assertTrue(source.contains("dev.ryanhcode.sable.api.sublevel.SubLevelContainer"));
        assertTrue(source.contains("getAllSubLevels"));
        assertTrue(source.contains("simulated$getPrimaryAssembler"));
        assertTrue(source.contains("getMassTracker"));
        assertTrue(source.contains("getCenterOfMass"));
        assertTrue(source.contains("MASS_REDUCTION_REQUIRED"));
        assertTrue(source.contains("propChildAbsent=true"));

        assertFalse(source.contains("import com.simibubi.create"));
        assertFalse(source.contains("import dev.simulated"));
        assertFalse(source.contains("import dev.ryanhcode.sable"));
    }

    @Test
    void fixtureCarriesHistoricalMainBodyAndGlueEnvelope() throws IOException {
        String source = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/"
                        + "SkyforgeWaveC12BellancaAssemblyAcceptance.java"));

        for (String id : new String[] {
                "create:white_sail",
                "simulated:red_portable_engine",
                "create:rotation_speed_controller",
                "create:large_cogwheel",
                "create:shaft",
                "aeronautics:propeller_bearing",
                "simulated:physics_assembler"
        }) {
            assertTrue(source.contains(id), id);
        }

        assertTrue(source.contains("rel(-13, 4, -1), rel(2, 4, 1)"));
        assertTrue(source.contains("rel(-2, 4, -1), rel(13, 4, 1)"));
        assertTrue(source.contains("rel(-1, 1, -8), rel(1, 4, 5)"));
        assertTrue(source.contains("rel(0, 1, 4), rel(0, 3, 6)"));
    }

    @Test
    void historicalManifestRemainsExplicitlyProposalOnly() throws IOException {
        String manifest = Files.readString(PROJECT_DIRECTORY.resolve(
                "../docs/design-audit/giuseppe-bellanca-b0a-assembly-manifest-v0.1.json")
                .normalize());
        String mass = Files.readString(PROJECT_DIRECTORY.resolve(
                "../docs/design-audit/giuseppe-bellanca-b0a-mass-and-balance-v0.1.md")
                .normalize());

        assertTrue(manifest.contains("historical_source_constrained_input_live_validation_in_progress"));
        assertTrue(manifest.contains("\"historical_pr\": 242"));
        assertTrue(mass.contains("No paper mass or centroid in this note is accepted aircraft evidence."));
    }
}
