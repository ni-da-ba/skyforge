package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import io.github.nidaba.skyforge.world.SurfaceFoundationAssessment;
import io.github.nidaba.skyforge.world.SurfaceFoundationRequirements;
import io.github.nidaba.skyforge.world.SurfaceSupportAssessment;
import io.github.nidaba.skyforge.world.SurfaceSupportRequirements;
import io.github.nidaba.skyforge.world.TerrainBoxObservation;
import io.github.nidaba.skyforge.world.TerrainBoxObservationRequirements;
import java.util.List;
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

    static Optional<MinecraftChunkMaterialization> materializeOccupancy(ChunkAccess chunk) {
        Objects.requireNonNull(chunk, "chunk");
        RuntimeBinding binding = ACTIVE.get();
        if (binding == null) {
            return Optional.empty();
        }
        return Optional.of(materialize(binding, chunk));
    }

    /** Backward-compatible scalar view of the richer early-height provenance query. */
    static OptionalInt queryBaseHeight(
            int worldX,
            int worldZ,
            Heightmap.Types type,
            int minimumY,
            int height) {
        Optional<MinecraftSkyforgeHeightClaim> claim = queryBaseHeightClaim(
                worldX, worldZ, type, minimumY, height);
        return claim.isPresent() ? OptionalInt.of(claim.orElseThrow().height()) : OptionalInt.empty();
    }

    /**
     * Evaluates the active Skyforge binding as a composite early generator height query.
     *
     * <p>This legacy aggregate query is retained for compatibility tests and diagnostics. Ordinary
     * base-world generation must not call it under SF-IMP-0052; island-owned generation uses the
     * exact-volume overload below.
     */
    static Optional<MinecraftSkyforgeHeightClaim> queryBaseHeightClaim(
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
            return Optional.empty();
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
                var volumeIds = binding.adapter().claimingVolumeIds(worldX, worldY, worldZ);
                if (volumeIds.isEmpty()) {
                    throw new IllegalStateException("materialized Skyforge height has no owning world volume");
                }
                return Optional.of(new MinecraftSkyforgeHeightClaim(worldY + 1, volumeIds));
            }
        }
        return Optional.empty();
    }

    /**
     * Evaluates one exact independently compiled island as an early height query.
     *
     * <p>Vanilla terrain and other stacked islands are intentionally invisible. An empty island
     * column remains empty rather than falling through to a different terrain owner.
     */
    static Optional<MinecraftSkyforgeHeightClaim> queryBaseHeightClaim(
            SkyIslandWorldVolumeId volumeId,
            int worldX,
            int worldZ,
            Heightmap.Types type,
            int minimumY,
            int height) {
        Objects.requireNonNull(volumeId, "volumeId");
        Objects.requireNonNull(type, "type");
        RuntimeBinding binding = ACTIVE.get();
        if (binding == null) {
            return Optional.empty();
        }
        OptionalInt firstFree = binding.adapter().firstFreeHeight(volumeId, worldX, worldZ, minimumY, height);
        return firstFree.isPresent()
                ? Optional.of(new MinecraftSkyforgeHeightClaim(firstFree.getAsInt(), List.of(volumeId)))
                : Optional.empty();
    }

    /** Evaluates neutral support requirements against the active compiled Skyforge catalog. */
    static Optional<List<SurfaceSupportAssessment>> assessSurfaceSupport(SurfaceSupportRequirements requirements) {
        Objects.requireNonNull(requirements, "requirements");
        RuntimeBinding binding = ACTIVE.get();
        return binding == null
                ? Optional.empty()
                : Optional.of(binding.adapter().assessSurfaceSupport(requirements));
    }

    /** Evaluates bounded fill-only accommodation requirements against the backend-neutral evaluator. */
    static Optional<List<SurfaceFoundationAssessment>> assessSurfaceFoundation(
            SurfaceFoundationRequirements requirements) {
        Objects.requireNonNull(requirements, "requirements");
        RuntimeBinding binding = ACTIVE.get();
        return binding == null
                ? Optional.empty()
                : Optional.of(binding.adapter().assessSurfaceFoundation(requirements));
    }

    /** Observes one finite 3-D box against one exact Skyforge volume without deriving eligibility. */
    static Optional<TerrainBoxObservation> observeTerrainBox(
            SkyIslandWorldVolumeId volumeId,
            TerrainBoxObservationRequirements requirements) {
        Objects.requireNonNull(volumeId, "volumeId");
        Objects.requireNonNull(requirements, "requirements");
        RuntimeBinding binding = ACTIVE.get();
        return binding == null
                ? Optional.empty()
                : Optional.of(binding.adapter().observeTerrainBox(volumeId, requirements));
    }

    /** Returns whether the exact recorded island volume owns a solid sample at the supplied block. */
    static Optional<Boolean> isSolidOwnedBy(
            SkyIslandWorldVolumeId volumeId,
            int worldX,
            int worldY,
            int worldZ) {
        Objects.requireNonNull(volumeId, "volumeId");
        RuntimeBinding binding = ACTIVE.get();
        return binding == null
                ? Optional.empty()
                : Optional.of(binding.adapter().isSolidOwnedBy(volumeId, worldX, worldY, worldZ));
    }

    static AutoCloseable install(
            SkyforgeNeoForge1211ChunkAdapter adapter,
            SkyforgeNeoForge1211ChunkWriter writer) {
        return install(adapter, writer, Optional.empty());
    }

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
