package io.github.nidaba.skyforge.recipes.skyisland;

import io.github.nidaba.skyforge.kernel.graph.ConstantNode;
import io.github.nidaba.skyforge.kernel.graph.GraphNode;
import io.github.nidaba.skyforge.kernel.graph.NodeId;
import io.github.nidaba.skyforge.kernel.graph.ProceduralGraph;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Recipe version 9: bounded local detail plus blended family-aware secondary morphology over an
 * accepted SF-IMP-0022 primary hybrid.
 */
public final class EnrichedHybridMorphologySkyIslandVolumeRecipe {
    /** Recipe version for the first complete enriched-hybrid proof. */
    public static final int RECIPE_VERSION = 9;

    private static final NodeId UPPER_DETAIL_AMPLITUDE =
            new NodeId("descriptor.signal-amplitude.upper");
    private static final NodeId UNDERSIDE_DETAIL_AMPLITUDE =
            new NodeId("descriptor.signal-amplitude.underside");
    private static final NodeId GENERIC_SECONDARY_AMPLITUDE =
            new NodeId("secondary.descriptor-amplitude");

    private final HybridMorphologySkyIslandVolumeRecipe hybridPrimaryRecipe =
            new HybridMorphologySkyIslandVolumeRecipe();
    private final FamilyAwareMorphologySkyIslandVolumeRecipe familyAwareRecipe =
            new FamilyAwareMorphologySkyIslandVolumeRecipe();
    private final SemanticSkyIslandVolumeRecipe semanticRecipe =
            new SemanticSkyIslandVolumeRecipe();

    /**
     * Compiles one recipe-layer hybrid with independent detail and blended secondary amplitudes.
     *
     * <p>The base descriptor must be schema 1 with zero signal amplitude. Hybrid selection and its
     * independent enrichment amplitudes remain recipe-layer state during this proof.
     */
    public CompiledSkyIslandVolume compile(
            SkyIslandVolumeDescriptor descriptor,
            HybridMorphologyEnrichment enrichment) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(enrichment, "enrichment");
        requireProofDescriptor(descriptor);

        MorphologyBlend blend = enrichment.blend();
        if (blend.secondWeight() == 0.0 || blend.secondWeight() == 1.0) {
            return endpoint(descriptor, enrichment);
        }

        CompiledSkyIslandVolume primary = hybridPrimaryRecipe.compile(descriptor, blend);
        if (enrichment.detailAmplitude() == 0.0
                && enrichment.secondaryMorphologyAmplitude() == 0.0) {
            return wrapPrimary(descriptor, enrichment, primary);
        }

        SkyIslandVolumeDescriptor carrierDescriptor = carrierDescriptor(descriptor);
        CompiledSkyIslandVolume genericHybrid = SuspendedVolumeEnrichmentComposition.apply(
                primary, carrierDescriptor, RECIPE_VERSION);
        CompiledSkyIslandVolume firstCarrier = familyAwareRecipe.compile(
                carrierDescriptor, blend.first());
        CompiledSkyIslandVolume secondCarrier = familyAwareRecipe.compile(
                carrierDescriptor, blend.second());
        CompiledSkyIslandVolume composed = HybridFamilyAwareSecondaryMorphologyComposition.apply(
                genericHybrid,
                firstCarrier,
                secondCarrier,
                blend,
                RECIPE_VERSION);

        Map<NodeId, Double> replacements = amplitudeReplacements(enrichment);
        ProceduralGraph upper = rewriteConstants(composed.upperSurfaceGraph(), replacements);
        ProceduralGraph underside = rewriteConstants(composed.undersideSurfaceGraph(), replacements);
        ProceduralGraph density = rewriteConstants(composed.densityGraph(), replacements);
        return new CompiledSkyIslandVolume(
                descriptor,
                RECIPE_VERSION,
                composed.graphSchemaVersion(),
                upper,
                underside,
                density,
                enrichedProvenance(composed.provenance(), enrichment));
    }

    private CompiledSkyIslandVolume endpoint(
            SkyIslandVolumeDescriptor descriptor,
            HybridMorphologyEnrichment enrichment) {
        MorphologyBlend blend = enrichment.blend();
        MorphologyFamily family = blend.secondWeight() == 0.0 ? blend.first() : blend.second();
        SkyIslandVolumeDescriptor semanticDescriptor = SkyIslandVolumeDescriptor.schema2(
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
                family.semanticFamily(),
                enrichment.detailAmplitude(),
                descriptor.signalScale(),
                enrichment.secondaryMorphologyAmplitude());
        CompiledSkyIslandVolume accepted = semanticRecipe.compile(semanticDescriptor);
        LinkedHashMap<String, List<NodeId>> provenance = new LinkedHashMap<>(accepted.provenance());
        provenance.put("morphology-hybrid-endpoint:" + blend.pairIdentifier(), List.of(
                new NodeId("profile.remaining"),
                new NodeId("family.upper-factor"),
                new NodeId("family.depth-factor")));
        return new CompiledSkyIslandVolume(
                descriptor,
                RECIPE_VERSION,
                accepted.graphSchemaVersion(),
                accepted.upperSurfaceGraph(),
                accepted.undersideSurfaceGraph(),
                accepted.densityGraph(),
                provenance);
    }

    private static CompiledSkyIslandVolume wrapPrimary(
            SkyIslandVolumeDescriptor descriptor,
            HybridMorphologyEnrichment enrichment,
            CompiledSkyIslandVolume primary) {
        LinkedHashMap<String, List<NodeId>> provenance = new LinkedHashMap<>(primary.provenance());
        provenance.put("hybrid-detail-amplitude", List.of());
        provenance.put("hybrid-secondary-morphology-amplitude", List.of());
        return new CompiledSkyIslandVolume(
                descriptor,
                RECIPE_VERSION,
                primary.graphSchemaVersion(),
                primary.upperSurfaceGraph(),
                primary.undersideSurfaceGraph(),
                primary.densityGraph(),
                provenance);
    }

    private static SkyIslandVolumeDescriptor carrierDescriptor(
            SkyIslandVolumeDescriptor descriptor) {
        return new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
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
                1.0,
                descriptor.signalScale());
    }

    private static Map<NodeId, Double> amplitudeReplacements(
            HybridMorphologyEnrichment enrichment) {
        MorphologyBlend blend = enrichment.blend();
        LinkedHashMap<NodeId, Double> result = new LinkedHashMap<>();
        result.put(UPPER_DETAIL_AMPLITUDE, enrichment.detailAmplitude());
        result.put(UNDERSIDE_DETAIL_AMPLITUDE, enrichment.detailAmplitude());
        result.put(GENERIC_SECONDARY_AMPLITUDE, enrichment.secondaryMorphologyAmplitude());
        result.put(
                HybridFamilyAwareSecondaryMorphologyComposition.prefixedParentAmplitudeNode(
                        HybridFamilyAwareSecondaryMorphologyComposition.FIRST_CARRIER_PREFIX,
                        blend.first()),
                enrichment.secondaryMorphologyAmplitude());
        result.put(
                HybridFamilyAwareSecondaryMorphologyComposition.prefixedParentAmplitudeNode(
                        HybridFamilyAwareSecondaryMorphologyComposition.SECOND_CARRIER_PREFIX,
                        blend.second()),
                enrichment.secondaryMorphologyAmplitude());
        return result;
    }

    private static ProceduralGraph rewriteConstants(
            ProceduralGraph source, Map<NodeId, Double> replacements) {
        List<GraphNode> nodes = new ArrayList<>(source.nodes().size());
        for (GraphNode node : source.nodes()) {
            Double replacement = replacements.get(node.id());
            if (replacement != null) {
                if (!(node instanceof ConstantNode constant)) {
                    throw new IllegalStateException(
                            "hybrid enrichment amplitude node is not constant: " + node.id());
                }
                nodes.add(new ConstantNode(constant.id(), constant.outputType(), replacement));
            } else {
                nodes.add(node);
            }
        }
        return new ProceduralGraph(nodes, source.output());
    }

    private static Map<String, List<NodeId>> enrichedProvenance(
            Map<String, List<NodeId>> base,
            HybridMorphologyEnrichment enrichment) {
        MorphologyBlend blend = enrichment.blend();
        LinkedHashMap<String, List<NodeId>> result = new LinkedHashMap<>(base);
        result.put("hybrid-detail-amplitude", List.of(
                UPPER_DETAIL_AMPLITUDE,
                UNDERSIDE_DETAIL_AMPLITUDE));
        result.put("hybrid-secondary-morphology-amplitude", List.of(
                GENERIC_SECONDARY_AMPLITUDE,
                HybridFamilyAwareSecondaryMorphologyComposition.prefixedParentAmplitudeNode(
                        HybridFamilyAwareSecondaryMorphologyComposition.FIRST_CARRIER_PREFIX,
                        blend.first()),
                HybridFamilyAwareSecondaryMorphologyComposition.prefixedParentAmplitudeNode(
                        HybridFamilyAwareSecondaryMorphologyComposition.SECOND_CARRIER_PREFIX,
                        blend.second())));
        return result;
    }

    private static void requireProofDescriptor(SkyIslandVolumeDescriptor descriptor) {
        if (descriptor.schemaVersion() != SkyIslandVolumeDescriptor.SCHEMA_VERSION_1) {
            throw new IllegalArgumentException(
                    "enriched hybrid proof requires descriptor schema 1");
        }
        if (descriptor.signalAmplitude() != 0.0) {
            throw new IllegalArgumentException(
                    "enriched hybrid proof requires zero descriptor signalAmplitude");
        }
    }
}
