package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.List;

/**
 * Interprets AUTH-0031 material character and AUTH-0032 overlapping domains as a small semantic
 * material-family vocabulary.
 *
 * <p>The planner intentionally preserves overlap. Host-fabric families describe broad host identity;
 * conditioned-host families remain gated by accepted AUTH-0032 mesoscale domains. No named rocks,
 * minerals, blocks, or backend palette choices are introduced here.
 */
public final class SkyIslandMaterialFamilyPlanner {
    public static final double EVIDENCE_EXPRESSION_THRESHOLD = 0.50;

    private SkyIslandMaterialFamilyPlanner() {}

    public static SkyIslandMaterialFamilyPlan plan(SkyIslandDescriptor descriptor) {
        SkyIslandMaterialDomainPlan domainPlan = SkyIslandMaterialDomainPlanner.plan(descriptor);
        SkyIslandSubsurfaceMaterialFieldSet material =
                SkyIslandSubsurfaceMaterialFieldSet.create(descriptor);

        int gridSize = domainPlan.gridSize();
        int depthSamples = domainPlan.depthSamples();
        int total = gridSize * depthSamples * gridSize;
        double[][] domainMembership =
                new double[SkyIslandMaterialDomainKind.values().length][total];

        for (SkyIslandMaterialDomain domain : domainPlan.domains()) {
            double[] membership = domainMembership[domain.kind().ordinal()];
            for (SkyIslandMaterialDomainCell cell : domain.cells()) {
                membership[cell.index()] = Math.max(membership[cell.index()], cell.membership());
            }
        }

        double radius = descriptor.nominalRadius();
        double horizontalSpacing = domainPlan.horizontalSpacing();
        double depthSpacing = domainPlan.depthSpacing();
        List<SkyIslandMaterialFamilyCell> cells =
                new ArrayList<>(domainPlan.activeHostCells());

        for (int iz = 0; iz < gridSize; iz++) {
            double z = -radius + iz * horizontalSpacing;
            for (int id = 0; id < depthSamples; id++) {
                double depth = id * depthSpacing;
                for (int ix = 0; ix < gridSize; ix++) {
                    int index = index(ix, id, iz, gridSize, depthSamples);
                    double x = -radius + ix * horizontalSpacing;
                    SkyIslandSubsurfacePosition position =
                            new SkyIslandSubsurfacePosition(x, z, depth);
                    SkyIslandSubsurfaceMaterialSample sample = material.sample(position);
                    if (!sample.materialPresent()) {
                        continue;
                    }

                    double alteredDomain = domainMembership[
                            SkyIslandMaterialDomainKind.ALTERED_ZONE.ordinal()][index];
                    double saturatedDomain = domainMembership[
                            SkyIslandMaterialDomainKind.SATURATED_BODY.ordinal()][index];
                    double mineralizedDomain = domainMembership[
                            SkyIslandMaterialDomainKind.MINERALIZED_BODY.ordinal()][index];
                    double fabricDomain = domainMembership[
                            SkyIslandMaterialDomainKind.STRUCTURAL_FABRIC_DOMAIN.ordinal()][index];

                    double coherentMassiveHost = clamp01(
                            0.18
                                    + 0.50 * sample.matrixIntegrity()
                                    + 0.22 * (1.0 - sample.alteration())
                                    + 0.10 * (1.0 - fabricDomain));
                    coherentMassiveHost *= 1.0 - 0.32 * fabricDomain;

                    double layeredFabricRichHost = fabricDomain > 0.0
                            ? clamp01(
                                    0.20
                                            + 0.34 * sample.matrixIntegrity()
                                            + 0.16 * (1.0 - sample.alteration())
                                            + 0.30 * fabricDomain)
                            : 0.0;

                    double stronglyAlteredHost = alteredDomain > 0.0
                            ? clamp01(
                                    0.46 * sample.alteration()
                                            + 0.30 * alteredDomain
                                            + 0.16 * (1.0 - sample.matrixIntegrity())
                                            + 0.08 * sample.caveWallAlteration())
                            : 0.0;

                    double waterConditionedHost = saturatedDomain > 0.0
                            ? clamp01(
                                    0.54 * sample.saturation()
                                            + 0.36 * saturatedDomain
                                            + 0.10 * sample.matrixIntegrity())
                            : 0.0;

                    double mineralBearingStructuralHost = mineralizedDomain > 0.0
                            ? clamp01(
                                    0.50 * sample.mineralizationTendency()
                                            + 0.34 * mineralizedDomain
                                            + 0.10 * Math.max(fabricDomain, alteredDomain)
                                            + 0.06 * sample.matrixIntegrity())
                            : 0.0;

                    cells.add(new SkyIslandMaterialFamilyCell(
                            index,
                            ix,
                            id,
                            iz,
                            position,
                            coherentMassiveHost,
                            layeredFabricRichHost,
                            stronglyAlteredHost,
                            waterConditionedHost,
                            mineralBearingStructuralHost));
                }
            }
        }

        if (cells.size() != domainPlan.activeHostCells()) {
            throw new IllegalStateException(
                    "AUTH-0033 active host volume diverged from AUTH-0032 planning volume");
        }

        return new SkyIslandMaterialFamilyPlan(
                descriptor,
                gridSize,
                depthSamples,
                horizontalSpacing,
                depthSpacing,
                domainPlan.activeHostCells(),
                cells);
    }

    private static int index(
            int ix,
            int depthIndex,
            int iz,
            int gridSize,
            int depthSamples) {
        return (iz * depthSamples + depthIndex) * gridSize + ix;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
