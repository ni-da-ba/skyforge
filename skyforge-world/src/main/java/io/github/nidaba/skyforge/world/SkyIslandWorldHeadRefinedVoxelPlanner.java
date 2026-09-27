package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * F4H removal-only re-quantization over exactly the already-authorized F4C columns.
 *
 * <p>No footprint discovery occurs here. F4H proves that shallower F4G continuous terrain can only
 * remove the same or fewer exact solid voxels than accepted F4C authority.
 */
public final class SkyIslandWorldHeadRefinedVoxelPlanner {
    private static final double EPSILON = 1.0e-9;

    private SkyIslandWorldHeadRefinedVoxelPlanner() {}

    public static SkyIslandWorldHeadRefinedVoxelPlan plan(
            SkyIslandWorldHeadRefinedTerrainPlan refinedTerrain) {
        Objects.requireNonNull(refinedTerrain, "refinedTerrain");

        SkyIslandFluvialVoxelQuantizationPlan direct =
                refinedTerrain.headRefinement()
                        .directQualification()
                        .terrainVoxelPlan();
        SkyIslandAuthoredRealizationAssociation association =
                direct.association();
        var volume = association.realizedVolume().compiledVolume();
        SkyIslandTerrainInterpreter terrain =
                new SkyIslandTerrainInterpreter(volume, SkyIslandTerrainProfile.reference());
        double relief = association.authoredDescriptor().reliefBudget();

        List<SkyIslandWorldHeadRefinedVoxelComponentPlan> components =
                new ArrayList<>();
        for (SkyIslandFluvialVoxelComponentPlan directComponent : direct.components()) {
            if (directComponent.status() != SkyIslandFluvialVoxelComponentStatus.QUALIFIED) {
                continue;
            }

            List<SkyIslandWorldHeadRefinedVoxelColumn> columns =
                    new ArrayList<>(directComponent.columns().size());
            for (SkyIslandFluvialVoxelColumn source : directComponent.columns()) {
                double localX = source.worldX() - volume.descriptor().centerX();
                double localZ = source.worldZ() - volume.descriptor().centerZ();
                SkyIslandLocalPosition local =
                        new SkyIslandLocalPosition(localX, localZ);
                SkyIslandQualifiedFluvialSample semantic =
                        refinedTerrain.terrainField().sampleDetailed(local);

                double upper =
                        terrain.upperSurfaceHeight(source.worldX(), source.worldZ());
                double underside =
                        terrain.undersideSurfaceHeight(source.worldX(), source.worldZ());
                double deltaWorld = semantic.terrainDeltaPotential() * relief;
                double targetUpper = upper + deltaWorld;
                SkyIslandProjectedFluvialTerrainSample projection =
                        new SkyIslandProjectedFluvialTerrainSample(
                                local,
                                semantic,
                                upper,
                                underside,
                                deltaWorld,
                                targetUpper);

                if (targetUpper + EPSILON
                        < source.projection().targetUpperSurfaceWorldY()) {
                    throw new IllegalStateException(
                            "F4H refined continuous terrain deepened an accepted F4C column");
                }

                int targetMaximumSolidY =
                        ceilToInt(targetUpper) - 1;
                targetMaximumSolidY =
                        Math.min(
                                targetMaximumSolidY,
                                source.originalSupport().maximumSolidY());
                if (targetMaximumSolidY < source.targetMaximumSolidY()) {
                    throw new IllegalStateException(
                            "F4H quantized target deepened an accepted F4C column");
                }
                if (!terrain.classify(
                                source.worldX(),
                                targetMaximumSolidY,
                                source.worldZ())
                        .isSolid()) {
                    throw new IllegalStateException(
                            "F4H retained refined target top is not exact compiled solid");
                }
                for (int y = targetMaximumSolidY + 1;
                        y <= source.originalSupport().maximumSolidY();
                        y++) {
                    if (!terrain.classify(source.worldX(), y, source.worldZ()).isSolid()) {
                        throw new IllegalStateException(
                                "F4H refined removal band contains non-owned AIR");
                    }
                }

                double quantizedUpper = targetMaximumSolidY + 1.0;
                double residual = quantizedUpper - targetUpper;
                int removed =
                        source.originalSupport().maximumSolidY()
                                - targetMaximumSolidY;
                columns.add(new SkyIslandWorldHeadRefinedVoxelColumn(
                        source,
                        projection,
                        targetMaximumSolidY,
                        quantizedUpper,
                        residual,
                        removed));
            }

            columns.sort(Comparator
                    .comparingInt((SkyIslandWorldHeadRefinedVoxelColumn value) ->
                            value.directColumn().worldX())
                    .thenComparingInt(value -> value.directColumn().worldZ()));
            components.add(new SkyIslandWorldHeadRefinedVoxelComponentPlan(
                    directComponent, columns));
        }

        components.sort(Comparator.comparingInt(
                SkyIslandWorldHeadRefinedVoxelComponentPlan::terminalCellIndex));
        SkyIslandWorldHeadRefinedVoxelPlan result =
                new SkyIslandWorldHeadRefinedVoxelPlan(
                        refinedTerrain, components);
        if (result.refinedRemovedSolidBlocks() > result.directRemovedSolidBlocks()) {
            throw new IllegalStateException(
                    "F4H aggregate removal count exceeded accepted F4C authority");
        }
        return result;
    }

    private static int ceilToInt(double value) {
        double v = Math.ceil(value);
        if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("F4H target coordinate exceeds integer range");
        }
        return (int) v;
    }
}
