package io.github.nidaba.skyforge.recipes.skyisland;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate2;
import io.github.nidaba.skyforge.kernel.coordinate.Coordinate3;
import io.github.nidaba.skyforge.kernel.evaluation.ReferenceEvaluator;
import io.github.nidaba.skyforge.kernel.field.ScalarField2;
import io.github.nidaba.skyforge.kernel.field.ScalarField3;
import io.github.nidaba.skyforge.kernel.serialization.CanonicalGraphJson;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import org.junit.jupiter.api.Test;

final class HybridMorphologySkyIslandVolumeRecipeTest {
    private static final double SUSPENSION = 256.0;

    private final HybridMorphologySkyIslandVolumeRecipe recipe =
            new HybridMorphologySkyIslandVolumeRecipe();
    private final MorphologyFamilySkyIslandVolumeRecipe parentRecipe =
            new MorphologyFamilySkyIslandVolumeRecipe();
    private final ReferenceEvaluator evaluator = new ReferenceEvaluator();
    private final CanonicalGraphJson graphJson = new CanonicalGraphJson();

    @Test
    void blendSpecificationCanonicalizesPairOrderAndRejectsInvalidInputs() {
        MorphologyBlend forward = new MorphologyBlend(
                MorphologyFamily.MASSIF, MorphologyFamily.SPINE, 0.25);
        MorphologyBlend reverse = new MorphologyBlend(
                MorphologyFamily.SPINE, MorphologyFamily.MASSIF, 0.75);

        assertEquals(forward, reverse);
        assertEquals("massif+spine", forward.pairIdentifier());
        assertThrows(IllegalArgumentException.class, () -> new MorphologyBlend(
                MorphologyFamily.MASSIF, MorphologyFamily.MASSIF, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new MorphologyBlend(
                MorphologyFamily.MASSIF, MorphologyFamily.SPINE, -0.01));
        assertThrows(IllegalArgumentException.class, () -> new MorphologyBlend(
                MorphologyFamily.MASSIF, MorphologyFamily.SPINE, 1.01));
    }

    @Test
    void endpointWeightsPreserveAcceptedParentGraphBytes() {
        SkyIslandVolumeDescriptor descriptor = descriptor(0L);
        for (int first = 0; first < MorphologyFamily.values().length; first++) {
            for (int second = first + 1; second < MorphologyFamily.values().length; second++) {
                MorphologyFamily a = MorphologyFamily.values()[first];
                MorphologyFamily b = MorphologyFamily.values()[second];
                CompiledSkyIslandVolume parentA = parentRecipe.compile(descriptor, a);
                CompiledSkyIslandVolume parentB = parentRecipe.compile(descriptor, b);
                CompiledSkyIslandVolume endpointA = recipe.compile(descriptor, new MorphologyBlend(a, b, 0.0));
                CompiledSkyIslandVolume endpointB = recipe.compile(descriptor, new MorphologyBlend(a, b, 1.0));

                assertGraphBytes(parentA, endpointA, "first endpoint " + a + "/" + b);
                assertGraphBytes(parentB, endpointB, "second endpoint " + a + "/" + b);
            }
        }
    }

    @Test
    void midpointHybridsShareOneExactUpperAndUndersideFootprintSign() {
        SkyIslandVolumeDescriptor descriptor = descriptor(0L);
        for (MorphologyBlend blend : midpointPairs()) {
            CompiledSkyIslandVolume compiled = recipe.compile(descriptor, blend);
            ScalarField2 upper = evaluator.field2(compiled.upperSurfaceGraph());
            ScalarField2 underside = evaluator.field2(compiled.undersideSurfaceGraph());
            for (int z = -384; z <= 384; z += 24) {
                for (int x = -384; x <= 384; x += 24) {
                    Coordinate2 point = new Coordinate2(x, z);
                    double upperOffset = upper.sample(point) - SUSPENSION;
                    double undersideOffset = underside.sample(point) - SUSPENSION;
                    assertEquals(
                            Math.signum(upperOffset),
                            -Math.signum(undersideOffset),
                            "hybrid rim drift for " + blend.pairIdentifier());
                }
            }
        }
    }

    @Test
    void everyMidpointIsMateriallyDifferentFromBothParents() {
        SkyIslandVolumeDescriptor descriptor = descriptor(0L);
        for (MorphologyBlend blend : midpointPairs()) {
            ScalarField2 hybridUpper = evaluator.field2(recipe.compile(descriptor, blend).upperSurfaceGraph());
            ScalarField2 firstUpper = evaluator.field2(
                    parentRecipe.compile(descriptor, blend.first()).upperSurfaceGraph());
            ScalarField2 secondUpper = evaluator.field2(
                    parentRecipe.compile(descriptor, blend.second()).upperSurfaceGraph());

            boolean differsFromFirst = false;
            boolean differsFromSecond = false;
            for (int z = -320; z <= 320; z += 32) {
                for (int x = -320; x <= 320; x += 32) {
                    Coordinate2 point = new Coordinate2(x, z);
                    double hybrid = hybridUpper.sample(point);
                    differsFromFirst |= Math.abs(hybrid - firstUpper.sample(point)) > 1.0e-8;
                    differsFromSecond |= Math.abs(hybrid - secondUpper.sample(point)) > 1.0e-8;
                }
            }
            assertTrue(differsFromFirst, "midpoint collapsed to first parent for " + blend.pairIdentifier());
            assertTrue(differsFromSecond, "midpoint collapsed to second parent for " + blend.pairIdentifier());
        }
    }

    @Test
    void canonicalSymmetryAndRepeatedCompilationAreByteDeterministic() {
        SkyIslandVolumeDescriptor descriptor = descriptor(0x534b59464f524745L);
        MorphologyBlend forward = new MorphologyBlend(
                MorphologyFamily.LOBED, MorphologyFamily.SPINE, 0.35);
        MorphologyBlend reverse = new MorphologyBlend(
                MorphologyFamily.SPINE, MorphologyFamily.LOBED, 0.65);

        CompiledSkyIslandVolume first = recipe.compile(descriptor, forward);
        CompiledSkyIslandVolume repeated = recipe.compile(descriptor, forward);
        CompiledSkyIslandVolume reversed = recipe.compile(descriptor, reverse);
        assertEquals(graphJson.writeString(first.upperSurfaceGraph()), graphJson.writeString(repeated.upperSurfaceGraph()));
        assertEquals(graphJson.writeString(first.undersideSurfaceGraph()), graphJson.writeString(repeated.undersideSurfaceGraph()));
        assertEquals(graphJson.writeString(first.densityGraph()), graphJson.writeString(repeated.densityGraph()));
        assertEquals(graphJson.writeString(first.densityGraph()), graphJson.writeString(reversed.densityGraph()));
    }

    @Test
    void densityIsExactIntersectionOfHybridUpperAndUnderside() {
        SkyIslandVolumeDescriptor descriptor = descriptor(0L);
        for (MorphologyBlend blend : midpointPairs()) {
            CompiledSkyIslandVolume compiled = recipe.compile(descriptor, blend);
            ScalarField2 upper = evaluator.field2(compiled.upperSurfaceGraph());
            ScalarField2 underside = evaluator.field2(compiled.undersideSurfaceGraph());
            ScalarField3 density = evaluator.field3(compiled.densityGraph());
            for (double x : new double[] {-288.0, -128.0, 0.0, 112.0, 288.0}) {
                for (double z : new double[] {-240.0, -64.0, 0.0, 96.0, 240.0}) {
                    for (double y : new double[] {96.0, 192.0, 256.0, 320.0, 400.0}) {
                        Coordinate2 horizontal = new Coordinate2(x, z);
                        double expected = Math.min(
                                upper.sample(horizontal) - y,
                                y - underside.sample(horizontal));
                        assertEquals(
                                Double.doubleToRawLongBits(expected),
                                Double.doubleToRawLongBits(density.sample(new Coordinate3(x, y, z))),
                                "density drift for " + blend.pairIdentifier());
                    }
                }
            }
        }
    }

    @Test
    void rejectsEnrichedDescriptorsDuringPrimaryHybridProof() {
        SkyIslandVolumeDescriptor enriched = new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                0L, 0.0, 0.0, SUSPENSION, 256.0, 96.0, 128.0, 64.0,
                Math.PI / 6.0, 0.65, 0.60, 0.25, 0.1, 32.0);
        assertThrows(IllegalArgumentException.class, () -> recipe.compile(
                enriched,
                new MorphologyBlend(MorphologyFamily.MASSIF, MorphologyFamily.SPINE, 0.5)));
    }

    private void assertGraphBytes(
            CompiledSkyIslandVolume expected,
            CompiledSkyIslandVolume actual,
            String message) {
        assertEquals(
                graphJson.writeString(expected.upperSurfaceGraph()),
                graphJson.writeString(actual.upperSurfaceGraph()),
                message + " upper");
        assertEquals(
                graphJson.writeString(expected.undersideSurfaceGraph()),
                graphJson.writeString(actual.undersideSurfaceGraph()),
                message + " underside");
        assertEquals(
                graphJson.writeString(expected.densityGraph()),
                graphJson.writeString(actual.densityGraph()),
                message + " density");
        assertNotEquals(expected.recipeVersion(), actual.recipeVersion());
    }

    private static MorphologyBlend[] midpointPairs() {
        MorphologyFamily[] families = MorphologyFamily.values();
        MorphologyBlend[] result = new MorphologyBlend[families.length * (families.length - 1) / 2];
        int index = 0;
        for (int first = 0; first < families.length; first++) {
            for (int second = first + 1; second < families.length; second++) {
                result[index++] = new MorphologyBlend(families[first], families[second], 0.5);
            }
        }
        return result;
    }

    private static SkyIslandVolumeDescriptor descriptor(long seed) {
        return new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                seed,
                0.0,
                0.0,
                SUSPENSION,
                256.0,
                96.0,
                128.0,
                64.0,
                Math.PI / 6.0,
                0.65,
                0.60,
                0.25,
                0.0,
                32.0);
    }
}
