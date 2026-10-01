package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Characterizes profile transitions and authored drops inside semantic macro reaches.
 *
 * <p>This diagnostic exists to decide whether a rejected long mixed reach should be retried by
 * explicit drop ownership, route refinement, or another upstream correction. It changes no
 * routing, hydraulics, terrain, or qualification.
 */
public final class SkyIslandHydraulicDiscontinuityDiagnosticsPlanner {
    private SkyIslandHydraulicDiscontinuityDiagnosticsPlanner() {}

    public static List<SkyIslandHydraulicDiscontinuityDiagnostics> measure(
            SkyIslandDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        SkyIslandSemanticChannelReachPlan reaches =
                SkyIslandSemanticChannelReachPlanner.plan(descriptor);
        SkyIslandWatershedPlan watershed =
                SkyIslandWatershedPlanner.plan(descriptor);
        Map<Integer, SkyIslandWatershedCell> cells = new HashMap<>();
        for (SkyIslandWatershedCell cell : watershed.cells()) {
            cells.put(cell.index(), cell);
        }

        List<SkyIslandHydraulicDiscontinuityDiagnostics> result = new ArrayList<>();
        for (SkyIslandSemanticChannelReach reach : reaches.reaches()) {
            List<SkyIslandChannelProfile> profiles = reach.profiles();
            int transitions = 0;
            int cascadeCount = 0;
            double accumulatedDownhillDrop = 0.0;
            double cascadeDrop = 0.0;
            double maximumDrop = 0.0;

            SkyIslandChannelProfileKind priorKind = null;
            for (SkyIslandChannelProfile profile : profiles) {
                if (priorKind != null && profile.kind() != priorKind) {
                    transitions++;
                }
                priorKind = profile.kind();
                if (profile.kind() == SkyIslandChannelProfileKind.CASCADE) {
                    cascadeCount++;
                }

                SkyIslandChannelSegment segment = profile.segment();
                SkyIslandWatershedCell source = requireCell(cells, segment.sourceCellIndex());
                SkyIslandWatershedCell downstream =
                        requireCell(cells, segment.downstreamCellIndex());
                double drop =
                        Math.max(0.0, source.surfacePotential() - downstream.surfacePotential());
                accumulatedDownhillDrop += drop;
                maximumDrop = Math.max(maximumDrop, drop);
                if (profile.kind() == SkyIslandChannelProfileKind.CASCADE) {
                    cascadeDrop += drop;
                }
            }

            SkyIslandWatershedCell source = requireCell(cells, reach.startCellIndex());
            SkyIslandWatershedCell terminal = requireCell(cells, reach.endCellIndex());
            double netDrop =
                    Math.max(0.0, source.surfacePotential() - terminal.surfacePotential());

            result.add(new SkyIslandHydraulicDiscontinuityDiagnostics(
                    reach,
                    transitions,
                    cascadeCount,
                    source.surfacePotential(),
                    terminal.surfacePotential(),
                    netDrop,
                    accumulatedDownhillDrop,
                    cascadeDrop,
                    maximumDrop));
        }
        return List.copyOf(result);
    }

    private static SkyIslandWatershedCell requireCell(
            Map<Integer, SkyIslandWatershedCell> cells,
            int index) {
        SkyIslandWatershedCell cell = cells.get(index);
        if (cell == null) {
            throw new IllegalStateException("missing watershed cell " + index);
        }
        return cell;
    }
}
