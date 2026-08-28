package io.github.nidaba.skyforge.recipes.skyisland;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate2;
import io.github.nidaba.skyforge.kernel.coordinate.Coordinate3;
import io.github.nidaba.skyforge.kernel.evaluation.ReferenceEvaluator;
import io.github.nidaba.skyforge.kernel.field.ScalarField2;
import io.github.nidaba.skyforge.kernel.field.ScalarField3;
import io.github.nidaba.skyforge.kernel.graph.NodeId;
import io.github.nidaba.skyforge.kernel.serialization.CanonicalGraphJson;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SecondaryMorphologySkyIslandVolumeRecipeTest {
    private static final double TOLERANCE = 1.0e-10;

    private final SecondaryMorphologySkyIslandVolumeRecipe recipe =
            new SecondaryMorphologySkyIslandVolumeRecipe();
    private final SeededSkyIslandVolumeRecipe seededRecipe = new SeededSkyIslandVolumeRecipe();
    private final ReferenceEvaluator evaluator = new ReferenceEvaluator();
    private final CanonicalGraphJson codec = new CanonicalGraphJson();

    @Test
    void zeroAmplitudePreservesExactAcceptedSignalFreeArtifact() {
        SkyIslandVolumeDescriptor descriptor = descriptor(0L, 0.0);
        CompiledSkyIslandVolume expected = seededRecipe.compile(descriptor);
        CompiledSkyIslandVolume actual = recipe.compile(descriptor);

        assertEquals(expected, actual);
    }

    @Test
    void structuredCompilationPreservesUndersideAndRecordsProvenance() {
        SkyIslandVolumeDescriptor descriptor = descriptor(42L, 1.0);
        CompiledSkyIslandVolume seeded = seededRecipe.compile(descriptor);
        CompiledSkyIslandVolume structured = recipe.compile(descriptor);

        assertAll(
                () -> assertEquals(
                        SecondaryMorphologySkyIslandVolumeRecipe.RECIPE_VERSION,
                        structured.recipeVersion()),
                () -> assertEquals(
                        CanonicalGraphJson.INTERSECTION_SCHEMA_VERSION,
                        structured.graphSchemaVersion()),
                () -> assertArrayEquals(
                        codec.write(seeded.undersideSurfaceGraph()),
                        codec.write(structured.undersideSurfaceGraph())),
                () -> assertTrue(structured.provenance().containsKey("secondary-morphology")),
                () -> assertEquals(5, structured.provenance().get("secondary-morphology").size()),
                () -> structured.upperSurfaceGraph()
                        .requireNode(new NodeId("secondary.main-ridge.basis")),
                () -> structured.upperSurfaceGraph()
                        .requireNode(new NodeId("secondary.spur.basis")),
                () -> structured.upperSurfaceGraph()
                        .requireNode(new NodeId("secondary.valley.basis")),
                () -> structured.upperSurfaceGraph()
                        .requireNode(new NodeId("secondary.upper-factor")));
    }

    @Test
    void upperOffsetFactorRemainsWithinAnalyticalEnvelope() {
        SkyIslandVolumeDescriptor descriptor = descriptor(123456789L, 1.0);
        ScalarField2 seeded = evaluator.field2(seededRecipe.compile(descriptor).upperSurfaceGraph());
        ScalarField2 structured = evaluator.field2(recipe.compile(descriptor).upperSurfaceGraph());
        double suspension = descriptor.suspensionElevation();

        for (int zIndex = 0; zIndex <= 24; zIndex++) {
            double z = -336.0 + 28.0 * zIndex;
            for (int xIndex = 0; xIndex <= 24; xIndex++) {
                double x = -336.0 + 28.0 * xIndex;
                Coordinate2 point = new Coordinate2(x, z);
                double baseOffset = seeded.sample(point) - suspension;
                double structuredOffset = structured.sample(point) - suspension;
                if (Math.abs(baseOffset) <= TOLERANCE) {
                    assertEquals(0.0, structuredOffset, TOLERANCE);
                    continue;
                }
                double ratio = structuredOffset / baseOffset;
                assertTrue(
                        ratio >= SecondaryMorphologySkyIslandVolumeRecipe.MINIMUM_UPPER_FACTOR
                                        - TOLERANCE
                                && ratio <= SecondaryMorphologySkyIslandVolumeRecipe.MAXIMUM_UPPER_FACTOR
                                        + TOLERANCE,
                        () -> "secondary upper factor outside analytical envelope: " + ratio);
            }
        }
    }

    @Test
    void structuredMorphologyPreservesExactRimAndSurfaceOrdering() {
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
    void densityRemainsExactIntersectionOfStructuredUpperAndAcceptedUnderside() {
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
    void sameSeedIsByteDeterministicAndDifferentSeedsChangeStructuredMorphology() {
        CompiledSkyIslandVolume first = recipe.compile(descriptor(0x534b59464f524745L, 1.0));
        CompiledSkyIslandVolume repeated = recipe.compile(descriptor(0x534b59464f524745L, 1.0));
        CompiledSkyIslandVolume different = recipe.compile(descriptor(Long.MAX_VALUE, 1.0));

        assertAll(
                () -> assertArrayEquals(
                        codec.write(first.upperSurfaceGraph()),
                        codec.write(repeated.upperSurfaceGraph())),
                () -> assertArrayEquals(
                        codec.write(first.densityGraph()), codec.write(repeated.densityGraph())),
                () -> assertFalse(Arrays.equals(
                        codec.write(first.upperSurfaceGraph()),
                        codec.write(different.upperSurfaceGraph()))));
    }

    @Test
    void structuredLayerProducesMaterialReliefBeyondSeededDetail() {
        SkyIslandVolumeDescriptor descriptor = descriptor(0x534b59464f524745L, 1.0);
        ScalarField2 seeded = evaluator.field2(seededRecipe.compile(descriptor).upperSurfaceGraph());
        ScalarField2 structured = evaluator.field2(recipe.compile(descriptor).upperSurfaceGraph());
        double maximumDifference = 0.0;

        for (int zIndex = 0; zIndex <= 32; zIndex++) {
            double z = -256.0 + 16.0 * zIndex;
            for (int xIndex = 0; xIndex <= 32; xIndex++) {
                double x = -256.0 + 16.0 * xIndex;
                Coordinate2 point = new Coordinate2(x, z);
                maximumDifference = Math.max(
                        maximumDifference,
                        Math.abs(structured.sample(point) - seeded.sample(point)));
            }
        }

        assertTrue(
                maximumDifference >= 8.0,
                "structured morphology should create visible multi-block relief; max="
                        + maximumDifference);
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
