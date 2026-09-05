package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * One AUTH-0043 local material-expression allocation paired with its stable AUTH-0042 decision.
 *
 * <p>Structural matrix shares form an exact local partition between PRIMARY_MATRIX and optional
 * SECONDARY_MATRIX. Conditioned expression claims are independent overlay opportunities and are
 * not normalized against the structural matrix or one another.
 */
public record SkyIslandMaterialExpressionAllocation(
        SkyIslandMaterialBindingRequestUse use,
        SkyIslandMaterialResolutionDecision decision,
        SkyIslandMaterialExpressionMode mode,
        double targetExpression) {

    public SkyIslandMaterialExpressionAllocation {
        use = Objects.requireNonNull(use, "use");
        decision = Objects.requireNonNull(decision, "decision");
        mode = Objects.requireNonNull(mode, "mode");
        if (!decision.request().equals(use.request())) {
            throw new IllegalArgumentException(
                    "material-expression allocation must retain the exact AUTH-0039 request");
        }
        if (!Double.isFinite(targetExpression)
                || targetExpression <= 0.0
                || targetExpression > 1.0) {
            throw new IllegalArgumentException(
                    "material-expression target must be finite and in (0, 1]");
        }
        if (targetExpression > use.localExpressionCeiling() + 1.0e-12) {
            throw new IllegalArgumentException(
                    "material-expression target cannot exceed AUTH-0037 local ceiling");
        }

        boolean matrixRole =
                use.request().role() == SkyIslandSemanticMaterialPaletteRole.PRIMARY_MATRIX
                        || use.request().role()
                                == SkyIslandSemanticMaterialPaletteRole.SECONDARY_MATRIX;
        if (matrixRole
                != (mode == SkyIslandMaterialExpressionMode.STRUCTURAL_MATRIX_SHARE)) {
            throw new IllegalArgumentException(
                    "matrix roles must use STRUCTURAL_MATRIX_SHARE and conditioned roles must use CONDITIONED_EXPRESSION_CLAIM");
        }
    }

    public SkyIslandSemanticMaterialPaletteRole role() {
        return use.request().role();
    }

    public SkyIslandSemanticPaletteBindingKey bindingKey() {
        return use.request().bindingKey();
    }

    public SkyIslandMaterialCapabilityProfile selectedProfile() {
        return decision.selectedProfile();
    }

    public double localSupport() {
        return use.localSupport();
    }

    public double localExpressionCeiling() {
        return use.localExpressionCeiling();
    }
}
