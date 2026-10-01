package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Durable ownership/completion ledger for post-admission native Skyforge structure placement. */
final class SkyforgeNativeStructurePlacementSavedData extends SavedData {
    static final String DATA_NAME = "skyforge_native_structure_placement";
    static final int MAXIMUM_IDENTITIES = 65_536;
    static final SavedData.Factory<SkyforgeNativeStructurePlacementSavedData> FACTORY =
            new SavedData.Factory<>(SkyforgeNativeStructurePlacementSavedData::new,
                    SkyforgeNativeStructurePlacementSavedData::load);

    private final LinkedHashSet<PlacementIdentity> owned = new LinkedHashSet<>();
    private final LinkedHashSet<PlacementIdentity> completed = new LinkedHashSet<>();

    private SkyforgeNativeStructurePlacementSavedData() {}

    static SkyforgeNativeStructurePlacementSavedData forLevel(ServerLevel level) {
        return Objects.requireNonNull(level, "level").getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    static SkyforgeNativeStructurePlacementSavedData emptyForTest() {
        return new SkyforgeNativeStructurePlacementSavedData();
    }

    boolean registerOwned(PlacementIdentity identity) {
        Objects.requireNonNull(identity, "identity");
        if (owned.size() >= MAXIMUM_IDENTITIES && !owned.contains(identity)) {
            throw new IllegalStateException("Skyforge native structure placement state exceeds safety cap");
        }
        boolean changed = owned.add(identity);
        if (changed) {
            setDirty();
        }
        return changed;
    }

    boolean completed(PlacementIdentity identity) {
        Objects.requireNonNull(identity, "identity");
        return completed.contains(identity);
    }

    void complete(PlacementIdentity identity) {
        Objects.requireNonNull(identity, "identity");
        if (!owned.contains(identity)) {
            throw new IllegalStateException("cannot complete an unowned Skyforge native structure start");
        }
        if (completed.add(identity)) {
            setDirty();
        }
    }

    Set<PlacementIdentity> ownedFor(SkyIslandWorldVolumeId volumeId, long targetChunkKey) {
        Objects.requireNonNull(volumeId, "volumeId");
        return owned.stream()
                .filter(identity -> identity.volumeId().equals(volumeId)
                        && identity.targetChunkKey() == targetChunkKey)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    static SkyforgeNativeStructurePlacementSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        SkyforgeNativeStructurePlacementSavedData data = new SkyforgeNativeStructurePlacementSavedData();
        readIdentities(tag.getList("owned", Tag.TAG_COMPOUND), data.owned);
        readIdentities(tag.getList("completed", Tag.TAG_COMPOUND), data.completed);
        if (data.owned.size() > MAXIMUM_IDENTITIES || data.completed.size() > MAXIMUM_IDENTITIES) {
            throw new IllegalStateException("persisted Skyforge native structure placement state exceeds safety cap");
        }
        if (!data.owned.containsAll(data.completed)) {
            throw new IllegalStateException("persisted completed native structure placement lacks ownership");
        }
        return data;
    }

    private static void readIdentities(ListTag list, Set<PlacementIdentity> target) {
        for (int index = 0; index < list.size(); index++) {
            PlacementIdentity identity = PlacementIdentity.load(list.getCompound(index));
            if (!target.add(identity)) {
                throw new IllegalStateException("duplicate persisted native structure placement identity");
            }
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("owned", saveIdentities(owned));
        tag.put("completed", saveIdentities(completed));
        return tag;
    }

    private static ListTag saveIdentities(Set<PlacementIdentity> identities) {
        ListTag list = new ListTag();
        identities.stream().sorted().forEach(identity -> list.add(identity.save()));
        return list;
    }

    record PlacementIdentity(
            SkyIslandWorldVolumeId volumeId,
            long targetChunkKey,
            ResourceLocation structureId,
            long startChunkKey,
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ) implements Comparable<PlacementIdentity> {
        PlacementIdentity {
            Objects.requireNonNull(volumeId, "volumeId");
            Objects.requireNonNull(structureId, "structureId");
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("invalid native structure placement bounds");
            }
        }

        @Override
        public int compareTo(PlacementIdentity other) {
            int comparison = volumeId.path().compareTo(other.volumeId.path());
            if (comparison != 0) return comparison;
            comparison = Long.compare(targetChunkKey, other.targetChunkKey);
            if (comparison != 0) return comparison;
            comparison = structureId.toString().compareTo(other.structureId.toString());
            if (comparison != 0) return comparison;
            comparison = Long.compare(startChunkKey, other.startChunkKey);
            if (comparison != 0) return comparison;
            comparison = Integer.compare(minX, other.minX);
            if (comparison != 0) return comparison;
            comparison = Integer.compare(minY, other.minY);
            if (comparison != 0) return comparison;
            comparison = Integer.compare(minZ, other.minZ);
            if (comparison != 0) return comparison;
            comparison = Integer.compare(maxX, other.maxX);
            if (comparison != 0) return comparison;
            comparison = Integer.compare(maxY, other.maxY);
            return comparison != 0 ? comparison : Integer.compare(maxZ, other.maxZ);
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("rootSeed", volumeId.archipelagoRootSeed());
            tag.putString("group", volumeId.groupIdentifier());
            tag.putInt("groupOrdinal", volumeId.groupOrdinal());
            tag.putInt("memberOrdinal", volumeId.memberOrdinal());
            tag.putLong("geometrySeed", volumeId.geometrySeed());
            tag.putLong("targetChunk", targetChunkKey);
            tag.putString("structure", structureId.toString());
            tag.putLong("startChunk", startChunkKey);
            tag.putInt("minX", minX); tag.putInt("minY", minY); tag.putInt("minZ", minZ);
            tag.putInt("maxX", maxX); tag.putInt("maxY", maxY); tag.putInt("maxZ", maxZ);
            return tag;
        }

        static PlacementIdentity load(CompoundTag tag) {
            ResourceLocation structureId = ResourceLocation.tryParse(tag.getString("structure"));
            if (structureId == null) {
                throw new IllegalStateException("invalid persisted native structure resource id");
            }
            return new PlacementIdentity(
                    new SkyIslandWorldVolumeId(
                            tag.getLong("rootSeed"), tag.getString("group"),
                            tag.getInt("groupOrdinal"), tag.getInt("memberOrdinal"),
                            tag.getLong("geometrySeed")),
                    tag.getLong("targetChunk"), structureId, tag.getLong("startChunk"),
                    tag.getInt("minX"), tag.getInt("minY"), tag.getInt("minZ"),
                    tag.getInt("maxX"), tag.getInt("maxY"), tag.getInt("maxZ"));
        }
    }
}
