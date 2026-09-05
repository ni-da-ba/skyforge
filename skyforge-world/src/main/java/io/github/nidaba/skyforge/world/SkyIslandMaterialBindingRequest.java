package io.github.nidaba.skyforge.world;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Stable backend-neutral AUTH-0039 request used to resolve one concrete material binding.
 *
 * <p>The request is invariant for one AUTH-0038 binding key. Local support and local expression
 * ceilings are intentionally carried separately by {@link SkyIslandMaterialBindingRequestUse}.
 */
public record SkyIslandMaterialBindingRequest(
        SkyIslandSemanticPaletteBindingKey bindingKey,
        boolean required,
        double minimumEligibleSupport,
        double minimumSecondaryHostRatio,
        double maximumExpressionCeiling,
        List<SkyIslandMaterialBindingAssemblageContext> assemblages,
        int contactId,
        SkyIslandLithologicContactKind contactKind) {

    public SkyIslandMaterialBindingRequest {
        bindingKey = Objects.requireNonNull(bindingKey, "bindingKey");
        requireNormalized("minimumEligibleSupport", minimumEligibleSupport);
        requireNormalized("minimumSecondaryHostRatio", minimumSecondaryHostRatio);
        requireNormalized("maximumExpressionCeiling", maximumExpressionCeiling);
        if (maximumExpressionCeiling <= 0.0) {
            throw new IllegalArgumentException(
                    "maximumExpressionCeiling must be positive");
        }
        if (!SkyIslandMaterialBindingRequestPolicy.sourceChannelAllowed(
                bindingKey.role(), bindingKey.sourceChannel())) {
            throw new IllegalArgumentException(
                    "material-binding request role/source channel is invalid");
        }
        if (required
                != SkyIslandMaterialBindingRequestPolicy.required(bindingKey.role())
                || !same(minimumEligibleSupport,
                        SkyIslandMaterialBindingRequestPolicy.minimumEligibleSupport(
                                bindingKey.role()))
                || !same(minimumSecondaryHostRatio,
                        SkyIslandMaterialBindingRequestPolicy.minimumSecondaryHostRatio(
                                bindingKey.role()))
                || !same(maximumExpressionCeiling,
                        SkyIslandMaterialBindingRequestPolicy.maximumExpressionCeiling(
                                bindingKey.role()))) {
            throw new IllegalArgumentException(
                    "material-binding request must retain the stable AUTH-0037 role policy");
        }

        assemblages = List.copyOf(assemblages);
        if (assemblages.isEmpty()) {
            throw new IllegalArgumentException(
                    "material-binding request requires lithologic assemblage context");
        }
        Set<Integer> seen = new HashSet<>();
        int previous = -1;
        for (SkyIslandMaterialBindingAssemblageContext assemblage : assemblages) {
            Objects.requireNonNull(assemblage, "assemblage context");
            if (!seen.add(assemblage.assemblageId())) {
                throw new IllegalArgumentException(
                        "material-binding request assemblage context must be unique");
            }
            if (assemblage.assemblageId() <= previous) {
                throw new IllegalArgumentException(
                        "material-binding request assemblages must be ordered by id");
            }
            previous = assemblage.assemblageId();
        }

        switch (bindingKey.domainKind()) {
            case ASSEMBLAGE_REGION -> {
                if (assemblages.size() != 1 || contactId != -1 || contactKind != null) {
                    throw new IllegalArgumentException(
                            "assemblage-region request requires one assemblage and no contact");
                }
            }
            case CONDITIONED_REGION -> {
                if (contactId != -1 || contactKind != null) {
                    throw new IllegalArgumentException(
                            "conditioned-region request cannot carry contact provenance");
                }
            }
            case CONTACT_TRANSITION -> {
                if (contactId < 0
                        || contactId != bindingKey.anchorId()
                        || contactKind == null
                        || assemblages.size() != 2) {
                    throw new IllegalArgumentException(
                            "contact-transition request requires its anchored contact and two parent assemblages");
                }
            }
        }
    }

    public SkyIslandSemanticMaterialPaletteRole role() {
        return bindingKey.role();
    }

    public SkyIslandLithologicRealizationChannel sourceChannel() {
        return bindingKey.sourceChannel();
    }

    public SkyIslandSemanticPaletteBindingDomainKind domainKind() {
        return bindingKey.domainKind();
    }

    private static boolean same(double first, double second) {
        return Math.abs(first - second) <= 1.0e-12;
    }

    private static void requireNormalized(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
    }
}
