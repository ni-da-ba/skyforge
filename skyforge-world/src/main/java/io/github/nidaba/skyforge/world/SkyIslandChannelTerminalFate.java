package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Explicit semantic linkage from one channel-network terminal through watershed routing to its fate.
 */
public record SkyIslandChannelTerminalFate(
        int channelTerminalCellIndex,
        int watershedTerminalCellIndex,
        SkyIslandChannelTerminalFateKind kind,
        Optional<SkyIslandWaterbodyKind> waterbodyKind,
        List<Integer> watershedPath) {

    public SkyIslandChannelTerminalFate {
        if (channelTerminalCellIndex < 0 || watershedTerminalCellIndex < 0) {
            throw new IllegalArgumentException("terminal-fate cell indices must be non-negative");
        }
        kind = Objects.requireNonNull(kind, "kind");
        waterbodyKind = Objects.requireNonNull(waterbodyKind, "waterbodyKind");
        watershedPath = List.copyOf(watershedPath);
        if (watershedPath.isEmpty()
                || watershedPath.getFirst() != channelTerminalCellIndex
                || watershedPath.getLast() != watershedTerminalCellIndex) {
            throw new IllegalArgumentException(
                    "watershed path must run from channel terminal to watershed terminal");
        }
        boolean retained = kind == SkyIslandChannelTerminalFateKind.RETAINED_OPEN_WATER
                || kind == SkyIslandChannelTerminalFateKind.RETAINED_WETLAND;
        if (retained != waterbodyKind.isPresent()) {
            throw new IllegalArgumentException(
                    "retained terminal fate must carry exactly one waterbody kind");
        }
        if (kind == SkyIslandChannelTerminalFateKind.RETAINED_OPEN_WATER
                && waterbodyKind.orElseThrow() == SkyIslandWaterbodyKind.WETLAND) {
            throw new IllegalArgumentException("open-water fate cannot carry WETLAND kind");
        }
        if (kind == SkyIslandChannelTerminalFateKind.RETAINED_WETLAND
                && waterbodyKind.orElseThrow() != SkyIslandWaterbodyKind.WETLAND) {
            throw new IllegalArgumentException("wetland fate must carry WETLAND kind");
        }
    }
}
