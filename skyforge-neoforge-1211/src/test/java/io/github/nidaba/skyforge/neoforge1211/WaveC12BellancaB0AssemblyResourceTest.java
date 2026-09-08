package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class WaveC12BellancaB0AssemblyResourceTest {
    private static final Path PROJECT_DIRECTORY =
            Path.of(System.getProperty("skyforge.test.projectDirectory", "."))
                    .toAbsolutePath()
                    .normalize();

    @Test
    void usesAcceptedFlightRuntimeAndRealPhysicsAssembler() throws IOException {
        String build = Files.readString(PROJECT_DIRECTORY.resolve("build.gradle.kts"));
        String source = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/"
                        + "SkyforgeWaveC12BellancaB0AssemblyAcceptance.java"));

        assertTrue(build.contains("waveC12BellancaB0AssemblyServer"));
        assertTrue(build.contains("sourceSet.set(waveC11Runtime)"));
        assertTrue(build.contains("run-wave-c12-bellanca-b0-assembly"));
        assertTrue(source.contains("assembleOrDisassemble"));
        assertTrue(source.contains("PhysicsAssemblerBlockEntity"));
        assertTrue(source.contains("ServerSubLevelContainer"));
        assertTrue(source.contains("getMassTracker"));
        assertFalse(source.contains("SimAssemblyHelper.assembleFromSingleBlock"));
        assertFalse(source.contains("import dev.simulated"));
        assertFalse(source.contains("import dev.ryanhcode.sable"));
        assertFalse(source.contains("import com.simibubi.create"));
    }

    @Test
    void preservesExactHistoricalMainBodyAndGlueDomains() throws IOException {
        String source = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/"
                        + "SkyforgeWaveC12BellancaB0AssemblyAcceptance.java"));
        String manifest = Files.readString(PROJECT_DIRECTORY.resolve(
                "../docs/design-audit/giuseppe-bellanca-b0a-assembly-manifest-v0.1.json")
                .normalize());

        assertTrue(source.contains("EXPECTED_UNIQUE_MAIN_BODY_BLOCKS = 105"));
        assertTrue(source.contains("create:white_sail"));
        assertTrue(source.contains("simulated:red_portable_engine"));
        assertTrue(source.contains("create:rotation_speed_controller"));
        assertTrue(source.contains("aeronautics:propeller_bearing"));
        assertTrue(source.contains("simulated:physics_assembler"));
        assertTrue(source.contains("new BlockPos(-13, 4, -1), new BlockPos(2, 4, 1)"));
        assertTrue(source.contains("new BlockPos(-2, 4, -1), new BlockPos(13, 4, 1)"));
        assertTrue(source.contains("new BlockPos(-1, 1, -8), new BlockPos(1, 4, 5)"));
        assertTrue(source.contains("new BlockPos(0, 1, 4), new BlockPos(0, 3, 6)"));

        assertTrue(manifest.contains("\"schema\": \"skyforge.gb1.b0a-assembly-topology.v0.1\""));
        assertTrue(manifest.contains("\"status\": \"proposal_requiring_live_acceptance\""));
        assertTrue(manifest.contains("\"count\": 66") || manifest.contains("\"count\": 33"));
    }

    @Test
    void liveEvidenceDoesNotPromotePaperMassToFact() throws IOException {
        String source = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/"
                        + "SkyforgeWaveC12BellancaB0AssemblyAcceptance.java"));
        String massNote = Files.readString(PROJECT_DIRECTORY.resolve(
                "../docs/design-audit/giuseppe-bellanca-b0a-mass-and-balance-v0.1.md")
                .normalize());

        assertTrue(source.contains("MASS_REVIEW_THRESHOLD_KPG = 60.0"));
        assertTrue(source.contains("WAVE_C12_BELLANCA_B0_ASSEMBLY PASS"));
        assertTrue(source.contains("localComX="));
        assertTrue(source.contains("localComY="));
        assertTrue(source.contains("localComZ="));
        assertFalse(source.contains("mass == 38.5"));
        assertTrue(massNote.contains("live Sable measurement is authoritative"));
        assertTrue(massNote.contains("analytic hypotheses"));
        assertTrue(massNote.contains("explicit-review rule"));
    }
}
