package io.github.nidaba.skyforge.recipes.skyisland;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;

/**
 * Package-internal seam for obtaining one signal-free primary suspended morphology.
 *
 * <p>This is intentionally not a public plugin ABI. SF-IMP-0022 uses it so composition logic does
 * not depend directly on a closed family switch. A future public provider contract may evolve from
 * this seam only after provider invariants and registration semantics are separately accepted.
 */
interface SkyIslandPrimaryMorphologyProvider {
    /** Stable provider identifier used for provenance and deterministic composition. */
    String identifier();

    /** Compiles one signal-free primary volume. */
    CompiledSkyIslandVolume compilePrimary(SkyIslandVolumeDescriptor descriptor);

    /** Returns the built-in provider for one accepted recipe-layer family. */
    static SkyIslandPrimaryMorphologyProvider builtIn(MorphologyFamily family) {
        return new BuiltInProvider(family);
    }

    /** Built-in adapter over the accepted SF-IMP-0018 primary recipe. */
    final class BuiltInProvider implements SkyIslandPrimaryMorphologyProvider {
        private final MorphologyFamily family;
        private final MorphologyFamilySkyIslandVolumeRecipe recipe =
                new MorphologyFamilySkyIslandVolumeRecipe();

        private BuiltInProvider(MorphologyFamily family) {
            this.family = java.util.Objects.requireNonNull(family, "family");
        }

        @Override
        public String identifier() {
            return "builtin:" + family.identifier();
        }

        @Override
        public CompiledSkyIslandVolume compilePrimary(SkyIslandVolumeDescriptor descriptor) {
            return recipe.compile(descriptor, family);
        }
    }
}
