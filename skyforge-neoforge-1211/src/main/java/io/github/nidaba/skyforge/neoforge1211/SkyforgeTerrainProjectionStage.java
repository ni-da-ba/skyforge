package io.github.nidaba.skyforge.neoforge1211;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * Thread-local terrain-domain context for native template placement executed by Skyforge.
 *
 * <p>The shared terrain-matching processor is global Minecraft state, but a Skyforge decoration
 * pass carries the immutable base-world surface snapshot captured before any island overlay. This
 * keeps the correction inert in ordinary generators and prevents base terrain queries from ever
 * consulting Skyforge island blocks.
 */
final class SkyforgeTerrainProjectionStage {
    private static final ThreadLocal<Context> ACTIVE = new ThreadLocal<>();

    private SkyforgeTerrainProjectionStage() {}

    static Scope open(MinecraftBaseTerrainSurfaceSnapshot baseTerrain) {
        Objects.requireNonNull(baseTerrain, "baseTerrain");
        if (ACTIVE.get() != null) {
            throw new IllegalStateException("Skyforge terrain-projection scope is already active on this thread");
        }
        ACTIVE.set(new Context(baseTerrain));
        return new Scope();
    }

    static boolean active() {
        return ACTIVE.get() != null;
    }

    static OptionalInt baseWorldFirstFreeHeight(int worldX, int worldZ) {
        Context context = ACTIVE.get();
        return context == null
                ? OptionalInt.empty()
                : context.baseTerrain().firstFreeHeight(worldX, worldZ);
    }

    private record Context(MinecraftBaseTerrainSurfaceSnapshot baseTerrain) {
        private Context {
            Objects.requireNonNull(baseTerrain, "baseTerrain");
        }
    }

    static final class Scope implements AutoCloseable {
        private boolean closed;

        private Scope() {}

        void requireActive() {
            if (closed || ACTIVE.get() == null) {
                throw new IllegalStateException("Skyforge terrain-projection scope is not active");
            }
        }

        @Override
        public void close() {
            if (closed) {
                throw new IllegalStateException("Skyforge terrain-projection scope already closed");
            }
            closed = true;
            ACTIVE.remove();
        }
    }
}
