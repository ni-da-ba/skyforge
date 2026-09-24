package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class SkyforgeHydrologyReferenceReviewSourceTest {
    private static final Path PROJECT_DIRECTORY = Path.of(
                    System.getProperty("skyforge.test.projectDirectory", "."))
            .toAbsolutePath()
            .normalize();

    @Test
    void readyReviewRetainsImmutableTerrainBindingForAuthoredWaterFence() throws IOException {
        String runtime = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeHydrologyReferenceReviewRuntime.java"));

        assertTrue(runtime.contains("closeGenerationPipelineForReview();"));
        int start = runtime.indexOf("private static synchronized void closeGenerationPipelineForReview()");
        int end = runtime.indexOf("private static void closeBinding(", start);
        assertTrue(start >= 0 && end > start);
        String closeForReview = runtime.substring(start, end);
        assertFalse(closeForReview.contains("terrainBinding"));
    }

    @Test
    void runtimeHydrologyApplicationUsesChunkProjectionInsteadOfWholeIslandDeploymentScan()
            throws IOException {
        String adapter = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeAuthoredVisibleHydrologyAdapter.java"));

        int start = adapter.indexOf("static int applyAvailable(");
        int end = adapter.indexOf("private static Optional<RawDeployment> atPath(", start);
        assertTrue(start >= 0 && end > start);
        String applyAvailable = adapter.substring(start, end);
        assertTrue(applyAvailable.contains("authoredHydrologyChunkProjection"));
        assertFalse(applyAvailable.contains("authoredHydrologyDeployments"));
    }

    @Test
    void reviewerDoesNotTrackSpecimenUntilDeferredLifecycleIsComplete() throws IOException {
        String runtime = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeHydrologyReferenceReviewRuntime.java"));
        int start = runtime.indexOf("static void onPlayerLoggedIn");
        int end = runtime.indexOf("@SubscribeEvent", start + 1);
        assertTrue(start >= 0 && end > start);
        String login = runtime.substring(start, end);
        assertTrue(login.contains("nominalRadius() * 3.0 + 512.0"));
        assertFalse(login.contains("SUSPENSION_Y + 92.0),\n                    0.0"));
    }

    @Test
    void reviewEcologyReusesTheFluvialFieldThatProducedMinecraftHydrology() throws IOException {
        String runtime = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeHydrologyReferenceReviewRuntime.java"));
        assertTrue(runtime.contains("terrain.authoredFluvialTerrainField(fixture.volume().id())"));
    }

    @Test
    void bootstrapInfoCommandDoesNotSynchronouslyReenterFixtureConstruction() throws IOException {
        String runtime = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeHydrologyReferenceReviewRuntime.java"));

        int start = runtime.indexOf("private static int info(ServerPlayer player)");
        int end = runtime.indexOf("private static int move(", start);
        assertTrue(start >= 0 && end > start);
        String info = runtime.substring(start, end);
        assertTrue(info.contains("Reference metadata is still bootstrapping."));
        assertTrue(info.contains("if (!ready && active == null && bootstrap == null)"));
    }
}
