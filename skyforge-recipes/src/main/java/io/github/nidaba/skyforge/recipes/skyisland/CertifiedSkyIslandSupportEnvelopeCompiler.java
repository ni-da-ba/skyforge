package io.github.nidaba.skyforge.recipes.skyisland;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import java.util.Objects;
import java.util.Optional;

/**
 * AUTH-0051 compiler for proof-grade support metadata attached to accepted compiled realizations.
 *
 * <p>The first accepted certificate path intentionally recognizes only the schema-2 semantic
 * built-in recipe. Unknown recipe/provider combinations remain uncertified rather than receiving a
 * guessed envelope.
 */
public final class CertifiedSkyIslandSupportEnvelopeCompiler {

    /**
     * Returns a proof-grade support certificate when the compiled volume belongs to an accepted
     * certifiable recipe path.
     */
    public Optional<CertifiedSkyIslandSupportEnvelope> certify(
            CompiledSkyIslandVolume volume) {
        Objects.requireNonNull(volume, "volume");
        SkyIslandVolumeDescriptor descriptor = volume.descriptor();

        if (volume.recipeVersion() != SemanticSkyIslandVolumeRecipe.RECIPE_VERSION
                || descriptor.schemaVersion() != SkyIslandVolumeDescriptor.SCHEMA_VERSION_2
                || !descriptor.hasSemanticMorphologyFamily()) {
            return Optional.empty();
        }

        MorphologyFamily family = MorphologyFamily.fromSemantic(descriptor.morphologyFamily());
        SkyIslandMorphologyProvider provider = SkyIslandMorphologyProviders.builtIn(family);
        Optional<PrimaryMorphologySupportEnvelope> primary =
                provider.certifiedPrimarySupportEnvelope(descriptor);
        if (primary.isEmpty()) {
            return Optional.empty();
        }

        double detailMaximumFactor =
                1.0
                        + SeededSkyIslandVolumeRecipe.MAXIMUM_RELATIVE_DISPLACEMENT
                                * descriptor.detailAmplitude();
        Optional<SecondaryMorphologyContribution> secondary =
                provider.compileSecondaryMorphology(
                        descriptor, descriptor.secondaryMorphologyAmplitude());
        double secondaryMaximumFactor =
                secondary.map(SecondaryMorphologyContribution::maximumFactor).orElse(1.0);

        PrimaryMorphologySupportEnvelope base = primary.orElseThrow();
        return Optional.of(
                new CertifiedSkyIslandSupportEnvelope(
                        base.maximumHorizontalRadius(),
                        base.maximumUpperOffset()
                                * detailMaximumFactor
                                * secondaryMaximumFactor,
                        base.maximumUndersideDepth() * detailMaximumFactor,
                        "semantic-built-in-v1:" + family.identifier()));
    }
}
