package io.github.nidaba.skyforge.recipes.island;

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
import io.github.nidaba.skyforge.model.island.IslandDescriptor;
import org.junit.jupiter.api.Test;

final class SeededIslandRecipeTest {
    private static final double TOLERANCE = 1.0e-12;

    private final SeededIslandRecipe recipe = new SeededIslandRecipe();
    private final SignalFreeIslandRecipe signalFreeRecipe = new SignalFreeIslandRecipe();
    private final ReferenceEvaluator evaluator = new ReferenceEvaluator();
    private final CanonicalGraphJson codec = new CanonicalGraphJson();

    @Test
    void zeroAmplitudeReturnsTheExactSignalFreeArtifact() {
        IslandDescriptor descriptor = descriptor(42L, 0.0);
        CompiledIsland expected = signalFreeRecipe.compile(descriptor);
        CompiledIsland actual = recipe.compile(descriptor);

        assertAll(
                () -> assertEquals(expected.recipeVersion(), actual.recipeVersion()),
                () -> assertEquals(expected.graphSchemaVersion(), actual.graphSchemaVersion()),
                () -> assertArrayEquals(codec.write(expected.heightGraph()), codec.write(actual.heightGraph())),
                () -> assertArrayEquals(codec.write(expected.densityGraph()), codec.write(actual.densityGraph())));
    }

    @Test
    void positiveAmplitudeProducesInspectableVersionedSignalGraphs() {
        IslandDescriptor descriptor = descriptor(Long.MIN_VALUE, 0.75);
        CompiledIsland compiled = recipe.compile(descriptor);
        PlanarValueSignalNode signal = (PlanarValueSignalNode)
                compiled.heightGraph().requireNode(new NodeId("signal.height-detail"));

        assertAll(
                () -> assertEquals(SeededIslandRecipe.RECIPE_VERSION, compiled.recipeVersion()),
                () -> assertEquals(CanonicalGraphJson.LATEST_SCHEMA_VERSION, compiled.graphSchemaVersion()),
                () -> assertEquals(new NodeId("height.seeded"), compiled.heightGraph().output()),
                () -> assertEquals(new NodeId("density"), compiled.densityGraph().output()),
                () -> assertEquals(descriptor.seed(), signal.rootSeed()),
                () -> assertEquals(SeededIslandRecipe.HEIGHT_SIGNAL_NAMESPACE, signal.namespace()),
                () -> assertEquals(descriptor.signalScale(), signal.scale()),
                () -> compiled.heightGraph().requireNode(new NodeId("descriptor.signal-amplitude")),
                () -> compiled.heightGraph().requireNode(new NodeId("signal.factor")));
    }

    @Test
    void signalModulatesHeightWithinTheDeclaredRelativeBoundWithoutChangingItsSign() {
        IslandDescriptor seededDescriptor = descriptor(91L, 1.0);
        IslandDescriptor baseDescriptor = descriptor(91L, 0.0);
        ScalarField2 seeded = evaluator.field2(recipe.compile(seededDescriptor).heightGraph());
        ScalarField2 base = evaluator.field2(signalFreeRecipe.compile(baseDescriptor).heightGraph());
        double maximumRelativeChange = SeededIslandRecipe.MAXIMUM_RELATIVE_DISPLACEMENT;
        boolean changed = false;

        for (int z = -96; z <= 96; z += 3) {
            for (int x = -96; x <= 96; x += 3) {
                Coordinate2 coordinate = new Coordinate2(x, z);
                double baseValue = base.sample(coordinate);
                double seededValue = seeded.sample(coordinate);
                assertEquals(Math.signum(baseValue), Math.signum(seededValue));
                if (baseValue != 0.0) {
                    assertTrue(Math.abs(seededValue / baseValue - 1.0) <= maximumRelativeChange + TOLERANCE);
                }
                changed |= Double.doubleToRawLongBits(baseValue)
                        != Double.doubleToRawLongBits(seededValue);
            }
        }
        assertTrue(changed);
    }

    @Test
    void seedChangesInteriorHeightsButNotTheDensitySurfaceContract() {
        CompiledIsland first = recipe.compile(descriptor(1L, 0.8));
        CompiledIsland second = recipe.compile(descriptor(2L, 0.8));
        ScalarField2 firstHeight = evaluator.field2(first.heightGraph());
        ScalarField2 secondHeight = evaluator.field2(second.heightGraph());
        ScalarField3 firstDensity = evaluator.field3(first.densityGraph());
        Coordinate2 horizontal = new Coordinate2(17.25, -31.5);
        double surface = firstHeight.sample(horizontal);

        assertAll(
                () -> assertNotEquals(
                        firstHeight.sample(horizontal), secondHeight.sample(horizontal)),
                () -> assertEquals(
                        0L,
                        Double.doubleToRawLongBits(
                                firstDensity.sample(new Coordinate3(horizontal.x(), surface, horizontal.z())))));
    }

    private static IslandDescriptor descriptor(long seed, double amplitude) {
        return new IslandDescriptor(
                IslandDescriptor.SCHEMA_VERSION,
                seed,
                0.0,
                0.0,
                80.0,
                30.0,
                20.0,
                Math.PI / 6.0,
                0.65,
                amplitude,
                16.0);
    }
}
