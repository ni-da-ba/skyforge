package io.github.nidaba.skyforge.neoforge1211;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Per-level, exact-source depletion state for the retained-CDG pumpjack bridge. */
final class SkyforgePetroleumSourceSavedData extends SavedData {
    static final String DATA_NAME = "skyforge_petroleum_sources";
    static final int MAXIMUM_SOURCES = 16_384;
    static final SavedData.Factory<SkyforgePetroleumSourceSavedData> FACTORY =
            new SavedData.Factory<>(SkyforgePetroleumSourceSavedData::new, SkyforgePetroleumSourceSavedData::load);

    private final TreeMap<SkyforgePetroleumSourceAdapter.SourceAddress, Integer> remaining = new TreeMap<>();
    private final Map<Long, SkyforgePetroleumSourceAdapter.SourceAddress> addressByTermination = new HashMap<>();

    private SkyforgePetroleumSourceSavedData() {}

    static SkyforgePetroleumSourceSavedData forLevel(ServerLevel level) {
        return Objects.requireNonNull(level, "level").getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    static SkyforgePetroleumSourceSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        SkyforgePetroleumSourceSavedData data = new SkyforgePetroleumSourceSavedData();
        ListTag entries = tag.getList("sources", Tag.TAG_COMPOUND);
        if (entries.size() > MAXIMUM_SOURCES) {
            throw new IllegalStateException("Skyforge petroleum source state exceeds safety cap: " + entries.size());
        }
        for (int index = 0; index < entries.size(); index++) {
            CompoundTag entry = entries.getCompound(index);
            ResourceLocation volume = ResourceLocation.tryParse(entry.getString("volume"));
            int amount = entry.getInt("remaining");
            if (volume == null || amount < 0) {
                throw new IllegalStateException("invalid persisted Skyforge petroleum source at index " + index);
            }
            var address = new SkyforgePetroleumSourceAdapter.SourceAddress(
                    volume, BlockPos.of(entry.getLong("termination")));
            data.putLoaded(address, amount);
        }
        return data;
    }

    Map<SkyforgePetroleumSourceAdapter.SourceAddress, Integer> snapshot() {
        return Map.copyOf(remaining);
    }

    int remainingAt(BlockPos termination) {
        Objects.requireNonNull(termination, "termination");
        var address = addressByTermination.get(termination.asLong());
        return address == null ? 0 : remaining.getOrDefault(address, 0);
    }

    void setRemainingAt(BlockPos termination, int amount) {
        Objects.requireNonNull(termination, "termination");
        if (amount < 0) {
            throw new IllegalArgumentException("petroleum amount must be nonnegative");
        }
        var address = addressByTermination.get(termination.asLong());
        if (address == null) {
            return;
        }
        Integer previous = remaining.put(address, amount);
        if (!Objects.equals(previous, amount)) {
            setDirty();
        }
    }

    void replace(Map<SkyforgePetroleumSourceAdapter.SourceAddress, Integer> next) {
        Objects.requireNonNull(next, "next");
        if (next.size() > MAXIMUM_SOURCES || next.values().stream().anyMatch(amount -> amount == null || amount < 0)) {
            throw new IllegalArgumentException("invalid Skyforge petroleum source state");
        }
        TreeMap<SkyforgePetroleumSourceAdapter.SourceAddress, Integer> ordered = new TreeMap<>(next);
        HashMap<Long, SkyforgePetroleumSourceAdapter.SourceAddress> index = new HashMap<>();
        ordered.keySet().forEach(address -> {
            var previous = index.putIfAbsent(address.termination().asLong(), address);
            if (previous != null && !previous.equals(address)) {
                throw new IllegalArgumentException(
                        "multiple exact petroleum sources cannot share one physical termination: "
                                + address.termination());
            }
        });
        if (!remaining.equals(ordered)) {
            remaining.clear();
            remaining.putAll(ordered);
            setDirty();
        }
        addressByTermination.clear();
        addressByTermination.putAll(index);
    }

    private void putLoaded(SkyforgePetroleumSourceAdapter.SourceAddress address, int amount) {
        if (remaining.putIfAbsent(address, amount) != null) {
            throw new IllegalStateException("duplicate persisted Skyforge petroleum source: " + address);
        }
        var previous = addressByTermination.putIfAbsent(address.termination().asLong(), address);
        if (previous != null && !previous.equals(address)) {
            throw new IllegalStateException(
                    "multiple persisted Skyforge petroleum sources share one physical termination: "
                            + address.termination());
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        remaining.forEach((address, amount) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("volume", address.volumeId().toString());
            entry.putLong("termination", address.termination().asLong());
            entry.putInt("remaining", amount);
            entries.add(entry);
        });
        tag.put("sources", entries);
        return tag;
    }
}
