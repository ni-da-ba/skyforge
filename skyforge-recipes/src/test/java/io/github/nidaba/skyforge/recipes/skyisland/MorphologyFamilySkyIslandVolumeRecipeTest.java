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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class MorphologyFamilySkyIslandVolumeRecipeTest {
    private static final double TOLERANCE = 1.0e-10;
    private static final double SUSPENSION = 256.0;

    private final MorphologyFamilySkyIslandVolumeRecipe recipe =
            new MorphologyFamilySkyIslandVolumeRecipe();
    private final ReferenceEvaluator evaluator = new ReferenceEvaluator();
    private final CanonicalGraphJson graphJson = new CanonicalGraphJson();

    @Test
    void rejectsLocalSignalEnrichmentDuringPrimaryFamilyProof() {
        SkyIslandVolumeDescriptor descriptor = descriptor(0L, 0.25);
        assertThrows(
                IllegalArgumentException.class,
                () -> recipe.compile(descriptor, MorphologyFamily.MASSIF));
    }

    @Test
    void everyFamilyPreservesTheSharedSignedFootprintEnvelope() {
        SkyIslandVolumeDescriptor descriptor = descriptor(0L, 0.0);
        for (MorphologyFamily family : MorphologyFamily.values()) {
            CompiledSkyIslandVolume compiled = recipe.compile(descriptor, family);
            ScalarField2 upper = evaluator.field2(compiled.upperSurfaceGraph());
            ScalarField2 underside = evaluator.field2(compiled.undersideSurfaceGraph());

            for (int z = -384; z <= 384; z += 24) {
                for (int x = -384; x <= 384; x += 24) {
                    Coordinate2 point = new Coordinate2(x, z);
                    double upperOffset = upper.sample(point) - SUSPENSION;
                    double lowerOffset = underside.sample(point) - SUSPENSION;
                    assertEquals(
                            Math.signum(upperOffset),
                            -Math.signum(lowerOffset),
                            "upper and underside must share one exact family footprint for " + family);
                }
            }
        }
    }

    @Test
    void basinHasARealLowerCenterAndRaisedInteriorRing() {
        CompiledSkyIslandVolume compiled =
                recipe.compile(descriptor(0L, 0.0), MorphologyFamily.BASIN);
        ScalarField2 upper = evaluator.field2(compiled.upperSurfaceGraph());
        double center = upper.sample(new Coordinate2(0.0, 0.0));
        double maximumRing = Double.NEGATIVE_INFINITY;
        double radius = 112.0;
        for (int index = 0; index < 64; index++) {
            double angle = 2.0 * Math.PI * index / 64.0;
            maximumRing = Math.max(
                    maximumRing,
                    upper.sample(new Coordinate2(radius * Math.cos(angle), radius * Math.sin(angle))));
        }
        assertTrue(maximumRing >= center + 8.0, "basin ring must rise materially above its center");
    }

    @Test
    void tablelandRetainsMoreMidRadiusElevationThanMassif() {
        SkyIslandVolumeDescriptor descriptor = descriptor(0L, 0.0);
        ScalarField2 massif = evaluator.field2(
                recipe.compile(descriptor, MorphologyFamily.MASSIF).upperSurfaceGraph());
        ScalarField2 tableland = evaluator.field2(
                recipe.compile(descriptor, MorphologyFamily.TABLELAND).upperSurfaceGraph());
        double massifCenter = massif.sample(new Coordinate2(0.0, 0.0));
        double tablelandCenter = tableland.sample(new Coordinate2(0.0, 0.0));

        double massifBestRatio = maximumRingRatio(massif, massifCenter, 112.0);
        double tablelandBestRatio = maximumRingRatio(tableland, tablelandCenter, 112.0);
        assertTrue(
                tablelandBestRatio > massifBestRatio,
                "tableland must preserve a flatter elevated interior than massif");
    }

    @Test
    void allFiveFamiliesHaveDistinctPrimaryFootprintMasks() {
        SkyIslandVolumeDescriptor descriptor = descriptor(0L, 0.0);
        Set<String> hashes = new HashSet<>();
        for (MorphologyFamily family : MorphologyFamily.values()) {
            ScalarField2 upper = evaluator.field2(recipe.compile(descriptor, family).upperSurfaceGraph());
            StringBuilder mask = new StringBuilder();
            for (int z = -384; z <= 384; z += 12) {
                for (int x = -384; x <= 384; x += 12) {
                    mask.append(upper.sample(new Coordinate2(x, z)) > SUSPENSION ? '1' : '0');
                }
            }
            hashes.add(sha256(mask.toString()));
        }
        assertEquals(MorphologyFamily.values().length, hashes.size());
    }

    @Test
    void familyCompilationIsByteDeterministicAndSeedSensitive() {
        SkyIslandVolumeDescriptor zero = descriptor(0L, 0.0);
        CompiledSkyIslandVolume first = recipe.compile(zero, MorphologyFamily.LOBED);
        CompiledSkyIslandVolume second = recipe.compile(zero, MorphologyFamily.LOBED);
        CompiledSkyIslandVolume differentSeed =
                recipe.compile(descriptor(1L, 0.0), MorphologyFamily.LOBED);

        assertEquals(
                graphJson.writeString(first.densityGraph()),
                graphJson.writeString(second.densityGraph()));
        assertNotEquals(
                graphJson.writeString(first.densityGraph()),
                graphJson.writeString(differentSeed.densityGraph()));
    }

    @Test
    void densityIsExactIntersectionOfFamilyUpperAndUnderside() {
        SkyIslandVolumeDescriptor descriptor = descriptor(0L, 0.0);
        for (MorphologyFamily family : MorphologyFamily.values()) {
            CompiledSkyIslandVolume compiled = recipe.compile(descriptor, family);
            ScalarField2 upper = evaluator.field2(compiled.upperSurfaceGraph());
            ScalarField2 underside = evaluator.field2(compiled.undersideSurfaceGraph());
            ScalarField3 density = evaluator.field3(compiled.densityGraph());

            for (double x : new double[] {-300.0, -128.0, 0.0, 96.0, 280.0}) {
                for (double z : new double[] {-240.0, -64.0, 0.0, 112.0, 240.0}) {
                    for (double y : new double[] {96.0, 192.0, 256.0, 320.0, 400.0}) {
                        Coordinate2 horizontal = new Coordinate2(x, z);
                        double expected = Math.min(
                                upper.sample(horizontal) - y,
                                y - underside.sample(horizontal));
                        assertEquals(
                                Double.doubleToRawLongBits(expected),
                                Double.doubleToRawLongBits(density.sample(new Coordinate3(x, y, z))),
                                "density intersection drift for " + family);
                    }
                }
            }
        }
    }

    private static double maximumRingRatio(
            ScalarField2 upper, double centerElevation, double radius) {
        double centerOffset = centerElevation - SUSPENSION;
        double maximum = Double.NEGATIVE_INFINITY;
        for (int index = 0; index < 64; index++) {
            double angle = 2.0 * Math.PI * index / 64.0;
            double sample = upper.sample(
                    new Coordinate2(radius * Math.cos(angle), radius * Math.sin(angle)));
            maximum = Math.max(maximum, (sample - SUSPENSION) / centerOffset);
        }
        return maximum;
    }

    private static SkyIslandVolumeDescriptor descriptor(long seed, double signalAmplitude) {
        return new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION,
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
                signalAmplitude,
                32.0);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
