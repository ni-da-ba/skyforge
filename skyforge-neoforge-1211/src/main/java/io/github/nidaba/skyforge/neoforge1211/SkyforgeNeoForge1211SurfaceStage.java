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
        if (binding.nativeSurfaceTopAdapter().isPresent()) {
            materialization = binding.nativeSurfaceTopAdapter().orElseThrow().adapt(chunk, materialization);
        }
        return Optional.of(binding.writer().writeSolidOverlay(chunk, materialization));
    }

    /**
     * Installs the accepted post-surface binding without native surface-top adaptation.
     *
     * <p>This path remains useful for exact engineering-palette proofs and compatibility tests.
     */
    static AutoCloseable install(
            SkyforgeNeoForge1211ChunkAdapter adapter,
            SkyforgeNeoForge1211ChunkWriter writer) {
        return install(adapter, writer, Optional.empty());
    }

    /**
     * Installs a post-surface binding that lets Minecraft's already-built native surface determine
     * the concrete block used for exposed Skyforge tops.
     */
    static AutoCloseable installNativeSurfaceAdapted(
            SkyforgeNeoForge1211ChunkAdapter adapter,
            SkyforgeNeoForge1211ChunkWriter writer) {
        return install(adapter, writer, Optional.of(new MinecraftNativeSurfaceTopAdapter()));
    }

    private static AutoCloseable install(
            SkyforgeNeoForge1211ChunkAdapter adapter,
            SkyforgeNeoForge1211ChunkWriter writer,
            Optional<MinecraftNativeSurfaceTopAdapter> nativeSurfaceTopAdapter) {
        RuntimeBinding binding = new RuntimeBinding(adapter, writer, nativeSurfaceTopAdapter);
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

    static boolean hasNativeSurfaceAdaptation() {
        RuntimeBinding binding = ACTIVE.get();
        return binding != null && binding.nativeSurfaceTopAdapter().isPresent();
    }

    private record RuntimeBinding(
            SkyforgeNeoForge1211ChunkAdapter adapter,
            SkyforgeNeoForge1211ChunkWriter writer,
            Optional<MinecraftNativeSurfaceTopAdapter> nativeSurfaceTopAdapter) {
        private RuntimeBinding {
            Objects.requireNonNull(adapter, "adapter");
            Objects.requireNonNull(writer, "writer");
            Objects.requireNonNull(nativeSurfaceTopAdapter, "nativeSurfaceTopAdapter");
        }
    }
}
