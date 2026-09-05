package io.github.nidaba.skyforge.world;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** AUTH-0043 local structural-matrix partition plus independent conditioned expression claims. */
public record SkyIslandMaterialExpressionSample(
        SkyIslandMaterialBindingRequestSelection source,
        List<SkyIslandMaterialExpressionAllocation> allocations) {

    public SkyIslandMaterialExpressionSample {
        source = Objects.requireNonNull(source, "source");
        allocations = List.copyOf(allocations);
        for (SkyIslandMaterialExpressionAllocation allocation : allocations) {
            Objects.requireNonNull(allocation, "material-expression allocation");
        }

        if (!source.materialPresent()) {
            if (!allocations.isEmpty()) {
                throw new IllegalArgumentException(
                        "non-material samples cannot contain material-expression allocations");
            }
        } else {
            if (allocations.size() != source.uses().size()) {
                throw new IllegalArgumentException(
                        "material-expression sample must allocate every AUTH-0039 request use exactly once");
            }

            EnumSet<SkyIslandSemanticMaterialPaletteRole> roles =
                    EnumSet.noneOf(SkyIslandSemanticMaterialPaletteRole.class);
            double matrixShare = 0.0;
            int primary = 0;

            for (SkyIslandMaterialExpressionAllocation allocation : allocations) {
                if (!roles.add(allocation.role())) {
                    throw new IllegalArgumentException(
                            "material-expression roles must be unique per sample");
                }
                SkyIslandMaterialBindingRequestUse expected =
                        source.use(allocation.role()).orElseThrow();
                if (!expected.equals(allocation.use())) {
                    throw new IllegalArgumentException(
                            "material-expression allocation must preserve local AUTH-0039 use");
                }
                if (allocation.mode()
                        == SkyIslandMaterialExpressionMode.STRUCTURAL_MATRIX_SHARE) {
                    matrixShare += allocation.targetExpression();
                }
                if (allocation.role()
                        == SkyIslandSemanticMaterialPaletteRole.PRIMARY_MATRIX) {
                    primary++;
                }
            }

            if (primary != 1) {
                throw new IllegalArgumentException(
                        "material-expression sample requires exactly one PRIMARY_MATRIX allocation");
            }
            if (Math.abs(matrixShare - 1.0) > 1.0e-12) {
                throw new IllegalArgumentException(
                        "structural matrix expression shares must sum exactly to 1");
            }
        }
    }

    public Optional<SkyIslandMaterialExpressionAllocation> allocation(
            SkyIslandSemanticMaterialPaletteRole role) {
        Objects.requireNonNull(role, "role");
        return allocations.stream()
                .filter(allocation -> allocation.role() == role)
                .findFirst();
    }

    public double primaryMatrixShare() {
        return allocation(SkyIslandSemanticMaterialPaletteRole.PRIMARY_MATRIX)
                .map(SkyIslandMaterialExpressionAllocation::targetExpression)
                .orElse(0.0);
    }

    public double secondaryMatrixShare() {
        return allocation(SkyIslandSemanticMaterialPaletteRole.SECONDARY_MATRIX)
                .map(SkyIslandMaterialExpressionAllocation::targetExpression)
                .orElse(0.0);
    }

    public double conditionedClaim(
            SkyIslandSemanticMaterialPaletteRole role) {
        if (role == SkyIslandSemanticMaterialPaletteRole.PRIMARY_MATRIX
                || role == SkyIslandSemanticMaterialPaletteRole.SECONDARY_MATRIX) {
            throw new IllegalArgumentException(
                    "conditionedClaim requires a conditioned material role");
        }
        return allocation(role)
                .map(SkyIslandMaterialExpressionAllocation::targetExpression)
                .orElse(0.0);
    }

    public int conditionedClaimCount() {
        return (int)
                allocations.stream()
                        .filter(
                                allocation ->
                                        allocation.mode()
                                                == SkyIslandMaterialExpressionMode
                                                        .CONDITIONED_EXPRESSION_CLAIM)
                        .count();
    }

    public double conditionedClaimSum() {
        return allocations.stream()
                .filter(
                        allocation ->
                                allocation.mode()
                                        == SkyIslandMaterialExpressionMode
                                                .CONDITIONED_EXPRESSION_CLAIM)
                .mapToDouble(SkyIslandMaterialExpressionAllocation::targetExpression)
                .sum();
    }
}
