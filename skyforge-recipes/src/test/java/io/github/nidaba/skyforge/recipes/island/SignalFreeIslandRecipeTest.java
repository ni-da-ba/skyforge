package io.github.nidaba.skyforge.recipes.island;

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
import io.github.nidaba.skyforge.model.island.IslandDescriptor;
import java.util.ArrayDeque;
import org.junit.jupiter.api.Test;

final class SignalFreeIslandRecipeTest {
    private static final double TOLERANCE = 1.0e-10;

    private final SignalFreeIslandRecipe recipe = new SignalFreeIslandRecipe();
    private final ReferenceEvaluator evaluator = new ReferenceEvaluator();
    private final CanonicalGraphJson codec = new CanonicalGraphJson();

    @Test
    void recordsVersionsProvenanceAndInspectableGraphOutputs() {
        IslandDescriptor descriptor = descriptor(10.0, 80.0, 20.0, 0.0, 0.5);
        CompiledIsland compiled = recipe.compile(descriptor);

        assertAll(
                () -> assertEquals(descriptor, compiled.descriptor()),
                () -> assertEquals(SignalFreeIslandRecipe.RECIPE_VERSION, compiled.recipeVersion()),
                () -> assertEquals(CanonicalGraphJson.SCHEMA_VERSION, compiled.graphSchemaVersion()),
                () -> assertEquals(GraphValueType.SCALAR_FIELD_2, compiled.heightGraph().outputType()),
                () -> assertEquals(GraphValueType.SCALAR_FIELD_3, compiled.densityGraph().outputType()),
                () -> assertEquals(new NodeId("height"), compiled.heightGraph().output()),
                () -> assertEquals(new NodeId("density"), compiled.densityGraph().output()),
                () -> compiled.heightGraph().requireNode(new NodeId("descriptor.maximum-elevation")),
                () -> compiled.heightGraph().requireNode(new NodeId("ridge.along-axis")),
                () -> compiled.heightGraph().requireNode(new NodeId("coast.normalized-profile")));
    }

    @Test
    void createsOneClosedFiniteLandformWithinTheStandardBoundary() {
        IslandDescriptor descriptor = descriptor(10.0, 80.0, 20.0, 0.0, 0.0);
        ScalarField2 height = height(descriptor);
        int resolution = 65;
        int landSamples = 0;
        boolean[][] land = new boolean[resolution][resolution];

        for (int zIndex = 0; zIndex < resolution; zIndex++) {
            double z = descriptor.centerZ() - 1.5 * descriptor.nominalRadius()
                    + 3.0 * descriptor.nominalRadius() * zIndex / (resolution - 1);
            for (int xIndex = 0; xIndex < resolution; xIndex++) {
                double x = descriptor.centerX() - 1.5 * descriptor.nominalRadius()
                        + 3.0 * descriptor.nominalRadius() * xIndex / (resolution - 1);
                double value = height.sample(new Coordinate2(x, z));
                assertTrue(Double.isFinite(value));
                assertTrue(value <= descriptor.maximumElevation() + TOLERANCE);
                if (value > 0.0) {
                    land[zIndex][xIndex] = true;
                    landSamples++;
                    assertFalse(xIndex == 0 || zIndex == 0 || xIndex == resolution - 1 || zIndex == resolution - 1);
                }
            }
        }

        assertTrue(landSamples > 0);
        assertEquals(1, connectedLandComponents(land));
        assertEquals(
                descriptor.maximumElevation(),
                height.sample(new Coordinate2(descriptor.centerX(), descriptor.centerZ())),
                TOLERANCE);
    }

    @Test
    void radiusAndElevationHaveIndependentMonotonicEffects() {
        IslandDescriptor small = descriptor(10.0, 80.0, 20.0, 0.0, 0.0);
        IslandDescriptor large = descriptor(10.0, 120.0, 20.0, 0.0, 0.0);
        IslandDescriptor tall = descriptor(10.0, 80.0, 40.0, 0.0, 0.0);
        Coordinate2 center = new Coordinate2(10.0, -5.0);
        Coordinate2 betweenShorelines = new Coordinate2(105.0, -5.0);

        assertAll(
                () -> assertTrue(height(small).sample(betweenShorelines) < 0.0),
                () -> assertTrue(height(large).sample(betweenShorelines) > 0.0),
                () -> assertEquals(20.0, height(small).sample(center), TOLERANCE),
                () -> assertEquals(40.0, height(tall).sample(center), TOLERANCE),
                () -> assertEquals(
                        Math.signum(height(small).sample(betweenShorelines)),
                        Math.signum(height(tall).sample(betweenShorelines))));
    }

    @Test
    void coastalFalloffChangesCoastalProfileWithoutMovingTheShoreline() {
        IslandDescriptor steep = descriptor(10.0, 100.0, 20.0, 0.0, 0.0, 10.0);
        IslandDescriptor gentle = descriptor(10.0, 100.0, 20.0, 0.0, 0.0, 100.0);
        Coordinate2 nearCoast = new Coordinate2(90.0, -5.0);
        Coordinate2 shoreline = new Coordinate2(110.0, -5.0);

        assertAll(
                () -> assertTrue(height(steep).sample(nearCoast) > height(gentle).sample(nearCoast)),
                () -> assertEquals(0.0, height(steep).sample(shoreline), TOLERANCE),
                () -> assertEquals(0.0, height(gentle).sample(shoreline), TOLERANCE));
    }

    @Test
    void ridgeStrengthAndAzimuthControlThePrincipalAxis() {
        IslandDescriptor eastWest = descriptor(10.0, 100.0, 20.0, 0.0, 0.8);
        IslandDescriptor northSouth = descriptor(10.0, 100.0, 20.0, 0.5 * Math.PI, 0.8);
        Coordinate2 east = new Coordinate2(125.0, -5.0);
        Coordinate2 north = new Coordinate2(10.0, 110.0);

        assertAll(
                () -> assertTrue(height(eastWest).sample(east) > 0.0),
                () -> assertTrue(height(eastWest).sample(north) < 0.0),
                () -> assertTrue(height(northSouth).sample(east) < 0.0),
                () -> assertTrue(height(northSouth).sample(north) > 0.0));
    }

    @Test
    void densityIsExactlyTheCompiledHeightMinusY() {
        IslandDescriptor descriptor = descriptor(10.0, 80.0, 20.0, 0.25 * Math.PI, 0.5);
        CompiledIsland compiled = recipe.compile(descriptor);
        ScalarField2 height = evaluator.field2(compiled.heightGraph());
        ScalarField3 density = evaluator.field3(compiled.densityGraph());
        double x = 34.0;
        double z = -21.0;
        double y = 7.5;
        double expected = height.sample(new Coordinate2(x, z)) - y;

        assertEquals(expected, density.sample(new Coordinate3(x, y, z)));
    }

    @Test
    void compilationIsCanonicalAndIndependentOfUnusedSeedBits() {
        IslandDescriptor first = descriptor(Long.MIN_VALUE, 10.0, 80.0, 20.0, 0.25 * Math.PI, 0.5, 20.0);
        IslandDescriptor repeated = descriptor(Long.MAX_VALUE, 10.0, 80.0, 20.0, 0.25 * Math.PI, 0.5, 20.0);

        assertAll(
                () -> assertArrayEquals(
                        codec.write(recipe.compile(first).heightGraph()),
                        codec.write(recipe.compile(first).heightGraph())),
                () -> assertArrayEquals(
                        codec.write(recipe.compile(first).heightGraph()),
                        codec.write(recipe.compile(repeated).heightGraph())),
                () -> assertArrayEquals(
                        codec.write(recipe.compile(first).densityGraph()),
                        codec.write(recipe.compile(repeated).densityGraph())));
    }

    @Test
    void rejectsSignalRequestsAndUnrepresentableDerivedRadii() {
        IslandDescriptor signal = new IslandDescriptor(
                1, 1L, 0.0, 0.0, 80.0, 20.0, 20.0, 0.0, 0.0, 0.1, 20.0);
        IslandDescriptor overflow = new IslandDescriptor(
                1, 1L, 0.0, 0.0, Double.MAX_VALUE, 20.0, Double.MAX_VALUE, 0.0, 1.0, 0.0, 20.0);

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> recipe.compile(signal)),
                () -> assertThrows(IllegalArgumentException.class, () -> recipe.compile(overflow)));
    }

    private ScalarField2 height(IslandDescriptor descriptor) {
        return evaluator.field2(recipe.compile(descriptor).heightGraph());
    }

    private static int connectedLandComponents(boolean[][] land) {
        boolean[][] visited = new boolean[land.length][land[0].length];
        int components = 0;
        for (int z = 0; z < land.length; z++) {
            for (int x = 0; x < land[z].length; x++) {
                if (land[z][x] && !visited[z][x]) {
                    components++;
                    visitComponent(land, visited, x, z);
                }
            }
        }
        return components;
    }

    private static void visitComponent(boolean[][] land, boolean[][] visited, int startX, int startZ) {
        int[] xOffsets = {1, -1, 0, 0};
        int[] zOffsets = {0, 0, 1, -1};
        ArrayDeque<Integer> pending = new ArrayDeque<>();
        pending.add(startZ * land[0].length + startX);
        visited[startZ][startX] = true;
        while (!pending.isEmpty()) {
            int encoded = pending.removeFirst();
            int x = encoded % land[0].length;
            int z = encoded / land[0].length;
            for (int direction = 0; direction < xOffsets.length; direction++) {
                int nextX = x + xOffsets[direction];
                int nextZ = z + zOffsets[direction];
                if (nextZ >= 0
                        && nextZ < land.length
                        && nextX >= 0
                        && nextX < land[nextZ].length
                        && land[nextZ][nextX]
                        && !visited[nextZ][nextX]) {
                    visited[nextZ][nextX] = true;
                    pending.add(nextZ * land[0].length + nextX);
                }
            }
        }
    }

    private static IslandDescriptor descriptor(
            double centerX,
            double radius,
            double elevation,
            double azimuth,
            double ridgeStrength) {
        return descriptor(1L, centerX, radius, elevation, azimuth, ridgeStrength, 20.0);
    }

    private static IslandDescriptor descriptor(
            double centerX,
            double radius,
            double elevation,
            double azimuth,
            double ridgeStrength,
            double falloff) {
        return descriptor(1L, centerX, radius, elevation, azimuth, ridgeStrength, falloff);
    }

    private static IslandDescriptor descriptor(
            long seed,
            double centerX,
            double radius,
            double elevation,
            double azimuth,
            double ridgeStrength,
            double falloff) {
        return new IslandDescriptor(
                1, seed, centerX, -5.0, radius, elevation, falloff, azimuth, ridgeStrength, 0.0, 20.0);
    }
}
