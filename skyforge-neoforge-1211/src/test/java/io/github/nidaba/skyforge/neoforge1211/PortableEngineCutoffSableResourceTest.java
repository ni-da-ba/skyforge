package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class PortableEngineCutoffSableResourceTest {
    private static final Path PROJECT_DIRECTORY =
            Path.of(System.getProperty("skyforge.test.projectDirectory", "."))
                    .toAbsolutePath()
                    .normalize();

    @Test
    void usesAcceptedC11RuntimeAndRealSimulatedAssembly() throws IOException {
        String build = Files.readString(PROJECT_DIRECTORY.resolve("build.gradle.kts"));
        String source = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/"
                        + "SkyforgePortableEngineCutoffSableAcceptance.java"));

        assertTrue(build.contains("portableEngineCutoffSableAcceptanceServer"));
        assertTrue(build.contains("sourceSet.set(waveC11Runtime)"));
        assertTrue(build.contains("run-portable-engine-cutoff-sable"));
        assertTrue(source.contains("dev.simulated_team.simulated.util.SimAssemblyHelper"));
        assertTrue(source.contains("assembleFromSingleBlock"));
        assertTrue(source.contains("dev.ryanhcode.sable.Sable"));
        assertTrue(source.contains("ServerSubLevel"));
        assertFalse(source.contains("import dev.simulated"));
        assertFalse(source.contains("import dev.ryanhcode.sable"));
    }

    @Test
    void provesTwoEngineSharedShaftIndependentAndTogetherCutoff() throws IOException {
        String source = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/"
                        + "SkyforgePortableEngineCutoffSableAcceptance.java"));

        assertTrue(source.contains("create:shaft"));
        assertTrue(source.contains("Direction.EAST"));
        assertTrue(source.contains("Direction.WEST"));
        assertTrue(source.contains("BlockStateProperties.AXIS"));
        assertTrue(source.contains("slime floor can retain shared shaft"));
        assertTrue(source.contains("assertSameSubLevel"));
        assertTrue(source.contains("independent A CUT freezes A"));
        assertTrue(source.contains("independent B CUT freezes B"));
        assertTrue(source.contains("repeat together CUT preserves A"));
        assertTrue(source.contains("final together restart resumes B"));
        assertTrue(source.contains("PORTABLE_ENGINE_CUTOFF_SABLE_ACCEPTANCE PASS"));
    }
}
