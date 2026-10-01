package io.github.nidaba.skyforge.world;

import java.util.Comparator;
import java.util.Objects;

/**
 * AUTH-0044 deterministic discrete semantic material realizer.
 *
 * <p>The realizer converts AUTH-0043 continuous allocations into one semantic winner at one exact
 * island-local point. It does not know or return a backend material identity.
 */
public final class SkyIslandMaterialExpressionRealizer {
    private static final double EPSILON = 1.0e-12;

    private SkyIslandMaterialExpressionRealizer() {}

    public static SkyIslandMaterialRealizationSelection realize(
            SkyIslandSubsurfacePosition position,
            SkyIslandMaterialExpressionSample sample) {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(sample, "sample");

        if (!sample.source().materialPresent()) {
            return new SkyIslandMaterialRealizationSelection(sample, null, null, 0);
        }

        SkyIslandMaterialExpressionAllocation primary =
                sample.allocation(SkyIslandSemanticMaterialPaletteRole.PRIMARY_MATRIX)
                        .orElseThrow();
        SkyIslandMaterialExpressionAllocation structuralWinner = primary;

        var secondary =
                sample.allocation(SkyIslandSemanticMaterialPaletteRole.SECONDARY_MATRIX);
        if (secondary.isPresent()) {
            SkyIslandMaterialExpressionAllocation allocation = secondary.orElseThrow();
            double field = spatialField(allocation, position);
            if (field < allocation.targetExpression()) {
                structuralWinner = allocation;
            }
        }

        SkyIslandMaterialExpressionAllocation conditionedWinner = null;
        double bestMargin = Double.NEGATIVE_INFINITY;
        int activeConditioned = 0;

        for (SkyIslandMaterialExpressionAllocation allocation : sample.allocations()) {
            if (allocation.mode()
                    != SkyIslandMaterialExpressionMode.CONDITIONED_EXPRESSION_CLAIM) {
                continue;
            }
            double field = spatialField(allocation, position);
            if (field >= allocation.targetExpression()) {
                continue;
            }

            activeConditioned++;
            double margin =
                    (allocation.targetExpression() - field)
                            / allocation.targetExpression();
            if (conditionedWinner == null
                    || margin > bestMargin + EPSILON
                    || (Math.abs(margin - bestMargin) <= EPSILON
                            && stableTieOrder(allocation, conditionedWinner) < 0)) {
                conditionedWinner = allocation;
                bestMargin = margin;
            }
        }

        SkyIslandMaterialExpressionAllocation winner =
                conditionedWinner == null ? structuralWinner : conditionedWinner;
        return new SkyIslandMaterialRealizationSelection(
                sample, structuralWinner, winner, activeConditioned);
    }

    public static double spatialField(
            SkyIslandMaterialExpressionAllocation allocation,
            SkyIslandSubsurfacePosition position) {
        Objects.requireNonNull(allocation, "allocation");
        Objects.requireNonNull(position, "position");
        return SkyIslandMaterialExpressionSpatialField.value(
                allocation.bindingKey(), position);
    }

    public static double conditionedMargin(
            SkyIslandMaterialExpressionAllocation allocation,
            SkyIslandSubsurfacePosition position) {
        if (allocation.mode()
                != SkyIslandMaterialExpressionMode.CONDITIONED_EXPRESSION_CLAIM) {
            throw new IllegalArgumentException(
                    "conditionedMargin requires a conditioned expression allocation");
        }
        double field = spatialField(allocation, position);
        if (field >= allocation.targetExpression()) {
            return 0.0;
        }
        return (allocation.targetExpression() - field)
                / allocation.targetExpression();
    }

    private static int stableTieOrder(
            SkyIslandMaterialExpressionAllocation first,
            SkyIslandMaterialExpressionAllocation second) {
        Comparator<SkyIslandMaterialExpressionAllocation> comparator =
                Comparator.comparing(
                                (SkyIslandMaterialExpressionAllocation allocation) ->
                                        allocation.bindingKey().canonicalToken())
                        .thenComparingInt(allocation -> allocation.role().ordinal());
        return comparator.compare(first, second);
    }
}
