package io.github.nidaba.skyforge.model.skyisland;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class SkyIslandVolumeDescriptorTest {
    @Test
    void acceptsEveryRootSeedAndCanonicalizesTheUnorientedRidgeAxis() {
        SkyIslandVolumeDescriptor minimumSeed = descriptor(Long.MIN_VALUE, -Math.PI / 4.0);
        SkyIslandVolumeDescriptor maximumSeed = descriptor(Long.MAX_VALUE, 9.0 * Math.PI / 4.0);

        assertAll(
                () -> assertEquals(3.0 * Math.PI / 4.0, minimumSeed.ridgeAzimuth()),
                () -> assertEquals(Math.PI / 4.0, maximumSeed.ridgeAzimuth()),
                () -> assertEquals(Long.MIN_VALUE, minimumSeed.seed()),
                () -> assertEquals(Long.MAX_VALUE, maximumSeed.seed()));
    }

    @Test
    void acceptsTheDeclaredClosedControlIntervals() {
        assertAll(
                () -> descriptorWithControls(0.0, 0.0, -1.0, 0.0),
                () -> descriptorWithControls(1.0, 1.0, 1.0, 1.0));
    }

    @Test
    void rejectsUnsupportedSchemaAndNonfiniteWorldControls() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new SkyIslandVolumeDescriptor(
                        2, 0L, 0.0, 0.0, 256.0, 256.0, 96.0, 128.0, 64.0, 0.0, 0.5, 0.5, 0.0, 0.0, 32.0)),
                () -> assertThrows(IllegalArgumentException.class, () -> new SkyIslandVolumeDescriptor(
                        1, 0L, Double.NaN, 0.0, 256.0, 256.0, 96.0, 128.0, 64.0, 0.0, 0.5, 0.5, 0.0, 0.0, 32.0)),
                () -> assertThrows(IllegalArgumentException.class, () -> new SkyIslandVolumeDescriptor(
                        1, 0L, 0.0, 0.0, Double.POSITIVE_INFINITY, 256.0, 96.0, 128.0, 64.0, 0.0, 0.5, 0.5, 0.0, 0.0, 32.0)));
    }

    @Test
    void rejectsNonpositiveScaleControlsAndOversizedFalloff() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> descriptorWithPositive(0.0, 96.0, 128.0, 64.0, 32.0)),
                () -> assertThrows(IllegalArgumentException.class, () -> descriptorWithPositive(256.0, 0.0, 128.0, 64.0, 32.0)),
                () -> assertThrows(IllegalArgumentException.class, () -> descriptorWithPositive(256.0, 96.0, 0.0, 64.0, 32.0)),
                () -> assertThrows(IllegalArgumentException.class, () -> descriptorWithPositive(256.0, 96.0, 128.0, 257.0, 32.0)),
                () -> assertThrows(IllegalArgumentException.class, () -> descriptorWithPositive(256.0, 96.0, 128.0, 64.0, 0.0)));
    }

    @Test
    void rejectsControlsOutsideTheirDeclaredBounds() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> descriptorWithControls(-0.01, 0.5, 0.0, 0.5)),
                () -> assertThrows(IllegalArgumentException.class, () -> descriptorWithControls(0.5, 1.01, 0.0, 0.5)),
                () -> assertThrows(IllegalArgumentException.class, () -> descriptorWithControls(0.5, 0.5, -1.01, 0.5)),
                () -> assertThrows(IllegalArgumentException.class, () -> descriptorWithControls(0.5, 0.5, 1.01, 0.5)),
                () -> assertThrows(IllegalArgumentException.class, () -> descriptorWithControls(0.5, 0.5, 0.0, 1.01)));
    }

    private static SkyIslandVolumeDescriptor descriptor(long seed, double azimuth) {
        return new SkyIslandVolumeDescriptor(
                1, seed, 12.0, -8.0, 256.0, 256.0, 96.0, 128.0, 64.0,
                azimuth, 0.65, 0.6, 0.25, 0.0, 32.0);
    }

    private static SkyIslandVolumeDescriptor descriptorWithControls(
            double ridgeStrength,
            double undersideTaper,
            double undersideAsymmetry,
            double signalAmplitude) {
        return new SkyIslandVolumeDescriptor(
                1, 0L, 0.0, 0.0, 256.0, 256.0, 96.0, 128.0, 64.0,
                0.0, ridgeStrength, undersideTaper, undersideAsymmetry, signalAmplitude, 32.0);
    }

    private static SkyIslandVolumeDescriptor descriptorWithPositive(
            double radius,
            double upper,
            double underside,
            double falloff,
            double signalScale) {
        return new SkyIslandVolumeDescriptor(
                1, 0L, 0.0, 0.0, 256.0, radius, upper, underside, falloff,
                0.0, 0.5, 0.5, 0.0, 0.0, signalScale);
    }
}
