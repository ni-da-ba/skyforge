package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/** Complete F4E water-head qualification for one F4A-realized terminal component. */
public record SkyIslandWorldWaterComponentQualification(
        SkyIslandHydraulicTerminalComponent component,
        SkyIslandWorldWaterComponentStatus status,
        List<SkyIslandWorldWaterReachQualification> reaches,
        List<String> blockers) {

    public SkyIslandWorldWaterComponentQualification {
        component = Objects.requireNonNull(component, "component");
        status = Objects.requireNonNull(status, "status");
        reaches = List.copyOf(reaches);
        blockers = List.copyOf(blockers);
        reaches.forEach(value -> Objects.requireNonNull(value, "reach qualification"));
        blockers.forEach(value -> {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("F4E blocker must be non-blank");
            }
        });
        if (status == SkyIslandWorldWaterComponentStatus.QUALIFIED) {
            if (reaches.isEmpty() || reaches.stream().anyMatch(reach -> !reach.accepted())
                    || !blockers.isEmpty()) {
                throw new IllegalArgumentException(
                        "qualified F4E component requires accepted reaches and no blockers");
            }
        } else if (blockers.isEmpty()) {
            throw new IllegalArgumentException(
                    "non-qualified F4E component requires blocker evidence");
        }
    }

    public int terminalCellIndex() {
        return component.terminalFate().channelTerminalCellIndex();
    }
}
