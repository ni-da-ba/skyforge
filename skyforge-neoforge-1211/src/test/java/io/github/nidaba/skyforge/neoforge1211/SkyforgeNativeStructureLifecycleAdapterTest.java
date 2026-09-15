package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class SkyforgeNativeStructureLifecycleAdapterTest {
    private static final Path PROJECT_DIRECTORY =
            Path.of(System.getProperty("skyforge.test.projectDirectory", "."))
                    .toAbsolutePath()
                    .normalize();

    @Test
    void exactVolumeAdapterDelegatesOnlyToInheritedNativeStructureLifecycle() throws IOException {
        String source = generatorSource();
        String adapter = adapterSource(source);

        assertTrue(adapter.contains("ChunkGeneratorStructureState structureState"));
        assertTrue(adapter.contains("SkyIslandWorldVolumeId volumeId"));
        assertTrue(adapter.contains("SkyforgeGenerationDomainStage.requireExactIslandVolume(volumeId);"));
        assertTrue(adapter.contains("SkyforgeNeoForge1211SurfaceStage.requireExactlyOneCandidateVolume(volumeId, chunk);"));
        assertTrue(adapter.contains("SkyforgePhysicalVolumeAdmissionStage.allowsPopulation(volumeId)"));
        assertTrue(adapter.contains("SkyforgeStructureCandidateStage.requireInactive();"));
        assertTrue(adapter.contains("super.createStructures("));

        assertFalse(adapter.contains("StructureSelectionEntry"));
        assertFalse(adapter.contains("getPlacementsForStructure"));
        assertFalse(adapter.contains("setStartForStructure"));
        assertFalse(adapter.contains("new StructureStart"));
    }

    @Test
    void inheritedCandidatePathRemainsReachableAndBaseWorldIsNotOverridden() throws IOException {
        String source = generatorSource();
        String adapter = adapterSource(source);

        assertTrue(source.contains("protected boolean tryGenerateStructure("));
        assertTrue(source.contains("generated = super.tryGenerateStructure("));
        assertTrue(adapter.contains("super.createStructures("));
        assertFalse(source.contains("@Override\n    public void createStructures("));
    }

    @Test
    void adapterIntroducesNoGlobalOrTransientCompletionMap() throws IOException {
        String adapter = adapterSource(generatorSource());

        assertFalse(adapter.contains("ThreadLocal<"));
        assertFalse(adapter.contains("HashMap"));
        assertFalse(adapter.contains("Map<"));
        assertFalse(adapter.contains("completion"));
        assertFalse(adapter.contains("completed"));
        assertFalse(adapter.contains("ticket"));
    }

    @Test
    void candidateTraceRejectsAmbiguousNestedLifecycleState() {
        try (var scope = SkyforgeStructureCandidateStage.open()) {
            assertTrue(scope.claims().isEmpty());
            org.junit.jupiter.api.Assertions.assertThrows(
                    IllegalStateException.class,
                    SkyforgeStructureCandidateStage::requireInactive);
        }
        SkyforgeStructureCandidateStage.requireInactive();
    }

    private static String generatorSource() throws IOException {
        return Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/"
                        + "SkyforgeNoiseBasedChunkGenerator.java"));
    }

    private static String adapterSource(String source) {
        int start = source.indexOf("void createStructuresForExactSkyforgeVolume(");
        int end = source.indexOf("\n    /**\n     * Wraps a native structure candidate", start);
        assertTrue(start >= 0, "exact-volume native structure adapter must exist");
        assertTrue(end > start, "adapter must remain isolated from candidate admission implementation");
        return source.substring(start, end);
    }
}
