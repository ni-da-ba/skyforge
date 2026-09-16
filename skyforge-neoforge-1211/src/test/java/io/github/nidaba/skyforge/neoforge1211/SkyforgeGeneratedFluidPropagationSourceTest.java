package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class SkyforgeGeneratedFluidPropagationSourceTest {
    private static final Path PROJECT_DIRECTORY = Path.of(
                    System.getProperty("skyforge.test.projectDirectory", "."))
            .toAbsolutePath()
            .normalize();

    @Test
    void persistedGeneratedFluidTicksFreezeWhenTerrainBindingIsAbsent() throws IOException {
        String stage = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeGeneratedFluidPropagationStage.java"));
        String mixin = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/mixin/SkyforgeFlowingFluidDomainMixin.java"));
        assertTrue(stage.contains("if (!SkyforgeNeoForge1211SurfaceStage.hasActiveBinding())"));
        assertTrue(stage.contains("return false;"));
        assertTrue(mixin.contains("cancellable = true"));
        assertTrue(mixin.contains("callback.cancel();"));
    }
}
