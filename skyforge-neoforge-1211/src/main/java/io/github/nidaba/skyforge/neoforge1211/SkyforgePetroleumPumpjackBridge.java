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
     * Live CDG read seam. The retained pumpjack still validates a physical bore against ordinary
     * host geology, while Skyforge owns whether the well column intersects a realized petroleum
     * source and how much recoverable petroleum remains there.
     */
    public static int oilAmountForPumpjack(ServerLevel level, BlockPos pumpjackPosition) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(pumpjackPosition, "pumpjackPosition");
        return SkyforgePetroleumSourceSavedData.forLevel(level).remainingForWell(pumpjackPosition);
    }

    /** Live CDG depletion seam corresponding to {@link #oilAmountForPumpjack}. */
    public static void setOilAmountForPumpjack(
            ServerLevel level,
            BlockPos pumpjackPosition,
            int remainingMillibuckets) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(pumpjackPosition, "pumpjackPosition");
        if (remainingMillibuckets < 0) {
            throw new IllegalArgumentException("remainingMillibuckets must be nonnegative");
        }
        SkyforgePetroleumSourceSavedData.forLevel(level)
                .setRemainingForWell(pumpjackPosition, remainingMillibuckets);
    }

    static final class Binding {
        private final SkyforgePetroleumSourceAdapter adapter;
        private final SkyforgePetroleumSourceSavedData saved;

        private Binding(SkyforgePetroleumSourceAdapter adapter, SkyforgePetroleumSourceSavedData saved) {
            this.adapter = adapter;
            this.saved = saved;
        }

        /** Resolves the nearest admitted source below a well in one exact Skyforge volume. */
        Optional<SkyforgePetroleumSourceAdapter.PumpjackSource> pumpjackSource(
                ResourceLocation exactVolume, BlockPos wellPosition, boolean baseWorld) {
            return adapter.pumpjackSource(exactVolume, wellPosition, baseWorld);
        }

        /** Extracts only from the selected admitted source and durably records depletion. */
        int extract(SkyforgePetroleumSourceAdapter.PumpjackSource source, int requestedMillibuckets) {
            int extracted = adapter.extract(source, requestedMillibuckets);
            saved.replace(adapter.save());
            return extracted;
        }
    }
}
