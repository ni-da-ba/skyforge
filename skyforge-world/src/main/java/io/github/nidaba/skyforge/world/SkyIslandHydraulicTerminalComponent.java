package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/**
 * One complete upstream semantic drainage component ending at a single authoritative terminal fate.
 */
public record SkyIslandHydraulicTerminalComponent(
        SkyIslandChannelTerminalFate terminalFate,
        List<SkyIslandHydraulicReachAssembly> reaches,
        SkyIslandHydraulicAssemblyStatus status,
        List<String> blockers,
        SkyIslandHydraulicComponentHeadSolver.Outcome sharedHeadSolve) {

    public SkyIslandHydraulicTerminalComponent {
        terminalFate = Objects.requireNonNull(terminalFate, "terminalFate");
        reaches = List.copyOf(reaches);
        status = Objects.requireNonNull(status, "status");
        blockers = List.copyOf(blockers);
        sharedHeadSolve = Objects.requireNonNull(sharedHeadSolve, "sharedHeadSolve");
        reaches.forEach(value -> Objects.requireNonNull(value, "reach assembly"));
        blockers.forEach(value -> {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("terminal-component blocker must be non-blank");
            }
        });
        if (reaches.isEmpty()) {
            throw new IllegalArgumentException("terminal component must contain at least one reach");
        }
        if (status == SkyIslandHydraulicAssemblyStatus.QUALIFIED
                && (!blockers.isEmpty() || !sharedHeadSolve.complete())) {
            throw new IllegalArgumentException(
                    "qualified terminal component requires a complete shared-head solve and no blockers");
        }
        if (status != SkyIslandHydraulicAssemblyStatus.QUALIFIED && blockers.isEmpty()) {
            throw new IllegalArgumentException("blocked terminal component requires blocker evidence");
        }
    }
}
