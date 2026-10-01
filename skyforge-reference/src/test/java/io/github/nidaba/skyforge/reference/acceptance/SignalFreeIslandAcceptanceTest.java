package io.github.nidaba.skyforge.reference.acceptance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate2;
import io.github.nidaba.skyforge.kernel.coordinate.Coordinate3;
import io.github.nidaba.skyforge.kernel.evaluation.ReferenceEvaluator;
import io.github.nidaba.skyforge.kernel.field.ScalarField2;
import io.github.nidaba.skyforge.kernel.field.ScalarField3;
import io.github.nidaba.skyforge.kernel.graph.ConstantNode;
import io.github.nidaba.skyforge.kernel.graph.NodeId;
import io.github.nidaba.skyforge.kernel.graph.ProceduralGraph;
import io.github.nidaba.skyforge.kernel.serialization.CanonicalGraphJson;
import io.github.nidaba.skyforge.model.island.IslandDescriptor;
import io.github.nidaba.skyforge.recipes.island.CompiledIsland;
import io.github.nidaba.skyforge.recipes.island.SignalFreeIslandRecipe;
import io.github.nidaba.skyforge.reference.EvidenceCli;
import io.github.nidaba.skyforge.reference.SignalFreeReferenceCorpus;
import io.github.nidaba.skyforge.reference.evidence.IslandEvidence;
import io.github.nidaba.skyforge.reference.evidence.IslandEvidenceGenerator;
import io.github.nidaba.skyforge.reference.sampling.GridSpec;
import io.github.nidaba.skyforge.reference.sampling.SamplingOrder;
import io.github.nidaba.skyforge.reference.sampling.ScalarGrid;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Executable SF-ISL acceptance gates for the signal-free island corpus. */
final class SignalFreeIslandAcceptanceTest {
    private static final int FAST_RESOLUTION = 257;
    private static final double NUMERICAL_TOLERANCE = 1.0e-10;
    private static final double ANGULAR_TOLERANCE = Math.PI / 180.0;
    private static final String GOLDEN_RESOURCE = "signal-free-island-v1.sha256";

    private final SignalFreeIslandRecipe recipe = new SignalFreeIslandRecipe();
    private final IslandEvidenceGenerator generator = new IslandEvidenceGenerator();
    private final ReferenceEvaluator evaluator = new ReferenceEvaluator();
    private final CanonicalGraphJson graphCodec = new CanonicalGraphJson();

    @TempDir
    Path temporaryDirectory;

    @Test
    void sfIsl001ClosedLandform() {
        IslandEvidence evidence = evidence(SignalFreeReferenceCorpus.standardDescriptor());
        GridSpec grid = evidence.height().specification();

        assertAll(
                () -> assertTrue(evidence.metrics().landSampleCount() > 0),
                () -> assertEquals(1, evidence.metrics().connectedLandComponents()),
                () -> assertEquals(0, evidence.metrics().boundaryLandSampleCount()),
                () -> assertEquals(
                        evidence.compiledIsland().descriptor().centerX(),
                        evidence.metrics().landCentroidX(),
                        grid.spacingX()),
                () -> assertEquals(
                        evidence.compiledIsland().descriptor().centerZ(),
                        evidence.metrics().landCentroidZ(),
                        grid.spacingZ()));
    }

    @Test
    void sfIsl002BoundedElevation() {
        IslandDescriptor descriptor = SignalFreeReferenceCorpus.standardDescriptor();
        IslandEvidence evidence = evidence(descriptor);
        double stretch = 1.0 + SignalFreeIslandRecipe.MAXIMUM_RIDGE_STRETCH * descriptor.ridgeStrength();
        double maximumNormalizedRadiusSquared = 4.5 * stretch * stretch;
        double quadraticWeight = descriptor.coastalFalloff() / descriptor.nominalRadius();
        double maximumProfile = quadraticWeight * maximumNormalizedRadiusSquared
                + (1.0 - quadraticWeight)
                        * maximumNormalizedRadiusSquared
                        * maximumNormalizedRadiusSquared;
        double analyticalLowerBound = descriptor.maximumElevation() * (1.0 - maximumProfile);

        for (double height : evidence.height().values()) {
            assertTrue(Double.isFinite(height));
            assertTrue(height >= analyticalLowerBound - NUMERICAL_TOLERANCE);
            assertTrue(height <= descriptor.maximumElevation() + NUMERICAL_TOLERANCE);
        }
        assertEquals(descriptor.maximumElevation(), evidence.heightStatistics().maximum(), NUMERICAL_TOLERANCE);
    }

    @Test
    void sfIsl003ScaleControl() {
        IslandDescriptor small = descriptorWith(192.0, 96.0, 48.0, Math.PI / 6.0, 0.65, 1L, 32.0);
        IslandDescriptor large = descriptorWith(288.0, 96.0, 72.0, Math.PI / 6.0, 0.65, 1L, 32.0);
        IslandEvidence smallEvidence = evidence(small);
        IslandEvidence largeEvidence = evidence(large);
        Footprint smallFootprint = footprint(smallEvidence.landMask());
        Footprint largeFootprint = footprint(largeEvidence.landMask());
        double scale = large.nominalRadius() / small.nominalRadius();

        assertAll(
                () -> assertEquals(
                        small.centerX(), smallEvidence.metrics().landCentroidX(), smallEvidence.height().specification().spacingX()),
                () -> assertEquals(
                        large.centerX(), largeEvidence.metrics().landCentroidX(), largeEvidence.height().specification().spacingX()),
                () -> assertTrue(largeEvidence.metrics().estimatedLandArea() > smallEvidence.metrics().estimatedLandArea()),
                () -> assertEquals(
                        scale * scale,
                        largeEvidence.metrics().estimatedLandArea() / smallEvidence.metrics().estimatedLandArea(),
                        NUMERICAL_TOLERANCE),
                () -> assertEquals(scale, largeFootprint.spanX() / smallFootprint.spanX(), NUMERICAL_TOLERANCE),
                () -> assertEquals(scale, largeFootprint.spanZ() / smallFootprint.spanZ(), NUMERICAL_TOLERANCE));
    }

    @Test
    void sfIsl004ElevationControl() {
        IslandDescriptor low = descriptorWith(256.0, 96.0, 64.0, Math.PI / 6.0, 0.65, 1L, 32.0);
        IslandDescriptor high = descriptorWith(256.0, 192.0, 64.0, Math.PI / 6.0, 0.65, 1L, 32.0);
        IslandEvidence lowEvidence = evidence(low);
        IslandEvidence highEvidence = evidence(high);

        assertAll(
                () -> assertEquals(lowEvidence.landMask().sha256(), highEvidence.landMask().sha256()),
                () -> assertEquals(lowEvidence.metrics(), highEvidence.metrics()),
                () -> assertEquals(
                        2.0,
                        highEvidence.heightStatistics().maximum() / lowEvidence.heightStatistics().maximum(),
                        NUMERICAL_TOLERANCE),
                () -> assertEquals(
                        2.0,
                        positivePercentile(highEvidence.height(), 0.90)
                                / positivePercentile(lowEvidence.height(), 0.90),
                        NUMERICAL_TOLERANCE));
    }

    @Test
    void sfIsl005RidgeControl() {
        double firstAzimuth = Math.PI / 12.0;
        double secondAzimuth = Math.PI / 3.0;
        IslandEvidence first = evidence(descriptorWith(256.0, 96.0, 64.0, firstAzimuth, 0.85, 1L, 32.0));
        IslandEvidence second = evidence(descriptorWith(256.0, 96.0, 64.0, secondAzimuth, 0.85, 1L, 32.0));
        double firstMeasured = principalAxisAzimuth(first.landMask());
        double secondMeasured = principalAxisAzimuth(second.landMask());

        assertAll(
                () -> assertTrue(axisDifference(firstAzimuth, firstMeasured) <= ANGULAR_TOLERANCE),
                () -> assertTrue(axisDifference(secondAzimuth, secondMeasured) <= ANGULAR_TOLERANCE),
                () -> assertTrue(
                        axisDifference(
                                        secondAzimuth - firstAzimuth,
                                        secondMeasured - firstMeasured)
                                <= ANGULAR_TOLERANCE));
    }

    @Test
    void sfIsl006ZeroSignalIsNeutral() {
        IslandDescriptor first = descriptorWith(256.0, 96.0, 64.0, Math.PI / 6.0, 0.65, Long.MIN_VALUE, 8.0);
        IslandDescriptor second = descriptorWith(256.0, 96.0, 64.0, Math.PI / 6.0, 0.65, Long.MAX_VALUE, 128.0);
        CompiledIsland firstCompiled = recipe.compile(first);
        CompiledIsland secondCompiled = recipe.compile(second);
        GridSpec grid = fastGrid(first);

        assertAll(
                () -> assertTrue(Arrays.equals(
                        graphCodec.write(firstCompiled.heightGraph()),
                        graphCodec.write(secondCompiled.heightGraph()))),
                () -> assertTrue(Arrays.equals(
                        graphCodec.write(firstCompiled.densityGraph()),
                        graphCodec.write(secondCompiled.densityGraph()))),
                () -> assertEquals(
                        generator.generate(firstCompiled, grid, SamplingOrder.FORWARD).height().sha256(),
                        generator.generate(secondCompiled, grid, SamplingOrder.PARALLEL).height().sha256()));
    }

    @Test
    void sfIsl008DensityMatchesHeightSurface() {
        CompiledIsland compiled = recipe.compile(SignalFreeReferenceCorpus.standardDescriptor());
        ScalarField2 height = evaluator.field2(compiled.heightGraph());
        ScalarField3 density = evaluator.field3(compiled.densityGraph());
        double[] horizontalCoordinates = {-320.0, -96.0, 0.0, 73.5, 320.0};
        double offset = 13.25;

        for (double x : horizontalCoordinates) {
            for (double z : horizontalCoordinates) {
                double surface = height.sample(new Coordinate2(x, z));
                assertAll(
                        () -> assertEquals(
                                0L,
                                Double.doubleToRawLongBits(density.sample(new Coordinate3(x, surface, z)))),
                        () -> assertEquals(
                                offset,
                                density.sample(new Coordinate3(x, surface - offset, z)),
                                NUMERICAL_TOLERANCE),
                        () -> assertEquals(
                                -offset,
                                density.sample(new Coordinate3(x, surface + offset, z)),
                                NUMERICAL_TOLERANCE));
            }
        }
    }

    @Test
    void sfIsl009DescriptorControlsRemainInspectable() {
        IslandDescriptor descriptor = SignalFreeReferenceCorpus.standardDescriptor();
        ProceduralGraph graph = recipe.compile(descriptor).heightGraph();
        double stretch = 1.0 + SignalFreeIslandRecipe.MAXIMUM_RIDGE_STRETCH * descriptor.ridgeStrength();

        assertAll(
                () -> assertEquals(descriptor.centerX(), constant(graph, "descriptor.center-x")),
                () -> assertEquals(descriptor.centerZ(), constant(graph, "descriptor.center-z")),
                () -> assertEquals(descriptor.maximumElevation(), constant(graph, "descriptor.maximum-elevation")),
                () -> assertEquals(descriptor.nominalRadius() * stretch, constant(graph, "ridge.major-radius")),
                () -> assertEquals(descriptor.nominalRadius() / stretch, constant(graph, "ridge.minor-radius")),
                () -> assertEquals(Math.cos(descriptor.ridgeAzimuth()), constant(graph, "ridge.cos-azimuth")),
                () -> assertEquals(Math.sin(descriptor.ridgeAzimuth()), constant(graph, "ridge.sin-azimuth")),
                () -> assertEquals(
                        descriptor.coastalFalloff() / descriptor.nominalRadius(),
                        constant(graph, "coast.quadratic-weight")),
                () -> graph.requireNode(new NodeId("coast.normalized-profile")),
                () -> graph.requireNode(new NodeId("height.remaining-fraction")));
    }

    @Test
    void standardCommandMatchesPinnedGoldenEvidence() throws IOException {
        Path output = temporaryDirectory.resolve(SignalFreeReferenceCorpus.STANDARD_ISLAND_ID);
        EvidenceCli.main(new String[] {output.toString()});
        Map<String, String> expected = goldenChecksums();

        for (Map.Entry<String, String> artifact : expected.entrySet()) {
            assertEquals(artifact.getValue(), sha256(output.resolve(artifact.getKey())), artifact.getKey());
        }

        String manifest = Files.readString(output.resolve("manifest.json"), StandardCharsets.UTF_8);
        assertAll(
                () -> assertTrue(manifest.contains("\"landSampleCount\":365334")),
                () -> assertTrue(manifest.contains("\"connectedLandComponents\":1")),
                () -> assertTrue(manifest.contains("\"boundaryLandSampleCount\":0")),
                () -> assertTrue(manifest.contains("\"estimatedLandArea\":\"0x1.92272a72c6bc2p17\"")),
                () -> assertTrue(manifest.contains("\"maximum\":\"0x1.7fffed3964a16p6\"")),
                () -> assertTrue(manifest.contains("\"heightGrid\":\"bf09f4c9586dd3a9f603b10f990b21df7d9e3230d0265bfbd2fda6fe49dca5e3\"")));
    }

    private IslandEvidence evidence(IslandDescriptor descriptor) {
        return generator.generate(recipe.compile(descriptor), fastGrid(descriptor), SamplingOrder.FORWARD);
    }

    private static GridSpec fastGrid(IslandDescriptor descriptor) {
        double halfWidth = IslandEvidenceGenerator.STANDARD_HALF_WIDTH_FACTOR * descriptor.nominalRadius();
        return new GridSpec(
                descriptor.centerX() - halfWidth,
                descriptor.centerX() + halfWidth,
                descriptor.centerZ() - halfWidth,
                descriptor.centerZ() + halfWidth,
                FAST_RESOLUTION,
                FAST_RESOLUTION);
    }

    private static IslandDescriptor descriptorWith(
            double radius,
            double elevation,
            double falloff,
            double azimuth,
            double ridgeStrength,
            long seed,
            double signalScale) {
        return new IslandDescriptor(
                IslandDescriptor.SCHEMA_VERSION,
                seed,
                0.0,
                0.0,
                radius,
                elevation,
                falloff,
                azimuth,
                ridgeStrength,
                0.0,
                signalScale);
    }

    private static double constant(ProceduralGraph graph, String identifier) {
        return ((ConstantNode) graph.requireNode(new NodeId(identifier))).value();
    }

    private static Footprint footprint(ScalarGrid mask) {
        GridSpec grid = mask.specification();
        double minimumX = Double.POSITIVE_INFINITY;
        double maximumX = Double.NEGATIVE_INFINITY;
        double minimumZ = Double.POSITIVE_INFINITY;
        double maximumZ = Double.NEGATIVE_INFINITY;
        for (int z = 0; z < grid.height(); z++) {
            for (int x = 0; x < grid.width(); x++) {
                if (mask.valueAt(x, z) > 0.0) {
                    minimumX = Math.min(minimumX, grid.xAt(x));
                    maximumX = Math.max(maximumX, grid.xAt(x));
                    minimumZ = Math.min(minimumZ, grid.zAt(z));
                    maximumZ = Math.max(maximumZ, grid.zAt(z));
                }
            }
        }
        return new Footprint(maximumX - minimumX, maximumZ - minimumZ);
    }

    private static double positivePercentile(ScalarGrid grid, double fraction) {
        double[] values = Arrays.stream(grid.values()).filter(value -> value > 0.0).sorted().toArray();
        int index = (int) Math.floor((values.length - 1) * fraction);
        return values[index];
    }

    private static double principalAxisAzimuth(ScalarGrid mask) {
        GridSpec grid = mask.specification();
        double sumX = 0.0;
        double sumZ = 0.0;
        int count = 0;
        for (int z = 0; z < grid.height(); z++) {
            for (int x = 0; x < grid.width(); x++) {
                if (mask.valueAt(x, z) > 0.0) {
                    sumX += grid.xAt(x);
                    sumZ += grid.zAt(z);
                    count++;
                }
            }
        }
        double centerX = sumX / count;
        double centerZ = sumZ / count;
        double xx = 0.0;
        double xz = 0.0;
        double zz = 0.0;
        for (int z = 0; z < grid.height(); z++) {
            for (int x = 0; x < grid.width(); x++) {
                if (mask.valueAt(x, z) > 0.0) {
                    double dx = grid.xAt(x) - centerX;
                    double dz = grid.zAt(z) - centerZ;
                    xx += dx * dx;
                    xz += dx * dz;
                    zz += dz * dz;
                }
            }
        }
        return normalizeAxis(0.5 * Math.atan2(2.0 * xz, xx - zz));
    }

    private static double axisDifference(double first, double second) {
        double difference = Math.abs(normalizeAxis(first) - normalizeAxis(second));
        return Math.min(difference, Math.PI - difference);
    }

    private static double normalizeAxis(double value) {
        double normalized = value % Math.PI;
        return normalized < 0.0 ? normalized + Math.PI : normalized;
    }

    private static Map<String, String> goldenChecksums() throws IOException {
        InputStream stream = SignalFreeIslandAcceptanceTest.class.getResourceAsStream(GOLDEN_RESOURCE);
        assertNotNull(stream, GOLDEN_RESOURCE);
        Map<String, String> checksums = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                String[] parts = line.split("  ", 2);
                if (parts.length != 2) {
                    throw new IOException("invalid golden checksum line: " + line);
                }
                checksums.put(parts[1], parts[0]);
            }
        }
        return checksums;
    }

    private static String sha256(Path path) throws IOException {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record Footprint(double spanX, double spanZ) {}
}
