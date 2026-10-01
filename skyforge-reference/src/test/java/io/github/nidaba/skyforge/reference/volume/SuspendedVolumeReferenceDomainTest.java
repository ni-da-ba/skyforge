package io.github.nidaba.skyforge.reference.volume;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.reference.sampling.VolumeGridSpec;
import org.junit.jupiter.api.Test;

final class SuspendedVolumeReferenceDomainTest {
    @Test
    void fixesTheCanonicalDescriptorDomainResolutionAndTraversal() {
        SkyIslandVolumeDescriptor descriptor = SuspendedVolumeReferenceDomain.descriptor();
        VolumeGridSpec grid = SuspendedVolumeReferenceDomain.grid();

        assertAll(
                () -> assertEquals(0.0, descriptor.centerX()),
                () -> assertEquals(0.0, descriptor.centerZ()),
                () -> assertEquals(256.0, descriptor.suspensionElevation()),
                () -> assertEquals(256.0, descriptor.nominalRadius()),
                () -> assertEquals(-384.0, grid.minimumX()),
                () -> assertEquals(384.0, grid.maximumX()),
                () -> assertEquals(0.0, grid.minimumY()),
                () -> assertEquals(512.0, grid.maximumY()),
                () -> assertEquals(-384.0, grid.minimumZ()),
                () -> assertEquals(384.0, grid.maximumZ()),
                () -> assertEquals(193, grid.xSamples()),
                () -> assertEquals(129, grid.ySamples()),
                () -> assertEquals(193, grid.zSamples()),
                () -> assertEquals(4_805_121, grid.sampleCount()),
                () -> assertEquals(4.0, grid.spacingX()),
                () -> assertEquals(4.0, grid.spacingY()),
                () -> assertEquals(4.0, grid.spacingZ()),
                () -> assertEquals(
                        "x-fastest, then z, then y",
                        SuspendedVolumeReferenceDomain.TRAVERSAL_ORDER));
    }

    @Test
    void declaredPrimaryEnvelopeHasAirMarginOnEveryFace() {
        SkyIslandVolumeDescriptor descriptor = SuspendedVolumeReferenceDomain.descriptor();
        VolumeGridSpec grid = SuspendedVolumeReferenceDomain.grid();
        double minimumSolidX = descriptor.centerX() - descriptor.nominalRadius();
        double maximumSolidX = descriptor.centerX() + descriptor.nominalRadius();
        double minimumSolidY = descriptor.suspensionElevation() - descriptor.undersideDepth();
        double maximumSolidY = descriptor.suspensionElevation() + descriptor.upperElevation();
        double minimumSolidZ = descriptor.centerZ() - descriptor.nominalRadius();
        double maximumSolidZ = descriptor.centerZ() + descriptor.nominalRadius();

        assertAll(
                () -> assertTrue(grid.minimumX() < minimumSolidX),
                () -> assertTrue(grid.maximumX() > maximumSolidX),
                () -> assertTrue(grid.minimumY() < minimumSolidY),
                () -> assertTrue(grid.maximumY() > maximumSolidY),
                () -> assertTrue(grid.minimumZ() < minimumSolidZ),
                () -> assertTrue(grid.maximumZ() > maximumSolidZ));
    }
}
