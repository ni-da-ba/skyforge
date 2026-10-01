package io.github.nidaba.skyforge.recipes.skyisland;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import java.util.Objects;

/**
 * Selects family-aware secondary upper morphology above the accepted cross-family composition.
 *
 * <p>SF-IMP-0020 keeps SF-IMP-0016 bounded detail unchanged and treats SF-IMP-0019 as an inspectable
 * generic comparison baseline. At nonzero signal amplitude the final upper surface is rebuilt from
 * the seeded-detail offset and a positive family-specific secondary factor. The underside remains
 * the accepted SF-IMP-0019 bounded-detail underside.
 */
public final class FamilyAwareMorphologySkyIslandVolumeRecipe {
    /** Recipe version for the first family-aware secondary morphology proof. */
    public static final int RECIPE_VERSION = 6;

    private final ComposedMorphologySkyIslandVolumeRecipe genericRecipe =
            new ComposedMorphologySkyIslandVolumeRecipe();

    /**
     * Compiles one family using family-aware secondary morphology.
     *
     * <p>At zero signal amplitude the exact accepted SF-IMP-0018 primary-family artifact is
     * returned. No descriptor or graph schema change is introduced.
     */
    public CompiledSkyIslandVolume compile(
            SkyIslandVolumeDescriptor descriptor,
            MorphologyFamily family) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(family, "family");
        CompiledSkyIslandVolume generic = genericRecipe.compile(descriptor, family);
        if (descriptor.signalAmplitude() == 0.0) {
            return generic;
        }
        return FamilyAwareSecondaryMorphologyComposition.apply(generic, family, RECIPE_VERSION);
    }

    /** Returns the analytical lower bound on the selected family-aware upper factor. */
    public static double minimumUpperFactor(MorphologyFamily family) {
        Objects.requireNonNull(family, "family");
        return FamilyAwareSecondaryMorphologyComposition.minimumUpperFactor(family);
    }

    /** Returns the analytical upper bound on the selected family-aware upper factor. */
    public static double maximumUpperFactor(MorphologyFamily family) {
        Objects.requireNonNull(family, "family");
        return FamilyAwareSecondaryMorphologyComposition.maximumUpperFactor(family);
    }
}
