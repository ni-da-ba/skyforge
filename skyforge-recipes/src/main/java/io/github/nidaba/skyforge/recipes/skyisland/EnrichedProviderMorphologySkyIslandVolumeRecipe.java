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
import java.util.Optional;

/** Recipe version 12: bounded detail and provider-aware secondary geography for one provider. */
public final class EnrichedProviderMorphologySkyIslandVolumeRecipe {
    /** Recipe version for direct enriched-provider composition. */
    public static final int RECIPE_VERSION = 12;

    private static final NodeId UPPER_DETAIL_AMPLITUDE =
            new NodeId("descriptor.signal-amplitude.upper");
    private static final NodeId UNDERSIDE_DETAIL_AMPLITUDE =
            new NodeId("descriptor.signal-amplitude.underside");
    private static final NodeId GENERIC_SECONDARY_AMPLITUDE =
            new NodeId("secondary.descriptor-amplitude");

    /** Compiles one explicit provider without manufacturing a pairwise blend. */
    public CompiledSkyIslandVolume compile(
            SkyIslandVolumeDescriptor descriptor,
            ProviderMorphologyEnrichment enrichment,
            SkyIslandMorphologyProviderRegistry registry) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(enrichment, "enrichment");
        Objects.requireNonNull(registry, "registry");
        requireProofDescriptor(descriptor);

        SkyIslandMorphologyProvider provider = registry.require(enrichment.providerId());
        CompiledSkyIslandVolume exactPrimary = provider.compilePrimary(descriptor).volume();
        if (enrichment.detailAmplitude() == 0.0
                && enrichment.secondaryMorphologyAmplitude() == 0.0) {
            return wrapPrimary(descriptor, enrichment, exactPrimary);
        }

        CompiledSkyIslandVolume carrierPrimary = ProviderPrimaryMorphologyCanonicalizer.canonicalize(
                descriptor, provider, RECIPE_VERSION);
        SkyIslandVolumeDescriptor carrierDescriptor = carrierDescriptor(descriptor);
        CompiledSkyIslandVolume generic = SuspendedVolumeEnrichmentComposition.apply(
                carrierPrimary, carrierDescriptor, RECIPE_VERSION);
        Optional<SecondaryMorphologyContribution> secondary = provider.compileSecondaryMorphology(
                descriptor, enrichment.secondaryMorphologyAmplitude());
        CompiledSkyIslandVolume composed = ProviderSecondaryMorphologyComposition.applySingle(
                generic, secondary, enrichment.providerId(), RECIPE_VERSION);

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
                enrichedProvenance(composed.provenance()));
    }

    private static CompiledSkyIslandVolume wrapPrimary(
            SkyIslandVolumeDescriptor descriptor,
            ProviderMorphologyEnrichment enrichment,
            CompiledSkyIslandVolume primary) {
        LinkedHashMap<String, List<NodeId>> provenance = new LinkedHashMap<>(primary.provenance());
        provenance.put("provider-detail-amplitude:" + enrichment.providerId(), List.of());
        provenance.put("provider-secondary-morphology-amplitude:" + enrichment.providerId(), List.of());
        return new CompiledSkyIslandVolume(
                descriptor,
                RECIPE_VERSION,
                primary.graphSchemaVersion(),
                primary.upperSurfaceGraph(),
                primary.undersideSurfaceGraph(),
                primary.densityGraph(),
                provenance);
    }

    private static SkyIslandVolumeDescriptor carrierDescriptor(SkyIslandVolumeDescriptor descriptor) {
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

    private static Map<NodeId, Double> amplitudeReplacements(ProviderMorphologyEnrichment enrichment) {
        LinkedHashMap<NodeId, Double> result = new LinkedHashMap<>();
        result.put(UPPER_DETAIL_AMPLITUDE, enrichment.detailAmplitude());
        result.put(UNDERSIDE_DETAIL_AMPLITUDE, enrichment.detailAmplitude());
        result.put(GENERIC_SECONDARY_AMPLITUDE, 0.0);
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
                            "provider enrichment amplitude node is not constant: " + node.id());
                }
                nodes.add(new ConstantNode(constant.id(), constant.outputType(), replacement));
            } else {
                nodes.add(node);
            }
        }
        return new ProceduralGraph(nodes, source.output());
    }

    private static Map<String, List<NodeId>> enrichedProvenance(Map<String, List<NodeId>> base) {
        LinkedHashMap<String, List<NodeId>> result = new LinkedHashMap<>(base);
        result.put("provider-detail-amplitude", List.of(
                UPPER_DETAIL_AMPLITUDE,
                UNDERSIDE_DETAIL_AMPLITUDE));
        result.put("provider-secondary-morphology-amplitude", List.of(
                new NodeId("provider-secondary.minimum-factor"),
                new NodeId("provider-secondary.maximum-factor"),
                new NodeId("provider-secondary.upper-factor")));
        result.put("provider-generic-secondary-neutralized", List.of(GENERIC_SECONDARY_AMPLITUDE));
        return result;
    }

    private static void requireProofDescriptor(SkyIslandVolumeDescriptor descriptor) {
        if (descriptor.schemaVersion() != SkyIslandVolumeDescriptor.SCHEMA_VERSION_1) {
            throw new IllegalArgumentException(
                    "enriched provider proof requires descriptor schema 1");
        }
        if (descriptor.signalAmplitude() != 0.0) {
            throw new IllegalArgumentException(
                    "enriched provider proof requires zero descriptor signalAmplitude");
        }
    }
}
