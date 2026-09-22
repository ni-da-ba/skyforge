package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class SkyforgeCarverReadVirtualizationSourceTest {
    private static final Path PROJECT_DIRECTORY = Path.of(
                    System.getProperty("skyforge.test.projectDirectory", "."))
            .toAbsolutePath()
            .normalize();

    @Test
    void nativeCarverReadsUseDeterministicExactVolumeTopology() throws IOException {
        String stage = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeCarverExecutionStage.java"));
        String mixin = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/mixin/SkyforgeLevelChunkCarverDomainMixin.java"));

        assertTrue(stage.contains("public static Optional<BlockState> virtualRead"));
        assertTrue(stage.contains("!execution.virtualizeReads"));
        assertTrue(stage.contains("openNativeCarver"));
        assertTrue(stage.contains("Blocks.BEDROCK.defaultBlockState()"));
        assertTrue(stage.contains("Blocks.STONE.defaultBlockState()"));
        assertTrue(stage.contains("Blocks.AIR.defaultBlockState()"));
        assertTrue(mixin.contains("getBlockState"));
        assertTrue(mixin.contains("getFluidState"));
        assertTrue(mixin.contains("SkyforgeCarverExecutionStage.virtualRead"));

        String heightMixin = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/mixin/SkyforgeChunkAccessPopulationHeightMixin.java"));
        assertTrue(stage.contains("public static OptionalInt virtualFirstFreeHeight"));
        assertTrue(heightMixin.contains("SkyforgeCarverExecutionStage.virtualFirstFreeHeight"));

        String cursor = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeNativeCarverCursor.java"));
        assertTrue(cursor.contains("SkyforgeCarverExecutionStage.openNativeCarver"));
        assertTrue(cursor.contains("this.noiseChunk = createNoiseChunk(targetChunk)"));
        assertTrue(cursor.contains("return NoiseChunk.forChunk("));
        assertFalse(cursor.contains("targetChunk.getOrCreateNoiseChunk("));
        assertTrue(stage.contains("acceptedPositions"));
        assertTrue(stage.contains("acceptedPositionDigest"));
        assertTrue(cursor.contains("writeSnapshot.acceptedPositionDigest()"));

        String production = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeNeoForge1211ProductionComposedCaveDevRuntime.java"));
        assertTrue(production.contains("nativeResult.acceptedPositionDigest()"));
        assertTrue(production.contains("\"nativeAcceptedCarveBlocks\""));
        assertFalse(production.contains("nativeCarveDigest = mix(nativeCarveDigest, nativeResult.changedPositionDigest())"));
    }
}
