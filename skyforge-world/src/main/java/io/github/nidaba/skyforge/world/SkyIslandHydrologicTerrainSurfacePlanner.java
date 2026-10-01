package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Applies accepted hydrologic terrain-response semantics to a derived coarse authored surface. */
public final class SkyIslandHydrologicTerrainSurfacePlanner {
    public static final double MAX_LOWERING = 0.16;
    public static final double MAX_RAISING = 0.08;

    private SkyIslandHydrologicTerrainSurfacePlanner() {}

    /** Historical/raw visible-channel diagnostic retained for accepted AUTH-0015 evidence. */
    public static SkyIslandHydrologicTerrainSurfacePlan plan(SkyIslandDescriptor descriptor) {
        return plan(
                descriptor,
                SkyIslandHydrologicTerrainInfluencePlanner.plan(descriptor),
                SkyIslandRiparianCorridorPlanner.plan(descriptor));
    }

    /** Applies one explicit internally consistent hydrologic influence/riparian composition. */
    public static SkyIslandHydrologicTerrainSurfacePlan plan(
            SkyIslandDescriptor descriptor,
            SkyIslandHydrologicTerrainInfluencePlan influence,
            SkyIslandRiparianCorridorPlan riparian) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(influence, "influence");
        Objects.requireNonNull(riparian, "riparian");
        SkyIslandWatershedPlan watershed = SkyIslandWatershedPlanner.plan(descriptor);
        SkyIslandWaterbodyFootprintPlan waterbodies = SkyIslandWaterbodyFootprintPlanner.plan(descriptor);
        SkyIslandWaterbodyMarginPlan margins = SkyIslandWaterbodyMarginPlanner.plan(descriptor);

        Map<Integer, SkyIslandWatershedCell> watershedByIndex = new HashMap<>();
        for (SkyIslandWatershedCell cell : watershed.cells()) {
            watershedByIndex.put(cell.index(), cell);
        }

        Map<Integer, SkyIslandHydrologicTerrainCell> influenceByIndex = new HashMap<>();
        for (SkyIslandHydrologicTerrainCell cell : influence.cells()) {
            influenceByIndex.put(cell.watershedCellIndex(), cell);
        }

        Map<Integer, SkyIslandRiparianCell> riparianByIndex = new HashMap<>();
        for (SkyIslandRiparianCell cell : riparian.cells()) {
            riparianByIndex.put(cell.watershedCellIndex(), cell);
        }

        Set<Integer> reserved = new HashSet<>();
        for (SkyIslandWaterbodyFootprint footprint : waterbodies.footprints()) {
            for (SkyIslandWaterbodyFootprintCell cell : footprint.cells()) {
                reserved.add(cell.watershedCellIndex());
            }
        }
        for (SkyIslandWaterbodyMargin margin : margins.margins()) {
            for (SkyIslandWaterbodyMarginCell cell : margin.cells()) {
                reserved.add(cell.watershedCellIndex());
            }
        }

        List<SkyIslandHydrologicTerrainSurfaceCell> result = new ArrayList<>(watershed.cells().size());
        for (SkyIslandWatershedCell watershedCell : watershed.cells()) {
            int index = watershedCell.index();
            double base = clamp01(watershedCell.surfacePotential());
            SkyIslandHydrologicTerrainCell terrainInfluence = influenceByIndex.get(index);
            if (reserved.contains(index) || terrainInfluence == null) {
                result.add(unchanged(watershedCell, base));
                continue;
            }

            double incisionLowering = 0.115 * terrainInfluence.incisionPotential();
            double dropLowering = 0.055 * terrainInfluence.dropShapingPotential();
            double depositionRaising = 0.045
                    * terrainInfluence.depositionPotential()
                    * (1.0 - 0.55 * terrainInfluence.incisionPotential());

            double floodplainAdjustment = 0.0;
            SkyIslandRiparianCell riparianCell = riparianByIndex.get(index);
            if (riparianCell != null && terrainInfluence.floodplainPotential() > 0.0) {
                SkyIslandWatershedCell source = requireCell(
                        watershedByIndex, riparianCell.channelSourceCellIndex());
                SkyIslandWatershedCell downstream = requireCell(
                        watershedByIndex, riparianCell.channelDownstreamCellIndex());
                double channelGrade = 0.5 * (source.surfacePotential() + downstream.surfacePotential());
                floodplainAdjustment = (channelGrade - base)
                        * 0.55
                        * terrainInfluence.floodplainPotential();
            }

            double rawNet = depositionRaising
                    + floodplainAdjustment
                    - incisionLowering
                    - dropLowering;
            double boundedNet = clamp(rawNet, -MAX_LOWERING, MAX_RAISING);
            double adjusted = clamp01(base + boundedNet);
            result.add(new SkyIslandHydrologicTerrainSurfaceCell(
                    index,
                    watershedCell.position(),
                    base,
                    adjusted,
                    incisionLowering,
                    depositionRaising,
                    floodplainAdjustment,
                    dropLowering));
        }

        return new SkyIslandHydrologicTerrainSurfacePlan(
                descriptor,
                watershed.gridSize(),
                watershed.spacing(),
                result);
    }

    private static SkyIslandHydrologicTerrainSurfaceCell unchanged(
            SkyIslandWatershedCell cell,
            double base) {
        return new SkyIslandHydrologicTerrainSurfaceCell(
                cell.index(), cell.position(), base, base, 0.0, 0.0, 0.0, 0.0);
    }

    private static SkyIslandWatershedCell requireCell(
            Map<Integer, SkyIslandWatershedCell> cells,
            int index) {
        SkyIslandWatershedCell cell = cells.get(index);
        if (cell == null) {
            throw new IllegalStateException("surface shaping references missing watershed cell " + index);
        }
        return cell;
    }

    private static double clamp01(double value) {
        return clamp(value, 0.0, 1.0);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
