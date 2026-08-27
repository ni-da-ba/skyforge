package io.github.nidaba.skyforge.recipes.skyisland;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate2;
import io.github.nidaba.skyforge.kernel.coordinate.Coordinate3;
import io.github.nidaba.skyforge.kernel.evaluation.ReferenceEvaluator;
import io.github.nidaba.skyforge.kernel.field.ScalarField2;
import io.github.nidaba.skyforge.kernel.field.ScalarField3;
import io.github.nidaba.skyforge.kernel.graph.NodeId;
import io.github.nidaba.skyforge.kernel.graph.PlanarValueSignalNode;
import io.github.nidaba.skyforge.kernel.serialization.CanonicalGraphJson;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SeededSkyIslandVolumeRecipeTest {
    private static final double TOLERANCE = 1.0e-10;

    private final SeededSkyIslandVolumeRecipe recipe = new SeededSkyIslandVolumeRecipe();
    private final SignalFreeSkyIslandVolumeRecipe signalFreeRecipe =
            new SignalFreeSkyIslandVolumeRecipe();
    private final ReferenceEvaluator evaluator = new ReferenceEvaluator();
    private final CanonicalGraphJson codec = new CanonicalGraphJson();

    @Test
    void zeroAmplitudePreservesExactSignalFreeArtifact() {
        SkyIslandVolumeDescriptor descriptor = descriptor(0L, 0.0);
        CompiledSkyIslandVolume expected = signalFreeRecipe.compile(descriptor);
        CompiledSkyIslandVolume actual = recipe.compile(descriptor);

        assertAll(
                () -> assertEquals(expected, actual),
                () -> assertArrayEquals(bytes(expected.upperSurfaceGraph()), bytes(actual.upperSurfaceGraph())),
                () -> assertArrayEquals(bytes(expected.undersideSurfaceGraph()), bytes(actual.undersideSurfaceGraph())),
                () -> assertArrayEquals(bytes(expected.densityGraph()), bytes(actual.densityGraph())));
    }

    @Test
    void seededCompilationRecordsIndependentStableSignalNamespaces() {
        CompiledSkyIslandVolume compiled = recipe.compile(descriptor(42L, 1.0));
        PlanarValueSignalNode upper = (PlanarValueSignalNode) compiled.upperSurfaceGraph()
                .requireNode(new NodeId("signal.upper-detail"));
        PlanarValueSignalNode underside = (PlanarValueSignalNode) compiled.undersideSurfaceGraph()
                .requireNode(new NodeId("signal.underside-detail"));

        assertAll(
                () -> assertEquals(SeededSkyIslandVolumeRecipe.RECIPE_VERSION, compiled.recipeVersion()),
                () -> assertEquals(CanonicalGraphJson.INTERSECTION_SCHEMA_VERSION, compiled.graphSchemaVersion()),
                () -> assertEquals(SeededSkyIslandVolumeRecipe.UPPER_SIGNAL_NAMESPACE, upper.namespace()),
                () -> assertEquals(SeededSkyIslandVolumeRecipe.UNDERSIDE_SIGNAL_NAMESPACE, underside.namespace()),
                () -> assertNotEquals(upper.namespace(), underside.namespace()),
                () -> assertEquals(42L, upper.rootSeed()),
                () -> assertEquals(42L, underside.rootSeed()),
                () -> assertEquals(6, compiled.provenance().get("signal-controls").size()));
    }

    @Test
    void enrichmentPreservesExactRimAndSurfaceOrdering() {
        SkyIslandVolumeDescriptor descriptor = descriptor(7L, 1.0);
        CompiledSkyIslandVolume compiled = recipe.compile(descriptor);
        ScalarField2 upper = evaluator.field2(compiled.upperSurfaceGraph());
        ScalarField2 underside = evaluator.field2(compiled.undersideSurfaceGraph());
        double stretch = 1.0
                + SignalFreeSkyIslandVolumeRecipe.MAXIMUM_RIDGE_STRETCH
                        * descriptor.ridgeStrength();
        double majorRadius = descriptor.nominalRadius() * stretch;
        Coordinate2 interior = coordinateAlong(descriptor, 0.65 * majorRadius);
        Coordinate2 rim = coordinateAlong(descriptor, majorRadius);
        Coordinate2 exterior = coordinateAlong(descriptor, 1.15 * majorRadius);

        assertAll(
                () -> assertTrue(upper.sample(interior) > underside.sample(interior)),
                () -> assertEquals(descriptor.suspensionElevation(), upper.sample(rim), TOLERANCE),
                () -> assertEquals(descriptor.suspensionElevation(), underside.sample(rim), TOLERANCE),
                () -> assertTrue(upper.sample(exterior) < underside.sample(exterior)));
    }

    @Test
    void surfaceOffsetsRemainWithinFifteenPercentEnvelope() {
        SkyIslandVolumeDescriptor descriptor = descriptor(123456789L, 1.0);
        SkyIslandVolumeDescriptor baseDescriptor = descriptor(123456789L, 0.0);
        ScalarField2 seededUpper = evaluator.field2(recipe.compile(descriptor).upperSurfaceGraph());
        ScalarField2 seededUnderside = evaluator.field2(recipe.compile(descriptor).undersideSurfaceGraph());
        ScalarField2 baseUpper = evaluator.field2(signalFreeRecipe.compile(baseDescriptor).upperSurfaceGraph());
        ScalarField2 baseUnderside = evaluator.field2(signalFreeRecipe.compile(baseDescriptor).undersideSurfaceGraph());

        for (int zIndex = 0; zIndex <= 16; zIndex++) {
            double z = -320.0 + 40.0 * zIndex;
            for (int xIndex = 0; xIndex <= 16; xIndex++) {
                double x = -320.0 + 40.0 * xIndex;
                Coordinate2 point = new Coordinate2(x, z);
                assertEnvelope(
                        descriptor.suspensionElevation(),
                        baseUpper.sample(point),
                        seededUpper.sample(point));
                assertEnvelope(
                        descriptor.suspensionElevation(),
                        baseUnderside.sample(point),
                        seededUnderside.sample(point));
            }
        }
    }

    @Test
    void densityRemainsExactIntersectionOfEnrichedSurfaces() {
        CompiledSkyIslandVolume compiled = recipe.compile(descriptor(-1L, 1.0));
        ScalarField2 upper = evaluator.field2(compiled.upperSurfaceGraph());
        ScalarField2 underside = evaluator.field2(compiled.undersideSurfaceGraph());
        ScalarField3 density = evaluator.field3(compiled.densityGraph());
        List<Coordinate3> samples = List.of(
                new Coordinate3(0.0, 256.0, 0.0),
                new Coordinate3(96.0, 300.0, -72.0),
                new Coordinate3(-144.0, 192.0, 64.0),
                new Coordinate3(280.0, 256.0, 0.0));

        for (Coordinate3 point : samples) {
            Coordinate2 horizontal = new Coordinate2(point.x(), point.z());
            double expected = Math.min(
                    upper.sample(horizontal) - point.y(),
                    point.y() - underside.sample(horizontal));
            assertEquals(expected, density.sample(point), TOLERANCE);
        }
    }

    @Test
    void sameSeedIsByteDeterministicAndDifferentSeedsChangeEnrichment() {
        CompiledSkyIslandVolume first = recipe.compile(descriptor(0x534b59464f524745L, 1.0));
        CompiledSkyIslandVolume repeated = recipe.compile(descriptor(0x534b59464f524745L, 1.0));
        CompiledSkyIslandVolume different = recipe.compile(descriptor(Long.MAX_VALUE, 1.0));

        assertAll(
                () -> assertArrayEquals(bytes(first.upperSurfaceGraph()), bytes(repeated.upperSurfaceGraph())),
                () -> assertArrayEquals(bytes(first.undersideSurfaceGraph()), bytes(repeated.undersideSurfaceGraph())),
                () -> assertArrayEquals(bytes(first.densityGraph()), bytes(repeated.densityGraph())),
                () -> assertNotEquals(
                        new String(bytes(first.densityGraph()), StandardCharsets.UTF_8),
                        new String(bytes(different.densityGraph()), StandardCharsets.UTF_8)));
    }

    private byte[] bytes(io.github.nidaba.skyforge.kernel.graph.ProceduralGraph graph) {
        return codec.write(graph).getBytes(StandardCharsets.UTF_8);
    }

    private static void assertEnvelope(double suspension, double base, double seeded) {
        double baseOffset = base - suspension;
        double seededOffset = seeded - suspension;
        if (Math.abs(baseOffset) <= TOLERANCE) {
            assertEquals(0.0, seededOffset, TOLERANCE);
            return;
        }
        double ratio = seededOffset / baseOffset;
        assertTrue(ratio >= 0.85 - TOLERANCE && ratio <= 1.15 + TOLERANCE,
                () -> "expected seeded/base offset ratio in [0.85, 1.15], got " + ratio);
    }

    private static Coordinate2 coordinateAlong(
            SkyIslandVolumeDescriptor descriptor,
            double distance) {
        return new Coordinate2(
                descriptor.centerX() + distance * Math.cos(descriptor.ridgeAzimuth()),
                descriptor.centerZ() + distance * Math.sin(descriptor.ridgeAzimuth()));
    }

    private static SkyIslandVolumeDescriptor descriptor(long seed, double amplitude) {
        return new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION,
                seed,
                0.0,
                0.0,
                256.0,
                256.0,
                96.0,
                128.0,
                64.0,
                Math.PI / 6.0,
                0.65,
                0.60,
                0.25,
                amplitude,
                32.0);
    }
}
