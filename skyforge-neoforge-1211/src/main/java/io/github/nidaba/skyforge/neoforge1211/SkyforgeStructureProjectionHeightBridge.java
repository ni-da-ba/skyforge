package io.github.nidaba.skyforge.neoforge1211;

import java.util.Objects;
import java.util.OptionalInt;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Resolves terrain-matching structure height without allowing one world domain to project onto an
 * unrelated vertically stacked Skyforge volume.
 *
 * <p>Vanilla's gravity structure processor normally reads the live worldgen heightmap. That is
 * correct until a neighboring chunk has already completed Skyforge realization: the live heightmap
 * can then report a suspended island even while the structure currently being placed belongs to
 * BASE_WORLD. This bridge preserves the live answer unless that exact vertical ambiguity exists.
 *
 * <p>An explicit Skyforge generation-domain scope is stronger: island-owned structure projection
 * must use that exact island's generator height regardless of other live surfaces at the same X/Z.
 */
public final class SkyforgeStructureProjectionHeightBridge {
    private SkyforgeStructureProjectionHeightBridge() {}

    /** Called only from the GravityProcessor mixin's redirected height lookup. */
    public static int resolve(
            LevelReader level,
            Heightmap.Types heightmapType,
            int worldX,
            int worldZ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(heightmapType, "heightmapType");

        int liveHeight = level.getHeight(heightmapType, worldX, worldZ);
        if (!(level instanceof WorldGenRegion region)
                || !SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()) {
            return liveHeight;
        }
        if (!(region.getChunkSource() instanceof ServerChunkCache chunkSource)) {
            return liveHeight;
        }

        int domainHeight = chunkSource.getGenerator().getBaseHeight(
                worldX,
                worldZ,
                heightmapType,
                region,
                chunkSource.randomState());

        // An explicit island-owned operation already defines its complete vertical world domain.
        // SkyforgeNoiseBasedChunkGenerator#getBaseHeight resolves that exact island while the scope
        // is active, so no live BASE_WORLD or foreign-island height may replace it.
        if (SkyforgeGenerationDomainStage.activeIslandVolumeId().isPresent()) {
            return domainHeight;
        }

        var skyforgeClaim = SkyforgeNeoForge1211SurfaceStage.queryBaseHeightClaim(
                worldX,
                worldZ,
                heightmapType,
                region.getMinBuildHeight(),
                region.getHeight());
        if (skyforgeClaim.isEmpty()) {
            return liveHeight;
        }

        // allowsPopulation is also the accepted physical-presence predicate: with no admission
        // stage installed historical fixtures realize directly; with the stage installed only an
        // ADMITTED exact volume may have reached the live chunk. PLANNED/REJECTED catalog entries
        // therefore cannot perturb BASE_WORLD projection.
        boolean physicallyPresent = skyforgeClaim.orElseThrow().volumeIds().stream()
                .anyMatch(SkyforgePhysicalVolumeAdmissionStage::allowsPopulation);
        if (!physicallyPresent) {
            return liveHeight;
        }

        return selectBaseWorldHeight(
                liveHeight,
                domainHeight,
                OptionalInt.of(skyforgeClaim.orElseThrow().height()));
    }

    /**
     * Pure selection rule kept separate so the ambiguity policy is regression-testable without a
     * live Minecraft world.
     */
    static int selectBaseWorldHeight(
            int liveHeight,
            int nativeDomainHeight,
            OptionalInt highestPhysicalSkyforgeHeight) {
        Objects.requireNonNull(highestPhysicalSkyforgeHeight, "highestPhysicalSkyforgeHeight");
        if (highestPhysicalSkyforgeHeight.isEmpty()) {
            return liveHeight;
        }

        int skyforgeHeight = highestPhysicalSkyforgeHeight.getAsInt();
        boolean skyforgeIsVerticallyForeign = skyforgeHeight > nativeDomainHeight;
        boolean liveHeightIncludesForeignSurface = liveHeight >= skyforgeHeight;
        return skyforgeIsVerticallyForeign && liveHeightIncludesForeignSurface
                ? nativeDomainHeight
                : liveHeight;
    }
}
