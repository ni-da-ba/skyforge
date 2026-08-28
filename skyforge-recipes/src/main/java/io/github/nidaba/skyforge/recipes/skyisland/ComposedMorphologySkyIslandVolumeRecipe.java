package io.github.nidaba.skyforge.recipes.skyisland;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import java.util.Objects;

/**
 * Composes the accepted SF-IMP-0016 detail and SF-IMP-0017 structured relief layers across one
 * accepted SF-IMP-0018 primary morphology family.
 */
public final class ComposedMorphologySkyIslandVolumeRecipe {
    /** Recipe version for the first cross-family enrichment composition proof. */
    public static final int RECIPE_VERSION = 5;

    private final MorphologyFamilySkyIslandVolumeRecipe primaryRecipe =
            new MorphologyFamilySkyIslandVolumeRecipe();

    /**
     * Compiles one family with the accepted bounded-detail and structured-relief transforms.
     *
     * <p>At zero signal amplitude this returns the exact SF-IMP-0018 primary-family artifact.
     * Nonzero amplitude first compiles the same primary family with zero local signal amplitude and
     * then applies the accepted SF-IMP-0016 and SF-IMP-0017 graph transforms in their established
     * order.
     */
    public CompiledSkyIslandVolume compile(
            SkyIslandVolumeDescriptor descriptor,
            MorphologyFamily family) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(family, "family");

        SkyIslandVolumeDescriptor primaryDescriptor = withoutSignalAmplitude(descriptor);
        CompiledSkyIslandVolume primary = primaryRecipe.compile(primaryDescriptor, family);
        if (descriptor.signalAmplitude() == 0.0) {
            return primary;
        }
        return SuspendedVolumeEnrichmentComposition.apply(primary, descriptor, RECIPE_VERSION);
    }

    private static SkyIslandVolumeDescriptor withoutSignalAmplitude(
            SkyIslandVolumeDescriptor descriptor) {
        return new SkyIslandVolumeDescriptor(
                descriptor.schemaVersion(),
                descriptor.seed(),
                descriptor.centerX(),
                descriptor.centerZ(),
                descriptor.suspensionElevation(),
                descriptor.nominalRadius(),
                descriptor.upperElevation(),
                descriptor.undersideDepth(),
                descriptor.coastalFalloff(),
                descriptor.ridgeAzimuth(),
                descriptor.ridgeStrength(),
                descriptor.undersideTaper(),
                descriptor.undersideAsymmetry(),
                0.0,
                descriptor.signalScale());
    }
}
