package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** Retained-water or wetland channel-terminal ownership site before basin transition realization. */
public record SkyIslandHydraulicBasinTransitionSite(
        SkyIslandChannelTerminalFate terminalFate,
        SkyIslandHydraulicTransitionBoundaryState riverBoundary) {

    public SkyIslandHydraulicBasinTransitionSite {
        terminalFate = Objects.requireNonNull(terminalFate, "terminalFate");
        riverBoundary = Objects.requireNonNull(riverBoundary, "riverBoundary");
        if (terminalFate.kind() != SkyIslandChannelTerminalFateKind.RETAINED_OPEN_WATER
                && terminalFate.kind() != SkyIslandChannelTerminalFateKind.RETAINED_WETLAND) {
            throw new IllegalArgumentException("basin transition requires retained terminal fate");
        }
        if (riverBoundary.role() != SkyIslandHydraulicTransitionBoundaryRole.INCOMING
                || Math.abs(riverBoundary.stationFraction() - 1.0) > 1.0e-10
                || riverBoundary.reachEndCellIndex() != terminalFate.channelTerminalCellIndex()) {
            throw new IllegalArgumentException(
                    "basin transition boundary must be the exact incoming channel terminal");
        }
    }
}
