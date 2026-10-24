package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyBiomeCatalog;
import io.github.nidaba.skyforge.model.skyisland.SkyBiomeId;
import io.github.nidaba.skyforge.model.skyisland.SkyBiomeZoneAssignment;
import io.github.nidaba.skyforge.model.skyisland.SkyBiomeZoneIdentity;
import io.github.nidaba.skyforge.world.SkyBiomeSpatialField;
import io.github.nidaba.skyforge.world.SkyBiomeSpatialFieldCompiler;
import io.github.nidaba.skyforge.world.SkyBiomeSpatialZone;
import io.github.nidaba.skyforge.world.SkyIslandWorldCatalog;
import io.github.nidaba.skyforge.world.WorldBounds;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

final class SkyforgeSkyBiomeZoneSavedDataTest {
    private static final long WORLD_SEED = 0xD15EA5ECAFEL;

    @Test
    void compiledSpatialFieldAssignmentsArePersistedAndIdempotent() {
        WorldBounds province = new WorldBounds(-128.0, 128.0, 0.0, 256.0, -128.0, 128.0);
        var field = new SkyBiomeSpatialFieldCompiler().compile(
                WORLD_SEED,
                province,
                new SkyIslandWorldCatalog(WORLD_SEED, List.of()),
                List.of(),
                24.0);
        var data = SkyforgeSkyBiomeZoneSavedData.emptyForTest(WORLD_SEED);

        assertEquals(1, data.assignField(field));
        assertEquals(0, data.assignField(field));

        CompoundTag saved = data.save(new CompoundTag(), null);
        var loaded = SkyforgeSkyBiomeZoneSavedData.load(saved.copy(), null);
        assertEquals(
                field.zones().stream().map(zone -> zone.assignment()).toList(),
                loaded.assignments());
        assertEquals(saved, loaded.save(new CompoundTag(), null));
    }

    @Test
    void compiledFieldConflictDoesNotPartiallyMutateSavedData() {
        WorldBounds province = new WorldBounds(-128.0, 128.0, 0.0, 256.0, -128.0, 128.0);
        var field = new SkyBiomeSpatialFieldCompiler().compile(
                WORLD_SEED,
                province,
                new SkyIslandWorldCatalog(WORLD_SEED, List.of()),
                List.of(new WorldBounds(-16.0, 16.0, 64.0, 96.0, -16.0, 16.0)),
                24.0);
        var conflictingZone = field.zones().stream()
                .filter(zone -> zone.assignment().skyBiomeId().equals(SkyBiomeCatalog.CLOUD_SHELF_ID))
                .findFirst()
                .orElseThrow()
                .assignment();
        var data = SkyforgeSkyBiomeZoneSavedData.emptyForTest(WORLD_SEED);
        var existing = assignment(conflictingZone.zoneIdentity().zoneKey(), SkyBiomeCatalog.OPEN_SKY_ID);
        assertTrue(data.assign(existing));

        assertThrows(IllegalStateException.class, () -> data.assignField(field));
        assertEquals(List.of(existing), data.assignments());
    }

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
    void rejectsWrongAssignmentListElementType() {
        CompoundTag malformed = new CompoundTag();
        malformed.putInt("schemaVersion", SkyforgeSkyBiomeZoneSavedData.DATA_SCHEMA_VERSION);
        malformed.putLong("worldSeed", WORLD_SEED);
        ListTag entries = new ListTag();
        entries.add(StringTag.valueOf("not an assignment"));
        malformed.put("assignments", entries);

        assertThrows(
                IllegalArgumentException.class,
                () -> SkyforgeSkyBiomeZoneSavedData.load(malformed, null));
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

        WorldBounds province = new WorldBounds(-128.0, 128.0, 0.0, 256.0, -128.0, 128.0);
        var overflowField = new SkyBiomeSpatialField(
                WORLD_SEED,
                province,
                List.of(
                        new SkyBiomeSpatialZone(
                                province,
                                assignment(-2L, SkyBiomeCatalog.OPEN_SKY_ID)),
                        new SkyBiomeSpatialZone(
                                new WorldBounds(-16.0, 16.0, 64.0, 96.0, -16.0, 16.0),
                                assignment(-1L, SkyBiomeCatalog.CLOUD_SHELF_ID))));
        assertThrows(IllegalStateException.class, () -> data.assignField(overflowField));
        assertEquals(SkyforgeSkyBiomeZoneSavedData.MAXIMUM_ASSIGNMENTS, data.assignments().size());

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
