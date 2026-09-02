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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;

/** Development-only acceptance fixture for SF-IMP-0056 whole-volume physical admission. */
final class SkyforgeNeoForge1211PhysicalAdmissionDevRuntime {
    static final String ENABLE_PROPERTY = "skyforge.dev.physicalAdmission";
    private static final long ROOT_SEED = 0x5346494d50303056L;
    private static final int PROOF_RADIUS_CHUNKS = 2;
    private static final int EXPECTED_PROOF_CHUNKS = 25;
    private static final int MAXIMUM_ATTACHMENT_DEPTH = 24;
    private static final int PROOF_X = 8;
    private static final int PROOF_Z = 8;
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211PhysicalAdmissionDevRuntime.class.getName());

    private static AutoCloseable persistentTerrainBinding;
    private static AutoCloseable persistentAdmissionBinding;
    private static AutoCloseable persistentPopulationBinding;
    private static boolean rejectedConflictPreserved;
    private static boolean proofComplete;

    private SkyforgeNeoForge1211PhysicalAdmissionDevRuntime() {}

    static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    static synchronized void installFromSystemProperty() {
        if (!enabled()
                || persistentTerrainBinding != null
                || persistentAdmissionBinding != null
                || persistentPopulationBinding != null) {
            return;
        }
        if (SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()) {
            throw new IllegalStateException("cannot install SF-IMP-0056 proof over another terrain binding");
        }
        if (SkyforgePhysicalVolumeAdmissionStage.active()) {
            throw new IllegalStateException("cannot install SF-IMP-0056 proof over another physical-admission binding");
        }
        if (SkyforgeNativeSurfacePopulationStage.hasActiveBinding()) {
            throw new IllegalStateException("cannot install SF-IMP-0056 proof over another population binding");
        }

        SkyIslandWorldCatalog catalog = catalog();
        var volumes = catalog.volumes();
        SkyIslandWorldVolumeId lowerId = volumes.get(0).id();
        SkyIslandWorldVolumeId upperId = volumes.get(1).id();
        SkyforgeExactVolumeBiomeResolver biomeResolver = (volumeId, x, y, z) -> {
            if (volumeId.equals(lowerId)) {
                return Biomes.FOREST;
            }
            if (volumeId.equals(upperId)) {
                return Biomes.TAIGA;
            }
            throw new IllegalArgumentException("unknown SF-IMP-0056 proof volume: " + volumeId.path());
        };

        persistentTerrainBinding = SkyforgeNeoForge1211SurfaceStage.installNativeSurfaceAdapted(
                new SkyforgeNeoForge1211ChunkAdapter(
                        catalog,
                        SkyIslandTerrainProfile.reference(),
                        new SkyforgeMinecraftBlockPalette()),
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()));
        persistentAdmissionBinding = SkyforgePhysicalVolumeAdmissionStage.install(catalog);
        persistentPopulationBinding = SkyforgeNativeSurfacePopulationStage.install((chunkPos, minimumY, height) -> {
            if (!isProofChunk(chunkPos)) {
                return List.of();
            }
            return List.of(
                    SkyforgeNativeSurfacePopulationPlan.surfaceEcology(
                            lowerId,
                            biomeResolver,
                            MAXIMUM_ATTACHMENT_DEPTH),
                    SkyforgeNativeSurfacePopulationPlan.surfaceEcology(
                            upperId,
                            biomeResolver,
                            MAXIMUM_ATTACHMENT_DEPTH));
        });

        LOGGER.log(
                System.Logger.Level.INFO,
                "Skyforge SF-IMP-0056 physical-admission specimen enabled. Create a NEW disposable Skyforge "
                        + "Development world and inspect the origin 5x5 chunk patch. The lower tableland occupies the "
                        + "known forced-mansion altitude and must be rejected without damaging native content. The "
                        + "upper tableland must remain absent until all 25 footprint chunks report clear BASE_WORLD "
                        + "evidence, then catch up terrain and taiga population without forcing unavailable chunks. "
                        + "Success emits 'SF-IMP-0056 PHYSICAL ADMISSION PASS'.");
    }

    static synchronized void observe(
            WorldGenLevel level,
            ChunkAccess chunk,
            ChunkGenerator generator) {
        if (!enabled() || proofComplete || !isProofChunk(chunk.getPos())) {
            return;
        }
        var volumes = catalog().volumes();
        SkyIslandWorldVolumeId lowerId = volumes.get(0).id();
        var lower = SkyforgePhysicalVolumeAdmissionStage.snapshot(lowerId);

        if (lower.state() == SkyforgePhysicalVolumeAdmissionState.REJECTED
                && !rejectedConflictPreserved
                && lower.firstConflict().isPresent()) {
            var conflict = lower.firstConflict().orElseThrow();
            long conflictChunkKey = new ChunkPos(
                            Math.floorDiv(conflict.position().getX(), 16),
                            Math.floorDiv(conflict.position().getZ(), 16))
                    .toLong();
            if (conflictChunkKey == chunk.getPos().toLong()) {
                var retained = chunk.getBlockState(conflict.position());
                if (!retained.equals(conflict.nativeState())) {
                    throw new IllegalStateException("SF-IMP-0056 rejected volume damaged its native conflict: position="
                            + conflict.position() + ", expected=" + conflict.nativeState() + ", actual=" + retained);
                }
                rejectedConflictPreserved = true;
            }
        }

        // Completion is checked on the stable loaded-chunk side. A generation-region callback may
        // still observe admission evidence, but it must never be responsible for deferred writes.
        if (level instanceof WorldGenRegion region
                && region.getLevel() instanceof ServerLevel serverLevel) {
            observeLoaded(serverLevel);
        }
    }

    static synchronized void observeLoaded(ServerLevel level) {
        if (!enabled() || proofComplete) {
            return;
        }
        var volumes = catalog().volumes();
        SkyIslandWorldVolumeId lowerId = volumes.get(0).id();
        SkyIslandWorldVolumeId upperId = volumes.get(1).id();
        var lower = SkyforgePhysicalVolumeAdmissionStage.snapshot(lowerId);
        var upper = SkyforgePhysicalVolumeAdmissionStage.snapshot(upperId);

        if (lower.state() == SkyforgePhysicalVolumeAdmissionState.REJECTED
                && !rejectedConflictPreserved
                && lower.firstConflict().isPresent()) {
            var conflict = lower.firstConflict().orElseThrow();
            int conflictChunkX = Math.floorDiv(conflict.position().getX(), 16);
            int conflictChunkZ = Math.floorDiv(conflict.position().getZ(), 16);
            var conflictChunk = level.getChunkSource().getChunkNow(conflictChunkX, conflictChunkZ);
            if (conflictChunk != null) {
                var retained = conflictChunk.getBlockState(conflict.position());
                if (!retained.equals(conflict.nativeState())) {
                    throw new IllegalStateException("SF-IMP-0056 rejected volume damaged its native conflict: position="
                            + conflict.position() + ", expected=" + conflict.nativeState() + ", actual=" + retained);
                }
                rejectedConflictPreserved = true;
            }
        }

        if (lower.state() != SkyforgePhysicalVolumeAdmissionState.REJECTED
                || upper.state() != SkyforgePhysicalVolumeAdmissionState.ADMITTED
                || !rejectedConflictPreserved
                || !SkyforgePhysicalVolumeAdmissionStage.pendingCatchupChunks(upperId).isEmpty()) {
            return;
        }
        if (upper.observedChunks() != EXPECTED_PROOF_CHUNKS
                || upper.requiredChunks() != EXPECTED_PROOF_CHUNKS) {
            throw new IllegalStateException("SF-IMP-0056 upper volume admitted with unexpected evidence counts: observed="
                    + upper.observedChunks() + ", required=" + upper.requiredChunks());
        }

        var originChunk = level.getChunkSource().getChunkNow(0, 0);
        if (originChunk == null) {
            return;
        }
        int upperSurfaceY = SkyforgeNeoForge1211SurfaceStage.queryBaseHeightClaim(
                        upperId,
                        PROOF_X,
                        PROOF_Z,
                        Heightmap.Types.WORLD_SURFACE_WG,
                        level.getMinBuildHeight(),
                        level.getHeight())
                .orElseThrow(() -> new IllegalStateException("SF-IMP-0056 upper proof volume has no origin surface"))
                .height();
        BlockPos upperSurface = new BlockPos(PROOF_X, upperSurfaceY - 1, PROOF_Z);
        if (originChunk.getBlockState(upperSurface).isAir()) {
            throw new IllegalStateException("SF-IMP-0056 admitted upper volume did not catch up its origin terrain");
        }

        int completedPhases = SkyforgeNativeSurfacePopulationStage.completedPhaseCount();
        if (completedPhases < EXPECTED_PROOF_CHUNKS) {
            return;
        }
        if (!SkyforgePhysicalVolumeAdmissionStage.pendingCatchupChunks(lowerId).isEmpty()) {
            throw new IllegalStateException("SF-IMP-0056 rejected lower volume retained deferred realization work");
        }

        var conflict = lower.firstConflict().orElseThrow();
        proofComplete = true;
        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0056 PHYSICAL ADMISSION PASS: lower={volume=" + lowerId.path()
                        + ", state=" + lower.state()
                        + ", conflict=" + conflict.position()
                        + ", block=" + conflict.nativeState()
                        + ", blockEntity=" + conflict.blockEntityPresent()
                        + ", preserved=true}, upper={volume=" + upperId.path()
                        + ", state=" + upper.state()
                        + ", observedChunks=" + upper.observedChunks()
                        + ", requiredChunks=" + upper.requiredChunks()
                        + ", pendingCatchup=0"
                        + ", originSurfaceY=" + upperSurfaceY
                        + ", completedPopulationPhases=" + completedPhases
                        + "}. Rejected native collision remained untouched; clear multi-chunk volume admitted atomically "
                        + "and completed deferred exact terrain/population through stable loaded chunks without forced "
                        + "future chunk generation.");
    }

    static SkyIslandWorldCatalog catalog() {
        long lowerSeed = ROOT_SEED ^ 0x4c4f574552L;
        long upperSeed = ROOT_SEED ^ 0x5550504552L;
        var lowerId = new SkyIslandWorldVolumeId(ROOT_SEED, "sf-imp-0056-physical", 0, 0, lowerSeed);
        var upperId = new SkyIslandWorldVolumeId(ROOT_SEED, "sf-imp-0056-physical", 0, 1, upperSeed);
        var lower = new SkyIslandWorldVolume(
                lowerId,
                new WorldBounds(-32.0, 47.0, 96.0, 168.0, -32.0, 47.0),
                compileTableland(lowerSeed, 8.0, 8.0, 136.0));
        var upper = new SkyIslandWorldVolume(
                upperId,
                new WorldBounds(-32.0, 47.0, 196.0, 268.0, -32.0, 47.0),
                compileTableland(upperSeed, 8.0, 8.0, 236.0));
        return new SkyIslandWorldCatalog(ROOT_SEED, List.of(lower, upper));
    }

    private static CompiledSkyIslandVolume compileTableland(
            long seed,
            double centerX,
            double centerZ,
            double elevation) {
        var descriptor = new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                seed,
                centerX,
                centerZ,
                elevation,
                32.0,
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

    private static boolean isProofChunk(ChunkPos chunkPos) {
        return chunkPos.x >= -PROOF_RADIUS_CHUNKS
                && chunkPos.x <= PROOF_RADIUS_CHUNKS
                && chunkPos.z >= -PROOF_RADIUS_CHUNKS
                && chunkPos.z <= PROOF_RADIUS_CHUNKS;
    }
}
