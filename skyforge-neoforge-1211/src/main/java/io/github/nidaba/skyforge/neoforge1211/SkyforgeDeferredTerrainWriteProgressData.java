package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Persistent per-level cursor ledger for deferred physical-volume terrain packets. */
final class SkyforgeDeferredTerrainWriteProgressData extends SavedData {
    private static final String DATA_NAME = "skyforge_deferred_terrain_write_progress";
    private static final Factory<SkyforgeDeferredTerrainWriteProgressData> FACTORY = new Factory<>(
            SkyforgeDeferredTerrainWriteProgressData::new,
            SkyforgeDeferredTerrainWriteProgressData::load);

    private final Map<Key, Progress> progressByObligation = new HashMap<>();

    static SkyforgeDeferredTerrainWriteProgressData get(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    Optional<Progress> find(SkyIslandWorldVolumeId volumeId, long chunkKey) {
        Objects.requireNonNull(volumeId, "volumeId");
        return Optional.ofNullable(progressByObligation.get(new Key(volumeId, chunkKey)));
    }

    Progress getOrCreate(
            SkyIslandWorldVolumeId volumeId,
            long chunkKey,
            int expectedSolidBlocks) {
        Objects.requireNonNull(volumeId, "volumeId");
        if (expectedSolidBlocks < 0) {
            throw new IllegalArgumentException("expectedSolidBlocks must be nonnegative");
        }
        Key key = new Key(volumeId, chunkKey);
        Progress existing = progressByObligation.get(key);
        if (existing != null) {
            if (existing.expectedSolidBlocks() != expectedSolidBlocks) {
                throw new IllegalStateException(
                        "deferred terrain materialization solid count changed for persisted obligation "
                                + volumeId.path());
            }
            return existing;
        }
        Progress created = new Progress(
                volumeId,
                chunkKey,
                0,
                0,
                0,
                expectedSolidBlocks,
                false);
        progressByObligation.put(key, created);
        setDirty();
        return created;
    }

    void store(Progress progress) {
        Objects.requireNonNull(progress, "progress");
        progressByObligation.put(new Key(progress.volumeId(), progress.chunkKey()), progress);
        setDirty();
    }

    static SkyforgeDeferredTerrainWriteProgressData load(
            CompoundTag tag,
            HolderLookup.Provider registries) {
        Objects.requireNonNull(tag, "tag");
        SkyforgeDeferredTerrainWriteProgressData data = new SkyforgeDeferredTerrainWriteProgressData();
        int count = tag.getInt("count");
        if (count < 0) {
            throw new IllegalStateException("negative deferred terrain progress count");
        }
        for (int index = 0; index < count; index++) {
            String key = "entry_" + index;
            if (!tag.contains(key, Tag.TAG_COMPOUND)) {
                throw new IllegalStateException("missing deferred terrain progress entry " + index);
            }
            CompoundTag entry = tag.getCompound(key);
            SkyIslandWorldVolumeId volumeId = new SkyIslandWorldVolumeId(
                    entry.getLong("archipelagoRootSeed"),
                    entry.getString("groupIdentifier"),
                    entry.getInt("groupOrdinal"),
                    entry.getInt("memberOrdinal"),
                    entry.getLong("geometrySeed"));
            Progress progress = new Progress(
                    volumeId,
                    entry.getLong("chunkKey"),
                    entry.getInt("nextLinearIndex"),
                    entry.getInt("cumulativeAssignedSolidWrites"),
                    entry.getInt("cumulativeSolidWrites"),
                    entry.getInt("expectedSolidBlocks"),
                    entry.getBoolean("terminal"));
            Key progressKey = new Key(volumeId, progress.chunkKey());
            if (data.progressByObligation.put(progressKey, progress) != null) {
                throw new IllegalStateException("duplicate deferred terrain progress obligation");
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        Objects.requireNonNull(tag, "tag");
        List<Progress> ordered = new ArrayList<>(progressByObligation.values());
        ordered.sort(Comparator
                .comparing((Progress progress) -> progress.volumeId().path())
                .thenComparingLong(Progress::chunkKey));
        tag.putInt("count", ordered.size());
        for (int index = 0; index < ordered.size(); index++) {
            Progress progress = ordered.get(index);
            CompoundTag entry = new CompoundTag();
            SkyIslandWorldVolumeId volumeId = progress.volumeId();
            entry.putLong("archipelagoRootSeed", volumeId.archipelagoRootSeed());
            entry.putString("groupIdentifier", volumeId.groupIdentifier());
            entry.putInt("groupOrdinal", volumeId.groupOrdinal());
            entry.putInt("memberOrdinal", volumeId.memberOrdinal());
            entry.putLong("geometrySeed", volumeId.geometrySeed());
            entry.putLong("chunkKey", progress.chunkKey());
            entry.putInt("nextLinearIndex", progress.nextLinearIndex());
            entry.putInt("cumulativeAssignedSolidWrites", progress.cumulativeAssignedSolidWrites());
            entry.putInt("cumulativeSolidWrites", progress.cumulativeSolidWrites());
            entry.putInt("expectedSolidBlocks", progress.expectedSolidBlocks());
            entry.putBoolean("terminal", progress.terminal());
            tag.put("entry_" + index, entry);
        }
        return tag;
    }

    private record Key(SkyIslandWorldVolumeId volumeId, long chunkKey) {
        Key {
            Objects.requireNonNull(volumeId, "volumeId");
        }
    }

    record Progress(
            SkyIslandWorldVolumeId volumeId,
            long chunkKey,
            int nextLinearIndex,
            int cumulativeAssignedSolidWrites,
            int cumulativeSolidWrites,
            int expectedSolidBlocks,
            boolean terminal) {
        Progress {
            Objects.requireNonNull(volumeId, "volumeId");
            if (nextLinearIndex < 0
                    || cumulativeAssignedSolidWrites < 0
                    || cumulativeSolidWrites < 0
                    || expectedSolidBlocks < 0
                    || cumulativeSolidWrites > cumulativeAssignedSolidWrites
                    || cumulativeAssignedSolidWrites > expectedSolidBlocks) {
                throw new IllegalArgumentException("invalid deferred terrain progress accounting");
            }
            if (terminal && cumulativeAssignedSolidWrites != expectedSolidBlocks) {
                throw new IllegalArgumentException("terminal deferred terrain progress must match expected solids");
            }
        }

        SkyforgeNeoForge1211ChunkWriter.DeferredSolidWriteCursor cursor() {
            return new SkyforgeNeoForge1211ChunkWriter.DeferredSolidWriteCursor(
                    nextLinearIndex,
                    cumulativeAssignedSolidWrites,
                    cumulativeSolidWrites);
        }

        Progress advance(
                SkyforgeNeoForge1211ChunkWriter.DeferredSolidWriteAdvance advance,
                boolean terminal) {
            Objects.requireNonNull(advance, "advance");
            var cursor = advance.cursor();
            return new Progress(
                    volumeId,
                    chunkKey,
                    cursor.nextLinearIndex(),
                    cursor.cumulativeAssignedSolidWrites(),
                    cursor.cumulativeSolidWrites(),
                    expectedSolidBlocks,
                    terminal);
        }
    }
}
