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
    void deferredHydrologyWritesNotifyTheStableChunkMutationLifecycle() throws IOException {
        String adapter = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeAuthoredVisibleHydrologyAdapter.java"));

        assertTrue(adapter.contains("private static void writeState("));
        assertTrue(adapter.contains("SkyforgeDeferredChunkMutationLifecycle.afterWrite("));

        String lifecycle = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeDeferredChunkMutationLifecycle.java"));
        assertTrue(lifecycle.contains("storedState.getFluidState()"));
        assertTrue(lifecycle.contains("state.level.scheduleTick(immutablePosition, fluid.getType(), 1)"));
    }

    @Test
    void deferredVegetationPlacesStructuralFeaturesBeforeLowVegetation() throws IOException {
        String runner = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeNativeBiomePopulationRunner.java"));

        assertTrue(runner.contains("orderedPlacedFeatures("));
        assertTrue(runner.contains("comparing(OrderedPlacedFeature::structuralVegetation)"));
        assertTrue(runner.contains("thenComparingInt(OrderedPlacedFeature::occurrenceIndex)"));
        assertTrue(runner.contains("sourceStepIndex"));
        assertFalse(runner.contains("GenerationStep.Decoration.VEGETAL_DECORATION.ordinal(),\n                        occurrenceIndex++"));
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
