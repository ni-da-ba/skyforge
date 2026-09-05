package io.github.nidaba.skyforge.recipes.skyisland.group;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.recipes.skyisland.EnrichedProviderHybridMorphologySkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.recipes.skyisland.EnrichedProviderMorphologySkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.recipes.skyisland.ProviderHybridMorphologyEnrichment;
import io.github.nidaba.skyforge.recipes.skyisland.ProviderMorphologyEnrichment;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviderRegistry;
import java.util.Objects;

/** Compiles one planned group's backend-neutral morphology intent into an island volume. */
public final class SkyIslandMorphologySpecCompiler {
    private final EnrichedProviderMorphologySkyIslandVolumeRecipe providerRecipe =
            new EnrichedProviderMorphologySkyIslandVolumeRecipe();
    private final EnrichedProviderHybridMorphologySkyIslandVolumeRecipe blendRecipe =
            new EnrichedProviderHybridMorphologySkyIslandVolumeRecipe();
    private final SkyIslandMorphologySpecSupportEnvelopeCompiler supportCompiler =
            new SkyIslandMorphologySpecSupportEnvelopeCompiler();

    /** Compiles either a single-provider or pairwise-provider morphology specification. */
    public CompiledSkyIslandVolume compile(
            SkyIslandVolumeDescriptor descriptor,
            SkyIslandMorphologySpec morphology,
            SkyIslandMorphologyProviderRegistry registry) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(morphology, "morphology");
        Objects.requireNonNull(registry, "registry");
        return switch (morphology) {
            case ProviderMorphologySpec provider -> providerRecipe.compile(
                    descriptor,
                    new ProviderMorphologyEnrichment(
                            provider.providerId(),
                            provider.detailAmplitude(),
                            provider.secondaryMorphologyAmplitude()),
                    registry);
            case ProviderBlendMorphologySpec blend -> blendRecipe.compile(
                    descriptor,
                    new ProviderHybridMorphologyEnrichment(
                            blend.blend(),
                            blend.detailAmplitude(),
                            blend.secondaryMorphologyAmplitude()),
                    registry);
        };
    }

    /**
     * Compiles one provider spec and, when analytically supported, attaches AUTH-0052 proof
     * metadata. Ordinary compile remains independent of provider support certification.
     */
    public SkyIslandMorphologySpecCompilation compileWithSupport(
            SkyIslandVolumeDescriptor descriptor,
            SkyIslandMorphologySpec morphology,
            SkyIslandMorphologyProviderRegistry registry) {
        CompiledSkyIslandVolume volume = compile(descriptor, morphology, registry);
        return new SkyIslandMorphologySpecCompilation(
                volume,
                supportCompiler.certify(descriptor, morphology, registry));
    }

    /** Compiles every member of an immutable plan in ordinal order. */
    public java.util.List<CompiledSkyIslandVolume> compile(
            SkyIslandGroupPlan plan,
            SkyIslandMorphologyProviderRegistry registry) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(registry, "registry");
        java.util.ArrayList<CompiledSkyIslandVolume> result = new java.util.ArrayList<>(plan.memberCount());
        for (SkyIslandGroupMemberPlan member : plan.members()) {
            result.add(compile(member.descriptor(), member.morphology(), registry));
        }
        return java.util.List.copyOf(result);
    }

    /** Compiles every member with optional AUTH-0052 support proof in ordinal order. */
    public java.util.List<SkyIslandMorphologySpecCompilation> compileWithSupport(
            SkyIslandGroupPlan plan,
            SkyIslandMorphologyProviderRegistry registry) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(registry, "registry");
        java.util.ArrayList<SkyIslandMorphologySpecCompilation> result =
                new java.util.ArrayList<>(plan.memberCount());
        for (SkyIslandGroupMemberPlan member : plan.members()) {
            result.add(
                    compileWithSupport(
                            member.descriptor(),
                            member.morphology(),
                            registry));
        }
        return java.util.List.copyOf(result);
    }
}
