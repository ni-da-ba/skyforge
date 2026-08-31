package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate2;
import io.github.nidaba.skyforge.kernel.evaluation.ReferenceEvaluator;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.EnrichedProviderMorphologySkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyFamily;
import io.github.nidaba.skyforge.recipes.skyisland.ProviderMorphologyEnrichment;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviders;
import org.junit.jupiter.api.Test;

final class SkyIslandTerrainInterpreterTest {
    private static final SkyIslandTerrainProfile PROFILE = SkyIslandTerrainProfile.reference();

    @Test
    void invalidProfilesFailEarly() {
        assertThrows(IllegalArgumentException.class,
                () -> new SkyIslandTerrainProfile(-1.0, 0.0, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new SkyIslandTerrainProfile(12.0, 16.0, 28.0, 10.0));
    }

    @Test
    void compiledVolumeClassifiesAirShellsShallowInteriorAndDeepMass() {
        var volume = compiledMassif();
        var interpreter = new SkyIslandTerrainInterpreter(volume, PROFILE);
        var evaluator = new ReferenceEvaluator();
        var upperField = evaluator.field2(volume.upperSurfaceGraph());
        var undersideField = evaluator.field2(volume.undersideSurfaceGraph());

        Coordinate2 center = new Coordinate2(0.0, 0.0);
        double upper = upperField.sample(center);
        double underside = undersideField.sample(center);
        assertTrue(upper - underside > 2.0 * PROFILE.shallowInteriorDepth());

        assertEquals(SkyIslandTerrainSemantic.AIR, interpreter.classify(0.0, upper + 1.0, 0.0));
        assertEquals(SkyIslandTerrainSemantic.AIR, interpreter.classify(0.0, upper, 0.0));
        assertEquals(
                SkyIslandTerrainSemantic.SURFACE_MANTLE,
                interpreter.classify(0.0, upper - 6.0, 0.0));
        assertEquals(
                SkyIslandTerrainSemantic.SHALLOW_INTERIOR,
                interpreter.classify(0.0, upper - 24.0, 0.0));
        assertEquals(
                SkyIslandTerrainSemantic.DEEP_MASS,
                interpreter.classify(0.0, 0.5 * (upper + underside), 0.0));
        assertEquals(
                SkyIslandTerrainSemantic.UNDERSIDE_SHELL,
                interpreter.classify(0.0, underside + 8.0, 0.0));
        assertEquals(SkyIslandTerrainSemantic.AIR, interpreter.classify(0.0, underside, 0.0));

        double edgeX = findThinPositiveColumn(upperField, undersideField);
        Coordinate2 edge = new Coordinate2(edgeX, 0.0);
        double edgeUpper = upperField.sample(edge);
        double edgeUnderside = undersideField.sample(edge);
        assertTrue(edgeUpper > edgeUnderside);
        assertTrue(edgeUpper - edgeUnderside <= PROFILE.edgeMaximumColumnThickness());
        assertEquals(
                SkyIslandTerrainSemantic.EDGE_SHELL,
                interpreter.classify(edgeX, 0.5 * (edgeUpper + edgeUnderside), 0.0));
    }

    private static double findThinPositiveColumn(
            io.github.nidaba.skyforge.kernel.field.ScalarField2 upper,
            io.github.nidaba.skyforge.kernel.field.ScalarField2 underside) {
        for (double x = 0.0; x <= 320.0; x += 1.0) {
            Coordinate2 point = new Coordinate2(x, 0.0);
            double thickness = upper.sample(point) - underside.sample(point);
            if (thickness > 0.0 && thickness <= PROFILE.edgeMaximumColumnThickness()) {
                return x;
            }
        }
        throw new AssertionError("reference Massif exposed no thin positive edge column");
    }

    private static io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume compiledMassif() {
        SkyIslandVolumeDescriptor descriptor = new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                0x534b59464f524745L,
                0.0,
                0.0,
                320.0,
                192.0,
                76.0,
                100.0,
                48.0,
                Math.PI / 6.0,
                0.65,
                0.60,
                0.25,
                0.0,
                28.0);
        var provider = SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF);
        return new EnrichedProviderMorphologySkyIslandVolumeRecipe().compile(
                descriptor,
                new ProviderMorphologyEnrichment(provider, 0.0, 0.0),
                SkyIslandMorphologyProviders.builtInRegistry());
    }
}
