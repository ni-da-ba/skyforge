package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * One local AUTH-0037/AUTH-0038 binding state paired with its stable AUTH-0039 resolver request.
 *
 * <p>The request is stable for the binding key. Local support and local expression ceiling remain
 * available through {@code binding.candidate()} for downstream spatial realization.
 */
public record SkyIslandMaterialBindingRequestUse(
        SkyIslandSemanticPaletteBindingCandidate binding,
        SkyIslandMaterialBindingRequest request) {

    public SkyIslandMaterialBindingRequestUse {
        binding = Objects.requireNonNull(binding, "binding");
        request = Objects.requireNonNull(request, "request");
        if (!binding.bindingKey().equals(request.bindingKey())) {
            throw new IllegalArgumentException(
                    "material-binding request use must retain the AUTH-0038 binding key");
        }
        if (binding.candidate().required() != request.required()) {
            throw new IllegalArgumentException(
                    "material-binding request required state must match local candidate");
        }
        if (binding.candidate().support() + 1.0e-12
                < request.minimumEligibleSupport()) {
            throw new IllegalArgumentException(
                    "local candidate support cannot violate request eligibility floor");
        }
        if (binding.candidate().expressionCeiling()
                > request.maximumExpressionCeiling() + 1.0e-12) {
            throw new IllegalArgumentException(
                    "local candidate expression ceiling cannot exceed stable request maximum");
        }
    }

    public double localSupport() {
        return binding.candidate().support();
    }

    public double localExpressionCeiling() {
        return binding.candidate().expressionCeiling();
    }
}
