package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.model.skyisland.SkyBiomeId;
import io.github.nidaba.skyforge.model.skyisland.SkyBiomeZoneAssignment;
import io.github.nidaba.skyforge.model.skyisland.SkyBiomeZoneIdentity;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/** Durable Overworld storage for Skyforge-authored coherent SkyBiome zone assignments. */
final class SkyforgeSkyBiomeZoneSavedData extends SavedData {
    static final String DATA_NAME = "skyforge_sky_biome_zones";
    static final int DATA_SCHEMA_VERSION = 1;
    static final int MAXIMUM_ASSIGNMENTS = 65_536;
    static final SavedData.Factory<SkyforgeSkyBiomeZoneSavedData> FACTORY =
            new SavedData.Factory<>(SkyforgeSkyBiomeZoneSavedData::new, SkyforgeSkyBiomeZoneSavedData::load);

    private static final String WORLD_SEED_KEY = "worldSeed";
    private static final String ASSIGNMENTS_KEY = "assignments";
    private static final String ZONE_SCHEMA_KEY = "zoneSchemaVersion";
    private static final String ASSIGNMENT_SCHEMA_KEY = "assignmentSchemaVersion";
    private static final String ZONE_KEY = "zoneKey";
    private static final String SKY_BIOME_ID_KEY = "skyBiomeId";

    private final NavigableMap<Long, SkyBiomeZoneAssignment> assignments =
            new TreeMap<>(Long::compareUnsigned);
    private Long worldSeed;

    private SkyforgeSkyBiomeZoneSavedData() {}

    static SkyforgeSkyBiomeZoneSavedData forLevel(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        if (!level.dimension().equals(Level.OVERWORLD)) {
            throw new IllegalArgumentException("SkyBiome zone assignments are stored in the Overworld");
        }
        SkyforgeSkyBiomeZoneSavedData data =
                level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
        data.bindWorldSeed(level.getSeed());
        return data;
    }

    static SkyforgeSkyBiomeZoneSavedData emptyForTest(long worldSeed) {
        SkyforgeSkyBiomeZoneSavedData data = new SkyforgeSkyBiomeZoneSavedData();
        data.bindWorldSeed(worldSeed);
        return data;
    }

    Optional<SkyBiomeZoneAssignment> assignment(long zoneKey) {
        return Optional.ofNullable(assignments.get(zoneKey));
    }

    List<SkyBiomeZoneAssignment> assignments() {
        return List.copyOf(assignments.values());
    }

    /**
     * Adds an immutable authored zone assignment. Repeating the exact value is idempotent; changing
     * a zone's SkyBiome in an existing world fails so a caller cannot silently rewrite authorship.
     */
    boolean assign(SkyBiomeZoneAssignment assignment) {
        Objects.requireNonNull(assignment, "assignment");
        if (worldSeed == null) {
            throw new IllegalStateException("SkyBiome SavedData must be bound to an Overworld seed first");
        }
        SkyBiomeZoneIdentity identity = assignment.zoneIdentity();
        if (identity.worldSeed() != worldSeed.longValue()) {
            throw new IllegalArgumentException("SkyBiome zone assignment belongs to a different world seed");
        }
        SkyBiomeZoneAssignment existing = assignments.get(identity.zoneKey());
        if (existing != null) {
            if (existing.equals(assignment)) {
                return false;
            }
            throw new IllegalStateException("SkyBiome zone key is already assigned to a different identity");
        }
        if (assignments.size() >= MAXIMUM_ASSIGNMENTS) {
            throw new IllegalStateException("SkyBiome assignment limit exceeded: " + MAXIMUM_ASSIGNMENTS);
        }
        assignments.put(identity.zoneKey(), assignment);
        setDirty();
        return true;
    }

    private void bindWorldSeed(long seed) {
        if (worldSeed == null) {
            worldSeed = seed;
            setDirty();
        } else if (worldSeed.longValue() != seed) {
            throw new IllegalStateException("SkyBiome SavedData world seed does not match the active Overworld");
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        Objects.requireNonNull(tag, "tag");
        if (worldSeed == null) {
            throw new IllegalStateException("SkyBiome SavedData cannot be written before binding a world seed");
        }
        tag.putInt("schemaVersion", DATA_SCHEMA_VERSION);
        tag.putLong(WORLD_SEED_KEY, worldSeed);
        ListTag serializedAssignments = new ListTag();
        for (SkyBiomeZoneAssignment assignment : assignments.values()) {
            CompoundTag serialized = new CompoundTag();
            serialized.putInt(ZONE_SCHEMA_KEY, assignment.zoneIdentity().schemaVersion());
            serialized.putInt(ASSIGNMENT_SCHEMA_KEY, assignment.schemaVersion());
            serialized.putLong(ZONE_KEY, assignment.zoneIdentity().zoneKey());
            serialized.putString(SKY_BIOME_ID_KEY, assignment.skyBiomeId().toString());
            serializedAssignments.add(serialized);
        }
        tag.put(ASSIGNMENTS_KEY, serializedAssignments);
        return tag;
    }

    static SkyforgeSkyBiomeZoneSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        Objects.requireNonNull(tag, "tag");
        if (!tag.contains("schemaVersion", Tag.TAG_INT)
                || tag.getInt("schemaVersion") != DATA_SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported SkyBiome SavedData schema");
        }
        if (!tag.contains(WORLD_SEED_KEY, Tag.TAG_LONG)) {
            throw new IllegalArgumentException("SkyBiome SavedData is missing its world seed");
        }
        if (!tag.contains(ASSIGNMENTS_KEY, Tag.TAG_LIST)) {
            throw new IllegalArgumentException("SkyBiome SavedData is missing its assignment list");
        }
        SkyforgeSkyBiomeZoneSavedData data = new SkyforgeSkyBiomeZoneSavedData();
        data.worldSeed = tag.getLong(WORLD_SEED_KEY);
        ListTag serializedAssignments = tag.getList(ASSIGNMENTS_KEY, Tag.TAG_COMPOUND);
        if (serializedAssignments.size() > MAXIMUM_ASSIGNMENTS) {
            throw new IllegalArgumentException("SkyBiome assignment limit exceeded: " + MAXIMUM_ASSIGNMENTS);
        }
        for (int index = 0; index < serializedAssignments.size(); index++) {
            CompoundTag serialized = serializedAssignments.getCompound(index);
            if (!serialized.contains(ZONE_SCHEMA_KEY, Tag.TAG_INT)
                    || !serialized.contains(ASSIGNMENT_SCHEMA_KEY, Tag.TAG_INT)
                    || !serialized.contains(ZONE_KEY, Tag.TAG_LONG)
                    || !serialized.contains(SKY_BIOME_ID_KEY, Tag.TAG_STRING)) {
                throw new IllegalArgumentException("SkyBiome assignment entry is missing required fields");
            }
            SkyBiomeZoneIdentity identity =
                    new SkyBiomeZoneIdentity(serialized.getInt(ZONE_SCHEMA_KEY), data.worldSeed, serialized.getLong(ZONE_KEY));
            SkyBiomeZoneAssignment assignment = new SkyBiomeZoneAssignment(
                    serialized.getInt(ASSIGNMENT_SCHEMA_KEY),
                    identity,
                    SkyBiomeId.parse(serialized.getString(SKY_BIOME_ID_KEY)));
            if (data.assignments.putIfAbsent(identity.zoneKey(), assignment) != null) {
                throw new IllegalArgumentException("duplicate SkyBiome zone key in SavedData");
            }
        }
        return data;
    }
}
