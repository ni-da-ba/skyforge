package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;

/**
 * Scoped, per-position authority for terrain emitted by a native provider.
 *
 * <p>Provider writes are kept separate from the compiled Skyforge solid mask. A caller may grant
 * only positions it derived from an exact island's admitted top-surface domain; the physical
 * admission stage rechecks the owner, bounds, and competing volumes at the final write.
 */
final class SkyforgeNativeTerrainWriteAuthority {
    private static final ThreadLocal<Map<Long, SkyIslandWorldVolumeId>> ACTIVE = new ThreadLocal<>();

    private SkyforgeNativeTerrainWriteAuthority() {}

    static AutoCloseable open(Map<Long, SkyIslandWorldVolumeId> claims) {
        Objects.requireNonNull(claims, "claims");
        Map<Long, SkyIslandWorldVolumeId> scoped = Map.copyOf(claims);
        scoped.forEach((position, owner) -> {
            Objects.requireNonNull(position, "claimed position");
            Objects.requireNonNull(owner, "claim owner");
        });
        Map<Long, SkyIslandWorldVolumeId> previous = ACTIVE.get();
        ACTIVE.set(scoped);
        return () -> {
            if (ACTIVE.get() != scoped) {
                throw new IllegalStateException("native terrain write-authority scopes closed out of order");
            }
            if (previous == null) {
                ACTIVE.remove();
            } else {
                ACTIVE.set(previous);
            }
        };
    }

    static Optional<SkyIslandWorldVolumeId> ownerAt(int worldX, int worldY, int worldZ) {
        Map<Long, SkyIslandWorldVolumeId> claims = ACTIVE.get();
        return claims == null
                ? Optional.empty()
                : Optional.ofNullable(claims.get(BlockPos.asLong(worldX, worldY, worldZ)));
    }
}
