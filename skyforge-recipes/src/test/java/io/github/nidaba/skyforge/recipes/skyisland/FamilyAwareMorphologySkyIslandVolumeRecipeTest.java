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
import io.github.nidaba.skyforge.kernel.serialization.CanonicalGraphJson;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import java.util.List;
import org.junit.jupiter.api.Test;

final class FamilyAwareMorphologySkyIslandVolumeRecipeTest {
    private static final double TOLERANCE = 1.0e-10;
    private static final double SUSPENSION = 256.0;

    private final FamilyAwareMorphologySkyIslandVolumeRecipe recipe =
            new FamilyAwareMorphologySkyIslandVolumeRecipe();
    private final MorphologyFamilySkyIslandVolumeRecipe primaryRecipe =
            new MorphologyFamilySkyIslandVolumeRecipe();
    private final ComposedMorphologySkyIslandVolumeRecipe genericRecipe =
            new ComposedMorphologySkyIslandVolumeRecipe();
    private final ReferenceEvaluator evaluator = new ReferenceEvaluator();
    private final CanonicalGraphJson codec = new CanonicalGraphJson();

    @Test
    void zeroAmplitudePreservesExactAcceptedPrimaryArtifacts() {
        for (MorphologyFamily family : MorphologyFamily.values()) {
            SkyIslandVolumeDescriptor descriptor = descriptor(42L, 0.0);
            CompiledSkyIslandVolume expected = primaryRecipe.compile(descriptor, family);
            CompiledSkyIslandVolume actual = recipe.compile(descriptor, family);
            assertEquals(expected, actual, family.identifier());
        }
    }

    @Test
    void massifRetainsGenericStructuredReliefAsControl() {
        SkyIslandVolumeDescriptor descriptor = descriptor(0x534b59464f524745L, 1.0);
        CompiledSkyIslandVolume generic = genericRecipe.compile(descriptor, MorphologyFamily.MASSIF);
        CompiledSkyIslandVolume aware = recipe.compile(descriptor, MorphologyFamily.MASSIF);
        ScalarField2 genericUpper = evaluator.field2(generic.upperSurfaceGraph());
        ScalarField2 awareUpper = evaluator.field2(aware.upperSurfaceGraph());
        ScalarField3 genericDensity = evaluator.field3(generic.densityGraph());
        ScalarField3 awareDensity = evaluator.field3(aware.densityGraph());

        for (Coordinate2 point : List.of(
                new Coordinate2(0.0, 0.0),
                new Coordinate2(80.0, -40.0),
                new Coordinate2(-120.0, 96.0),
                new Coordinate2(220.0, 20.0))) {
            assertEquals(genericUpper.sample(point), awareUpper.sample(point), TOLERANCE);
        }
        for (Coordinate3 point : List.of(
                new Coordinate3(0.0, 256.0, 0.0),
                new Coordinate3(80.0, 300.0, -40.0),
                new Coordinate3(-120.0, 210.0, 96.0))) {
            assertEquals(genericDensity.sample(point), awareDensity.sample(point), TOLERANCE);
        }
        assertArrayEquals(
                codec.write(generic.undersideSurfaceGraph()),
                codec.write(aware.undersideSurfaceGraph()));
    }

    @Test
    void everyFamilyFactorRemainsInsideItsPositiveAnalyticalEnvelope() {
        for (MorphologyFamily family : MorphologyFamily.values()) {
            CompiledSkyIslandVolume compiled = recipe.compile(descriptor(-1L, 1.0), family);
            NodeId factorId = family == MorphologyFamily.MASSIF
                    ? new NodeId("secondary.upper-factor")
                    : new NodeId("family-aware.upper-factor");
            ScalarField2 factor = evaluator.field2(compiled.upperSurfaceGraph(), factorId);
            double minimum = FamilyAwareMorphologySkyIslandVolumeRecipe.minimumUpperFactor(family);
            double maximum = FamilyAwareMorphologySkyIslandVolumeRecipe.maximumUpperFactor(family);
            for (int z = -320; z <= 320; z += 40) {
                for (int x = -320; x <= 320; x += 40) {
                    double value = factor.sample(new Coordinate2(x, z));
                    assertTrue(
                            value >= minimum - TOLERANCE && value <= maximum + TOLERANCE,
                            () -> family.identifier() + " factor=" + value
                                    + " outside [" + minimum + ", " + maximum + "]");
                }
            }
        }
    }

    @Test
    void tablelandAndBasinPreserveTheirPrimaryCenter() {
        SkyIslandVolumeDescriptor descriptor = descriptor(7L, 1.0);
        Coordinate2 center = new Coordinate2(descriptor.centerX(), descriptor.centerZ());
        for (MorphologyFamily family : List.of(MorphologyFamily.TABLELAND, MorphologyFamily.BASIN)) {
            CompiledSkyIslandVolume compiled = recipe.compile(descriptor, family);
            ScalarField2 factor = evaluator.field2(
                    compiled.upperSurfaceGraph(), new NodeId("family-aware.upper-factor"));
            assertEquals(1.0, factor.sample(center), TOLERANCE, family.identifier());
        }
    }

    @Test
    void selectedFamiliesExposeDifferentSecondaryVocabularies() {
        CompiledSkyIslandVolume tableland = recipe.compile(descriptor(9L, 1.0), MorphologyFamily.TABLELAND);
        CompiledSkyIslandVolume spine = recipe.compile(descriptor(9L, 1.0), MorphologyFamily.SPINE);
        CompiledSkyIslandVolume basin = recipe.compile(descriptor(9L, 1.0), MorphologyFamily.BASIN);
        CompiledSkyIslandVolume lobed = recipe.compile(descriptor(9L, 1.0), MorphologyFamily.LOBED);

        assertAll(
                () -> tableland.upperSurfaceGraph().requireNode(new NodeId("family-aware.tableland.outer-gate")),
                () -> spine.upperSurfaceGraph().requireNode(new NodeId("family-aware.spine.axial-ridge.basis")),
                () -> basin.upperSurfaceGraph().requireNode(new NodeId("family-aware.basin.ring.basis")),
                () -> lobed.upperSurfaceGraph().requireNode(new NodeId("family-aware.lobed.shoulders")),
                () -> assertNotEquals(
                        tableland.provenance().keySet(),
                        spine.provenance().keySet()));
    }

    @Test
    void fullCompositionPreservesPrimaryFootprintSignsAndAcceptedUnderside() {
        for (MorphologyFamily family : MorphologyFamily.values()) {
            SkyIslandVolumeDescriptor full = descriptor(Long.MIN_VALUE, 1.0);
            SkyIslandVolumeDescriptor zero = descriptor(Long.MIN_VALUE, 0.0);
            CompiledSkyIslandVolume primary = primaryRecipe.compile(zero, family);
            CompiledSkyIslandVolume aware = recipe.compile(full, family);
            CompiledSkyIslandVolume generic = genericRecipe.compile(full, family);
            ScalarField2 primaryUpper = evaluator.field2(primary.upperSurfaceGraph());
            ScalarField2 primaryUnder = evaluator.field2(primary.undersideSurfaceGraph());
            ScalarField2 awareUpper = evaluator.field2(aware.upperSurfaceGraph());
            ScalarField2 awareUnder = evaluator.field2(aware.undersideSurfaceGraph());

            assertArrayEquals(
                    codec.write(generic.undersideSurfaceGraph()),
                    codec.write(aware.undersideSurfaceGraph()),
                    family.identifier());
            for (int z = -320; z <= 320; z += 40) {
                for (int x = -320; x <= 320; x += 40) {
                    Coordinate2 point = new Coordinate2(x, z);
                    assertEquals(
                            sign(primaryUpper.sample(point) - SUSPENSION),
                            sign(awareUpper.sample(point) - SUSPENSION),
                            family.identifier() + " upper at " + point);
                    assertEquals(
                            sign(SUSPENSION - primaryUnder.sample(point)),
                            sign(SUSPENSION - awareUnder.sample(point)),
                            family.identifier() + " underside at " + point);
                }
            }
        }
    }

    @Test
    void densityRemainsExactIntersectionOfFamilyAwareSurfaces() {
        for (MorphologyFamily family : MorphologyFamily.values()) {
            CompiledSkyIslandVolume compiled = recipe.compile(descriptor(123456789L, 1.0), family);
            ScalarField2 upper = evaluator.field2(compiled.upperSurfaceGraph());
            ScalarField2 underside = evaluator.field2(compiled.undersideSurfaceGraph());
            ScalarField3 density = evaluator.field3(compiled.densityGraph());
            for (Coordinate3 point : List.of(
                    new Coordinate3(0.0, 256.0, 0.0),
                    new Coordinate3(96.0, 300.0, -72.0),
                    new Coordinate3(-144.0, 192.0, 64.0),
                    new Coordinate3(280.0, 256.0, 0.0))) {
                Coordinate2 horizontal = new Coordinate2(point.x(), point.z());
                double expected = Math.min(
                        upper.sample(horizontal) - point.y(),
                        point.y() - underside.sample(horizontal));
                assertEquals(expected, density.sample(point), TOLERANCE, family.identifier());
            }
        }
    }

    @Test
    void compilationIsByteDeterministicForEveryFamily() {
        for (MorphologyFamily family : MorphologyFamily.values()) {
            SkyIslandVolumeDescriptor descriptor = descriptor(0x534b59464f524745L, 1.0);
            CompiledSkyIslandVolume first = recipe.compile(descriptor, family);
            CompiledSkyIslandVolume repeated = recipe.compile(descriptor, family);
            assertAll(
                    () -> assertArrayEquals(codec.write(first.upperSurfaceGraph()), codec.write(repeated.upperSurfaceGraph())),
                    () -> assertArrayEquals(codec.write(first.undersideSurfaceGraph()), codec.write(repeated.undersideSurfaceGraph())),
                    () -> assertArrayEquals(codec.write(first.densityGraph()), codec.write(repeated.densityGraph())));
        }
    }

    private static int sign(double value) {
        if (Math.abs(value) <= TOLERANCE) {
            return 0;
        }
        return value > 0.0 ? 1 : -1;
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
