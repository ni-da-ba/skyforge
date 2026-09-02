package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Resolves a terrain-matching top only inside one already-selected terrain domain.
 *
 * <p>The resolver deliberately has no concept of a global highest surface. Base Minecraft terrain
 * is supplied from a pre-Skyforge snapshot, while a Skyforge domain is supplied from the exact
 * compiled island identity. The two sources never compete and no downward scan crosses domains.
 */
final class MinecraftTerrainProjectionResolver {
    private MinecraftTerrainProjectionResolver() {}

    static OptionalInt resolveTop(
            MinecraftTerrainDomain domain,
            Supplier<OptionalInt> baseWorldTop,
            Function<SkyIslandWorldVolumeId, OptionalInt> skyforgeTop) {
        Objects.requireNonNull(domain, "domain");
        Objects.requireNonNull(baseWorldTop, "baseWorldTop");
        Objects.requireNonNull(skyforgeTop, "skyforgeTop");

        if (domain == MinecraftTerrainDomain.BaseWorld.INSTANCE) {
            return Objects.requireNonNull(baseWorldTop.get(), "baseWorldTop result");
        }
        if (domain instanceof MinecraftTerrainDomain.SkyforgeVolume skyforgeVolume) {
            return Objects.requireNonNull(
                    skyforgeTop.apply(skyforgeVolume.volumeId()),
                    "skyforgeTop result");
        }
        throw new IllegalStateException("unsupported Minecraft terrain domain: " + domain);
    }
}
