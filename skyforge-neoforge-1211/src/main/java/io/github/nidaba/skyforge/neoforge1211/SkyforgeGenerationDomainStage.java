package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.Objects;
import java.util.Optional;

/**
 * Thread-confined declaration that one native worldgen operation belongs to one exact Skyforge
 * island volume.
 *
 * <p>No scope means {@code BASE_WORLD}. That default is deliberate: ordinary vanilla and modded
 * worldgen must never become Skyforge-owned merely because an island overlaps the same X/Z column.
 * Only an explicit island-population pass may open this scope.
 */
final class SkyforgeGenerationDomainStage {
    private static final ThreadLocal<SkyIslandWorldVolumeId> ACTIVE_ISLAND = new ThreadLocal<>();

    private SkyforgeGenerationDomainStage() {}

    static Scope openIsland(SkyIslandWorldVolumeId volumeId) {
        Objects.requireNonNull(volumeId, "volumeId");
        if (ACTIVE_ISLAND.get() != null) {
            throw new IllegalStateException("nested Skyforge generation-domain scopes are not supported");
        }
        ACTIVE_ISLAND.set(volumeId);
        return new Scope(volumeId);
    }

    static Optional<SkyIslandWorldVolumeId> activeIslandVolumeId() {
        return Optional.ofNullable(ACTIVE_ISLAND.get());
    }

    static boolean isBaseWorld() {
        return ACTIVE_ISLAND.get() == null;
    }

    static final class Scope implements AutoCloseable {
        private final SkyIslandWorldVolumeId volumeId;
        private boolean closed;

        private Scope(SkyIslandWorldVolumeId volumeId) {
            this.volumeId = volumeId;
        }

        void requireActive() {
            if (closed || !volumeId.equals(ACTIVE_ISLAND.get())) {
                throw new IllegalStateException("Skyforge generation-domain scope is not active");
            }
        }

        @Override
        public void close() {
            requireActive();
            closed = true;
            ACTIVE_ISLAND.remove();
        }
    }
}
