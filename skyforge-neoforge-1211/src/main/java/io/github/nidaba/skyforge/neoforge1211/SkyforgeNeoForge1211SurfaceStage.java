package io.github.nidaba.skyforge.neoforge1211;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * Runtime binding consumed by the Skyforge post-surface chunk-generator seam.
 *
 * <p>The registered chunk generator remains inert until a binding is installed explicitly. The
 * binding owns only already-compiled backend runtime state: no world/group/archipelago planning is
 * performed from the per-chunk generation path.
 */
public final class SkyforgeNeoForge1211SurfaceStage {
    private static final AtomicReference<RuntimeBinding> ACTIVE = new AtomicReference<>();

    private SkyforgeNeoForge1211SurfaceStage() {}

    /**
     * Realizes Skyforge into the supplied chunk after vanilla surface construction.
     *
     * <p>An empty result means no runtime binding is active and therefore no block was touched.
     */
    static Optional<MinecraftChunkWriteResult> realize(ChunkAccess chunk) {
        Objects.requireNonNull(chunk, "chunk");
        RuntimeBinding binding = ACTIVE.get();
        if (binding == null) {
            return Optional.empty();
        }

        MinecraftChunkMaterialization materialization = binding.adapter().materialize(
                chunk.getPos(),
                chunk.getMinBuildHeight(),
                chunk.getHeight());
        return Optional.of(binding.writer().writeSolidOverlay(chunk, materialization));
    }

    /** Installs exactly one compiled runtime binding for the post-surface generator seam. */
    static AutoCloseable install(
            SkyforgeNeoForge1211ChunkAdapter adapter,
            SkyforgeNeoForge1211ChunkWriter writer) {
        RuntimeBinding binding = new RuntimeBinding(adapter, writer);
        if (!ACTIVE.compareAndSet(null, binding)) {
            throw new IllegalStateException("a Skyforge post-surface runtime binding is already installed");
        }
        return () -> {
            if (!ACTIVE.compareAndSet(binding, null)) {
                throw new IllegalStateException("Skyforge post-surface runtime binding changed before close");
            }
        };
    }

    static boolean hasActiveBinding() {
        return ACTIVE.get() != null;
    }

    private record RuntimeBinding(
            SkyforgeNeoForge1211ChunkAdapter adapter,
            SkyforgeNeoForge1211ChunkWriter writer) {
        private RuntimeBinding {
            Objects.requireNonNull(adapter, "adapter");
            Objects.requireNonNull(writer, "writer");
        }
    }
}
