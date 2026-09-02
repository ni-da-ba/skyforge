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
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;

/** Development-only proof that stacked exact volumes can consume different native biome settings. */
final class SkyforgeNeoForge1211BiomePopulationDevRuntime {
    static final String ENABLE_PROPERTY = "skyforge.dev.biomePopulation";
    private static final long ROOT_SEED = 0x5346494d50303054L;
    private static final ChunkPos PROOF_CHUNK = new ChunkPos(0, 0);
    private static final int PROOF_X = 8;
    private static final int PROOF_Z = 8;
    private static final int MAXIMUM_ATTACHMENT_DEPTH = 24;
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211BiomePopulationDevRuntime.class.getName());

    private static AutoCloseable persistentBinding;
    private static boolean proofComplete;

    private SkyforgeNeoForge1211BiomePopulationDevRuntime() {}

    static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    static synchronized void installFromSystemProperty() {
        if (!enabled() || persistentBinding != null) {
            return;
        }
        if (SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()) {
            throw new IllegalStateException("cannot install SF-IMP-0054 biome proof over another Skyforge binding");
        }
        persistentBinding = SkyforgeNeoForge1211SurfaceStage.install(
                adapter(),
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()));
        LOGGER.log(
                System.Logger.Level.INFO,
                "Skyforge SF-IMP-0054 exact-volume biome specimen enabled. Create a NEW disposable "
                        + "Skyforge Development world and inspect the two vertically aligned tableland islands near "
                        + "x=" + PROOF_X + ", z=" + PROOF_Z
                        + ". Lower resolves minecraft:forest; upper resolves minecraft:taiga. Native vegetation "
                        + "placement modifiers choose occurrence positions inside each exact volume.");
    }

    static synchronized void populate(
            WorldGenLevel level,
            ChunkAccess chunk,
            ChunkGenerator generator) {
        if (!enabled() || proofComplete || !chunk.getPos().equals(PROOF_CHUNK)) {
            return;
        }
        if (!SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()) {
            throw new IllegalStateException("SF-IMP-0054 biome proof ran without its Skyforge binding");
        }

        var volumes = catalog().volumes();
        if (volumes.size() != 2) {
            throw new IllegalStateException("SF-IMP-0054 fixture requires exactly two stacked volumes");
        }
        SkyIslandWorldVolumeId lowerId = volumes.get(0).id();
        SkyIslandWorldVolumeId upperId = volumes.get(1).id();
        int lowerSurfaceY = surfaceY(level, lowerId);
        int upperSurfaceY = surfaceY(level, upperId);
        if (lowerSurfaceY == upperSurfaceY) {
            throw new IllegalStateException("SF-IMP-0054 stacked biome proof resolved one shared surface");
        }

        SkyforgeExactVolumeBiomeResolver resolver = (volumeId, x, y, z) -> {
            if (volumeId.equals(lowerId)) {
                return Biomes.FOREST;
            }
            if (volumeId.equals(upperId)) {
                return Biomes.TAIGA;
            }
            throw new IllegalArgumentException("unknown SF-IMP-0054 proof volume: " + volumeId.path());
        };

        var lower = SkyforgeNativeBiomePopulationRunner.populateStep(
                level,
                generator,
                resolver,
                lowerId,
                PROOF_CHUNK,
                lowerSurfaceY,
                GenerationStep.Decoration.VEGETAL_DECORATION,
                MAXIMUM_ATTACHMENT_DEPTH);
        var upper = SkyforgeNativeBiomePopulationRunner.populateStep(
                level,
                generator,
                resolver,
                upperId,
                PROOF_CHUNK,
                upperSurfaceY,
                GenerationStep.Decoration.VEGETAL_DECORATION,
                MAXIMUM_ATTACHMENT_DEPTH);

        requireResult("lower", lower, Biomes.FOREST);
        requireResult("upper", upper, Biomes.TAIGA);
        if (lower.biomeKey().equals(upper.biomeKey())) {
            throw new IllegalStateException("stacked exact volumes resolved the same biome identity");
        }
        if (lower.featureKeys().equals(upper.featureKeys())) {
            throw new IllegalStateException("forest and taiga proof domains exposed identical vegetation feature lists");
        }

        proofComplete = true;
        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0054 BIOME POPULATION STACKED PASS: lower={volume=" + lowerId.path()
                        + ", biome=" + lower.biomeKey().location()
                        + ", y=" + lowerSurfaceY
                        + ", attempted=" + lower.attemptedFeatures()
                        + ", successful=" + lower.successfulFeatures()
                        + ", attachments=" + lower.attachmentWrites()
                        + "}, upper={volume=" + upperId.path()
                        + ", biome=" + upper.biomeKey().location()
                        + ", y=" + upperSurfaceY
                        + ", attempted=" + upper.attemptedFeatures()
                        + ", successful=" + upper.successfulFeatures()
                        + ", attachments=" + upper.attachmentWrites()
                        + "}. Both domains consumed their final registered biome vegetation settings in the same X/Z chunk.");
    }

    private static void requireResult(
            String label,
            SkyforgeNativeBiomePopulationRunner.Result result,
            ResourceKey<Biome> expectedBiome) {
        if (!result.biomeKey().equals(expectedBiome)) {
            throw new IllegalStateException(label + " proof volume resolved unexpected biome: "
                    + result.biomeKey().location());
        }
        if (result.attemptedFeatures() == 0) {
            throw new IllegalStateException(label + " proof biome exposed no vegetal decoration features");
        }
        if (result.successfulFeatures() == 0) {
            throw new IllegalStateException(label + " proof biome produced no successful native vegetation placements");
        }
    }

    private static int surfaceY(
            WorldGenLevel level,
            SkyIslandWorldVolumeId volumeId) {
        return SkyforgeNeoForge1211SurfaceStage.queryBaseHeightClaim(
                        volumeId,
                        PROOF_X,
                        PROOF_Z,
                        Heightmap.Types.WORLD_SURFACE_WG,
                        level.getMinBuildHeight(),
                        level.getHeight())
                .orElseThrow(() -> new IllegalStateException("stacked biome volume has no proof-column surface: "
                        + volumeId.path()))
                .height();
    }

    static SkyIslandWorldCatalog catalog() {
        long lowerSeed = ROOT_SEED ^ 0x464f52455354L;
        long upperSeed = ROOT_SEED ^ 0x5441494741L;
        var lowerId = new SkyIslandWorldVolumeId(ROOT_SEED, "sf-imp-0054-biomes", 0, 0, lowerSeed);
        var upperId = new SkyIslandWorldVolumeId(ROOT_SEED, "sf-imp-0054-biomes", 0, 1, upperSeed);
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
}
