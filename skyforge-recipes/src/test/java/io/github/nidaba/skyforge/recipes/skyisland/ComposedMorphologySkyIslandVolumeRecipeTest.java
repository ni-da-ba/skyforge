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
import io.github.nidaba.skyforge.kernel.graph.ProceduralGraph;
import io.github.nidaba.skyforge.kernel.serialization.CanonicalGraphJson;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ComposedMorphologySkyIslandVolumeRecipeTest {
    private static final double TOLERANCE = 1.0e-10;

    private final ComposedMorphologySkyIslandVolumeRecipe recipe =
            new ComposedMorphologySkyIslandVolumeRecipe();
    private final MorphologyFamilySkyIslandVolumeRecipe primaryRecipe =
            new MorphologyFamilySkyIslandVolumeRecipe();
    private final ReferenceEvaluator evaluator = new ReferenceEvaluator();
    private final CanonicalGraphJson codec = new CanonicalGraphJson();

    @Test
    void genericCompositionIsExactlyCompatibleWithAcceptedLegacyStructuredRecipe() {
        SkyIslandVolumeDescriptor target = descriptor(0x534b59464f524745L, 1.0);
        SkyIslandVolumeDescriptor primaryDescriptor = descriptor(target.seed(), 0.0);
        CompiledSkyIslandVolume base = new SignalFreeSkyIslandVolumeRecipe().compile(primaryDescriptor);
        CompiledSkyIslandVolume expected = new SecondaryMorphologySkyIslandVolumeRecipe().compile(target);
        CompiledSkyIslandVolume actual = SuspendedVolumeEnrichmentComposition.apply(
                base, target, SecondaryMorphologySkyIslandVolumeRecipe.RECIPE_VERSION);

        assertEquals(expected, actual);
    }

    @Test
    void zeroAmplitudePreservesExactPrimaryFamilyArtifacts() {
        SkyIslandVolumeDescriptor descriptor = descriptor(42L, 0.0);
        for (MorphologyFamily family : MorphologyFamily.values()) {
            CompiledSkyIslandVolume expected = primaryRecipe.compile(descriptor, family);
            CompiledSkyIslandVolume actual = recipe.compile(descriptor, family);
            assertEquals(expected, actual, family::identifier);
        }
    }

    @Test
    void fullCompositionPreservesPrimaryFootprintAcrossEveryFamily() {
        SkyIslandVolumeDescriptor target = descriptor(-1L, 1.0);
        SkyIslandVolumeDescriptor primaryDescriptor = descriptor(-1L, 0.0);
        for (MorphologyFamily family : MorphologyFamily.values()) {
            CompiledSkyIslandVolume primary = primaryRecipe.compile(primaryDescriptor, family);
            CompiledSkyIslandVolume composed = recipe.compile(target, family);
            ScalarField2 primaryUpper = evaluator.field2(primary.upperSurfaceGraph());
            ScalarField2 primaryUnder = evaluator.field2(primary.undersideSurfaceGraph());
            ScalarField2 composedUpper = evaluator.field2(composed.upperSurfaceGraph());
            ScalarField2 composedUnder = evaluator.field2(composed.undersideSurfaceGraph());

            for (int zIndex = 0; zIndex <= 24; zIndex++) {
                double z = -360.0 + 30.0 * zIndex;
                for (int xIndex = 0; xIndex <= 24; xIndex++) {
                    double x = -360.0 + 30.0 * xIndex;
                    Coordinate2 point = new Coordinate2(x, z);
                    double primaryThickness = primaryUpper.sample(point) - primaryUnder.sample(point);
                    double composedThickness = composedUpper.sample(point) - composedUnder.sample(point);
                    assertEquals(
                            sign(primaryThickness),
                            sign(composedThickness),
                            () -> family.identifier() + " changed footprint sign at " + point);
                }
            }
        }
    }

    @Test
    void acceptedModifierFactorsRemainWithinTheirAnalyticalBoundsOnEveryFamily() {
        SkyIslandVolumeDescriptor descriptor = descriptor(123456789L, 1.0);
        for (MorphologyFamily family : MorphologyFamily.values()) {
            CompiledSkyIslandVolume compiled = recipe.compile(descriptor, family);
            ScalarField2 upperSignal = evaluator.field2(subgraph(
                    compiled.upperSurfaceGraph(), "signal.upper.factor"));
            ScalarField2 undersideSignal = evaluator.field2(subgraph(
                    compiled.undersideSurfaceGraph(), "signal.underside.factor"));
            ScalarField2 secondary = evaluator.field2(subgraph(
                    compiled.upperSurfaceGraph(), "secondary.upper-factor"));

            for (int zIndex = 0; zIndex <= 12; zIndex++) {
                double z = -300.0 + 50.0 * zIndex;
                for (int xIndex = 0; xIndex <= 12; xIndex++) {
                    Coordinate2 point = new Coordinate2(-300.0 + 50.0 * xIndex, z);
                    assertBetween(upperSignal.sample(point), 0.85, 1.15, family, "upper signal");
                    assertBetween(undersideSignal.sample(point), 0.85, 1.15, family, "underside signal");
                    assertBetween(
                            secondary.sample(point),
                            SecondaryMorphologySkyIslandVolumeRecipe.MINIMUM_UPPER_FACTOR,
                            SecondaryMorphologySkyIslandVolumeRecipe.MAXIMUM_UPPER_FACTOR,
                            family,
                            "secondary factor");
                }
            }
        }
    }

    @Test
    void densityRemainsExactIntersectionOfComposedSurfacesForEveryFamily() {
        SkyIslandVolumeDescriptor descriptor = descriptor(Long.MIN_VALUE, 1.0);
        List<Coordinate3> samples = List.of(
                new Coordinate3(0.0, 256.0, 0.0),
                new Coordinate3(96.0, 300.0, -72.0),
                new Coordinate3(-144.0, 192.0, 64.0),
                new Coordinate3(280.0, 256.0, 0.0));
        for (MorphologyFamily family : MorphologyFamily.values()) {
            CompiledSkyIslandVolume compiled = recipe.compile(descriptor, family);
            ScalarField2 upper = evaluator.field2(compiled.upperSurfaceGraph());
            ScalarField2 underside = evaluator.field2(compiled.undersideSurfaceGraph());
            ScalarField3 density = evaluator.field3(compiled.densityGraph());
            for (Coordinate3 point : samples) {
                Coordinate2 horizontal = new Coordinate2(point.x(), point.z());
                double expected = Math.min(
                        upper.sample(horizontal) - point.y(),
                        point.y() - underside.sample(horizontal));
                assertEquals(expected, density.sample(point), TOLERANCE, family::identifier);
            }
        }
    }

    @Test
    void fullCompositionRetainsFiveDistinctFamilyMasksForSharedSeed() {
        SkyIslandVolumeDescriptor descriptor = descriptor(0x534b59464f524745L, 1.0);
        Set<String> masks = new HashSet<>();
        for (MorphologyFamily family : MorphologyFamily.values()) {
            CompiledSkyIslandVolume compiled = recipe.compile(descriptor, family);
            ScalarField2 upper = evaluator.field2(compiled.upperSurfaceGraph());
            ScalarField2 underside = evaluator.field2(compiled.undersideSurfaceGraph());
            StringBuilder mask = new StringBuilder();
            for (int zIndex = 0; zIndex <= 48; zIndex++) {
                double z = -360.0 + 15.0 * zIndex;
                for (int xIndex = 0; xIndex <= 48; xIndex++) {
                    double x = -360.0 + 15.0 * xIndex;
                    Coordinate2 point = new Coordinate2(x, z);
                    mask.append(upper.sample(point) > underside.sample(point) ? '1' : '0');
                }
            }
            assertTrue(masks.add(mask.toString()), () -> "duplicate composed mask: " + family.identifier());
        }
        assertEquals(MorphologyFamily.values().length, masks.size());
    }

    @Test
    void sameSeedAndFamilyAreByteDeterministicAndDifferentFamiliesRemainDifferent() {
        SkyIslandVolumeDescriptor descriptor = descriptor(7L, 1.0);
        CompiledSkyIslandVolume massif = recipe.compile(descriptor, MorphologyFamily.MASSIF);
        CompiledSkyIslandVolume repeated = recipe.compile(descriptor, MorphologyFamily.MASSIF);
        CompiledSkyIslandVolume spine = recipe.compile(descriptor, MorphologyFamily.SPINE);

        assertAll(
                () -> assertEquals(ComposedMorphologySkyIslandVolumeRecipe.RECIPE_VERSION, massif.recipeVersion()),
                () -> assertArrayEquals(bytes(massif.upperSurfaceGraph()), bytes(repeated.upperSurfaceGraph())),
                () -> assertArrayEquals(bytes(massif.undersideSurfaceGraph()), bytes(repeated.undersideSurfaceGraph())),
                () -> assertArrayEquals(bytes(massif.densityGraph()), bytes(repeated.densityGraph())),
                () -> assertFalse(Arrays.equals(bytes(massif.densityGraph()), bytes(spine.densityGraph()))),
                () -> assertTrue(massif.provenance().containsKey("secondary-morphology")),
                () -> assertEquals(6, massif.provenance().get("signal-controls").size()));
    }

    private ProceduralGraph subgraph(ProceduralGraph graph, String output) {
        return new ProceduralGraph(graph.nodes(), new NodeId(output));
    }

    private byte[] bytes(ProceduralGraph graph) {
        return codec.write(graph);
    }

    private static int sign(double value) {
        if (Math.abs(value) <= TOLERANCE) {
            return 0;
        }
        return value > 0.0 ? 1 : -1;
    }

    private static void assertBetween(
            double value,
            double minimum,
            double maximum,
            MorphologyFamily family,
            String label) {
        assertTrue(
                value >= minimum - TOLERANCE && value <= maximum + TOLERANCE,
                () -> family.identifier() + " " + label + " outside [" + minimum + ", "
                        + maximum + "]: " + value);
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
