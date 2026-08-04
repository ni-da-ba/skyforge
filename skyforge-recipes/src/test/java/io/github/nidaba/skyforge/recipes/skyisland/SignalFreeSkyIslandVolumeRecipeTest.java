package io.github.nidaba.skyforge.recipes.skyisland;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate2;
import io.github.nidaba.skyforge.kernel.coordinate.Coordinate3;
import io.github.nidaba.skyforge.kernel.evaluation.ReferenceEvaluator;
import io.github.nidaba.skyforge.kernel.field.ScalarField2;
import io.github.nidaba.skyforge.kernel.field.ScalarField3;
import io.github.nidaba.skyforge.kernel.graph.GraphValueType;
import io.github.nidaba.skyforge.kernel.graph.NodeId;
import io.github.nidaba.skyforge.kernel.serialization.CanonicalGraphJson;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class SignalFreeSkyIslandVolumeRecipeTest {
    private static final double TOLERANCE = 1.0e-10;
    private static final Set<String> SEMANTIC_CONTROLS = Set.of(
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

    private final SignalFreeSkyIslandVolumeRecipe recipe =
            new SignalFreeSkyIslandVolumeRecipe();
    private final ReferenceEvaluator evaluator = new ReferenceEvaluator();
    private final CanonicalGraphJson codec = new CanonicalGraphJson();

    @Test
    void recordsVersionsGraphsAndSemanticProvenance() {
        SkyIslandVolumeDescriptor descriptor = descriptor();
        CompiledSkyIslandVolume compiled = recipe.compile(descriptor);

        assertAll(
                () -> assertEquals(descriptor, compiled.descriptor()),
                () -> assertEquals(
                        SignalFreeSkyIslandVolumeRecipe.RECIPE_VERSION,
                        compiled.recipeVersion()),
                () -> assertEquals(
                        CanonicalGraphJson.INTERSECTION_SCHEMA_VERSION,
                        compiled.graphSchemaVersion()),
                () -> assertEquals(
                        GraphValueType.SCALAR_FIELD_2,
                        compiled.upperSurfaceGraph().outputType()),
                () -> assertEquals(
                        GraphValueType.SCALAR_FIELD_2,
                        compiled.undersideSurfaceGraph().outputType()),
                () -> assertEquals(
                        GraphValueType.SCALAR_FIELD_3,
                        compiled.densityGraph().outputType()),
                () -> assertEquals(new NodeId("upper.surface"), compiled.upperSurfaceGraph().output()),
                () -> assertEquals(
                        new NodeId("underside.surface"),
                        compiled.undersideSurfaceGraph().output()),
                () -> assertEquals(
                        new NodeId("density.solid-intersection"),
                        compiled.densityGraph().output()),
                () -> assertEquals(SEMANTIC_CONTROLS, compiled.provenance().keySet()),
                () -> assertTrue(compiled.provenance().get("signal-controls").isEmpty()),
                () -> compiled.densityGraph().requireNode(
                        new NodeId("underside.asymmetry-factor")));
    }

    @Test
    void upperAndUndersideMeetAtTheRimAndOrderCorrectlyElsewhere() {
        SkyIslandVolumeDescriptor descriptor = descriptor();
        CompiledSkyIslandVolume compiled = recipe.compile(descriptor);
        ScalarField2 upper = evaluator.field2(compiled.upperSurfaceGraph());
        ScalarField2 underside = evaluator.field2(compiled.undersideSurfaceGraph());
        double stretch = 1.0
                + SignalFreeSkyIslandVolumeRecipe.MAXIMUM_RIDGE_STRETCH
                        * descriptor.ridgeStrength();
        double majorRadius = descriptor.nominalRadius() * stretch;
        Coordinate2 center = coordinateAlong(descriptor, 0.0);
        Coordinate2 interior = coordinateAlong(descriptor, 0.5 * majorRadius);
        Coordinate2 rim = coordinateAlong(descriptor, majorRadius);
        Coordinate2 exterior = coordinateAlong(descriptor, 1.25 * majorRadius);

        assertAll(
                () -> assertEquals(
                        descriptor.suspensionElevation() + descriptor.upperElevation(),
                        upper.sample(center),
                        TOLERANCE),
                () -> assertEquals(
                        descriptor.suspensionElevation() - descriptor.undersideDepth(),
                        underside.sample(center),
                        TOLERANCE),
                () -> assertTrue(upper.sample(interior) > underside.sample(interior)),
                () -> assertEquals(
                        descriptor.suspensionElevation(), upper.sample(rim), TOLERANCE),
                () -> assertEquals(
                        descriptor.suspensionElevation(), underside.sample(rim), TOLERANCE),
                () -> assertTrue(upper.sample(exterior) < underside.sample(exterior)));
    }

    @Test
    void densityIsExactlyTheIntersectionOfUpperAndLowerConstraints() {
        CompiledSkyIslandVolume compiled = recipe.compile(descriptor());
        ScalarField2 upper = evaluator.field2(compiled.upperSurfaceGraph());
        ScalarField2 underside = evaluator.field2(compiled.undersideSurfaceGraph());
        ScalarField3 density = evaluator.field3(compiled.densityGraph());
        Coordinate2 horizontal = new Coordinate2(64.0, -48.0);
        double y = 233.5;
        double expected = Math.min(
                upper.sample(horizontal) - y,
                y - underside.sample(horizontal));

        assertEquals(
                expected,
                density.sample(new Coordinate3(horizontal.x(), y, horizontal.z())));
    }

    @Test
    void canonicalSpecimenIsOneFiniteSuspendedMassAtDiagnosticResolution() {
        ScalarField3 density = evaluator.field3(recipe.compile(descriptor()).densityGraph());
        int xCount = 49;
        int yCount = 65;
        int zCount = 49;
        boolean[] solid = new boolean[xCount * yCount * zCount];
        int solidSamples = 0;
        int faceContacts = 0;

        for (int yIndex = 0; yIndex < yCount; yIndex++) {
            double y = 512.0 * yIndex / (yCount - 1);
            for (int zIndex = 0; zIndex < zCount; zIndex++) {
                double z = -384.0 + 768.0 * zIndex / (zCount - 1);
                for (int xIndex = 0; xIndex < xCount; xIndex++) {
                    double x = -384.0 + 768.0 * xIndex / (xCount - 1);
                    double value = density.sample(new Coordinate3(x, y, z));
                    assertTrue(Double.isFinite(value));
                    if (value > 0.0) {
                        solid[index(xIndex, yIndex, zIndex, xCount, zCount)] = true;
                        solidSamples++;
                        if (xIndex == 0
                                || xIndex == xCount - 1
                                || yIndex == 0
                                || yIndex == yCount - 1
                                || zIndex == 0
                                || zIndex == zCount - 1) {
                            faceContacts++;
                        }
                    }
                }
            }
        }

        int observedSolidSamples = solidSamples;
        int observedFaceContacts = faceContacts;
        assertAll(
                () -> assertTrue(observedSolidSamples > 0),
                () -> assertEquals(0, observedFaceContacts),
                () -> assertEquals(1, connectedComponents(solid, xCount, yCount, zCount)));
    }

    @Test
    void verticalControlsTranslateOrChangeOnlyTheirNamedSurface() {
        SkyIslandVolumeDescriptor base = descriptor();
        SkyIslandVolumeDescriptor raised = withVertical(base, 288.0, 96.0, 128.0);
        SkyIslandVolumeDescriptor taller = withVertical(base, 256.0, 120.0, 128.0);
        SkyIslandVolumeDescriptor deeper = withVertical(base, 256.0, 96.0, 160.0);
        Coordinate2 center = new Coordinate2(base.centerX(), base.centerZ());

        assertAll(
                () -> assertEquals(
                        upper(base).sample(center) + 32.0,
                        upper(raised).sample(center),
                        TOLERANCE),
                () -> assertEquals(
                        underside(base).sample(center) + 32.0,
                        underside(raised).sample(center),
                        TOLERANCE),
                () -> assertEquals(
                        upper(base).sample(center) + 24.0,
                        upper(taller).sample(center),
                        TOLERANCE),
                () -> assertEquals(
                        underside(base).sample(center),
                        underside(taller).sample(center),
                        TOLERANCE),
                () -> assertEquals(
                        upper(base).sample(center),
                        upper(deeper).sample(center),
                        TOLERANCE),
                () -> assertEquals(
                        underside(base).sample(center) - 32.0,
                        underside(deeper).sample(center),
                        TOLERANCE));
    }

    @Test
    void taperAndAsymmetryHavePredictableUndersideResponses() {
        SkyIslandVolumeDescriptor base = descriptor();
        SkyIslandVolumeDescriptor concentrated = withUnderside(base, 1.0, 0.0);
        SkyIslandVolumeDescriptor positive = withUnderside(base, 0.0, 0.75);
        SkyIslandVolumeDescriptor negative = withUnderside(base, 0.0, -0.75);
        double stretch = 1.0
                + SignalFreeSkyIslandVolumeRecipe.MAXIMUM_RIDGE_STRETCH
                        * base.ridgeStrength();
        double halfMajor = 0.5 * base.nominalRadius() * stretch;
        Coordinate2 positiveSide = coordinateAlong(base, halfMajor);
        Coordinate2 negativeSide = coordinateAlong(base, -halfMajor);

        assertAll(
                () -> assertTrue(
                        underside(concentrated).sample(positiveSide)
                                > underside(withUnderside(base, 0.0, 0.0)).sample(positiveSide)),
                () -> assertTrue(
                        underside(positive).sample(positiveSide)
                                < underside(positive).sample(negativeSide)),
                () -> assertTrue(
                        underside(negative).sample(negativeSide)
                                < underside(negative).sample(positiveSide)));
    }

    @Test
    void compilationIsCanonicalAndIgnoresInactiveSeedBits() {
        SkyIslandVolumeDescriptor first = descriptor(Long.MIN_VALUE, 0.0);
        SkyIslandVolumeDescriptor repeated = descriptor(Long.MAX_VALUE, 0.0);

        assertAll(
                () -> assertArrayEquals(
                        codec.write(recipe.compile(first).upperSurfaceGraph()),
                        codec.write(recipe.compile(repeated).upperSurfaceGraph())),
                () -> assertArrayEquals(
                        codec.write(recipe.compile(first).undersideSurfaceGraph()),
                        codec.write(recipe.compile(repeated).undersideSurfaceGraph())),
                () -> assertArrayEquals(
                        codec.write(recipe.compile(first).densityGraph()),
                        codec.write(recipe.compile(repeated).densityGraph())),
                () -> assertArrayEquals(
                        codec.write(recipe.compile(first).densityGraph()),
                        codec.write(codec.read(codec.write(recipe.compile(first).densityGraph())))));
    }

    @Test
    void rejectsSeededRequestsAndUnrepresentableDerivedRadii() {
        SkyIslandVolumeDescriptor seeded = descriptor(1L, 0.2);
        SkyIslandVolumeDescriptor overflow = new SkyIslandVolumeDescriptor(
                1,
                1L,
                0.0,
                0.0,
                256.0,
                Double.MAX_VALUE,
                96.0,
                128.0,
                64.0,
                0.0,
                1.0,
                0.5,
                0.0,
                0.0,
                32.0);

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> recipe.compile(seeded)),
                () -> assertThrows(IllegalArgumentException.class, () -> recipe.compile(overflow)));
    }

    private ScalarField2 upper(SkyIslandVolumeDescriptor descriptor) {
        return evaluator.field2(recipe.compile(descriptor).upperSurfaceGraph());
    }

    private ScalarField2 underside(SkyIslandVolumeDescriptor descriptor) {
        return evaluator.field2(recipe.compile(descriptor).undersideSurfaceGraph());
    }

    private static Coordinate2 coordinateAlong(
            SkyIslandVolumeDescriptor descriptor, double distance) {
        return new Coordinate2(
                descriptor.centerX() + distance * Math.cos(descriptor.ridgeAzimuth()),
                descriptor.centerZ() + distance * Math.sin(descriptor.ridgeAzimuth()));
    }

    private static int connectedComponents(
            boolean[] solid, int xCount, int yCount, int zCount) {
        boolean[] visited = new boolean[solid.length];
        int components = 0;
        for (int start = 0; start < solid.length; start++) {
            if (solid[start] && !visited[start]) {
                components++;
                visit(solid, visited, start, xCount, yCount, zCount);
            }
        }
        return components;
    }

    private static void visit(
            boolean[] solid,
            boolean[] visited,
            int start,
            int xCount,
            int yCount,
            int zCount) {
        ArrayDeque<Integer> pending = new ArrayDeque<>();
        pending.add(start);
        visited[start] = true;
        int layer = xCount * zCount;
        int[] offsets = {1, -1, xCount, -xCount, layer, -layer};
        while (!pending.isEmpty()) {
            int current = pending.removeFirst();
            int x = current % xCount;
            int z = current % layer / xCount;
            int y = current / layer;
            for (int direction = 0; direction < offsets.length; direction++) {
                if ((direction == 0 && x == xCount - 1)
                        || (direction == 1 && x == 0)
                        || (direction == 2 && z == zCount - 1)
                        || (direction == 3 && z == 0)
                        || (direction == 4 && y == yCount - 1)
                        || (direction == 5 && y == 0)) {
                    continue;
                }
                int next = current + offsets[direction];
                if (solid[next] && !visited[next]) {
                    visited[next] = true;
                    pending.add(next);
                }
            }
        }
    }

    private static int index(
            int x, int y, int z, int xCount, int zCount) {
        return y * xCount * zCount + z * xCount + x;
    }

    private static SkyIslandVolumeDescriptor descriptor() {
        return descriptor(0L, 0.0);
    }

    private static SkyIslandVolumeDescriptor descriptor(long seed, double signalAmplitude) {
        return new SkyIslandVolumeDescriptor(
                1,
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
                signalAmplitude,
                32.0);
    }

    private static SkyIslandVolumeDescriptor withVertical(
            SkyIslandVolumeDescriptor source,
            double suspension,
            double upper,
            double underside) {
        return copy(source, suspension, upper, underside, source.undersideTaper(), source.undersideAsymmetry());
    }

    private static SkyIslandVolumeDescriptor withUnderside(
            SkyIslandVolumeDescriptor source, double taper, double asymmetry) {
        return copy(
                source,
                source.suspensionElevation(),
                source.upperElevation(),
                source.undersideDepth(),
                taper,
                asymmetry);
    }

    private static SkyIslandVolumeDescriptor copy(
            SkyIslandVolumeDescriptor source,
            double suspension,
            double upper,
            double underside,
            double taper,
            double asymmetry) {
        return new SkyIslandVolumeDescriptor(
                source.schemaVersion(),
                source.seed(),
                source.centerX(),
                source.centerZ(),
                suspension,
                source.nominalRadius(),
                upper,
                underside,
                source.coastalFalloff(),
                source.ridgeAzimuth(),
                source.ridgeStrength(),
                taper,
                asymmetry,
                source.signalAmplitude(),
                source.signalScale());
    }
}
