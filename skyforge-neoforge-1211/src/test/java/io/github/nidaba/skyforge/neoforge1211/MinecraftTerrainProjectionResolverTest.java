package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

final class MinecraftTerrainProjectionResolverTest {
    private static final SkyIslandWorldVolumeId ISLAND =
            new SkyIslandWorldVolumeId(1L, "island", 0, 0, 11L);

    @Test
    void baseWorldReadsOnlyBaseWorldAuthority() {
        var result = MinecraftTerrainProjectionResolver.resolveTop(
                MinecraftTerrainDomain.BaseWorld.INSTANCE,
                () -> OptionalInt.of(71),
                id -> OptionalInt.of(224));

        assertEquals(71, result.orElseThrow());
    }

    @Test
    void skyforgeDomainReadsOnlyExactIslandAuthority() {
        var result = MinecraftTerrainProjectionResolver.resolveTop(
                new MinecraftTerrainDomain.SkyforgeVolume(ISLAND),
                () -> OptionalInt.of(71),
                id -> id.equals(ISLAND) ? OptionalInt.of(224) : OptionalInt.empty());

        assertEquals(224, result.orElseThrow());
    }

    @Test
    void missingBaseWorldEvidenceFailsOpen() {
        var result = MinecraftTerrainProjectionResolver.resolveTop(
                MinecraftTerrainDomain.BaseWorld.INSTANCE,
                OptionalInt::empty,
                id -> OptionalInt.of(224));

        assertTrue(result.isEmpty());
    }

    @Test
    void missingExactIslandEvidenceFailsOpen() {
        var result = MinecraftTerrainProjectionResolver.resolveTop(
                new MinecraftTerrainDomain.SkyforgeVolume(ISLAND),
                () -> OptionalInt.of(71),
                id -> OptionalInt.empty());

        assertTrue(result.isEmpty());
    }
}
