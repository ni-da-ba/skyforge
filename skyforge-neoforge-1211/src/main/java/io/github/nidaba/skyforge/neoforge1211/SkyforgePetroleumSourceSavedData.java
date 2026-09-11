package io.github.nidaba.skyforge.neoforge1211;

import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
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
    private final Map<Long, NavigableMap<Integer, SkyforgePetroleumSourceAdapter.SourceAddress>> addressesByColumn =
            new HashMap<>();

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
            String anchorKey = entry.contains("source_anchor", Tag.TAG_LONG) ? "source_anchor" : "termination";
            if (volume == null || amount < 0 || !entry.contains(anchorKey, Tag.TAG_LONG)) {
                throw new IllegalStateException("invalid persisted Skyforge petroleum source at index " + index);
            }
            var address = new SkyforgePetroleumSourceAdapter.SourceAddress(
                    volume, BlockPos.of(entry.getLong(anchorKey)));
            data.putLoaded(address, amount);
        }
        return data;
    }

    Map<SkyforgePetroleumSourceAdapter.SourceAddress, Integer> snapshot() {
        return Map.copyOf(remaining);
    }

    int remainingForWell(BlockPos wellPosition) {
        return sourceForWell(wellPosition).map(address -> remaining.getOrDefault(address, 0)).orElse(0);
    }

    void setRemainingForWell(BlockPos wellPosition, int amount) {
        Objects.requireNonNull(wellPosition, "wellPosition");
        if (amount < 0) {
            throw new IllegalArgumentException("petroleum amount must be nonnegative");
        }
        sourceForWell(wellPosition).ifPresent(address -> {
            Integer previous = remaining.put(address, amount);
            if (!Objects.equals(previous, amount)) {
                setDirty();
            }
        });
    }

    Optional<SkyforgePetroleumSourceAdapter.SourceAddress> sourceForWell(BlockPos wellPosition) {
        Objects.requireNonNull(wellPosition, "wellPosition");
        NavigableMap<Integer, SkyforgePetroleumSourceAdapter.SourceAddress> column =
                addressesByColumn.get(columnKey(wellPosition));
        if (column == null) return Optional.empty();
        Map.Entry<Integer, SkyforgePetroleumSourceAdapter.SourceAddress> source =
                column.lowerEntry(wellPosition.getY());
        return source == null ? Optional.empty() : Optional.of(source.getValue());
    }

    void replace(Map<SkyforgePetroleumSourceAdapter.SourceAddress, Integer> next) {
        Objects.requireNonNull(next, "next");
        if (next.size() > MAXIMUM_SOURCES || next.values().stream().anyMatch(amount -> amount == null || amount < 0)) {
            throw new IllegalArgumentException("invalid Skyforge petroleum source state");
        }
        TreeMap<SkyforgePetroleumSourceAdapter.SourceAddress, Integer> ordered = new TreeMap<>(next);
        Map<Long, NavigableMap<Integer, SkyforgePetroleumSourceAdapter.SourceAddress>> index = buildIndex(ordered);
        if (!remaining.equals(ordered)) {
            remaining.clear();
            remaining.putAll(ordered);
            setDirty();
        }
        addressesByColumn.clear();
        addressesByColumn.putAll(index);
    }

    private void putLoaded(SkyforgePetroleumSourceAdapter.SourceAddress address, int amount) {
        if (remaining.putIfAbsent(address, amount) != null) {
            throw new IllegalStateException("duplicate persisted Skyforge petroleum source: " + address);
        }
        try {
            index(addressesByColumn, address);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(exception.getMessage(), exception);
        }
    }

    private static Map<Long, NavigableMap<Integer, SkyforgePetroleumSourceAdapter.SourceAddress>> buildIndex(
            Map<SkyforgePetroleumSourceAdapter.SourceAddress, Integer> sources) {
        Map<Long, NavigableMap<Integer, SkyforgePetroleumSourceAdapter.SourceAddress>> index = new HashMap<>();
        sources.keySet().forEach(address -> index(index, address));
        return index;
    }

    private static void index(
            Map<Long, NavigableMap<Integer, SkyforgePetroleumSourceAdapter.SourceAddress>> index,
            SkyforgePetroleumSourceAdapter.SourceAddress address) {
        BlockPos anchor = address.sourceAnchor();
        NavigableMap<Integer, SkyforgePetroleumSourceAdapter.SourceAddress> column =
                index.computeIfAbsent(columnKey(anchor), ignored -> new TreeMap<>());
        var previous = column.putIfAbsent(anchor.getY(), address);
        if (previous != null && !previous.equals(address)) {
            throw new IllegalArgumentException(
                    "multiple exact petroleum sources cannot share one source-field coordinate: " + anchor);
        }
    }

    private static long columnKey(BlockPos position) {
        return ((long) position.getX() << 32) ^ (position.getZ() & 0xffffffffL);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        remaining.forEach((address, amount) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("volume", address.volumeId().toString());
            entry.putLong("source_anchor", address.sourceAnchor().asLong());
            entry.putInt("remaining", amount);
            entries.add(entry);
        });
        tag.put("sources", entries);
        return tag;
    }
}
