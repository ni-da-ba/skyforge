package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/** Atomic F4C integer-column result for one complete F3E/F4A terminal component. */
public record SkyIslandFluvialVoxelComponentPlan(
        SkyIslandHydraulicTerminalComponent component,
        SkyIslandFluvialVoxelComponentStatus status,
        List<SkyIslandFluvialVoxelColumn> columns,
        List<String> blockers) {

    public SkyIslandFluvialVoxelComponentPlan {
        component = Objects.requireNonNull(component, "component");
        status = Objects.requireNonNull(status, "status");
        columns = List.copyOf(columns);
        blockers = List.copyOf(blockers);
        columns.forEach(value -> Objects.requireNonNull(value, "column"));
        blockers.forEach(value -> {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("F4C blocker must be non-blank");
            }
        });
        if (component.status() != SkyIslandHydraulicAssemblyStatus.QUALIFIED) {
            throw new IllegalArgumentException(
                    "F4C consumes only F3E-qualified realized components");
        }
        if (status == SkyIslandFluvialVoxelComponentStatus.QUALIFIED) {
            if (!blockers.isEmpty() || columns.isEmpty()) {
                throw new IllegalArgumentException(
                        "qualified F4C component requires columns and no blockers");
            }
        } else {
            if (blockers.isEmpty()) {
                throw new IllegalArgumentException(
                        "rejected F4C component requires blocker evidence");
            }
            if (!columns.isEmpty()) {
                throw new IllegalArgumentException(
                        "rejected F4C component cannot expose partial voxel authority");
            }
        }
    }

    public int terminalCellIndex() {
        return component.terminalFate().channelTerminalCellIndex();
    }
}
