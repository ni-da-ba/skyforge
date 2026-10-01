package io.github.nidaba.skyforge.recipes.skyisland;

import io.github.nidaba.skyforge.kernel.graph.ConstantNode;
import io.github.nidaba.skyforge.kernel.graph.GraphNode;
import io.github.nidaba.skyforge.kernel.graph.NodeId;
import io.github.nidaba.skyforge.kernel.graph.ProceduralGraph;
import io.github.nidaba.skyforge.kernel.serialization.CanonicalGraphJson;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Descriptor-driven schema-2 compiler for the accepted built-in sky-island morphology vocabulary.
 *
 * <p>The implementation deliberately reuses the accepted SF-IMP-0020 graph construction as a
 * full-amplitude carrier and rewrites only the named local-detail and secondary-morphology amplitude
 * constants. This keeps the accepted morphology mathematics and graph topology intact while making
 * the two semantic amplitudes independent.
 */
public final class SemanticSkyIslandVolumeRecipe {
    /** Recipe version for descriptor schema-2 semantic morphology controls. */
    public static final int RECIPE_VERSION = 7;

    private static final NodeId UPPER_DETAIL_AMPLITUDE =
            new NodeId("descriptor.signal-amplitude.upper");
    private static final NodeId UNDERSIDE_DETAIL_AMPLITUDE =
            new NodeId("descriptor.signal-amplitude.underside");
    private static final NodeId GENERIC_SECONDARY_AMPLITUDE =
            new NodeId("secondary.descriptor-amplitude");

    private final MorphologyFamilySkyIslandVolumeRecipe primaryRecipe =
            new MorphologyFamilySkyIslandVolumeRecipe();
    private final FamilyAwareMorphologySkyIslandVolumeRecipe familyAwareRecipe =
            new FamilyAwareMorphologySkyIslandVolumeRecipe();

    /**
     * Compiles one schema-2 descriptor without a separate recipe-layer family argument.
     *
     * @throws NullPointerException if {@code descriptor} is null
     * @throws IllegalArgumentException if the descriptor is not schema 2
     */
    public CompiledSkyIslandVolume compile(SkyIslandVolumeDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        if (descriptor.schemaVersion() != SkyIslandVolumeDescriptor.SCHEMA_VERSION_2) {
            throw new IllegalArgumentException("semantic morphology recipe requires descriptor schema 2");
        }

        MorphologyFamily family = MorphologyFamily.fromSemantic(descriptor.morphologyFamily());
        if (descriptor.detailAmplitude() == 0.0
                && descriptor.secondaryMorphologyAmplitude() == 0.0) {
            CompiledSkyIslandVolume primary = primaryRecipe.compile(descriptor, family);
            return new CompiledSkyIslandVolume(
                    descriptor,
                    RECIPE_VERSION,
                    primary.graphSchemaVersion(),
                    primary.upperSurfaceGraph(),
                    primary.undersideSurfaceGraph(),
                    primary.densityGraph(),
                    semanticProvenance(primary.provenance(), family, false));
        }

        SkyIslandVolumeDescriptor carrierDescriptor = carrierDescriptor(descriptor);
        CompiledSkyIslandVolume carrier = familyAwareRecipe.compile(carrierDescriptor, family);
        Map<NodeId, Double> replacements = amplitudeReplacements(descriptor, family);
        ProceduralGraph upper = rewriteConstants(carrier.upperSurfaceGraph(), replacements);
        ProceduralGraph underside = rewriteConstants(carrier.undersideSurfaceGraph(), replacements);
        ProceduralGraph density = rewriteConstants(carrier.densityGraph(), replacements);
        return new CompiledSkyIslandVolume(
                descriptor,
                RECIPE_VERSION,
                CanonicalGraphJson.INTERSECTION_SCHEMA_VERSION,
                upper,
                underside,
                density,
                semanticProvenance(carrier.provenance(), family, true));
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
                descriptor.detailScale());
    }

    private static Map<NodeId, Double> amplitudeReplacements(
            SkyIslandVolumeDescriptor descriptor, MorphologyFamily family) {
        LinkedHashMap<NodeId, Double> result = new LinkedHashMap<>();
        result.put(UPPER_DETAIL_AMPLITUDE, descriptor.detailAmplitude());
        result.put(UNDERSIDE_DETAIL_AMPLITUDE, descriptor.detailAmplitude());
        result.put(GENERIC_SECONDARY_AMPLITUDE, descriptor.secondaryMorphologyAmplitude());
        if (family != MorphologyFamily.MASSIF) {
            result.put(
                    new NodeId("family-aware." + family.identifier() + ".descriptor-amplitude"),
                    descriptor.secondaryMorphologyAmplitude());
        }
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
                            "semantic amplitude node is not constant: " + node.id());
                }
                nodes.add(new ConstantNode(constant.id(), constant.outputType(), replacement));
            } else {
                nodes.add(node);
            }
        }
        return new ProceduralGraph(nodes, source.output());
    }

    private static Map<String, List<NodeId>> semanticProvenance(
            Map<String, List<NodeId>> base,
            MorphologyFamily family,
            boolean enriched) {
        LinkedHashMap<String, List<NodeId>> result = new LinkedHashMap<>(base);
        List<NodeId> familyNodes = base.get("morphology-family:" + family.identifier());
        result.put(
                "semantic-morphology-family:" + family.semanticFamily().identifier(),
                familyNodes == null ? List.of() : familyNodes);
        if (enriched) {
            result.put("detail-amplitude", List.of(
                    UPPER_DETAIL_AMPLITUDE,
                    UNDERSIDE_DETAIL_AMPLITUDE));
            List<NodeId> secondary = new ArrayList<>();
            secondary.add(GENERIC_SECONDARY_AMPLITUDE);
            if (family != MorphologyFamily.MASSIF) {
                secondary.add(new NodeId(
                        "family-aware." + family.identifier() + ".descriptor-amplitude"));
            }
            result.put("secondary-morphology-amplitude", List.copyOf(secondary));
        } else {
            result.put("detail-amplitude", List.of());
            result.put("secondary-morphology-amplitude", List.of());
        }
        return result;
    }
}
