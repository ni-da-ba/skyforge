package io.github.nidaba.skyforge.reference.acceptance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate2;
import io.github.nidaba.skyforge.kernel.evaluation.ReferenceEvaluator;
import io.github.nidaba.skyforge.kernel.field.ScalarField2;
import io.github.nidaba.skyforge.kernel.field.ScalarField3;
import io.github.nidaba.skyforge.kernel.graph.ConstantNode;
import io.github.nidaba.skyforge.kernel.graph.NodeId;
import io.github.nidaba.skyforge.kernel.graph.ProceduralGraph;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.recipes.skyisland.SignalFreeSkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.reference.FixedSeedCorpusVerifier;
import io.github.nidaba.skyforge.reference.SuspendedVolumeGoldenVerifier;
import io.github.nidaba.skyforge.reference.evidence.SuspendedVolumeEvidence;
import io.github.nidaba.skyforge.reference.evidence.SuspendedVolumeEvidenceGenerator;
import io.github.nidaba.skyforge.reference.evidence.SuspendedVolumeEvidenceWriter;
import io.github.nidaba.skyforge.reference.evidence.VolumeMetrics;
import io.github.nidaba.skyforge.reference.sampling.DeterministicVolumeSampler;
import io.github.nidaba.skyforge.reference.sampling.OccupancyVolumeGrid;
import io.github.nidaba.skyforge.reference.sampling.SamplingOrder;
import io.github.nidaba.skyforge.reference.sampling.ScalarVolumeGrid;
import io.github.nidaba.skyforge.reference.sampling.VolumeGridSpec;
import io.github.nidaba.skyforge.reference.volume.SuspendedVolumeReferenceDomain;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

/** Executable signal-free SF-VOL acceptance gates and golden-specimen contract. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
final class SignalFreeSuspendedVolumeAcceptanceTest {
    private static final double NUMERICAL_TOLERANCE = 1.0e-10;
    private static final SignalFreeSkyIslandVolumeRecipe RECIPE = new SignalFreeSkyIslandVolumeRecipe();
    private static final ReferenceEvaluator EVALUATOR = new ReferenceEvaluator();
    private static final SkyIslandVolumeDescriptor DESCRIPTOR = SuspendedVolumeReferenceDomain.descriptor();
    private static final CompiledSkyIslandVolume COMPILED = RECIPE.compile(DESCRIPTOR);
    private static final SuspendedVolumeEvidence CANONICAL = new SuspendedVolumeEvidenceGenerator().generate(
            COMPILED, SuspendedVolumeReferenceDomain.grid(), SamplingOrder.FORWARD);

    @TempDir
    Path temporaryDirectory;

    private Path writtenEvidence;

    @Test
    void sfVol001FiniteClosure() {
        VolumeGridSpec grid = CANONICAL.density().specification();
        VolumeMetrics metrics = CANONICAL.metrics();

        assertAll(
                () -> assertEquals(0, metrics.faceContacts().total()),
                () -> assertTrue(metrics.bounds().minimumX() > grid.minimumX()),
                () -> assertTrue(metrics.bounds().maximumX() < grid.maximumX()),
                () -> assertTrue(metrics.bounds().minimumY() > grid.minimumY()),
                () -> assertTrue(metrics.bounds().maximumY() < grid.maximumY()),
                () -> assertTrue(metrics.bounds().minimumZ() > grid.minimumZ()),
                () -> assertTrue(metrics.bounds().maximumZ() < grid.maximumZ()));
    }

    @Test
    void sfVol002Suspension() {
        ScalarVolumeGrid density = CANONICAL.density();
        VolumeGridSpec grid = density.specification();

        assertAll(
                () -> assertEquals(128.0, CANONICAL.metrics().airClearance().minimumY()),
                () -> assertEquals(128.0, CANONICAL.metrics().bounds().minimumY()),
                () -> assertTrue(CANONICAL.metrics().airClearance().minimumY() > 0.0));
        for (int z = 0; z < grid.zSamples(); z++) {
            for (int x = 0; x < grid.xSamples(); x++) {
                assertTrue(density.valueAt(x, 0, z) < 0.0, "lower domain face must remain air");
            }
        }
    }

    @Test
    void sfVol003ConnectedMass() {
        assertAll(
                () -> assertEquals(1, CANONICAL.metrics().connectedSolidComponents()),
                () -> assertTrue(CANONICAL.metrics().solidSampleCount() > 0),
                () -> assertEquals(366_912, CANONICAL.metrics().solidSampleCount()));
    }

    @Test
    void sfVol004SurfaceOrdering() {
        OccupancyVolumeGrid occupancy = CANONICAL.occupancy();
        for (int z = 0; z < occupancy.specification().zSamples(); z++) {
            for (int x = 0; x < occupancy.specification().xSamples(); x++) {
                if (solidColumn(occupancy, x, z)) {
                    assertTrue(
                            CANONICAL.upperSurface().valueAt(x, z)
                                    > CANONICAL.undersideSurface().valueAt(x, z),
                            "sampled solid footprint must have upper surface above underside");
                }
            }
        }

        ScalarField2 upper = EVALUATOR.field2(COMPILED.upperSurfaceGraph());
        ScalarField2 underside = EVALUATOR.field2(COMPILED.undersideSurfaceGraph());
        Coordinate2 majorRim = principalCoordinate(DESCRIPTOR, 1.0);
        Coordinate2 minorRim = minorCoordinate(DESCRIPTOR, 1.0);
        Coordinate2 beyondMajorRim = principalCoordinate(DESCRIPTOR, 1.01);

        assertAll(
                () -> assertEquals(upper.sample(majorRim), underside.sample(majorRim), NUMERICAL_TOLERANCE),
                () -> assertEquals(upper.sample(minorRim), underside.sample(minorRim), NUMERICAL_TOLERANCE),
                () -> assertEquals(DESCRIPTOR.suspensionElevation(), upper.sample(majorRim), NUMERICAL_TOLERANCE),
                () -> assertTrue(upper.sample(beyondMajorRim) < underside.sample(beyondMajorRim)));
    }

    @Test
    void sfVol005RadiusAndRidgeControls() {
        SkyIslandVolumeDescriptor small = descriptorWith(256.0, 192.0, 96.0, 128.0, Math.PI / 6.0, 0.65, 0.60, 0.25);
        SkyIslandVolumeDescriptor large = descriptorWith(256.0, 288.0, 96.0, 128.0, Math.PI / 6.0, 0.65, 0.60, 0.25);
        SkyIslandVolumeDescriptor rotated = descriptorWith(256.0, 192.0, 96.0, 128.0, Math.PI / 3.0, 0.65, 0.60, 0.25);
        ProceduralGraph smallGraph = RECIPE.compile(small).upperSurfaceGraph();
        ProceduralGraph largeGraph = RECIPE.compile(large).upperSurfaceGraph();
        ProceduralGraph rotatedGraph = RECIPE.compile(rotated).upperSurfaceGraph();
        double scale = large.nominalRadius() / small.nominalRadius();

        assertAll(
                () -> assertEquals(scale, constant(largeGraph, "ridge.major-radius")
                        / constant(smallGraph, "ridge.major-radius"), NUMERICAL_TOLERANCE),
                () -> assertEquals(scale, constant(largeGraph, "ridge.minor-radius")
                        / constant(smallGraph, "ridge.minor-radius"), NUMERICAL_TOLERANCE),
                () -> assertEquals(Math.cos(rotated.ridgeAzimuth()), constant(rotatedGraph, "ridge.cos-azimuth")),
                () -> assertEquals(Math.sin(rotated.ridgeAzimuth()), constant(rotatedGraph, "ridge.sin-azimuth")),
                () -> assertFalse(constant(smallGraph, "ridge.cos-azimuth")
                        == constant(rotatedGraph, "ridge.cos-azimuth")));
    }

    @Test
    void sfVol005VerticalControls() {
        SkyIslandVolumeDescriptor base = descriptorWith(256.0, 256.0, 96.0, 128.0, Math.PI / 6.0, 0.65, 0.60, 0.25);
        SkyIslandVolumeDescriptor raised = descriptorWith(296.0, 256.0, 96.0, 128.0, Math.PI / 6.0, 0.65, 0.60, 0.25);
        SkyIslandVolumeDescriptor taller = descriptorWith(256.0, 256.0, 128.0, 128.0, Math.PI / 6.0, 0.65, 0.60, 0.25);
        SkyIslandVolumeDescriptor deeper = descriptorWith(256.0, 256.0, 96.0, 160.0, Math.PI / 6.0, 0.65, 0.60, 0.25);
        Coordinate2 center = new Coordinate2(0.0, 0.0);

        double baseUpper = upper(base).sample(center);
        double baseLower = underside(base).sample(center);
        assertAll(
                () -> assertEquals(40.0, upper(raised).sample(center) - baseUpper, NUMERICAL_TOLERANCE),
                () -> assertEquals(40.0, underside(raised).sample(center) - baseLower, NUMERICAL_TOLERANCE),
                () -> assertEquals(32.0, upper(taller).sample(center) - baseUpper, NUMERICAL_TOLERANCE),
                () -> assertEquals(baseLower, underside(taller).sample(center), NUMERICAL_TOLERANCE),
                () -> assertEquals(baseUpper, upper(deeper).sample(center), NUMERICAL_TOLERANCE),
                () -> assertEquals(-32.0, underside(deeper).sample(center) - baseLower, NUMERICAL_TOLERANCE));
    }

    @Test
    void sfVol005UndersideShapeControls() {
        SkyIslandVolumeDescriptor lowTaper = descriptorWith(256.0, 256.0, 96.0, 128.0, Math.PI / 6.0, 0.65, 0.0, 0.25);
        SkyIslandVolumeDescriptor highTaper = descriptorWith(256.0, 256.0, 96.0, 128.0, Math.PI / 6.0, 0.65, 1.0, 0.25);
        SkyIslandVolumeDescriptor negativeAsymmetry = descriptorWith(256.0, 256.0, 96.0, 128.0, Math.PI / 6.0, 0.65, 0.60, -1.0);
        SkyIslandVolumeDescriptor positiveAsymmetry = descriptorWith(256.0, 256.0, 96.0, 128.0, Math.PI / 6.0, 0.65, 0.60, 1.0);
        Coordinate2 point = principalCoordinate(DESCRIPTOR, 0.5);

        assertAll(
                () -> assertTrue(underside(highTaper).sample(point) > underside(lowTaper).sample(point)),
                () -> assertTrue(underside(positiveAsymmetry).sample(point)
                        < underside(negativeAsymmetry).sample(point)));
    }

    @Test
    void sfVol007DeterministicIdentity() {
        ScalarField3 densityField = EVALUATOR.field3(COMPILED.densityGraph());
        DeterministicVolumeSampler sampler = new DeterministicVolumeSampler();
        String expected = CANONICAL.density().sha256();

        for (SamplingOrder order : SamplingOrder.values()) {
            ScalarVolumeGrid candidate = sampler.sample(
                    densityField, SuspendedVolumeReferenceDomain.grid(), order);
            assertEquals(expected, candidate.sha256(), order.name());
        }
    }

    @Test
    void sfVol008Inspectability() {
        Set<String> requiredControls = Set.of(
                "horizontal-center",
                "suspension-elevation",
                "nominal-radius",
                "upper-elevation",
                "underside-depth",
                "coastal-falloff",
                "primary-ridge",
                "underside-taper",
                "underside-asymmetry",
                "signal-controls");

        assertAll(
                () -> assertEquals(DESCRIPTOR, COMPILED.descriptor()),
                () -> assertEquals(1, COMPILED.recipeVersion()),
                () -> assertEquals(3, COMPILED.graphSchemaVersion()),
                () -> assertTrue(COMPILED.provenance().keySet().containsAll(requiredControls)),
                () -> assertTrue(COMPILED.provenance().get("signal-controls").isEmpty()),
                () -> COMPILED.upperSurfaceGraph().requireNode(new NodeId("upper.surface")),
                () -> COMPILED.undersideSurfaceGraph().requireNode(new NodeId("underside.surface")),
                () -> COMPILED.densityGraph().requireNode(new NodeId("density.solid-intersection")));
    }

    @Test
    void sfVol009LegacyPreservation() throws IOException {
        InputStream stream = SignalFreeSuspendedVolumeAcceptanceTest.class
                .getResourceAsStream(FixedSeedCorpusVerifier.GOLDEN_RESOURCE);
        assertTrue(stream != null, "released v0.1 golden checksum resource must remain present");
        int entries = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (!line.isBlank() && !line.startsWith("#")) {
                    entries++;
                }
            }
        }
        assertEquals(49, entries);
    }

    @Test
    void sfVol010VisualEvidence() throws IOException {
        Path output = writtenEvidence();
        List<String> reviewArtifacts = List.of(
                "upper-surface.png",
                "underside.png",
                "suspension-occupancy.png",
                "east-west.png",
                "north-south.png",
                "isometric.png",
                "index.html");
        for (String artifact : reviewArtifacts) {
            Path path = output.resolve(artifact);
            assertTrue(Files.isRegularFile(path), artifact);
            assertTrue(Files.size(path) > 0L, artifact);
        }
    }

    @Test
    void goldenSpecimenPinsCanonicalArtifactsAndMetrics() throws IOException {
        Path output = writtenEvidence();
        VolumeMetrics metrics = CANONICAL.metrics();

        assertAll(
                () -> assertEquals(19, SuspendedVolumeGoldenVerifier.verify(output)),
                () -> assertEquals(366_912, metrics.solidSampleCount()),
                () -> assertEquals(1, metrics.connectedSolidComponents()),
                () -> assertEquals(0, metrics.faceContacts().total()),
                () -> assertEquals(88.0, metrics.airClearance().minimum()),
                () -> assertEquals(-296.0, metrics.bounds().minimumX()),
                () -> assertEquals(296.0, metrics.bounds().maximumX()),
                () -> assertEquals(128.0, metrics.bounds().minimumY()),
                () -> assertEquals(348.0, metrics.bounds().maximumY()),
                () -> assertEquals(-236.0, metrics.bounds().minimumZ()),
                () -> assertEquals(236.0, metrics.bounds().maximumZ()));
    }

    private Path writtenEvidence() throws IOException {
        if (writtenEvidence == null) {
            writtenEvidence = temporaryDirectory.resolve("signal-free-suspended-volume-v1");
            new SuspendedVolumeEvidenceWriter().write(CANONICAL, writtenEvidence, "0.1.0");
        }
        return writtenEvidence;
    }

    private static ScalarField2 upper(SkyIslandVolumeDescriptor descriptor) {
        return EVALUATOR.field2(RECIPE.compile(descriptor).upperSurfaceGraph());
    }

    private static ScalarField2 underside(SkyIslandVolumeDescriptor descriptor) {
        return EVALUATOR.field2(RECIPE.compile(descriptor).undersideSurfaceGraph());
    }

    private static boolean solidColumn(OccupancyVolumeGrid occupancy, int x, int z) {
        for (int y = 0; y < occupancy.specification().ySamples(); y++) {
            if (occupancy.isSolidAt(x, y, z)) {
                return true;
            }
        }
        return false;
    }

    private static Coordinate2 principalCoordinate(SkyIslandVolumeDescriptor descriptor, double fraction) {
        double majorRadius = descriptor.nominalRadius()
                * (1.0 + SignalFreeSkyIslandVolumeRecipe.MAXIMUM_RIDGE_STRETCH * descriptor.ridgeStrength());
        double distance = majorRadius * fraction;
        return new Coordinate2(
                descriptor.centerX() + distance * Math.cos(descriptor.ridgeAzimuth()),
                descriptor.centerZ() + distance * Math.sin(descriptor.ridgeAzimuth()));
    }

    private static Coordinate2 minorCoordinate(SkyIslandVolumeDescriptor descriptor, double fraction) {
        double stretch = 1.0
                + SignalFreeSkyIslandVolumeRecipe.MAXIMUM_RIDGE_STRETCH * descriptor.ridgeStrength();
        double distance = descriptor.nominalRadius() / stretch * fraction;
        return new Coordinate2(
                descriptor.centerX() - distance * Math.sin(descriptor.ridgeAzimuth()),
                descriptor.centerZ() + distance * Math.cos(descriptor.ridgeAzimuth()));
    }

    private static double constant(ProceduralGraph graph, String identifier) {
        return ((ConstantNode) graph.requireNode(new NodeId(identifier))).value();
    }

    private static SkyIslandVolumeDescriptor descriptorWith(
            double suspension,
            double radius,
            double upperElevation,
            double undersideDepth,
            double azimuth,
            double ridgeStrength,
            double taper,
            double asymmetry) {
        return new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION,
                0L,
                0.0,
                0.0,
                suspension,
                radius,
                upperElevation,
                undersideDepth,
                64.0,
                azimuth,
                ridgeStrength,
                taper,
                asymmetry,
                0.0,
                32.0);
    }
}
