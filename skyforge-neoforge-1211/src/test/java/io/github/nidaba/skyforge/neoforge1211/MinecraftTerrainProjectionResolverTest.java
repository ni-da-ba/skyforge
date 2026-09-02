package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

final class MinecraftTerrainProjectionResolverTest {
    private static final SkyIslandWorldVolumeId LOWER =
            new SkyIslandWorldVolumeId(1L, "lower", 0, 0, 11L);
    private static final SkyIslandWorldVolumeId UPPER =
            new SkyIslandWorldVolumeId(1L, "upper", 0, 1, 12L);

    @Test
    void vanillaTopWithoutSkyforgeOwnershipIsPreserved() {
        var result = MinecraftTerrainProjectionResolver.resolveTop(
                70,
                71,
                -64,
                y -> y == 70,
                y -> Optional.of(List.of()),
                id -> OptionalDouble.empty());

        assertEquals(71, result.orElseThrow());
    }

    @Test
    void provenUpperIslandIsSkippedToLowerVanillaTerrain() {
        var result = MinecraftTerrainProjectionResolver.resolveTop(
                70,
                224,
                -64,
                y -> y == 223 || y == 70,
                y -> Optional.of(y == 223 ? List.of(LOWER) : List.of()),
                id -> OptionalDouble.of(184.0));

        assertEquals(71, result.orElseThrow());
    }

    @Test
    void multipleProvenUpperVolumesCanBeSkippedWithoutMergingTheirIdentity() {
        var result = MinecraftTerrainProjectionResolver.resolveTop(
                70,
                264,
                -64,
                y -> y == 263 || y == 223 || y == 70,
                y -> Optional.of(switch (y) {
                    case 263 -> List.of(UPPER);
                    case 223 -> List.of(LOWER);
                    default -> List.of();
                }),
                id -> OptionalDouble.of(id.equals(UPPER) ? 240.0 : 184.0));

        assertEquals(71, result.orElseThrow());
    }

    @Test
    void anchorInsideVolumePreservesItsSurface() {
        var result = MinecraftTerrainProjectionResolver.resolveTop(
                200,
                224,
                -64,
                y -> y == 223,
                y -> Optional.of(y == 223 ? List.of(LOWER) : List.of()),
                id -> OptionalDouble.of(184.0));

        assertEquals(224, result.orElseThrow());
    }

    @Test
    void overlappingOwnershipFailsOpenInsteadOfChoosingAnIsland() {
        var result = MinecraftTerrainProjectionResolver.resolveTop(
                70,
                224,
                -64,
                y -> y == 223 || y == 70,
                y -> Optional.of(y == 223 ? List.of(LOWER, UPPER) : List.of()),
                id -> OptionalDouble.of(184.0));

        assertTrue(result.isEmpty());
    }

    @Test
    void missingUndersideEvidenceFailsOpen() {
        var result = MinecraftTerrainProjectionResolver.resolveTop(
                70,
                224,
                -64,
                y -> y == 223 || y == 70,
                y -> Optional.of(y == 223 ? List.of(LOWER) : List.of()),
                id -> OptionalDouble.empty());

        assertTrue(result.isEmpty());
    }

    @Test
    void absentLowerOpaqueTerrainFailsOpen() {
        var result = MinecraftTerrainProjectionResolver.resolveTop(
                70,
                224,
                -64,
                y -> y == 223,
                y -> Optional.of(y == 223 ? List.of(LOWER) : List.of()),
                id -> OptionalDouble.of(184.0));

        assertTrue(result.isEmpty());
    }
}
