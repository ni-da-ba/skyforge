package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Measures continuous retained-basin geometry before any littoral grading or terrain mutation. */
public final class SkyIslandContinuousWaterbodyDiagnosticsPlanner {
    private SkyIslandContinuousWaterbodyDiagnosticsPlanner() {}

    public static List<SkyIslandContinuousWaterbodyDiagnostics> measure(
            SkyIslandDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        SkyIslandContinuousWaterbodyPlan plan =
                SkyIslandContinuousWaterbodyPlanner.plan(descriptor);
        SkyIslandSemanticField terrain =
                SkyIslandPreHydrologicTerrainField.create(descriptor);
        SkyIslandHydraulicChannelNetworkPlan hydraulics =
                SkyIslandHydraulicChannelNetworkPlanner.plan(descriptor);
        List<SkyIslandContinuousWaterbodyDiagnostics> result =
                new ArrayList<>(plan.basins().size());
        for (SkyIslandContinuousWaterbodyBasin basin : plan.basins()) {
            result.add(measure(descriptor, basin, terrain, hydraulics));
        }
        return List.copyOf(result);
    }

    static SkyIslandContinuousWaterbodyDiagnostics measure(
            SkyIslandDescriptor descriptor,
            SkyIslandContinuousWaterbodyBasin basin,
            SkyIslandSemanticField terrain,
            SkyIslandHydraulicChannelNetworkPlan hydraulics) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(basin, "basin");
        Objects.requireNonNull(terrain, "terrain");
        Objects.requireNonNull(hydraulics, "hydraulics");

        double equivalentDiameter =
                2.0 * Math.sqrt(basin.approximateArea() / Math.PI);
        double maximumDepthWorld =
                basin.maximumDepthPotential() * descriptor.reliefBudget();
        double depthToDiameter =
                maximumDepthWorld / equivalentDiameter;
        double spillHeadroom =
                Math.max(0.0, basin.spillSurfacePotential() - basin.waterSurfacePotential())
                        * descriptor.reliefBudget();

        double maximumShorelineGrade = 0.0;
        double delta = basin.sampleSpacing();
        for (SkyIslandLocalPosition point : basin.shorelineCrossings()) {
            double dx = terrain.sample(new SkyIslandLocalPosition(point.x() + delta, point.z()))
                    - terrain.sample(new SkyIslandLocalPosition(point.x() - delta, point.z()));
            double dz = terrain.sample(new SkyIslandLocalPosition(point.x(), point.z() + delta))
                    - terrain.sample(new SkyIslandLocalPosition(point.x(), point.z() - delta));
            double worldDx = dx * descriptor.reliefBudget() / (2.0 * delta);
            double worldDz = dz * descriptor.reliefBudget() / (2.0 * delta);
            maximumShorelineGrade =
                    Math.max(maximumShorelineGrade, Math.hypot(worldDx, worldDz));
        }

        int matchedTerminalReachCount = 0;
        double minimumChannelDatumOffset = Double.POSITIVE_INFINITY;
        double maximumChannelDatumOffset = Double.NEGATIVE_INFINITY;
        int sinkCell = basin.sourceCandidate().sinkCellIndex();
        for (SkyIslandHydraulicReachGeometry reach : hydraulics.reaches()) {
            if (reach.geomorphicRoute().semanticReach().endCellIndex() != sinkCell) {
                continue;
            }
            matchedTerminalReachCount++;
            double offset =
                    (reach.endWaterSurfacePotential() - basin.waterSurfacePotential())
                            * descriptor.reliefBudget();
            minimumChannelDatumOffset = Math.min(minimumChannelDatumOffset, offset);
            maximumChannelDatumOffset = Math.max(maximumChannelDatumOffset, offset);
        }
        if (matchedTerminalReachCount == 0) {
            minimumChannelDatumOffset = 0.0;
            maximumChannelDatumOffset = 0.0;
        }
        double maximumChannelDatumMismatch =
                Math.max(Math.abs(minimumChannelDatumOffset), Math.abs(maximumChannelDatumOffset));

        return new SkyIslandContinuousWaterbodyDiagnostics(
                basin,
                equivalentDiameter,
                maximumDepthWorld,
                depthToDiameter,
                maximumShorelineGrade,
                spillHeadroom,
                matchedTerminalReachCount,
                minimumChannelDatumOffset,
                maximumChannelDatumOffset,
                maximumChannelDatumMismatch,
                basin.reachesSearchBoundary(),
                basin.shorelineCrossings().size());
    }
}
