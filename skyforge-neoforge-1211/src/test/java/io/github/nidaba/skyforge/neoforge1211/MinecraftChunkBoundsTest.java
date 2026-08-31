package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

final class MinecraftChunkBoundsTest {
    @Test
    void negativeChunkCoordinatesTranslateToExactClosedBlockBounds() {
        MinecraftChunkBounds bounds = new MinecraftChunkBounds(new ChunkPos(-2, 3), -64, 384);

        assertEquals(-32.0, bounds.worldBounds().minimumX());
        assertEquals(-17.0, bounds.worldBounds().maximumX());
        assertEquals(48.0, bounds.worldBounds().minimumZ());
        assertEquals(63.0, bounds.worldBounds().maximumZ());
        assertEquals(-64.0, bounds.worldBounds().minimumY());
        assertEquals(319.0, bounds.worldBounds().maximumY());
        assertEquals(319, bounds.maximumY());
    }

    @Test
    void invalidVerticalIntervalsFailEarly() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MinecraftChunkBounds(new ChunkPos(0, 0), 0, 0));
        assertThrows(
                ArithmeticException.class,
                () -> new MinecraftChunkBounds(new ChunkPos(0, 0), Integer.MAX_VALUE, 2));
    }
}
