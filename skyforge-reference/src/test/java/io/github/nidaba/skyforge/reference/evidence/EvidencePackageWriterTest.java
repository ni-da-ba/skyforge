package io.github.nidaba.skyforge.reference.evidence;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.island.IslandDescriptor;
import io.github.nidaba.skyforge.recipes.island.CompiledIsland;
import io.github.nidaba.skyforge.recipes.island.SignalFreeIslandRecipe;
import io.github.nidaba.skyforge.reference.sampling.GridSpec;
import io.github.nidaba.skyforge.reference.sampling.SamplingOrder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class EvidencePackageWriterTest {
    private static final List<String> EXPECTED_FILES = List.of(
            "density-graph.json",
            "descriptor.json",
            "east-west.csv",
            "east-west.png",
            "height-graph.json",
            "height.grid",
            "height.png",
            "land-mask.grid",
            "land-mask.png",
            "manifest.json",
            "north-south.csv",
            "north-south.png",
            "slope.grid",
            "slope.png");

    @TempDir
    Path temporaryDirectory;

    @Test
    void writesCompleteByteRepeatableEvidencePackage() throws IOException {
        IslandEvidence evidence = evidence();
        EvidencePackageWriter writer = new EvidencePackageWriter();
        Path first = temporaryDirectory.resolve("first");
        Path second = temporaryDirectory.resolve("second");
        writer.write(evidence, first, "0.1.0-test");
        writer.write(evidence, second, "0.1.0-test");

        assertEquals(EXPECTED_FILES, fileNames(first));
        assertEquals(EXPECTED_FILES, fileNames(second));
        for (String name : EXPECTED_FILES) {
            assertArrayEquals(Files.readAllBytes(first.resolve(name)), Files.readAllBytes(second.resolve(name)), name);
        }
    }

    @Test
    void manifestRecordsVersionsStatisticsMetricsAndArtifactHashes() throws IOException {
        IslandEvidence evidence = evidence();
        Path output = temporaryDirectory.resolve("manifest-test");
        Path manifestPath = new EvidencePackageWriter().write(evidence, output, "test\"version\n1");
        String manifest = Files.readString(manifestPath, StandardCharsets.UTF_8);
        byte[] png = Files.readAllBytes(output.resolve("height.png"));

        assertAll(
                () -> assertTrue(manifest.startsWith("{\"schemaVersion\":1,")),
                () -> assertTrue(manifest.contains("\"engineVersion\":\"test\\\"version\\n1\"")),
                () -> assertTrue(manifest.contains("\"recipeVersion\":1")),
                () -> assertTrue(manifest.contains("\"graphSchemaVersion\":1")),
                () -> assertTrue(manifest.contains("\"gridBinarySchemaVersion\":1")),
                () -> assertTrue(manifest.contains("\"connectedLandComponents\":1")),
                () -> assertTrue(manifest.contains("\"boundaryLandSampleCount\":0")),
                () -> assertTrue(manifest.contains("\"canonicalChecksums\"")),
                () -> assertTrue(manifest.contains("\"path\":\"height.grid\"")),
                () -> assertTrue(manifest.contains("\"path\":\"height.png\"")),
                () -> assertArrayEquals(
                        new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a},
                        java.util.Arrays.copyOf(png, 8)));
    }

    private static List<String> fileNames(Path directory) throws IOException {
        try (var paths = Files.list(directory)) {
            return paths.map(path -> path.getFileName().toString()).sorted().toList();
        }
    }

    private static IslandEvidence evidence() {
        IslandDescriptor descriptor = new IslandDescriptor(
                IslandDescriptor.SCHEMA_VERSION,
                -1L,
                10.0,
                -5.0,
                80.0,
                20.0,
                20.0,
                0.25 * Math.PI,
                0.5,
                0.0,
                20.0);
        CompiledIsland compiled = new SignalFreeIslandRecipe().compile(descriptor);
        double halfWidth = 1.5 * descriptor.nominalRadius();
        GridSpec grid = new GridSpec(
                descriptor.centerX() - halfWidth,
                descriptor.centerX() + halfWidth,
                descriptor.centerZ() - halfWidth,
                descriptor.centerZ() + halfWidth,
                33,
                33);
        return new IslandEvidenceGenerator().generate(compiled, grid, SamplingOrder.PERMUTED);
    }
}
