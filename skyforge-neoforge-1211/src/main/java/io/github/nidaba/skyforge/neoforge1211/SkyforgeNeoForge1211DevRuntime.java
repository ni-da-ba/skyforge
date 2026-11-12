package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.EnrichedProviderMorphologySkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyFamily;
import io.github.nidaba.skyforge.recipes.skyisland.ProviderMorphologyEnrichment;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviders;
import io.github.nidaba.skyforge.world.SkyIslandTerrainProfile;
import io.github.nidaba.skyforge.world.SkyIslandWorldCatalog;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import io.github.nidaba.skyforge.world.WorldBounds;
import java.util.List;

/**
 * Explicitly development-only runtime used for interactive Minecraft world-generation proofs.
 *
 * <p>The production mod does not install a world binding by default. ModDevGradle's SF-IMP-0043
 * client run opts into this class with {@value #ENABLE_PROPERTY}. The development-only world
 * preset selects {@link SkyforgeNoiseBasedChunkGenerator}; this runtime supplies that generator's
 * already-compiled post-surface Skyforge catalog, native Minecraft surface-top adaptation and the
 * accepted early generator height-query bridge. Development data forces a vanilla desert-pyramid
 * structure candidate at the origin so Minecraft's normal structure-start path can be inspected
 * against the floating Massif. No late ChunkEvent.Load binding is installed.
 */
final class SkyforgeNeoForge1211DevRuntime {
    static final String ENABLE_PROPERTY = "skyforge.dev.specimen";
    static final String TECTONIC_REVIEW_ENABLE_PROPERTY = "skyforge.dev.tectonicTerrainReviewIsland";
    static final String TECTONIC_BIOME_SURFACE_REVIEW_PROPERTY = "skyforge.dev.tectonicBiomeSurfaceReview";
    static final long ROOT_SEED = 0x534b59464f524745L;
    static final int INSPECTION_X = 0;
    static final int INSPECTION_Y = 300;
    static final int INSPECTION_Z = 0;

    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211DevRuntime.class.getName());
    private static AutoCloseable persistentBinding;

    private SkyforgeNeoForge1211DevRuntime() {}

    /** Installs the development specimen or the Tectonic review island when explicitly enabled. */
    static synchronized void installFromSystemProperty() {
        boolean structureSpecimen = Boolean.getBoolean(ENABLE_PROPERTY);
        boolean tectonicReviewIsland = Boolean.getBoolean(TECTONIC_REVIEW_ENABLE_PROPERTY);
        boolean tectonicBiomeSurfaceReview = Boolean.getBoolean(TECTONIC_BIOME_SURFACE_REVIEW_PROPERTY);
        if ((!structureSpecimen && !tectonicReviewIsland && !tectonicBiomeSurfaceReview)
                || persistentBinding != null) {
            return;
        }
        if (SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()) {
            throw new IllegalStateException(
                    "cannot install the Skyforge development specimen over an existing post-surface binding");
        }

        persistentBinding = installSpecimen();
        if (structureSpecimen) {
            LOGGER.log(
                    System.Logger.Level.INFO,
                    "Skyforge SF-IMP-0043 development specimen enabled for native structure-start visibility. "
                            + "Create a NEW disposable world using the Skyforge Development world type and inspect near "
                            + "x=" + INSPECTION_X
                            + ", y=" + INSPECTION_Y
                            + ", z=" + INSPECTION_Z
                            + ". Development data forces minecraft:desert_pyramid candidates with a guaranteed origin "
                            + "candidate and temporarily permits desert pyramids in Overworld biomes. The expected proof "
                            + "is a vanilla pyramid whose start follows the elevated Massif rather than the lower native ground.");
        } else {
            LOGGER.log(
                    System.Logger.Level.INFO,
                    "Skyforge Tectonic review island binding enabled. Nearby missing chunks will generate the same "
                            + "persisted island footprint without enabling the SF-IMP-0043 structure-start review hook.");
        }
    }

    /** Installs one disposable post-surface specimen binding and returns its cleanup handle. */
    static AutoCloseable installSpecimen() {
        return SkyforgeNeoForge1211SurfaceStage.installNativeSurfaceAdapted(
                adapter(),
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()));
    }

    static SkyforgeNeoForge1211ChunkAdapter adapter() {
        return new SkyforgeNeoForge1211ChunkAdapter(
                catalog(),
                SkyIslandTerrainProfile.reference(),
                new SkyforgeMinecraftBlockPalette());
    }

    static SkyIslandWorldCatalog catalog() {
        if (Boolean.getBoolean(TECTONIC_BIOME_SURFACE_REVIEW_PROPERTY)) {
            // Keep the accepted central Massif at its exact review coordinates and pair it with the
            // same-level Basin family at the established east review position.
            return new SkyIslandWorldCatalog(
                    ROOT_SEED,
                    List.of(
                            reviewIsland("sf-imp-0043-dev-massif", 0, MorphologyFamily.MASSIF, 0, 224, 0),
                            reviewIsland("sf-imp-0054-review-basin", 3, MorphologyFamily.BASIN, 384, 224, 0)));
        }
        if (!Boolean.getBoolean(TECTONIC_REVIEW_ENABLE_PROPERTY)) {
            return new SkyIslandWorldCatalog(
                    ROOT_SEED,
                    List.of(reviewIsland(
                            "sf-imp-0043-dev-massif", 0, MorphologyFamily.MASSIF, 0, 224, 0)));
        }

        // Keep the original middle Massif bit-for-bit positioned as the vanilla/Tectonic A/B baseline.
        // Four accepted morphology families flank it at the same center elevation for direct visual review.
        return new SkyIslandWorldCatalog(
                ROOT_SEED,
                List.of(
                        reviewIsland("sf-imp-0054-review-tableland", 1, MorphologyFamily.TABLELAND, -768, 224, 0),
                        reviewIsland("sf-imp-0054-review-spine", 2, MorphologyFamily.SPINE, -384, 224, 0),
                        reviewIsland("sf-imp-0043-dev-massif", 0, MorphologyFamily.MASSIF, 0, 224, 0),
                        reviewIsland("sf-imp-0054-review-basin", 3, MorphologyFamily.BASIN, 384, 224, 0),
                        reviewIsland("sf-imp-0054-review-lobed", 4, MorphologyFamily.LOBED, 768, 224, 0)));
    }

    private static SkyIslandWorldVolume reviewIsland(
            String volumeName,
            int memberOrdinal,
            MorphologyFamily family,
            int centerX,
            int centerY,
            int centerZ) {
        var compiled = compiledMorphology(family, centerX, centerY, centerZ);
        var id = new SkyIslandWorldVolumeId(
                ROOT_SEED, volumeName, 0, memberOrdinal, ROOT_SEED);
        return new SkyIslandWorldVolume(
                id,
                new WorldBounds(
                        centerX - 160.0,
                        centerX + 160.0,
                        centerY - 128.0,
                        centerY + 80.0,
                        centerZ - 160.0,
                        centerZ + 160.0),
                compiled);
    }

    private static io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume compiledMorphology(
            MorphologyFamily family, double centerX, double centerY, double centerZ) {
        SkyIslandVolumeDescriptor descriptor = new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                ROOT_SEED,
                centerX,
                centerZ,
                centerY,
                96.0,
                42.0,
                72.0,
                28.0,
                Math.PI / 6.0,
                0.65,
                0.60,
                0.25,
                0.0,
                20.0);
        var provider = SkyIslandMorphologyProviders.builtInId(family);
        return new EnrichedProviderMorphologySkyIslandVolumeRecipe().compile(
                descriptor,
                new ProviderMorphologyEnrichment(provider, 0.0, 0.0),
                SkyIslandMorphologyProviders.builtInRegistry());
    }
}
