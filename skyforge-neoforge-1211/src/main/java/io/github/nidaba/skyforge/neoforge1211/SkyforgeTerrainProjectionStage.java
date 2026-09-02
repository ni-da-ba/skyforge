package io.github.nidaba.skyforge.neoforge1211;

/**
 * Thread-local marker for native structure/template placement executed by the Skyforge chunk
 * generator's biome-decoration seam.
 *
 * <p>The terrain-matching projection processor is shared globally by Minecraft. This scope keeps
 * Skyforge's vertical-ownership correction inert in ordinary generators and dimensions even when a
 * Skyforge runtime binding exists elsewhere in the process.
 */
final class SkyforgeTerrainProjectionStage {
    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> false);

    private SkyforgeTerrainProjectionStage() {}

    static Scope open() {
        if (ACTIVE.get()) {
            throw new IllegalStateException("Skyforge terrain-projection scope is already active on this thread");
        }
        ACTIVE.set(true);
        return new Scope();
    }

    static boolean active() {
        return ACTIVE.get();
    }

    static final class Scope implements AutoCloseable {
        private boolean closed;

        private Scope() {}

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
