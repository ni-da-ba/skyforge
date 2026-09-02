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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
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
    private static final int PROOF_RADIUS_CHUNKS = 2;
    private static final int CANDIDATE_PROOF_CHUNKS =
            (PROOF_RADIUS_CHUNKS * 2 + 1) * (PROOF_RADIUS_CHUNKS * 2 + 1);
    private static final int MINIMUM_ELIGIBLE_PROOF_CHUNKS = 9;
    private static final int PROOF_X = 8;
    private static final int PROOF_Z = 8;
    private static final int MAXIMUM_ATTACHMENT_DEPTH = 24;
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211BiomePopulationDevRuntime.class.getName());

    private static final Set<Long> scannedProofChunks = new HashSet<>();
    private static final Set<Long> eligibleProofChunks = new HashSet<>();
    private static final Aggregate lowerAggregate = new Aggregate();
    private static final Aggregate upperAggregate = new Aggregate();
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
                        + ". Lower resolves minecraft:forest; upper resolves minecraft:taiga. A deterministic "
                        + (PROOF_RADIUS_CHUNKS * 2 + 1) + "x" + (PROOF_RADIUS_CHUNKS * 2 + 1)
                        + " candidate patch is scanned, but only chunks containing a shared X/Z terrain sample in "
                        + "both exact volumes are eligible for the stacked population proof.");
    }

    static synchronized void populate(
            WorldGenLevel level,
            ChunkAccess chunk,
            ChunkGenerator generator) {
        if (!enabled() || proofComplete) {
            return;
        }
        ChunkPos chunkPos = chunk.getPos();
        if (!isProofChunk(chunkPos) || !scannedProofChunks.add(chunkPos.toLong())) {
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

        Optional<SharedSurfaceSample> sample = sharedSurfaceSample(level, lowerId, upperId, chunkPos);
        if (sample.isEmpty()) {
            evaluateCompletion(lowerId, upperId);
            return;
        }
        SharedSurfaceSample shared = sample.orElseThrow();
        eligibleProofChunks.add(chunkPos.toLong());

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
                chunkPos,
                shared.lowerSurfaceY(),
                GenerationStep.Decoration.VEGETAL_DECORATION,
                MAXIMUM_ATTACHMENT_DEPTH);
        var upper = SkyforgeNativeBiomePopulationRunner.populateStep(
                level,
                generator,
                resolver,
                upperId,
                chunkPos,
                shared.upperSurfaceY(),
                GenerationStep.Decoration.VEGETAL_DECORATION,
                MAXIMUM_ATTACHMENT_DEPTH);

        requireChunkResult("lower", lower, Biomes.FOREST);
        requireChunkResult("upper", upper, Biomes.TAIGA);
        if (lower.biomeKey().equals(upper.biomeKey())) {
            throw new IllegalStateException("stacked exact volumes resolved the same biome identity");
        }
        if (lower.featureKeys().equals(upper.featureKeys())) {
            throw new IllegalStateException("forest and taiga proof domains exposed identical vegetation feature lists");
        }

        lowerAggregate.add(lower);
        upperAggregate.add(upper);
        evaluateCompletion(lowerId, upperId);
    }

    private static void evaluateCompletion(
            SkyIslandWorldVolumeId lowerId,
            SkyIslandWorldVolumeId upperId) {
        boolean enoughEligibleChunks = eligibleProofChunks.size() >= MINIMUM_ELIGIBLE_PROOF_CHUNKS;
        boolean bothBiomesPlaced = lowerAggregate.successfulFeatures > 0 && upperAggregate.successfulFeatures > 0;
        if (enoughEligibleChunks && bothBiomesPlaced) {
            completeProof(lowerId, upperId);
            return;
        }
        if (scannedProofChunks.size() < CANDIDATE_PROOF_CHUNKS) {
            return;
        }
        if (!enoughEligibleChunks) {
            throw new IllegalStateException("SF-IMP-0054 candidate region exposed too little shared stacked terrain: "
                    + "eligible=" + eligibleProofChunks.size()
                    + ", required=" + MINIMUM_ELIGIBLE_PROOF_CHUNKS
                    + ", scanned=" + scannedProofChunks.size());
        }
        requireAggregate("lower", lowerAggregate, Biomes.FOREST);
        requireAggregate("upper", upperAggregate, Biomes.TAIGA);
        completeProof(lowerId, upperId);
    }

    private static void completeProof(
            SkyIslandWorldVolumeId lowerId,
            SkyIslandWorldVolumeId upperId) {
        if (proofComplete) {
            return;
        }
        proofComplete = true;
        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0054 BIOME POPULATION STACKED PASS: scannedChunks=" + scannedProofChunks.size()
                        + ", eligibleChunks=" + eligibleProofChunks.size()
                        + ", lower={volume=" + lowerId.path()
                        + ", biome=" + lowerAggregate.biomeKey().location()
                        + ", attempted=" + lowerAggregate.attemptedFeatures
                        + ", successful=" + lowerAggregate.successfulFeatures
                        + ", attachments=" + lowerAggregate.attachmentWrites
                        + "}, upper={volume=" + upperId.path()
                        + ", biome=" + upperAggregate.biomeKey().location()
                        + ", attempted=" + upperAggregate.attemptedFeatures
                        + ", successful=" + upperAggregate.successfulFeatures
                        + ", attachments=" + upperAggregate.attachmentWrites
                        + "}. Both domains consumed their final registered biome vegetation settings across a "
                        + "deterministic region of shared stacked terrain.");
    }

    private static boolean isProofChunk(ChunkPos chunkPos) {
        return Math.abs(chunkPos.x) <= PROOF_RADIUS_CHUNKS && Math.abs(chunkPos.z) <= PROOF_RADIUS_CHUNKS;
    }

    private static Optional<SharedSurfaceSample> sharedSurfaceSample(
            WorldGenLevel level,
            SkyIslandWorldVolumeId lowerId,
            SkyIslandWorldVolumeId upperId,
            ChunkPos chunkPos) {
        int middleX = chunkPos.getMiddleBlockX();
        int middleZ = chunkPos.getMiddleBlockZ();
        SharedSurfaceSample best = null;
        int bestDistance = Integer.MAX_VALUE;

        for (int x = chunkPos.getMinBlockX(); x <= chunkPos.getMaxBlockX(); x++) {
            for (int z = chunkPos.getMinBlockZ(); z <= chunkPos.getMaxBlockZ(); z++) {
                var lowerClaim = SkyforgeNeoForge1211SurfaceStage.queryBaseHeightClaim(
                        lowerId,
                        x,
                        z,
                        Heightmap.Types.WORLD_SURFACE_WG,
                        level.getMinBuildHeight(),
                        level.getHeight());
                if (lowerClaim.isEmpty()) {
                    continue;
                }
                var upperClaim = SkyforgeNeoForge1211SurfaceStage.queryBaseHeightClaim(
                        upperId,
                        x,
                        z,
                        Heightmap.Types.WORLD_SURFACE_WG,
                        level.getMinBuildHeight(),
                        level.getHeight());
                if (upperClaim.isEmpty()) {
                    continue;
                }

                int lowerSurfaceY = lowerClaim.orElseThrow().height();
                int upperSurfaceY = upperClaim.orElseThrow().height();
                if (lowerSurfaceY == upperSurfaceY) {
                    throw new IllegalStateException("SF-IMP-0054 stacked biome proof resolved one shared surface at x="
                            + x + ", z=" + z + ", chunk=" + chunkPos);
                }
                int distance = Math.abs(x - middleX) + Math.abs(z - middleZ);
                if (distance < bestDistance) {
                    best = new SharedSurfaceSample(x, z, lowerSurfaceY, upperSurfaceY);
                    bestDistance = distance;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static void requireChunkResult(
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
    }

    private static void requireAggregate(
            String label,
            Aggregate aggregate,
            ResourceKey<Biome> expectedBiome) {
        if (!aggregate.biomeKey().equals(expectedBiome)) {
            throw new IllegalStateException(label + " proof aggregate resolved unexpected biome: "
                    + aggregate.biomeKey().location());
        }
        if (aggregate.successfulFeatures == 0) {
            throw new IllegalStateException(label + " proof biome produced no successful native vegetation placements "
                    + "across " + aggregate.chunks + " eligible deterministic chunks; attempted="
                    + aggregate.attemptedFeatures + ", features=" + aggregate.featureKeys);
        }
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

    private record SharedSurfaceSample(
            int x,
            int z,
            int lowerSurfaceY,
            int upperSurfaceY) {}

    private static final class Aggregate {
        private ResourceKey<Biome> biomeKey;
        private int chunks;
        private int attemptedFeatures;
        private int successfulFeatures;
        private int attachmentWrites;
        private final Set<ResourceLocation> featureKeys = new LinkedHashSet<>();

        void add(SkyforgeNativeBiomePopulationRunner.Result result) {
            if (biomeKey == null) {
                biomeKey = result.biomeKey();
            } else if (!biomeKey.equals(result.biomeKey())) {
                throw new IllegalStateException("biome identity changed inside one SF-IMP-0054 proof aggregate");
            }
            chunks = Math.addExact(chunks, 1);
            attemptedFeatures = Math.addExact(attemptedFeatures, result.attemptedFeatures());
            successfulFeatures = Math.addExact(successfulFeatures, result.successfulFeatures());
            attachmentWrites = Math.addExact(attachmentWrites, result.attachmentWrites());
            featureKeys.addAll(result.featureKeys());
        }

        ResourceKey<Biome> biomeKey() {
            if (biomeKey == null) {
                throw new IllegalStateException("empty SF-IMP-0054 proof aggregate");
            }
            return biomeKey;
        }
    }
}
