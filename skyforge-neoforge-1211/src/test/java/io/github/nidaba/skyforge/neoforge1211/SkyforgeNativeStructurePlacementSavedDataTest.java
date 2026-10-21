package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class SkyforgeNativeStructurePlacementSavedDataTest {
    @Test
    void completedPlacementSurvivesSaveLoadAndRemainsOwned() {
        var volumeId = new SkyIslandWorldVolumeId(9L, "dr30", 1, 2, 10L);
        var identity = new SkyforgeNativeStructurePlacementSavedData.PlacementIdentity(
                volumeId,
                44L,
                ResourceLocation.parse("minecraft:woodland_mansion"),
                45L,
                0, 100, 0, 31, 140, 31);
        var data = SkyforgeNativeStructurePlacementSavedData.emptyForTest();

        assertTrue(data.registerOwned(identity));
        assertFalse(data.completed(identity));
        data.complete(identity);
        assertTrue(data.completed(identity));

        CompoundTag serialized = data.save(new CompoundTag(), null);
        var loaded = SkyforgeNativeStructurePlacementSavedData.load(serialized, null);
        assertTrue(loaded.ownedFor(volumeId, 44L).contains(identity));
        assertTrue(loaded.completed(identity));
        assertFalse(loaded.registerOwned(identity));
    }
}
