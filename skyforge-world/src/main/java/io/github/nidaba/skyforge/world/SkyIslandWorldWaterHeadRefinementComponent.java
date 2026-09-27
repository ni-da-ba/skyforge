package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/** Atomic F4F outcome for one F4E component requiring world-head refinement. */
public record SkyIslandWorldWaterHeadRefinementComponent(
        SkyIslandHydraulicTerminalComponent component,
        SkyIslandWorldWaterHeadRefinementStatus status,
        List<SkyIslandWorldWaterHeadRefinementReach> reaches,
        List<String> blockers) {

    public SkyIslandWorldWaterHeadRefinementComponent {
        component = Objects.requireNonNull(component, "component");
        status = Objects.requireNonNull(status, "status");
        reaches = List.copyOf(reaches);
        blockers = List.copyOf(blockers);
        reaches.forEach(value -> Objects.requireNonNull(value, "reach"));
        blockers.forEach(value -> {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("F4F blocker must be non-blank");
            }
        });
        if (status == SkyIslandWorldWaterHeadRefinementStatus.SOLVED) {
            if (reaches.isEmpty()
                    || reaches.stream().anyMatch(value ->
                            value.status() != SkyIslandWorldWaterHeadRefinementStatus.SOLVED)
                    || !blockers.isEmpty()) {
                throw new IllegalArgumentException("solved F4F component requires only solved reaches");
            }
        } else if (blockers.isEmpty()) {
            throw new IllegalArgumentException("blocked F4F component requires blocker evidence");
        }
    }

    public int terminalCellIndex() {
        return component.terminalFate().channelTerminalCellIndex();
    }
}
