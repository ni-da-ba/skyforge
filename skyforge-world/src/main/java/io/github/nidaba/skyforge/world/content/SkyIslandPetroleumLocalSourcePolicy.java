package io.github.nidaba.skyforge.world.content;

import io.github.nidaba.skyforge.world.SkyIslandPetroleumSystemOpportunityCell;
import io.github.nidaba.skyforge.world.SkyIslandPetroleumSystemOpportunityProfile;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * CONTENT C26 local literal-source admission policy over exact AUTH-0098 petroleum-system evidence.
 *
 * <p>This policy exposes where downstream physical petroleum realization may be considered. It does
 * not select a deposit, assign quantity, or convert opportunity magnitude into physical production.
 */
public final class SkyIslandPetroleumLocalSourcePolicy {

    public record SourceColumn(
            int xIndex,
            int zIndex,
            List<SkyIslandPetroleumSystemOpportunityCell> eligibleCells) {
        public SourceColumn {
            if (xIndex < 0 || zIndex < 0) {
                throw new IllegalArgumentException("source-column indices must be non-negative");
            }
            eligibleCells = List.copyOf(Objects.requireNonNull(eligibleCells, "eligibleCells"));
            if (eligibleCells.isEmpty()) {
                throw new IllegalArgumentException("source column requires at least one eligible cell");
            }
            for (SkyIslandPetroleumSystemOpportunityCell cell : eligibleCells) {
                Objects.requireNonNull(cell, "eligible source cell");
                if (!locallyEligible(cell)) {
                    throw new IllegalArgumentException(
                            "source column cannot contain zero-opportunity AUTH-0098 cells");
                }
                if (cell.sourceCell().xIndex() != xIndex || cell.sourceCell().zIndex() != zIndex) {
                    throw new IllegalArgumentException(
                            "source column cells must preserve one exact AUTH-0098 x/z column");
                }
            }
        }
    }

    public record Plan(
            SkyIslandPetroleumSystemOpportunityProfile sourceProfile,
            List<SkyIslandPetroleumSystemOpportunityCell> eligibleCells,
            List<SourceColumn> eligibleColumns) {
        public Plan {
            sourceProfile = Objects.requireNonNull(sourceProfile, "sourceProfile");
            eligibleCells = List.copyOf(Objects.requireNonNull(eligibleCells, "eligibleCells"));
            eligibleColumns = List.copyOf(Objects.requireNonNull(eligibleColumns, "eligibleColumns"));

            List<SkyIslandPetroleumSystemOpportunityCell> expectedCells =
                    expectedEligibleCells(sourceProfile);
            if (!eligibleCells.equals(expectedCells)) {
                throw new IllegalArgumentException(
                        "C26 eligible cells must be the exact nonzero AUTH-0098 cell view");
            }
            List<SourceColumn> expectedColumns = expectedColumns(expectedCells);
            if (!eligibleColumns.equals(expectedColumns)) {
                throw new IllegalArgumentException(
                        "C26 eligible columns must derive exactly from eligible AUTH-0098 cells");
            }
        }
    }

    public Plan plan(SkyIslandPetroleumSystemOpportunityProfile profile) {
        Objects.requireNonNull(profile, "profile");
        List<SkyIslandPetroleumSystemOpportunityCell> cells = expectedEligibleCells(profile);
        return new Plan(profile, cells, expectedColumns(cells));
    }

    /** Exact AUTH-0098 zero/nonzero local admission boundary. */
    public static boolean locallyEligible(SkyIslandPetroleumSystemOpportunityCell cell) {
        Objects.requireNonNull(cell, "cell");
        return cell.systemOpportunity() > 0.0;
    }

    private static List<SkyIslandPetroleumSystemOpportunityCell> expectedEligibleCells(
            SkyIslandPetroleumSystemOpportunityProfile profile) {
        return profile.cells().stream()
                .filter(SkyIslandPetroleumLocalSourcePolicy::locallyEligible)
                .toList();
    }

    private static List<SourceColumn> expectedColumns(
            List<SkyIslandPetroleumSystemOpportunityCell> eligibleCells) {
        Map<ColumnKey, ArrayList<SkyIslandPetroleumSystemOpportunityCell>> grouped =
                new LinkedHashMap<>();
        for (SkyIslandPetroleumSystemOpportunityCell cell : eligibleCells) {
            ColumnKey key =
                    new ColumnKey(cell.sourceCell().xIndex(), cell.sourceCell().zIndex());
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(cell);
        }

        ArrayList<SourceColumn> result = new ArrayList<>(grouped.size());
        grouped.forEach((key, cells) ->
                result.add(new SourceColumn(key.xIndex(), key.zIndex(), cells)));
        return List.copyOf(result);
    }

    private record ColumnKey(int xIndex, int zIndex) {}
}
