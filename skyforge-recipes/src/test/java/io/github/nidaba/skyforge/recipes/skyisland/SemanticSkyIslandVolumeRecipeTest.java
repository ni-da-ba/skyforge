package io.github.nidaba.skyforge.recipes.skyisland;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate2;
import io.github.nidaba.skyforge.kernel.evaluation.ReferenceEvaluator;
import io.github.nidaba.skyforge.kernel.field.ScalarField2;
import io.github.nidaba.skyforge.kernel.graph.ConstantNode;
import io.github.nidaba.skyforge.kernel.graph.NodeId;
import io.github.nidaba.skyforge.kernel.serialization.CanonicalGraphJson;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandMorphologyFamily;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SemanticSkyIslandVolumeRecipeTest {
    private static final double TOLERANCE = 1.0e-10;

    private final SemanticSkyIslandVolumeRecipe recipe = new SemanticSkyIslandVolumeRecipe();
    private final FamilyAwareMorphologySkyIslandVolumeRecipe acceptedRecipe =
            new FamilyAwareMorphologySkyIslandVolumeRecipe();
    private final MorphologyFamilySkyIslandVolumeRecipe primaryRecipe =
            new MorphologyFamilySkyIslandVolumeRecipe();
    private final ReferenceEvaluator evaluator = new ReferenceEvaluator();
    private final CanonicalGraphJson codec = new CanonicalGraphJson();

    @Test
    void schemaOneIsRejectedByDescriptorDrivenRecipe() {
        assertThrows(
                IllegalArgumentException.class,
                () -> recipe.compile(legacyDescriptor(0L, 1.0)));
    }

    @Test
    void fullAmplitudesAreGraphIdenticalToAcceptedFamilyAwareRecipe() {
        for (SkyIslandMorphologyFamily semanticFamily : SkyIslandMorphologyFamily.values()) {
            MorphologyFamily family = MorphologyFamily.fromSemantic(semanticFamily);
            CompiledSkyIslandVolume expected = acceptedRecipe.compile(
                    legacyDescriptor(0x534b59464f524745L, 1.0), family);
            CompiledSkyIslandVolume actual = recipe.compile(
                    semanticDescriptor(0x534b59464f524745L, semanticFamily, 1.0, 1.0));

            assertAll(
                    () -> assertArrayEquals(
                            codec.write(expected.upperSurfaceGraph()),
                            codec.write(actual.upperSurfaceGraph()),
                            semanticFamily.identifier() + " upper"),
                    () -> assertArrayEquals(
                            codec.write(expected.undersideSurfaceGraph()),
                            codec.write(actual.undersideSurfaceGraph()),
                            semanticFamily.identifier() + " underside"),
                    () -> assertArrayEquals(
                            codec.write(expected.densityGraph()),
                            codec.write(actual.densityGraph()),
                            semanticFamily.identifier() + " density"),
                    () -> assertEquals(SemanticSkyIslandVolumeRecipe.RECIPE_VERSION, actual.recipeVersion()),
                    () -> assertEquals(semanticFamily, actual.descriptor().morphologyFamily()),
                    () -> assertTrue(actual.provenance().containsKey(
                            "semantic-morphology-family:" + semanticFamily.identifier())));
        }
    }

    @Test
    void zeroDetailStillAllowsSecondaryMorphologyWithoutUndersideDetail() {
        for (SkyIslandMorphologyFamily semanticFamily : SkyIslandMorphologyFamily.values()) {
            MorphologyFamily family = MorphologyFamily.fromSemantic(semanticFamily);
            SkyIslandVolumeDescriptor semantic = semanticDescriptor(7L, semanticFamily, 0.0, 1.0);
            CompiledSkyIslandVolume actual = recipe.compile(semantic);
            CompiledSkyIslandVolume primary = primaryRecipe.compile(legacyDescriptor(7L, 0.0), family);
            ScalarField2 actualUnder = evaluator.field2(actual.undersideSurfaceGraph());
            ScalarField2 primaryUnder = evaluator.field2(primary.undersideSurfaceGraph());

            for (Coordinate2 point : samplePoints()) {
                assertEquals(
                        primaryUnder.sample(point),
                        actualUnder.sample(point),
                        TOLERANCE,
                        semanticFamily.identifier() + " underside at " + point);
            }
            assertEquals(
                    0.0,
                    constant(actual, "descriptor.signal-amplitude.underside"),
                    TOLERANCE);
            assertEquals(
                    1.0,
                    constant(actual, secondaryAmplitudeNode(family)),
                    TOLERANCE);
        }
    }

    @Test
    void zeroSecondaryLeavesDetailEnabledAndRewritesOnlySecondaryAmplitude() {
        for (SkyIslandMorphologyFamily semanticFamily : SkyIslandMorphologyFamily.values()) {
            MorphologyFamily family = MorphologyFamily.fromSemantic(semanticFamily);
            CompiledSkyIslandVolume actual = recipe.compile(
                    semanticDescriptor(-1L, semanticFamily, 1.0, 0.0));
            CompiledSkyIslandVolume acceptedFull = acceptedRecipe.compile(
                    legacyDescriptor(-1L, 1.0), family);
            ScalarField2 actualUnder = evaluator.field2(actual.undersideSurfaceGraph());
            ScalarField2 acceptedUnder = evaluator.field2(acceptedFull.undersideSurfaceGraph());

            for (Coordinate2 point : samplePoints()) {
                assertEquals(
                        acceptedUnder.sample(point),
                        actualUnder.sample(point),
                        TOLERANCE,
                        semanticFamily.identifier() + " detailed underside at " + point);
            }
            assertEquals(1.0, constant(actual, "descriptor.signal-amplitude.upper"), TOLERANCE);
            assertEquals(1.0, constant(actual, "descriptor.signal-amplitude.underside"), TOLERANCE);
            assertEquals(0.0, constant(actual, secondaryAmplitudeNode(family)), TOLERANCE);
        }
    }

    @Test
    void independentSecondaryAmplitudeChangesUpperButNotUnderside() {
        SkyIslandVolumeDescriptor low = semanticDescriptor(
                123456789L, SkyIslandMorphologyFamily.BASIN, 0.6, 0.0);
        SkyIslandVolumeDescriptor high = semanticDescriptor(
                123456789L, SkyIslandMorphologyFamily.BASIN, 0.6, 1.0);
        CompiledSkyIslandVolume lowCompiled = recipe.compile(low);
        CompiledSkyIslandVolume highCompiled = recipe.compile(high);

        assertArrayEquals(
                codec.write(lowCompiled.undersideSurfaceGraph()),
                codec.write(highCompiled.undersideSurfaceGraph()));
        assertNotEquals(
                java.util.Arrays.hashCode(codec.write(lowCompiled.upperSurfaceGraph())),
                java.util.Arrays.hashCode(codec.write(highCompiled.upperSurfaceGraph())));
    }

    @Test
    void sameSchemaTwoDescriptorIsByteDeterministic() {
        SkyIslandVolumeDescriptor descriptor = semanticDescriptor(
                Long.MIN_VALUE, SkyIslandMorphologyFamily.LOBED, 0.35, 0.8);
        CompiledSkyIslandVolume first = recipe.compile(descriptor);
        CompiledSkyIslandVolume repeated = recipe.compile(descriptor);

        assertAll(
                () -> assertEquals(first.descriptor(), repeated.descriptor()),
                () -> assertArrayEquals(codec.write(first.upperSurfaceGraph()), codec.write(repeated.upperSurfaceGraph())),
                () -> assertArrayEquals(codec.write(first.undersideSurfaceGraph()), codec.write(repeated.undersideSurfaceGraph())),
                () -> assertArrayEquals(codec.write(first.densityGraph()), codec.write(repeated.densityGraph())));
    }

    private static String secondaryAmplitudeNode(MorphologyFamily family) {
        return family == MorphologyFamily.MASSIF
                ? "secondary.descriptor-amplitude"
                : "family-aware." + family.identifier() + ".descriptor-amplitude";
    }

    private static double constant(CompiledSkyIslandVolume compiled, String identifier) {
        NodeId id = new NodeId(identifier);
        ConstantNode node;
        try {
            node = (ConstantNode) compiled.upperSurfaceGraph().requireNode(id);
        } catch (IllegalArgumentException missingUpper) {
            node = (ConstantNode) compiled.undersideSurfaceGraph().requireNode(id);
        }
        return node.value();
    }

    private static List<Coordinate2> samplePoints() {
        return List.of(
                new Coordinate2(0.0, 0.0),
                new Coordinate2(72.0, -48.0),
                new Coordinate2(-128.0, 96.0),
                new Coordinate2(192.0, 24.0));
    }

    private static SkyIslandVolumeDescriptor legacyDescriptor(long seed, double amplitude) {
        return new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
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

    private static SkyIslandVolumeDescriptor semanticDescriptor(
            long seed,
            SkyIslandMorphologyFamily family,
            double detailAmplitude,
            double secondaryAmplitude) {
        return SkyIslandVolumeDescriptor.schema2(
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
                family,
                detailAmplitude,
                32.0,
                secondaryAmplitude);
    }
}
