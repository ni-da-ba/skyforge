package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * AUTH-0043 backend-neutral local material-expression allocator.
 *
 * <p>The allocator consumes one accepted AUTH-0042 decision per stable AUTH-0039 binding key.
 * Concrete backend material identities remain outside the world model.
 */
public final class SkyIslandMaterialExpressionAllocator {
    private SkyIslandMaterialExpressionAllocator() {}

    public static SkyIslandMaterialExpressionSample allocate(
            SkyIslandMaterialBindingRequestSelection source,
            Map<SkyIslandSemanticPaletteBindingKey, SkyIslandMaterialResolutionDecision>
                    decisions) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(decisions, "decisions");

        if (!source.materialPresent()) {
            return new SkyIslandMaterialExpressionSample(source, List.of());
        }

        double secondaryShare =
                source.use(SkyIslandSemanticMaterialPaletteRole.SECONDARY_MATRIX)
                        .map(SkyIslandMaterialExpressionAllocator::authoredOptionalExpression)
                        .orElse(0.0);
        double primaryShare = 1.0 - secondaryShare;

        List<SkyIslandMaterialExpressionAllocation> allocations =
                new ArrayList<>(source.uses().size());
        for (SkyIslandMaterialBindingRequestUse use : source.uses()) {
            SkyIslandMaterialResolutionDecision decision =
                    decisionFor(use, decisions);
            SkyIslandSemanticMaterialPaletteRole role = use.request().role();

            double target;
            SkyIslandMaterialExpressionMode mode;
            switch (role) {
                case PRIMARY_MATRIX -> {
                    target = primaryShare;
                    mode = SkyIslandMaterialExpressionMode.STRUCTURAL_MATRIX_SHARE;
                }
                case SECONDARY_MATRIX -> {
                    target = secondaryShare;
                    mode = SkyIslandMaterialExpressionMode.STRUCTURAL_MATRIX_SHARE;
                }
                case ALTERATION_OVERPRINT,
                        HYDROLOGIC_CONDITIONING,
                        MINERAL_BEARING_STRUCTURE -> {
                    target = authoredOptionalExpression(use);
                    mode = SkyIslandMaterialExpressionMode.CONDITIONED_EXPRESSION_CLAIM;
                }
                default -> throw new IllegalStateException(
                        "unhandled semantic material role " + role);
            }

            allocations.add(
                    new SkyIslandMaterialExpressionAllocation(
                            use, decision, mode, target));
        }

        allocations.sort(
                Comparator.comparingInt(
                        allocation -> allocation.role().ordinal()));
        return new SkyIslandMaterialExpressionSample(source, allocations);
    }

    public static double authoredOptionalExpression(
            SkyIslandMaterialBindingRequestUse use) {
        Objects.requireNonNull(use, "use");
        if (use.request().role()
                == SkyIslandSemanticMaterialPaletteRole.PRIMARY_MATRIX) {
            throw new IllegalArgumentException(
                    "PRIMARY_MATRIX expression is the residual structural share");
        }
        return use.localSupport() * use.localExpressionCeiling();
    }

    private static SkyIslandMaterialResolutionDecision decisionFor(
            SkyIslandMaterialBindingRequestUse use,
            Map<SkyIslandSemanticPaletteBindingKey, SkyIslandMaterialResolutionDecision>
                    decisions) {
        SkyIslandSemanticPaletteBindingKey key = use.request().bindingKey();
        SkyIslandMaterialResolutionDecision decision = decisions.get(key);
        if (decision == null) {
            throw new IllegalArgumentException(
                    "missing AUTH-0042 material-resolution decision for " + key.canonicalToken());
        }
        if (!decision.request().equals(use.request())) {
            throw new IllegalArgumentException(
                    "AUTH-0042 decision does not match the local AUTH-0039 request");
        }
        return decision;
    }
}
