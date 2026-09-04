package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandTerrainProfile;
import io.github.nidaba.skyforge.world.SkyIslandWorldCatalog;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;

/** Development-only first runtime proof for SF-IMP-0061 exact-volume native AIR carvers. */
final class SkyforgeNeoForge1211CarverDevRuntime {
    static final String ENABLE_PROPERTY = "skyforge.dev.nativeCarver";

    private static final ChunkPos PROOF_CHUNK = new ChunkPos(0, 0);
    private static final int PROOF_X = 8;
    private static final int PROOF_Z = 8;
    private static final int INTERIOR_MARGIN = 8;
    private static final int BASE_COLUMN_MINIMUM_Y = -64;
    private static final int BASE_COLUMN_MAXIMUM_Y = 150;
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211CarverDevRuntime.class.getName());

    private static AutoCloseable persistentTerrainBinding;
    private static AutoCloseable persistentAdmissionBinding;
    private static boolean proofStarted;
    private static boolean proofComplete;

    private SkyforgeNeoForge1211CarverDevRuntime() {}

    static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    static synchronized void installFromSystemProperty() {
        if (!enabled() || persistentTerrainBinding != null || persistentAdmissionBinding != null) {
            return;
        }
        if (SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()) {
            throw new IllegalStateException("cannot install SF-IMP-0061 proof over another terrain binding");
        }
        if (SkyforgePhysicalVolumeAdmissionStage.active()) {
            throw new IllegalStateException("cannot install SF-IMP-0061 proof over another admission binding");
        }

        SkyIslandWorldCatalog catalog = catalog();
        persistentTerrainBinding = SkyforgeNeoForge1211SurfaceStage.install(
                new SkyforgeNeoForge1211ChunkAdapter(
                        catalog,
                        SkyIslandTerrainProfile.reference(),
                        new SkyforgeMinecraftBlockPalette()),
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()));
        persistentAdmissionBinding = SkyforgePhysicalVolumeAdmissionStage.install(catalog);

        LOGGER.log(
                System.Logger.Level.INFO,
                "Skyforge SF-IMP-0061 native-carver specimen enabled. Create a NEW disposable Skyforge "
                        + "Development world. The high tableland is physically admitted first, then final-registry "
                        + "taiga AIR carvers replay against the already-loaded origin chunk through a conservative "
                        + "owner-solid interior Y frame. If the proof waits for client tracking, teleport to x=8, "
                        + "y=280, z=8. Aquifer semantics are intentionally disabled-air for this first cave-topology "
                        + "milestone.");
    }

    static synchronized void observeLoaded(ServerLevel level) {
        if (!enabled() || proofStarted || proofComplete || !level.dimension().equals(Level.OVERWORLD)) {
            return;
        }
        if (level.players().isEmpty()) {
            return;
        }

        SkyIslandWorldVolume volume = catalog().volumes().getFirst();
        SkyIslandWorldVolumeId volumeId = volume.id();
        var admission = SkyforgePhysicalVolumeAdmissionStage.snapshot(volumeId);
        if (admission.state() != SkyforgePhysicalVolumeAdmissionState.ADMITTED
                || !SkyforgePhysicalVolumeAdmissionStage.pendingCatchupChunks(volumeId).isEmpty()) {
            return;
        }

        LevelChunk chunk = level.getChunkSource().getChunkNow(PROOF_CHUNK.x, PROOF_CHUNK.z);
        if (chunk == null) {
            return;
        }

        OwnerSpan ownerSpan = ownerSpan(volumeId);
        int targetMinimumY = ownerSpan.minimumY() + INTERIOR_MARGIN;
        int targetMaximumY = ownerSpan.maximumY() - INTERIOR_MARGIN;
        if (targetMaximumY <= targetMinimumY) {
            throw new IllegalStateException(
                    "SF-IMP-0061 proof island has no interior after safety margin: ownerSpan=" + ownerSpan);
        }

        var generator = level.getChunkSource().getGenerator();
        if (!(generator instanceof NoiseBasedChunkGenerator noiseGenerator)) {
            throw new IllegalStateException(
                    "SF-IMP-0061 requires the active Minecraft noise generator, found " + generator.getClass());
        }

        List<OwnedState> ownerBefore = captureOwnerStates(level, volumeId, chunk);
        List<BlockState> baseColumnBefore = captureBaseColumn(level);
        proofStarted = true;

        var resolver = (SkyforgeExactVolumeBiomeResolver) (candidateId, x, y, z) -> {
            if (!candidateId.equals(volumeId)) {
                throw new IllegalArgumentException(
                        "SF-IMP-0061 proof resolved unexpected volume " + candidateId.path());
            }
            return Biomes.TAIGA;
        };

        var result = SkyforgeNativeCarverRunner.carveAir(
                level,
                noiseGenerator,
                resolver,
                volumeId,
                chunk,
                new BlockPos(PROOF_X, (targetMinimumY + targetMaximumY) / 2, PROOF_Z),
                targetMinimumY,
                targetMaximumY);

        if (result.configuredCarvers() <= 0
                || result.startChecks() <= 0
                || result.startChunks() <= 0
                || result.carveCalls() <= 0) {
            throw new IllegalStateException(
                    "SF-IMP-0061 final-registry biome did not exercise native AIR carvers: " + result);
        }
        if (result.sampledHeights() <= 0 || result.mappedOutsideTarget() != 0) {
            throw new IllegalStateException(
                    "SF-IMP-0061 native carver height sampling did not remain inside its local frame: " + result);
        }
        if (result.acceptedWrites() <= 0 || result.changedBlocks() <= 0) {
            throw new IllegalStateException(
                    "SF-IMP-0061 native carvers produced no persistent exact-owner mutation: " + result);
        }

        CarvedEvidence carved = compareOwnerStates(level, ownerBefore);
        if (carved.changedBlocks() <= 0 || carved.airBlocks() <= 0) {
            throw new IllegalStateException(
                    "SF-IMP-0061 carver execution reported writes without persistent interior cave air: " + carved);
        }
        requireBaseColumnPreserved(level, baseColumnBefore);

        proofComplete = true;
        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0061 NATIVE CARVER PASS: volume=" + volumeId.path()
                        + ", admission={observedChunks=" + admission.observedChunks()
                        + ", requiredChunks=" + admission.requiredChunks()
                        + ", pendingCatchup=0}"
                        + ", ownerSpanY=[" + ownerSpan.minimumY() + "," + ownerSpan.maximumY() + "]"
                        + ", targetFrameY=[" + targetMinimumY + "," + targetMaximumY + "]"
                        + ", biome=" + result.biomeKey().location()
                        + ", configuredCarvers=" + result.configuredCarvers()
                        + ", startChecks=" + result.startChecks()
                        + ", startChunks=" + result.startChunks()
                        + ", carveCalls=" + result.carveCalls()
                        + ", successfulCalls=" + result.successfulCalls()
                        + ", startedCarverKeys=" + result.startedCarverKeys()
                        + ", sampledHeights=" + result.sampledHeights()
                        + ", nativeSampleYRange=[" + result.minimumNativeSampleY()
                        + "," + result.maximumNativeSampleY() + "]"
                        + ", mappedSampleYRange=[" + result.minimumMappedSampleY()
                        + "," + result.maximumMappedSampleY() + "]"
                        + ", mappedOutsideTarget=" + result.mappedOutsideTarget()
                        + ", standaloneAnchors=" + result.standaloneAnchors()
                        + ", writeAttempts=" + result.writeAttempts()
                        + ", acceptedWrites=" + result.acceptedWrites()
                        + ", rejectedWrites=" + result.rejectedWrites()
                        + ", changedBlocks=" + result.changedBlocks()
                        + ", persistentCarving={changed=" + carved.changedBlocks()
                        + ", air=" + carved.airBlocks()
                        + ", lava=" + carved.lavaBlocks()
                        + ", sample=" + carved.samplePosition() + "}"
                        + ", transformDigest=" + Long.toUnsignedString(result.transformDigest(), 16)
                        + ", carveDigest=" + Long.toUnsignedString(result.changedPositionDigest(), 16)
                        + ", baseColumnPreserved=true, clientTrackingActive=true. Native HeightProvider sampling "
                        + "completed before vertical mapping; direct LevelChunk writes were fenced to exact compiled "
                        + "owner terrain and published through the stable chunk's normal block-change channel.");
    }

    static SkyIslandWorldCatalog catalog() {
        return SkyforgeNeoForge1211LocalModificationsDevRuntime.catalog();
    }

    private static OwnerSpan ownerSpan(SkyIslandWorldVolumeId volumeId) {
        var bounds = SkyforgeNeoForge1211SurfaceStage.volumeBounds(volumeId)
                .orElseThrow(() -> new IllegalStateException("SF-IMP-0061 runtime binding lost volume bounds"));
        int minimum = (int) Math.ceil(bounds.minimumY());
        int maximum = (int) Math.floor(bounds.maximumY());
        int first = Integer.MAX_VALUE;
        int last = Integer.MIN_VALUE;
        for (int y = minimum; y <= maximum; y++) {
            if (SkyforgeNeoForge1211SurfaceStage.isSolidOwnedBy(volumeId, PROOF_X, y, PROOF_Z)
                    .orElseThrow()) {
                first = Math.min(first, y);
                last = y;
            }
        }
        if (first == Integer.MAX_VALUE) {
            throw new IllegalStateException("SF-IMP-0061 proof column contains no owner-solid terrain");
        }
        return new OwnerSpan(first, last);
    }

    private static List<OwnedState> captureOwnerStates(
            ServerLevel level,
            SkyIslandWorldVolumeId volumeId,
            LevelChunk chunk) {
        var bounds = SkyforgeNeoForge1211SurfaceStage.volumeBounds(volumeId).orElseThrow();
        int minimumY = Math.max(level.getMinBuildHeight(), (int) Math.ceil(bounds.minimumY()));
        int maximumY = Math.min(
                level.getMinBuildHeight() + level.getHeight() - 1,
                (int) Math.floor(bounds.maximumY()));
        List<OwnedState> result = new ArrayList<>();
        for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++) {
            for (int z = chunk.getPos().getMinBlockZ(); z <= chunk.getPos().getMaxBlockZ(); z++) {
                for (int y = minimumY; y <= maximumY; y++) {
                    if (!SkyforgeNeoForge1211SurfaceStage.isSolidOwnedBy(volumeId, x, y, z).orElse(false)) {
                        continue;
                    }
                    BlockPos position = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(position);
                    if (state.isAir()) {
                        throw new IllegalStateException(
                                "SF-IMP-0061 owner terrain was already air before carving at " + position);
                    }
                    result.add(new OwnedState(position.asLong(), state));
                }
            }
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("SF-IMP-0061 captured no owner terrain in proof chunk");
        }
        return List.copyOf(result);
    }

    private static CarvedEvidence compareOwnerStates(
            ServerLevel level,
            List<OwnedState> before) {
        int changed = 0;
        int air = 0;
        int lava = 0;
        BlockPos sample = null;
        for (OwnedState owned : before) {
            BlockPos position = BlockPos.of(owned.position());
            BlockState actual = level.getBlockState(position);
            if (actual.equals(owned.state())) {
                continue;
            }
            changed++;
            if (actual.isAir()) {
                air++;
            } else if (actual.is(Blocks.LAVA)) {
                lava++;
            } else {
                throw new IllegalStateException(
                        "SF-IMP-0061 native carver produced an unexpected owner mutation at "
                                + position + ": before=" + owned.state() + ", after=" + actual);
            }
            if (sample == null) {
                sample = position.immutable();
            }
        }
        return new CarvedEvidence(changed, air, lava, sample);
    }

    private static List<BlockState> captureBaseColumn(ServerLevel level) {
        int minimumY = Math.max(level.getMinBuildHeight(), BASE_COLUMN_MINIMUM_Y);
        int maximumY = Math.min(
                level.getMinBuildHeight() + level.getHeight() - 1,
                BASE_COLUMN_MAXIMUM_Y);
        List<BlockState> states = new ArrayList<>(maximumY - minimumY + 1);
        for (int y = minimumY; y <= maximumY; y++) {
            states.add(level.getBlockState(new BlockPos(PROOF_X, y, PROOF_Z)));
        }
        return List.copyOf(states);
    }

    private static void requireBaseColumnPreserved(
            ServerLevel level,
            List<BlockState> before) {
        int minimumY = Math.max(level.getMinBuildHeight(), BASE_COLUMN_MINIMUM_Y);
        for (int index = 0; index < before.size(); index++) {
            int y = minimumY + index;
            BlockState actual = level.getBlockState(new BlockPos(PROOF_X, y, PROOF_Z));
            if (!actual.equals(before.get(index))) {
                throw new IllegalStateException(
                        "SF-IMP-0061 native carver mutated vertically unrelated BASE_WORLD at y=" + y
                                + ": before=" + before.get(index) + ", after=" + actual);
            }
        }
    }

    private record OwnerSpan(int minimumY, int maximumY) {}

    private record OwnedState(long position, BlockState state) {}

    private record CarvedEvidence(
            int changedBlocks,
            int airBlocks,
            int lavaBlocks,
            BlockPos samplePosition) {}
}
