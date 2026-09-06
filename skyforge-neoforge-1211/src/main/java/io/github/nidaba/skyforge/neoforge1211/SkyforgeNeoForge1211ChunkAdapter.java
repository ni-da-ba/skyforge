package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandSurfaceFoundationEvaluator;
import io.github.nidaba.skyforge.world.SkyIslandSurfaceSupportEvaluator;
import io.github.nidaba.skyforge.world.SkyIslandTerrainBoxObserver;
import io.github.nidaba.skyforge.world.SkyIslandTerrainInterpreter;
import io.github.nidaba.skyforge.world.SkyIslandTerrainProfile;
import io.github.nidaba.skyforge.world.SkyIslandTerrainSampleContext;
import io.github.nidaba.skyforge.world.SkyIslandTerrainSemantic;
import io.github.nidaba.skyforge.world.SkyIslandWorldCatalog;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import io.github.nidaba.skyforge.world.SurfaceFoundationAssessment;
import io.github.nidaba.skyforge.world.SurfaceFoundationRequirements;
import io.github.nidaba.skyforge.world.SurfaceSupportAssessment;
import io.github.nidaba.skyforge.world.SurfaceSupportRequirements;
import io.github.nidaba.skyforge.world.TerrainBoxObservation;
import io.github.nidaba.skyforge.world.TerrainBoxObservationRequirements;
import io.github.nidaba.skyforge.world.WorldBounds;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

/**
 * First concrete Minecraft-facing Skyforge realization seam.
 *
 * <p>The adapter queries the accepted world catalog once per chunk interval, evaluates only the
 * returned independently compiled island volumes, classifies the accepted terrain semantic at
 * each Minecraft block coordinate, then projects that semantic to a concrete vanilla block key.
 * It does not rerun composition planning or define backend-only morphology.
 */
public final class SkyforgeNeoForge1211ChunkAdapter {
    private static final int CHUNK_WIDTH = 16;

    private final SkyIslandWorldCatalog catalog;
    private final SkyIslandTerrainProfile terrainProfile;
    private final SkyforgeMinecraftBlockPalette palette;
    private final Map<SkyIslandWorldVolumeId, SkyIslandTerrainInterpreter> interpretersByVolumeId;

    public SkyforgeNeoForge1211ChunkAdapter(
            SkyIslandWorldCatalog catalog,
            SkyIslandTerrainProfile terrainProfile,
            SkyforgeMinecraftBlockPalette palette) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.terrainProfile = Objects.requireNonNull(terrainProfile, "terrainProfile");
        this.palette = Objects.requireNonNull(palette, "palette");

        var cachedInterpreters = new LinkedHashMap<SkyIslandWorldVolumeId, SkyIslandTerrainInterpreter>();
        for (var volume : catalog.volumes()) {
            SkyIslandTerrainInterpreter previous = cachedInterpreters.put(
                    volume.id(),
                    new SkyIslandTerrainInterpreter(volume.compiledVolume(), terrainProfile));
            if (previous != null) {
                throw new IllegalArgumentException(
                        "world catalog contains duplicate exact volume id: " + volume.id().path());
            }
        }
        this.interpretersByVolumeId = Map.copyOf(cachedInterpreters);
    }

    /** Returns whether the supplied Minecraft chunk interval intersects any planned Skyforge volume. */
    boolean hasCandidateVolume(ChunkPos chunkPos, int minimumY, int height) {
        Objects.requireNonNull(chunkPos, "chunkPos");
        if (height <= 0) {
            throw new IllegalArgumentException("height must be positive");
        }
        MinecraftChunkBounds chunkBounds = new MinecraftChunkBounds(chunkPos, minimumY, height);
        return !catalog.query(chunkBounds.worldBounds()).isEmpty();
    }

    /** Materializes one Minecraft chunk's composite Skyforge contribution for the supplied span. */
    public MinecraftChunkMaterialization materialize(ChunkPos chunkPos, int minimumY, int height) {
        MinecraftChunkBounds chunkBounds = new MinecraftChunkBounds(chunkPos, minimumY, height);
        var candidates = catalog.query(chunkBounds.worldBounds());
        List<SkyIslandTerrainInterpreter> interpreters = candidates.stream()
                .map(candidate -> requireInterpreter(candidate.id()))
                .toList();
        return materialize(chunkPos, minimumY, height, interpreters, candidates.size());
    }

    /**
     * Deterministically rematerializes one exact independently compiled volume in one chunk.
     *
     * <p>This is the deferred-realization seam used after whole-volume physical admission. It never
     * consults another Skyforge volume and therefore allows a pending catch-up key to contain only
     * {@code (volumeId, chunkPos)} rather than retaining mutable generation-region state.
     */
    public MinecraftChunkMaterialization materialize(
            SkyIslandWorldVolumeId volumeId,
            ChunkPos chunkPos,
            int minimumY,
            int height) {
        Objects.requireNonNull(volumeId, "volumeId");
        return materialize(chunkPos, minimumY, height, List.of(requireInterpreter(volumeId)), 1);
    }

    private MinecraftChunkMaterialization materialize(
            ChunkPos chunkPos,
            int minimumY,
            int height,
            List<SkyIslandTerrainInterpreter> interpreters,
            int candidateVolumeReferences) {
        Objects.requireNonNull(chunkPos, "chunkPos");
        Objects.requireNonNull(interpreters, "interpreters");
        if (height <= 0) {
            throw new IllegalArgumentException("height must be positive");
        }

        ResourceLocation[] blockKeys = new ResourceLocation[Math.multiplyExact(
                Math.multiplyExact(CHUNK_WIDTH, CHUNK_WIDTH), height)];
        int minimumX = chunkPos.getMinBlockX();
        int minimumZ = chunkPos.getMinBlockZ();

        for (int localY = 0; localY < height; localY++) {
            int worldY = Math.addExact(minimumY, localY);
            for (int localZ = 0; localZ < CHUNK_WIDTH; localZ++) {
                int worldZ = Math.addExact(minimumZ, localZ);
                for (int localX = 0; localX < CHUNK_WIDTH; localX++) {
                    int worldX = Math.addExact(minimumX, localX);
                    SkyIslandTerrainSemantic semantic = classify(interpreters, worldX, worldY, worldZ);
                    SkyIslandTerrainSampleContext context =
                            new SkyIslandTerrainSampleContext(worldX, worldY, worldZ, semantic);
                    ResourceLocation blockKey = palette.blockKey(context);
                    if (!palette.preservesOccupancy(semantic, blockKey)) {
                        throw new IllegalStateException("Minecraft palette changed authoritative Skyforge occupancy");
                    }
                    blockKeys[linearIndex(localX, localY, localZ)] = blockKey;
                }
            }
        }

        return new MinecraftChunkMaterialization(
                chunkPos,
                minimumY,
                height,
                blockKeys,
                candidateVolumeReferences);
    }

    /** Returns the backend-neutral bounds of one exact compiled world volume. */
    Optional<WorldBounds> volumeBounds(SkyIslandWorldVolumeId volumeId) {
        Objects.requireNonNull(volumeId, "volumeId");
        return catalog.volumes().stream()
                .filter(candidate -> candidate.id().equals(volumeId))
                .findFirst()
                .map(candidate -> candidate.bounds());
    }

    /**
     * Returns the independently compiled island volumes that actually own a solid Skyforge sample.
     *
     * <p>This is a provenance query for the Minecraft adapter only. It does not merge vertically
     * stacked islands and therefore preserves the world catalog's independent-volume semantics.
     */
    List<SkyIslandWorldVolumeId> claimingVolumeIds(int worldX, int worldY, int worldZ) {
        WorldBounds pointBounds = pointBounds(worldX, worldY, worldZ);
        return catalog.query(pointBounds).stream()
                .filter(candidate -> requireInterpreter(candidate.id())
                        .classify(worldX, worldY, worldZ)
                        .isSolid())
                .map(candidate -> candidate.id())
                .toList();
    }

    /** Returns whether one exact compiled island owns a solid sample at the supplied coordinate. */
    boolean isSolidOwnedBy(
            SkyIslandWorldVolumeId volumeId,
            int worldX,
            int worldY,
            int worldZ) {
        Objects.requireNonNull(volumeId, "volumeId");
        SkyIslandTerrainInterpreter interpreter = interpretersByVolumeId.get(volumeId);
        return interpreter != null
                && interpreter.classify(worldX, worldY, worldZ).isSolid();
    }

    /** Returns whether any different exact compiled volume owns this solid sample. */
    boolean isSolidOwnedByOtherVolume(
            SkyIslandWorldVolumeId volumeId,
            int worldX,
            int worldY,
            int worldZ) {
        Objects.requireNonNull(volumeId, "volumeId");
        if (interpretersByVolumeId.size() <= 1) {
            return false;
        }
        return catalog.query(pointBounds(worldX, worldY, worldZ)).stream()
                .filter(candidate -> !candidate.id().equals(volumeId))
                .anyMatch(candidate -> requireInterpreter(candidate.id())
                        .classify(worldX, worldY, worldZ)
                        .isSolid());
    }

    /**
     * Returns the first-free Minecraft Y for one exact island volume in the requested build span.
     *
     * <p>This intentionally ignores vanilla terrain and every other Skyforge volume. A missing solid
     * column yields an empty result rather than falling through to another terrain owner.
     */
    OptionalInt firstFreeHeight(
            SkyIslandWorldVolumeId volumeId,
            int worldX,
            int worldZ,
            int minimumY,
            int height) {
        Objects.requireNonNull(volumeId, "volumeId");
        if (height <= 0) {
            throw new IllegalArgumentException("height must be positive");
        }
        SkyIslandTerrainInterpreter interpreter = requireInterpreter(volumeId);
        int maximumYExclusive = Math.addExact(minimumY, height);
        for (int worldY = maximumYExclusive - 1; worldY >= minimumY; worldY--) {
            if (interpreter.classify(worldX, worldY, worldZ).isSolid()) {
                return OptionalInt.of(worldY + 1);
            }
        }
        return OptionalInt.empty();
    }

    private SkyIslandTerrainInterpreter requireInterpreter(SkyIslandWorldVolumeId volumeId) {
        SkyIslandTerrainInterpreter interpreter = interpretersByVolumeId.get(volumeId);
        if (interpreter == null) {
            throw new IllegalArgumentException("unknown Skyforge world volume: " + volumeId.path());
        }
        return interpreter;
    }

    /** Delegates structure-sized support assessment to the accepted backend-neutral evaluator. */
    List<SurfaceSupportAssessment> assessSurfaceSupport(SurfaceSupportRequirements requirements) {
        return new SkyIslandSurfaceSupportEvaluator().assess(catalog, requirements);
    }

    /** Delegates bounded fill-only accommodation assessment to the backend-neutral evaluator. */
    List<SurfaceFoundationAssessment> assessSurfaceFoundation(SurfaceFoundationRequirements requirements) {
        return new SkyIslandSurfaceFoundationEvaluator().assess(catalog, requirements);
    }

    /**
     * Observes one finite 3-D box against one exact compiled Skyforge volume without deriving policy.
     *
     * <p>The exact identity lookup intentionally does not spatially prefilter the requested box: a
     * future caller may need evidence that native geometry lies wholly above or below the admitted
     * volume's own surface envelope.
     */
    TerrainBoxObservation observeTerrainBox(
            SkyIslandWorldVolumeId volumeId,
            TerrainBoxObservationRequirements requirements) {
        Objects.requireNonNull(volumeId, "volumeId");
        Objects.requireNonNull(requirements, "requirements");
        var volume = catalog.volumes().stream()
                .filter(candidate -> candidate.id().equals(volumeId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown Skyforge world volume: " + volumeId.path()));
        return new SkyIslandTerrainBoxObserver().observe(volume, terrainProfile, requirements);
    }

    private static WorldBounds pointBounds(int worldX, int worldY, int worldZ) {
        return new WorldBounds(
                worldX,
                worldX,
                worldY,
                worldY,
                worldZ,
                worldZ);
    }

    private static SkyIslandTerrainSemantic classify(
            List<SkyIslandTerrainInterpreter> interpreters,
            double x,
            double y,
            double z) {
        for (SkyIslandTerrainInterpreter interpreter : interpreters) {
            SkyIslandTerrainSemantic semantic = interpreter.classify(x, y, z);
            if (semantic.isSolid()) {
                return semantic;
            }
        }
        return SkyIslandTerrainSemantic.AIR;
    }

    private static int linearIndex(int localX, int localY, int localZ) {
        return localX + CHUNK_WIDTH * (localZ + CHUNK_WIDTH * localY);
    }
}
