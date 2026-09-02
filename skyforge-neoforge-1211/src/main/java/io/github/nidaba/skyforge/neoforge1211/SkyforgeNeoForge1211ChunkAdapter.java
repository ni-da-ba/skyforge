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
import java.util.ArrayList;
import java.util.List;
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

    public SkyforgeNeoForge1211ChunkAdapter(
            SkyIslandWorldCatalog catalog,
            SkyIslandTerrainProfile terrainProfile,
            SkyforgeMinecraftBlockPalette palette) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.terrainProfile = Objects.requireNonNull(terrainProfile, "terrainProfile");
        this.palette = Objects.requireNonNull(palette, "palette");
    }

    /** Materializes one Minecraft chunk's owned block coordinates for the supplied vertical span. */
    public MinecraftChunkMaterialization materialize(ChunkPos chunkPos, int minimumY, int height) {
        MinecraftChunkBounds chunkBounds = new MinecraftChunkBounds(chunkPos, minimumY, height);
        var candidates = catalog.query(chunkBounds.worldBounds());
        List<SkyIslandTerrainInterpreter> interpreters = candidates.stream()
                .map(candidate -> new SkyIslandTerrainInterpreter(candidate.compiledVolume(), terrainProfile))
                .toList();

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
                candidates.size());
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
                .filter(candidate -> new SkyIslandTerrainInterpreter(candidate.compiledVolume(), terrainProfile)
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
        return catalog.query(pointBounds(worldX, worldY, worldZ)).stream()
                .filter(candidate -> candidate.id().equals(volumeId))
                .findFirst()
                .map(candidate -> new SkyIslandTerrainInterpreter(candidate.compiledVolume(), terrainProfile)
                        .classify(worldX, worldY, worldZ)
                        .isSolid())
                .orElse(false);
    }

    /** Returns the authoritative compiled underside height for one exact island at one X/Z. */
    double undersideSurfaceHeight(
            SkyIslandWorldVolumeId volumeId,
            int worldX,
            int worldZ) {
        Objects.requireNonNull(volumeId, "volumeId");
        var volume = catalog.volumes().stream()
                .filter(candidate -> candidate.id().equals(volumeId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown Skyforge world volume: " + volumeId.path()));
        return new SkyIslandTerrainInterpreter(volume.compiledVolume(), terrainProfile)
                .undersideSurfaceHeight(worldX, worldZ);
    }

    /**
     * Resolves the independent terrain domain containing one native placement anchor.
     *
     * <p>A placement anchor below every Skyforge volume belongs to the base world even when an
     * island is the globally highest surface at the same X/Z. An anchor within exactly one
     * deterministic island envelope belongs to that exact island. Overlapping candidate envelopes
     * are intentionally ambiguous and therefore return empty rather than merging identities.
     */
    Optional<MinecraftTerrainDomain> resolveTerrainDomain(int worldX, int placementAnchorY, int worldZ) {
        List<MinecraftTerrainDomain.SkyforgeVolume> candidates = new ArrayList<>();
        for (var volume : catalog.volumes()) {
            SkyIslandTerrainInterpreter interpreter =
                    new SkyIslandTerrainInterpreter(volume.compiledVolume(), terrainProfile);
            OptionalInt firstFree = firstFreeHeight(interpreter, worldX, worldZ);
            if (firstFree.isEmpty()) {
                continue;
            }
            double underside = interpreter.undersideSurfaceHeight(worldX, worldZ);
            if (placementAnchorY > underside
                    && placementAnchorY <= Math.addExact(firstFree.orElseThrow(), 1)) {
                candidates.add(new MinecraftTerrainDomain.SkyforgeVolume(volume.id()));
            }
        }
        if (candidates.isEmpty()) {
            return Optional.of(MinecraftTerrainDomain.BaseWorld.INSTANCE);
        }
        return candidates.size() == 1 ? Optional.of(candidates.getFirst()) : Optional.empty();
    }

    /** Returns one exact island's deterministic Minecraft first-free surface at X/Z. */
    OptionalInt firstFreeHeight(SkyIslandWorldVolumeId volumeId, int worldX, int worldZ) {
        Objects.requireNonNull(volumeId, "volumeId");
        return catalog.volumes().stream()
                .filter(candidate -> candidate.id().equals(volumeId))
                .findFirst()
                .map(candidate -> firstFreeHeight(
                        new SkyIslandTerrainInterpreter(candidate.compiledVolume(), terrainProfile),
                        worldX,
                        worldZ))
                .orElse(OptionalInt.empty());
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

    private static OptionalInt firstFreeHeight(
            SkyIslandTerrainInterpreter interpreter,
            int worldX,
            int worldZ) {
        double upper = interpreter.upperSurfaceHeight(worldX, worldZ);
        double underside = interpreter.undersideSurfaceHeight(worldX, worldZ);
        if (!(upper > underside)) {
            return OptionalInt.empty();
        }

        long highestCandidate = (long) Math.ceil(upper) - 1L;
        long lowestCandidate = (long) Math.floor(underside) + 1L;
        if (highestCandidate > Integer.MAX_VALUE
                || highestCandidate < Integer.MIN_VALUE
                || lowestCandidate > Integer.MAX_VALUE
                || lowestCandidate < Integer.MIN_VALUE) {
            return OptionalInt.empty();
        }
        for (int worldY = (int) highestCandidate; worldY >= (int) lowestCandidate; worldY--) {
            if (interpreter.classify(worldX, worldY, worldZ).isSolid()) {
                return OptionalInt.of(Math.addExact(worldY, 1));
            }
        }
        return OptionalInt.empty();
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
