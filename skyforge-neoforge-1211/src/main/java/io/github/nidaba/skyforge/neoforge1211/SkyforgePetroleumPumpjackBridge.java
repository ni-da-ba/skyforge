package io.github.nidaba.skyforge.neoforge1211;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/** Concrete lifecycle binding used by the retained Create: Diesel Generators pumpjack path. */
public final class SkyforgePetroleumPumpjackBridge {
    private SkyforgePetroleumPumpjackBridge() {}

    static Binding bind(ServerLevel level, SkyforgePetroleumSourceAdapter adapter) {
        Objects.requireNonNull(adapter, "adapter");
        SkyforgePetroleumSourceSavedData saved = SkyforgePetroleumSourceSavedData.forLevel(level);
        if (saved.snapshot().isEmpty()) {
            saved.replace(adapter.save());
        } else {
            adapter.reload(saved.snapshot());
        }
        return new Binding(adapter, saved);
    }

    /**
     * Live CDG read seam. CDG has already validated its vertical pipe against the oil-deposit tag;
     * Skyforge independently resolves the concrete termination block and fails closed unless that
     * exact position is registered in Skyforge's persistent source state.
     */
    public static int oilAmountForPumpjack(ServerLevel level, BlockPos pumpjackPosition) {
        Objects.requireNonNull(level, "level");
        return findTermination(level, pumpjackPosition)
                .map(termination -> SkyforgePetroleumSourceSavedData.forLevel(level).remainingAt(termination))
                .orElse(0);
    }

    /** Live CDG depletion seam corresponding to {@link #oilAmountForPumpjack}. */
    public static void setOilAmountForPumpjack(
            ServerLevel level,
            BlockPos pumpjackPosition,
            int remainingMillibuckets) {
        Objects.requireNonNull(level, "level");
        if (remainingMillibuckets < 0) {
            throw new IllegalArgumentException("remainingMillibuckets must be nonnegative");
        }
        findTermination(level, pumpjackPosition).ifPresent(
                termination -> SkyforgePetroleumSourceSavedData.forLevel(level)
                        .setRemainingAt(termination, remainingMillibuckets));
    }

    static Optional<BlockPos> findTermination(ServerLevel level, BlockPos pumpjackPosition) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(pumpjackPosition, "pumpjackPosition");
        for (int y = pumpjackPosition.getY() - 1; y >= level.getMinBuildHeight(); y--) {
            BlockPos candidate = new BlockPos(pumpjackPosition.getX(), y, pumpjackPosition.getZ());
            if (level.getBlockState(candidate).is(SkyforgePetroleumSourceBlocks.PETROLEUM_SOURCE.get())) {
                return Optional.of(candidate.immutable());
            }
        }
        return Optional.empty();
    }

    static final class Binding {
        private final SkyforgePetroleumSourceAdapter adapter;
        private final SkyforgePetroleumSourceSavedData saved;

        private Binding(SkyforgePetroleumSourceAdapter adapter, SkyforgePetroleumSourceSavedData saved) {
            this.adapter = adapter;
            this.saved = saved;
        }

        /** Resolves the exact termination that CDG invokes through the petroleum-source tag. */
        Optional<SkyforgePetroleumSourceAdapter.PumpjackTermination> pumpjackTermination(
                ResourceLocation exactVolume, BlockPos termination, boolean baseWorld) {
            return adapter.pumpjackTermination(exactVolume, termination, baseWorld);
        }

        /** Extracts only from the invoked admitted termination and durably records depletion. */
        int extract(SkyforgePetroleumSourceAdapter.PumpjackTermination termination, int requestedMillibuckets) {
            int extracted = adapter.extract(termination, requestedMillibuckets);
            saved.replace(adapter.save());
            return extracted;
        }
    }
}
