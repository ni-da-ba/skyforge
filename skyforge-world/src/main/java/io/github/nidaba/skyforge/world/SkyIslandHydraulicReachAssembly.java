package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/** Complete F3E evidence for one semantic reach after ordinary spans and explicit transitions. */
public record SkyIslandHydraulicReachAssembly(
        SkyIslandSemanticChannelReach semanticReach,
        List<SkyIslandOrdinarySpanOutcome> ordinarySpans,
        List<SkyIslandCascadeHeadCompatibilityOutcome> cascades,
        SkyIslandHydraulicAssemblyStatus status,
        List<String> blockers) {

    public SkyIslandHydraulicReachAssembly {
        semanticReach = Objects.requireNonNull(semanticReach, "semanticReach");
        ordinarySpans = List.copyOf(ordinarySpans);
        cascades = List.copyOf(cascades);
        status = Objects.requireNonNull(status, "status");
        blockers = List.copyOf(blockers);
        ordinarySpans.forEach(value -> Objects.requireNonNull(value, "ordinary span"));
        cascades.forEach(value -> Objects.requireNonNull(value, "cascade outcome"));
        blockers.forEach(value -> {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("assembly blocker must be non-blank");
            }
        });
        if (status == SkyIslandHydraulicAssemblyStatus.QUALIFIED && !blockers.isEmpty()) {
            throw new IllegalArgumentException("qualified reach assembly cannot carry blockers");
        }
        if (status != SkyIslandHydraulicAssemblyStatus.QUALIFIED && blockers.isEmpty()) {
            throw new IllegalArgumentException("blocked reach assembly requires blocker evidence");
        }
    }

    public long identity() {
        return ((long) semanticReach.startCellIndex() << 32)
                ^ Integer.toUnsignedLong(semanticReach.endCellIndex());
    }
}
