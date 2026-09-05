package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate3;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * AUTH-0047 backend-neutral world-space sampler for the accepted native material-authorship chain.
 *
 * <p>The sampler consumes one explicit AUTH-0046 association. It never discovers an island by
 * spatial proximity or bounds. World X/Z are translated through the associated compiled volume's
 * declared center, while physical Y is transformed to semantic depth through the authoritative
 * compiled upper/underside column.
 */
public final class SkyIslandWorldAuthoredMaterialSampler {
    private final SkyIslandAuthoredRealizationAssociation association;
    private final SkyIslandMaterialBindingRequestField requests;
    private final SkyIslandSemanticDepthRealizationTransform depthTransform;
    private final SkyIslandVolumeDescriptor realizedDescriptor;

    public SkyIslandWorldAuthoredMaterialSampler(
            SkyIslandAuthoredRealizationAssociation association) {
        this.association = Objects.requireNonNull(association, "association");
        this.realizedDescriptor =
                association.realizedVolume().compiledVolume().descriptor();
        this.requests =
                SkyIslandMaterialBindingRequestField.create(
                        association.authoredDescriptor());
        this.depthTransform =
                new SkyIslandSemanticDepthRealizationTransform(
                        new SkyIslandCompiledVolumeColumnField(
                                association.realizedVolume().compiledVolume()));
    }

    public SkyIslandAuthoredRealizationAssociation association() {
        return association;
    }

    public SkyIslandMaterialBindingRequestCatalog requestCatalog() {
        return requests.catalog();
    }

    public SkyIslandWorldAuthoredMaterialSample sample(
            Coordinate3 worldPosition,
            SkyIslandMaterialResolutionDecisionProvider decisionProvider) {
        Objects.requireNonNull(worldPosition, "worldPosition");
        Objects.requireNonNull(decisionProvider, "decisionProvider");

        SkyIslandLocalPosition local =
                new SkyIslandLocalPosition(
                        worldPosition.x() - realizedDescriptor.centerX(),
                        worldPosition.z() - realizedDescriptor.centerZ());
        SkyIslandRealizedSubsurfacePosition realized =
                new SkyIslandRealizedSubsurfacePosition(local, worldPosition.y());
        Optional<SkyIslandSubsurfacePosition> semantic =
                depthTransform.toSemantic(realized);
        if (semantic.isEmpty()) {
            return SkyIslandWorldAuthoredMaterialSample.outsidePhysical(
                    association, worldPosition);
        }

        SkyIslandSubsurfacePosition semanticPosition = semantic.orElseThrow();
        SkyIslandMaterialBindingRequestSelection source =
                requests.sample(semanticPosition);
        Map<SkyIslandSemanticPaletteBindingKey, SkyIslandMaterialResolutionDecision>
                decisions = decisionsFor(source, decisionProvider);
        SkyIslandMaterialExpressionSample expression =
                SkyIslandMaterialExpressionAllocator.allocate(source, decisions);
        SkyIslandMaterialRealizationSelection realization =
                SkyIslandMaterialExpressionRealizer.realize(
                        semanticPosition, expression);
        SkyIslandMaterialBindingApplication application =
                SkyIslandMaterialBindingApplication.from(realization).orElse(null);

        return new SkyIslandWorldAuthoredMaterialSample(
                association,
                worldPosition,
                realized,
                semanticPosition,
                realization,
                application);
    }

    private static Map<SkyIslandSemanticPaletteBindingKey, SkyIslandMaterialResolutionDecision>
            decisionsFor(
                    SkyIslandMaterialBindingRequestSelection source,
                    SkyIslandMaterialResolutionDecisionProvider provider) {
        Map<SkyIslandSemanticPaletteBindingKey, SkyIslandMaterialResolutionDecision>
                result = new LinkedHashMap<>();
        for (SkyIslandMaterialBindingRequestUse use : source.uses()) {
            SkyIslandMaterialBindingRequest request = use.request();
            SkyIslandMaterialResolutionDecision decision =
                    Objects.requireNonNull(
                            provider.decision(request),
                            "material-resolution decision provider returned null");
            if (!decision.request().equals(request)) {
                throw new IllegalArgumentException(
                        "material-resolution decision provider returned a decision for a different request");
            }
            SkyIslandMaterialResolutionDecision previous =
                    result.put(request.bindingKey(), decision);
            if (previous != null && !previous.equals(decision)) {
                throw new IllegalArgumentException(
                        "material-resolution decision provider returned inconsistent decisions for one binding key");
            }
        }
        return Map.copyOf(result);
    }
}
