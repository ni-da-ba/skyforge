package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyBiomeCatalog;
import io.github.nidaba.skyforge.model.skyisland.SkyBiomeId;
import io.github.nidaba.skyforge.model.skyisland.SkyBiomeZoneAssignment;
import io.github.nidaba.skyforge.model.skyisland.SkyBiomeZoneIdentity;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

final class SkyforgeSkyBiomeZoneSavedDataTest {
    private static final long WORLD_SEED = 0xD15EA5ECAFEL;

    @Test
    void assignmentIsDeterministicAndSurvivesSavedDataRoundTrip() {
        var data = SkyforgeSkyBiomeZoneSavedData.emptyForTest(WORLD_SEED);
        var lateUnsignedKey = assignment(-1L, SkyBiomeCatalog.ISLAND_LEE_ID);
        var earlyUnsignedKey = assignment(1L, SkyBiomeCatalog.CLOUD_SHELF_ID);

        assertTrue(data.assign(lateUnsignedKey));
        assertTrue(data.assign(earlyUnsignedKey));
        assertFalse(data.assign(lateUnsignedKey));

        CompoundTag saved = data.save(new CompoundTag(), null);
        var loaded = SkyforgeSkyBiomeZoneSavedData.load(saved.copy(), null);

        assertEquals(List.of(earlyUnsignedKey, lateUnsignedKey), loaded.assignments());
        assertEquals(saved, loaded.save(new CompoundTag(), null));
        assertEquals(
                lateUnsignedKey,
                loaded.assignment(-1L).orElseThrow());
    }

    @Test
    void rejectsCrossWorldAndConflictingZoneAssignments() {
        var data = SkyforgeSkyBiomeZoneSavedData.emptyForTest(WORLD_SEED);
        assertThrows(
                IllegalArgumentException.class,
                () -> data.assign(assignmentForSeed(WORLD_SEED + 1, 4L, SkyBiomeCatalog.OPEN_SKY_ID)));

        assertTrue(data.assign(assignment(4L, SkyBiomeCatalog.OPEN_SKY_ID)));
        assertThrows(
                IllegalStateException.class,
                () -> data.assign(assignment(4L, SkyBiomeCatalog.CLOUD_SHELF_ID)));
        assertThrows(IllegalStateException.class, () -> data.bindWorldSeed(WORLD_SEED + 1));
    }

    @Test
    void rejectsUnsupportedSchemaAndDuplicateZoneKeysOnLoad() {
        CompoundTag unsupported = SkyforgeSkyBiomeZoneSavedData.emptyForTest(WORLD_SEED)
                .save(new CompoundTag(), null);
        unsupported.putInt("schemaVersion", 99);
        assertThrows(
                IllegalArgumentException.class,
                () -> SkyforgeSkyBiomeZoneSavedData.load(unsupported, null));

        CompoundTag duplicate = SkyforgeSkyBiomeZoneSavedData.emptyForTest(WORLD_SEED)
                .save(new CompoundTag(), null);
        var data = SkyforgeSkyBiomeZoneSavedData.emptyForTest(WORLD_SEED);
        assertTrue(data.assign(assignment(5L, SkyBiomeCatalog.OPEN_SKY_ID)));
        duplicate = data.save(new CompoundTag(), null);
        ListTag entries = duplicate.getList("assignments", Tag.TAG_COMPOUND);
        entries.add(entries.getCompound(0).copy());
        duplicate.put("assignments", entries);
        CompoundTag duplicatedTag = duplicate;
        assertThrows(
                IllegalArgumentException.class,
                () -> SkyforgeSkyBiomeZoneSavedData.load(duplicatedTag, null));
    }

    @Test
    void rejectsTooManyAssignments() {
        var data = SkyforgeSkyBiomeZoneSavedData.emptyForTest(WORLD_SEED);
        for (int i = 0; i < SkyforgeSkyBiomeZoneSavedData.MAXIMUM_ASSIGNMENTS; i++) {
            assertTrue(data.assign(assignment(i, SkyBiomeCatalog.OPEN_SKY_ID)));
        }
        assertThrows(
                IllegalStateException.class,
                () -> data.assign(assignment(SkyforgeSkyBiomeZoneSavedData.MAXIMUM_ASSIGNMENTS, SkyBiomeCatalog.OPEN_SKY_ID)));

        CompoundTag oversized = data.save(new CompoundTag(), null);
        ListTag entries = oversized.getList("assignments", Tag.TAG_COMPOUND);
        entries.add(entries.getCompound(0).copy());
        oversized.put("assignments", entries);
        assertThrows(
                IllegalArgumentException.class,
                () -> SkyforgeSkyBiomeZoneSavedData.load(oversized, null));
    }

    private static SkyBiomeZoneAssignment assignment(long zoneKey, SkyBiomeId id) {
        return assignmentForSeed(WORLD_SEED, zoneKey, id);
    }

    private static SkyBiomeZoneAssignment assignmentForSeed(long seed, long zoneKey, SkyBiomeId id) {
        return SkyBiomeZoneAssignment.of(SkyBiomeZoneIdentity.of(seed, zoneKey), id);
    }
}
