package io.github.nidaba.skyforge.neoforge1211;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

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

        MinecraftChunkMaterialization materialization = materialize(binding, chunk);
        if (binding.nativeSurfaceTopAdapter().isPresent()) {
            materialization = binding.nativeSurfaceTopAdapter().orElseThrow().adapt(chunk, materialization);
        }
        return Optional.of(binding.writer().writeSolidOverlay(chunk, materialization));
    }

    /**
     * Re-evaluates the active binding's authoritative Skyforge occupancy for a live chunk.
     *
     * <p>This is used by later Minecraft-owned stages that need to distinguish Skyforge-authored
     * solids from preserved native terrain. Representation adaptation is intentionally not applied
     * here because occupancy, rather than concrete top material, is the required fact.
     */
    static Optional<MinecraftChunkMaterialization> materializeOccupancy(ChunkAccess chunk) {
        Objects.requireNonNull(chunk, "chunk");
        RuntimeBinding binding = ACTIVE.get();
        if (binding == null) {
            return Optional.empty();
        }
        return Optional.of(materialize(binding, chunk));
    }

    /**
     * Evaluates the active Skyforge binding as an early generator height query without mutating a
     * chunk. The returned value follows Minecraft heightmap convention: one block above the highest
     * matching block in the requested column.
     *
     * <p>The query uses the same backend-owned block-state projection as live realization and then
     * applies Minecraft's own predicate for the requested heightmap type. Vanilla terrain is not
     * represented here; callers combine this optional Skyforge answer with the vanilla generator's
     * answer.
     */
    static OptionalInt queryBaseHeight(
            int worldX,
            int worldZ,
            Heightmap.Types type,
            int minimumY,
            int height) {
        Objects.requireNonNull(type, "type");
        if (height <= 0) {
            throw new IllegalArgumentException("height must be positive");
        }

        RuntimeBinding binding = ACTIVE.get();
        if (binding == null) {
            return OptionalInt.empty();
        }

        ChunkPos chunkPos = new ChunkPos(Math.floorDiv(worldX, 16), Math.floorDiv(worldZ, 16));
        MinecraftChunkMaterialization materialization = binding.adapter().materialize(
                chunkPos,
                minimumY,
                height);
        int localX = worldX - chunkPos.getMinBlockX();
        int localZ = worldZ - chunkPos.getMinBlockZ();
        int maximumYExclusive = Math.addExact(minimumY, height);

        for (int worldY = maximumYExclusive - 1; worldY >= minimumY; worldY--) {
            var key = materialization.blockKeyAt(localX, worldY, localZ);
            var state = binding.writer().resolveForQuery(key);
            if (type.isOpaque().test(state)) {
                return OptionalInt.of(worldY + 1);
            }
        }
        return OptionalInt.empty();
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

    private static MinecraftChunkMaterialization materialize(RuntimeBinding binding, ChunkAccess chunk) {
        return binding.adapter().materialize(
                chunk.getPos(),
                chunk.getMinBuildHeight(),
                chunk.getHeight());
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
