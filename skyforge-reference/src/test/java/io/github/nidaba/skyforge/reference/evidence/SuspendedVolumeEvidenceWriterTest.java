package io.github.nidaba.skyforge.reference.evidence;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.recipes.skyisland.SignalFreeSkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.reference.sampling.SamplingOrder;
import io.github.nidaba.skyforge.reference.sampling.VolumeGridSpec;
import io.github.nidaba.skyforge.reference.volume.SuspendedVolumeReferenceDomain;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SuspendedVolumeEvidenceWriterTest {
    private static final List<String> EXPECTED_FILES = List.of(
            "density-graph.json",
            "density.volume",
            "descriptor.json",
            "east-west.csv",
            "east-west.png",
            "evidence.sha256",
            "index.html",
            "isometric.png",
            "manifest.json",
            "north-south.csv",
            "north-south.png",
            "occupancy.volume",
            "provenance.json",
            "suspension-density.grid",
            "suspension-occupancy.png",
            "underside-surface-graph.json",
            "underside-surface.grid",
            "underside.png",
            "upper-surface-graph.json",
            "upper-surface.grid",
            "upper-surface.png");

    @TempDir
    Path temporaryDirectory;

    @Test
    void writesCompleteByteRepeatableNumericalAndVisualPackage() throws IOException {
        SuspendedVolumeEvidence evidence = evidence();
        SuspendedVolumeEvidenceWriter writer = new SuspendedVolumeEvidenceWriter();
        Path first = temporaryDirectory.resolve("first");
        Path second = temporaryDirectory.resolve("second");
        writer.write(evidence, first, "0.2.0-test");
        writer.write(evidence, second, "0.2.0-test");

        assertEquals(EXPECTED_FILES, fileNames(first));
        assertEquals(EXPECTED_FILES, fileNames(second));
        for (String name : EXPECTED_FILES) {
            assertArrayEquals(
                    Files.readAllBytes(first.resolve(name)),
                    Files.readAllBytes(second.resolve(name)),
                    name);
        }
    }

    @Test
    void manifestAndChecksumsExposeVersionsTopologyClearanceAndProvenance() throws IOException {
        Path output = temporaryDirectory.resolve("manifest-test");
        Path manifestPath = new SuspendedVolumeEvidenceWriter()
                .write(evidence(), output, "test\"version\n2");
        String manifest = Files.readString(manifestPath, StandardCharsets.UTF_8);
        String checksums = Files.readString(output.resolve("evidence.sha256"), StandardCharsets.UTF_8);
        byte[] isometric = Files.readAllBytes(output.resolve("isometric.png"));

        assertAll(
                () -> assertTrue(manifest.startsWith("{\"schemaVersion\":1,")),
                () -> assertTrue(manifest.contains("\"engineVersion\":\"test\\\"version\\n2\"")),
                () -> assertTrue(manifest.contains("\"graphSchemaVersion\":3")),
                () -> assertTrue(manifest.contains("\"connectedSolidComponents\":1")),
                () -> assertTrue(manifest.contains("\"faceContacts\"")),
                () -> assertTrue(manifest.contains("\"airClearance\"")),
                () -> assertTrue(manifest.contains("\"densityVolume\"")),
                () -> assertTrue(checksums.contains("  manifest.json\n")),
                () -> assertTrue(checksums.contains("  density.volume\n")),
                () -> assertArrayEquals(
                        new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a},
                        java.util.Arrays.copyOf(isometric, 8)));
    }

    private static List<String> fileNames(Path directory) throws IOException {
        try (var paths = Files.list(directory)) {
            return paths.map(path -> path.getFileName().toString()).sorted().toList();
        }
    }

    private static SuspendedVolumeEvidence evidence() {
        var compiled = new SignalFreeSkyIslandVolumeRecipe()
                .compile(SuspendedVolumeReferenceDomain.descriptor());
        var grid = new VolumeGridSpec(
                -384.0, 384.0, 0.0, 512.0, -384.0, 384.0, 33, 25, 33);
        return new SuspendedVolumeEvidenceGenerator().generate(
                compiled, grid, SamplingOrder.PERMUTED);
    }
}
