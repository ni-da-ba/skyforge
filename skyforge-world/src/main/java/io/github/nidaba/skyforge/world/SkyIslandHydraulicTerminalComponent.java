package io.github.nidaba.skyforge.world;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * One complete upstream semantic drainage component ending at a single authoritative terminal fate.
 *
 * <p>The F3E shared-head outcome is retained as provenance. An F4-qualified network realization may
 * supersede that legacy component-wide head gate only when its complete candidate-backed network
 * solution is present; the original assembly plan remains unchanged.
 */
public record SkyIslandHydraulicTerminalComponent(
        SkyIslandChannelTerminalFate terminalFate,
        List<SkyIslandHydraulicReachAssembly> reaches,
        SkyIslandHydraulicAssemblyStatus status,
        List<String> blockers,
        SkyIslandHydraulicComponentHeadSolver.Outcome sharedHeadSolve,
        Optional<SkyIslandGameScaleDrainageNetworkModel.NetworkSolution> networkSolution) {

    public SkyIslandHydraulicTerminalComponent(
            SkyIslandChannelTerminalFate terminalFate,
            List<SkyIslandHydraulicReachAssembly> reaches,
            SkyIslandHydraulicAssemblyStatus status,
            List<String> blockers,
            SkyIslandHydraulicComponentHeadSolver.Outcome sharedHeadSolve) {
        this(terminalFate, reaches, status, blockers, sharedHeadSolve, Optional.empty());
    }

    public SkyIslandHydraulicTerminalComponent {
        terminalFate = Objects.requireNonNull(terminalFate, "terminalFate");
        reaches = List.copyOf(reaches);
        status = Objects.requireNonNull(status, "status");
        blockers = List.copyOf(blockers);
        sharedHeadSolve = Objects.requireNonNull(sharedHeadSolve, "sharedHeadSolve");
        networkSolution = Objects.requireNonNull(networkSolution, "networkSolution");
        reaches.forEach(value -> Objects.requireNonNull(value, "reach assembly"));
        blockers.forEach(value -> {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("terminal-component blocker must be non-blank");
            }
        });
        if (reaches.isEmpty()) {
            throw new IllegalArgumentException("terminal component must contain at least one reach");
        }
        if (networkSolution.isPresent()) {
            Set<String> expectedReachIds = new HashSet<>();
            reaches.forEach(reach -> expectedReachIds.add(
                    reach.semanticReach().startCellIndex() + "->"
                            + reach.semanticReach().endCellIndex()));
            if (status != SkyIslandHydraulicAssemblyStatus.QUALIFIED
                    || !blockers.isEmpty()
                    || !networkSolution.orElseThrow().reachSlicesById().keySet().equals(expectedReachIds)) {
                throw new IllegalArgumentException(
                        "network-qualified component must carry one complete solved tree and no blockers");
            }
        }
        if (status == SkyIslandHydraulicAssemblyStatus.QUALIFIED
                && ( !blockers.isEmpty()
                        || (!sharedHeadSolve.complete() && networkSolution.isEmpty()))) {
            throw new IllegalArgumentException(
                    "qualified terminal component requires a complete shared-head or network solve and no blockers");
        }
        if (status != SkyIslandHydraulicAssemblyStatus.QUALIFIED && blockers.isEmpty()) {
            throw new IllegalArgumentException("blocked terminal component requires blocker evidence");
        }
    }
}
