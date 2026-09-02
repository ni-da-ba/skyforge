package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** Development-only proof that stacked exact island volumes can run independent native features. */
final class SkyforgeNeoForge1211PopulationDevRuntime {
    static final String ENABLE_PROPERTY = "skyforge.dev.population";
    private static final long ROOT_SEED = 0x5346494d50303053L;
    private static final ChunkPos PROOF_CHUNK = new ChunkPos(0, 0);
    private static final int PROOF_X = 8;
    private static final int PROOF_Z = 8;
    private static final int GENERATION_STEP = 0;
    private static final int MAXIMUM_ATTACHMENT_DEPTH = 16;
    private static final ResourceLocation FEATURE_LOCATION = ResourceLocation.withDefaultNamespace("oak_checked");
    private static final ResourceKey<PlacedFeature> FEATURE_KEY =
            ResourceKey.create(Registries.PLACED_FEATURE, FEATURE_LOCATION);
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211PopulationDevRuntime.class.getName());

    private static AutoCloseable persistentBinding;
    private static boolean proofComplete;

    private SkyforgeNeoForge1211PopulationDevRuntime() {}

    static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    static synchronized void installFromSystemProperty() {
        if (!enabled() || persistentBinding != null) {
            return;
        }
        if (SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()) {
            throw new IllegalStateException("cannot install SF-IMP-0053 population proof over another Skyforge binding");
        }
        persistentBinding = SkyforgeNeoForge1211SurfaceStage.install(
                adapter(),
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()));
        LOGGER.log(
                System.Logger.Level.INFO,
                "Skyforge SF-IMP-0053 stacked native-population specimen enabled. Create a NEW disposable "
                        + "Skyforge Development world and inspect the two vertically aligned tableland islands near "
                        + "x=" + PROOF_X + ", z=" + PROOF_Z + ".");
    }

    static synchronized void populate(
            WorldGenLevel level,
            ChunkAccess chunk,
            ChunkGenerator generator) {
        // This is the single accepted post-realization population callback. Deferred physical
        // catch-up is serviced first so an earlier PLANNED chunk receives exact terrain and exact
        // population in the same lifecycle order once its whole volume becomes ADMITTED. The
        // catch-up service never forces unavailable chunks into existence.
        SkyforgeNeoForge1211SurfaceStage.serviceAvailableCatchup(level, generator);

        // A reusable native surface-population binding is serviced next and is inert unless
        // explicitly installed. Development fixtures then observe or exercise that same lifecycle
        // seam without adding milestone-specific hooks to SkyforgeNoiseBasedChunkGenerator.
        SkyforgeNativeSurfacePopulationStage.populate(level, chunk, generator);
        SkyforgeNeoForge1211SurfacePopulationDevRuntime.observe(level, chunk, generator);
        SkyforgeNeoForge1211BiomePopulationDevRuntime.populate(level, chunk, generator);

        if (!enabled() || proofComplete || !chunk.getPos().equals(PROOF_CHUNK)) {
            return;
        }
        if (!SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()) {
            throw new IllegalStateException("SF-IMP-0053 population proof ran without its Skyforge binding");
        }

        Holder<PlacedFeature> feature = level.registryAccess()
                .registryOrThrow(Registries.PLACED_FEATURE)
                .getHolder(FEATURE_KEY)
                .orElseThrow(() -> new IllegalStateException("missing native placed feature " + FEATURE_LOCATION));

        var volumes = catalog().volumes();
        if (volumes.size() != 2) {
            throw new IllegalStateException("SF-IMP-0053 fixture requires exactly two stacked volumes");
        }

        Proof first = populateVolume(level, generator, feature, volumes.get(0).id(), 0);
        Proof second = populateVolume(level, generator, feature, volumes.get(1).id(), 0);
        if (first.seed() == second.seed()) {
            throw new IllegalStateException("stacked island population operations derived the same seed");
        }
        if (first.surfaceY() == second.surfaceY()) {
            throw new IllegalStateException("stacked island population proof did not resolve distinct vertical surfaces");
        }
        proofComplete = true;
        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0053 NATIVE POPULATION STACKED PASS: feature=" + FEATURE_LOCATION
                        + ", lower={volume=" + first.volumeId().path()
                        + ", y=" + first.surfaceY()
                        + ", seed=" + Long.toUnsignedString(first.seed())
                        + ", attachments=" + first.attachmentWrites()
                        + "}, upper={volume=" + second.volumeId().path()
                        + ", y=" + second.surfaceY()
                        + ", seed=" + Long.toUnsignedString(second.seed())
                        + ", attachments=" + second.attachmentWrites()
                        + "}. Both native placements succeeded in the same X/Z chunk under separate exact-volume scopes.");
    }

    private static Proof populateVolume(
            WorldGenLevel level,
            ChunkGenerator generator,
            Holder<PlacedFeature> feature,
            SkyIslandWorldVolumeId volumeId,
            int occurrenceIndex) {
        int surfaceY = SkyforgeNeoForge1211SurfaceStage.queryBaseHeightClaim(
                        volumeId,
                        PROOF_X,
                        PROOF_Z,
                        Heightmap.Types.WORLD_SURFACE_WG,
                        level.getMinBuildHeight(),
                        level.getHeight())
                .orElseThrow(() -> new IllegalStateException("stacked population volume has no proof-column surface: "
                        + volumeId.path()))
                .height();
        var operation = SkyforgePopulationOperation.create(
                volumeId,
                PROOF_CHUNK,
                FEATURE_LOCATION,
                GENERATION_STEP,
                occurrenceIndex);
        var result = SkyforgeNativePlacedFeatureRunner.place(
                level,
                generator,
                feature,
                operation,
                new BlockPos(PROOF_X, surfaceY, PROOF_Z),
                MAXIMUM_ATTACHMENT_DEPTH);
        if (!result.placed()) {
            throw new IllegalStateException("native placed feature failed on exact Skyforge volume " + volumeId.path());
        }
        return new Proof(volumeId, surfaceY, operation.seed(), result.attachmentWrites());
    }

    static SkyIslandWorldCatalog catalog() {
        long lowerSeed = ROOT_SEED ^ 0x4c4f574552L;
        long upperSeed = ROOT_SEED ^ 0x5550504552L;
        var lowerId = new SkyIslandWorldVolumeId(ROOT_SEED, "sf-imp-0053-stacked", 0, 0, lowerSeed);
        var upperId = new SkyIslandWorldVolumeId(ROOT_SEED, "sf-imp-0053-stacked", 0, 1, upperSeed);
        var lower = new SkyIslandWorldVolume(
                lowerId,
                new WorldBounds(-72.0, 72.0, 96.0, 168.0, -72.0, 72.0),
                compileTableland(lowerSeed, 136.0));
        var upper = new SkyIslandWorldVolume(
                upperId,
                new WorldBounds(-72.0, 72.0, 196.0, 268.0, -72.0, 72.0),
                compileTableland(upperSeed, 236.0));
        return new SkyIslandWorldCatalog(ROOT_SEED, List.of(lower, upper));
    }

    private static SkyforgeNeoForge1211ChunkAdapter adapter() {
        return new SkyforgeNeoForge1211ChunkAdapter(
                catalog(),
                SkyIslandTerrainProfile.reference(),
                new SkyforgeMinecraftBlockPalette());
    }

    private static CompiledSkyIslandVolume compileTableland(long seed, double elevation) {
        var descriptor = new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                seed,
                0.0,
                0.0,
                elevation,
                56.0,
                12.0,
                28.0,
                10.0,
                0.0,
                0.15,
                0.70,
                0.0,
                0.0,
                18.0);
        var provider = SkyIslandMorphologyProviders.builtInId(MorphologyFamily.TABLELAND);
        return new EnrichedProviderMorphologySkyIslandVolumeRecipe().compile(
                descriptor,
                new ProviderMorphologyEnrichment(provider, 0.0, 0.0),
                SkyIslandMorphologyProviders.builtInRegistry());
    }

    private record Proof(
            SkyIslandWorldVolumeId volumeId,
            int surfaceY,
            long seed,
            int attachmentWrites) {}
}
