package io.github.nidaba.skyforge.neoforge1211;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/** Concrete lifecycle binding used by the retained Create: Diesel Generators pumpjack path. */
final class SkyforgePetroleumPumpjackBridge {
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
