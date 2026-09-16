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
        if (!volumeId.equals(Objects.requireNonNull(candidateVolumeId, "candidateVolumeId"))) {
            throw new IllegalArgumentException(
                    "DR-40 ecology resolver references a foreign exact volume: "
                            + candidateVolumeId.path());
        }
        var surface = ecology.sample(volumeId, new Coordinate2(worldX, worldZ));
        var authored = surface.ecologySample().orElseThrow(() -> new IllegalStateException(
                "DR-40 native population sampled physical terrain without authored surface ecology at "
                        + worldX + "," + worldZ));
        var cell = hydrology.cellForWorldColumn(volumeId, worldX, worldZ);
        if (cell.isPresent() && hydrology.hasAuthoredFreshwaterOrRiparianContext(cell.orElseThrow())) {
            return Biomes.SWAMP;
        }
        return carrier(authored.regime());
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
