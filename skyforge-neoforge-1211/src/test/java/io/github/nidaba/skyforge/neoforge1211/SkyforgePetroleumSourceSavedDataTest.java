package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class SkyforgePetroleumSourceSavedDataTest {
    private static final ResourceLocation LOWER = ResourceLocation.fromNamespaceAndPath("skyforge", "lower");
    private static final ResourceLocation UPPER = ResourceLocation.fromNamespaceAndPath("skyforge", "upper");

    @Test
    void savesAndReloadsSortedExactVolumeTerminationAddresses() {
        SkyforgePetroleumSourceSavedData data = SkyforgePetroleumSourceSavedData.load(new CompoundTag(), null);
        var upper = new SkyforgePetroleumSourceAdapter.SourceAddress(UPPER, new BlockPos(24, 176, -8));
        var lower = new SkyforgePetroleumSourceAdapter.SourceAddress(LOWER, new BlockPos(24, 96, -8));
        data.replace(java.util.Map.of(upper, 1_000, lower, 725));

        CompoundTag encoded = data.save(new CompoundTag(), null);
        SkyforgePetroleumSourceSavedData restored = SkyforgePetroleumSourceSavedData.load(encoded, null);

        assertEquals(java.util.Map.of(upper, 1_000, lower, 725), restored.snapshot());
        assertEquals("skyforge:lower", encoded.getList("sources", 10).getCompound(0).getString("volume"));
    }

    @Test
    void rejectsMalformedDuplicateOrNegativeState() {
        CompoundTag invalid = new CompoundTag();
        ListTag entries = new ListTag();
        CompoundTag entry = new CompoundTag();
        entry.putString("volume", "skyforge:lower");
        entry.putLong("termination", BlockPos.ZERO.asLong());
        entry.putInt("remaining", -1);
        entries.add(entry);
        invalid.put("sources", entries);

        assertThrows(IllegalStateException.class, () -> SkyforgePetroleumSourceSavedData.load(invalid, null));
    }
}
