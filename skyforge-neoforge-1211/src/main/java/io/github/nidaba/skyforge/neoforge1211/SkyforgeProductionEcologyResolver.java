package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate2;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandEcologyRegime;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationAssociation;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationCatalog;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationSurfaceEcologyResolver;
import io.github.nidaba.skyforge.world.SkyIslandSurfaceSiteCapabilityProfiler;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

/** DR-40 adapter-owned mapping from accepted authored ecology to native Minecraft biome carriers. */
final class SkyforgeProductionEcologyResolver implements SkyforgeExactVolumeBiomeResolver {
    private final SkyIslandWorldVolumeId volumeId;
    private final SkyIslandAuthoredRealizationSurfaceEcologyResolver ecology;
    private final SkyforgeAuthoredSurfaceCellRasterizer hydrology;

    SkyforgeProductionEcologyResolver(SkyIslandAuthoredRealizationAssociation association) {
        Objects.requireNonNull(association, "association");
        this.volumeId = association.realizedVolumeId();
        var catalog = new SkyIslandAuthoredRealizationCatalog(
                association.authoredIdentity().worldSeed(),
                association.realizedVolumeId().archipelagoRootSeed(),
                List.of(association));
        this.ecology = new SkyIslandAuthoredRealizationSurfaceEcologyResolver(catalog);
        this.hydrology = new SkyforgeAuthoredSurfaceCellRasterizer(
                new SkyIslandSurfaceSiteCapabilityProfiler().profile(association));
    }

    @Override
    public ResourceKey<Biome> resolve(
            SkyIslandWorldVolumeId candidateVolumeId,
            int worldX,
            int worldY,
            int worldZ) {
        requireVolume(candidateVolumeId);
        // Native cave/interior algorithms require a registered biome context for every exact
        // physical column. PLAINS is a neutral technical fallback only; supportsSurface() remains
        // false there, so it cannot authorize vegetation or persistent surface-biome presentation.
        return resolveAuthoredSurface(candidateVolumeId, worldX, worldZ).orElse(Biomes.PLAINS);
    }

    @Override
    public boolean supportsSurface(
            SkyIslandWorldVolumeId candidateVolumeId,
            int worldX,
            int worldY,
            int worldZ) {
        requireVolume(candidateVolumeId);
        return resolveAuthoredSurface(candidateVolumeId, worldX, worldZ).isPresent();
    }

    Optional<ResourceKey<Biome>> resolveAuthoredSurface(
            SkyIslandWorldVolumeId candidateVolumeId,
            int worldX,
            int worldZ) {
        requireVolume(candidateVolumeId);
        var cell = hydrology.cellForWorldColumn(volumeId, worldX, worldZ);
        if (cell.isPresent() && hydrology.hasAuthoredFreshwaterOrRiparianContext(cell.orElseThrow())) {
            // AUTH-0096 freshwater/riparian evidence is independently accepted surface context.
            // Do not require a second ecology sample before assigning its already-accepted wet
            // Minecraft carrier; otherwise the native base-world biome can leak through precisely
            // along an authored channel margin.
            return Optional.of(Biomes.SWAMP);
        }
        var surface = ecology.sample(volumeId, new Coordinate2(worldX, worldZ));
        var authored = surface.ecologySample();
        if (authored.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(carrier(authored.orElseThrow().regime()));
    }

    boolean supportsCoordinatorSurface(
            SkyforgeNeoForge1211ChunkAdapter terrain,
            ChunkPos chunkPos) {
        Objects.requireNonNull(terrain, "terrain");
        Objects.requireNonNull(chunkPos, "chunkPos");
        for (var probe : SkyforgeNativeSurfacePopulationCoordinator.surfaceProbeOrder()) {
            int worldX = chunkPos.getMinBlockX() + probe.localX();
            int worldZ = chunkPos.getMinBlockZ() + probe.localZ();
            if (terrain.integerSolidRange(volumeId, worldX, worldZ).isEmpty()) {
                continue;
            }
            if (supportsSurface(volumeId, worldX, rangeY(terrain, worldX, worldZ), worldZ)) {
                return true;
            }
        }
        return false;
    }

    private void requireVolume(SkyIslandWorldVolumeId candidateVolumeId) {
        if (!volumeId.equals(Objects.requireNonNull(candidateVolumeId, "candidateVolumeId"))) {
            throw new IllegalArgumentException(
                    "DR-40 ecology resolver references a foreign exact volume: "
                            + candidateVolumeId.path());
        }
    }

    private int rangeY(SkyforgeNeoForge1211ChunkAdapter terrain, int worldX, int worldZ) {
        return terrain.integerSolidRange(volumeId, worldX, worldZ)
                .orElseThrow()
                .maximumY() + 1;
    }

    static ResourceKey<Biome> carrier(SkyIslandEcologyRegime regime) {
        Objects.requireNonNull(regime, "regime");
        return switch (regime) {
            case COLD_BARREN -> Biomes.SNOWY_PLAINS;
            case ALPINE -> Biomes.GROVE;
            case BOREAL_WOODLAND -> Biomes.TAIGA;
            case TEMPERATE_WOODLAND -> Biomes.FOREST;
            case HUMID_WOODLAND -> Biomes.JUNGLE;
            case OPEN_GRASSLAND -> Biomes.PLAINS;
            case DRY_SCRUB -> Biomes.SAVANNA;
            case WETLAND -> Biomes.SWAMP;
        };
    }
}
