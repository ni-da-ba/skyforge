package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * F4E backend-neutral projection of the accepted F4A hydraulic water surface into the exact F4B
 * compiled world frame.
 *
 * <p>F4E does not solve a new grade. It preserves the F4A water-surface offset from original
 * semantic terrain using the same relief-budget mapping as F4B.
 */
public final class SkyIslandComponentFluvialWorldWaterProjection {
    private final SkyIslandAuthoredRealizationAssociation association;
    private final SkyIslandComponentFluvialTerrainCandidatePlan candidatePlan;
    private final SkyIslandComponentFluvialWorldSurfaceProjection terrainProjection;

    public SkyIslandComponentFluvialWorldWaterProjection(
            SkyIslandAuthoredRealizationAssociation association,
            SkyIslandComponentFluvialTerrainCandidatePlan candidatePlan) {
        this.association = Objects.requireNonNull(association, "association");
        this.candidatePlan = Objects.requireNonNull(candidatePlan, "candidatePlan");
        if (!association.authoredDescriptor().equals(candidatePlan.descriptor())) {
            throw new IllegalArgumentException(
                    "F4E association and F4A candidate must share one authored descriptor");
        }
        this.terrainProjection =
                new SkyIslandComponentFluvialWorldSurfaceProjection(
                        association, candidatePlan);
    }

    public SkyIslandProjectedFluvialWaterSample sampleWorld(
            double worldX,
            double worldZ) {
        SkyIslandProjectedFluvialTerrainSample terrain =
                terrainProjection.sampleWorld(worldX, worldZ);
        SkyIslandQualifiedFluvialSample semantic = terrain.semanticSample();
        if (!semantic.wet()) {
            return new SkyIslandProjectedFluvialWaterSample(
                    terrain, OptionalDouble.empty(), 0.0);
        }

        double reliefBudget = association.authoredDescriptor().reliefBudget();
        double waterSurfaceWorldY =
                terrain.originalUpperSurfaceWorldY()
                        + (semantic.waterSurfacePotential()
                                        - semantic.originalTerrainPotential())
                                * reliefBudget;
        double waterDepthWorld =
                waterSurfaceWorldY - terrain.targetUpperSurfaceWorldY();

        double semanticDepthWorld =
                (semantic.waterSurfacePotential()
                                - semantic.targetTerrainPotential())
                        * reliefBudget;
        if (Math.abs(waterDepthWorld - semanticDepthWorld) > 1.0e-9) {
            throw new IllegalStateException(
                    "F4E world mapping changed accepted F4A hydraulic depth");
        }

        return new SkyIslandProjectedFluvialWaterSample(
                terrain,
                OptionalDouble.of(waterSurfaceWorldY),
                waterDepthWorld);
    }

    public SkyIslandAuthoredRealizationAssociation association() {
        return association;
    }

    public SkyIslandComponentFluvialTerrainCandidatePlan candidatePlan() {
        return candidatePlan;
    }
}
