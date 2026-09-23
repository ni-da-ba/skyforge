package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Deterministic physical interpretation of all routed watershed edges on one island. */
public record SkyIslandDrainageTransferPlan(
        SkyIslandDescriptor descriptor,
        List<SkyIslandDrainageTransfer> transfers) {

    public SkyIslandDrainageTransferPlan {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        transfers = transfers.stream()
                .map(transfer -> Objects.requireNonNull(transfer, "transfer"))
                .sorted(Comparator.comparingInt(SkyIslandDrainageTransfer::sourceCellIndex))
                .toList();
    }

    public Optional<SkyIslandDrainageTransfer> transferFrom(int sourceCellIndex) {
        return transfers.stream()
                .filter(transfer -> transfer.sourceCellIndex() == sourceCellIndex)
                .findFirst();
    }

    public long count(SkyIslandDrainageTransferKind kind) {
        return transfers.stream().filter(transfer -> transfer.kind() == kind).count();
    }
}
