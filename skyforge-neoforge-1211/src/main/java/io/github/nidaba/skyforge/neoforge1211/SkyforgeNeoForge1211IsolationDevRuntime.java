package io.github.nidaba.skyforge.neoforge1211;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.ChunkAccess;

/** Development-only SF-IMP-0052 proof that base-world generation completes before Skyforge exists. */
final class SkyforgeNeoForge1211IsolationDevRuntime {
    static final String ENABLE_PROPERTY = "skyforge.dev.domainIsolation";
    static final int INSPECTION_X = 8;
    static final int INSPECTION_Y = 242;
    static final int INSPECTION_Z = 8;

    private static final int PROOF_CHUNK_X = 0;
    private static final int PROOF_CHUNK_Z = 0;
    private static final int ISLAND_PROOF_Y = 223;
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211IsolationDevRuntime.class.getName());
    private static AutoCloseable persistentBinding;

    private SkyforgeNeoForge1211IsolationDevRuntime() {}

    static synchronized void installFromSystemProperty() {
        if (!enabled() || persistentBinding != null) {
            return;
        }
        if (SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()) {
            throw new IllegalStateException("cannot install the SF-IMP-0052 isolation specimen over another binding");
        }
        persistentBinding = SkyforgeNeoForge1211SurfaceStage.installNativeSurfaceAdapted(
                SkyforgeNeoForge1211DevRuntime.adapter(),
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()));
        LOGGER.log(
                System.Logger.Level.INFO,
                "Skyforge SF-IMP-0052 domain-isolation specimen enabled. Create a NEW disposable world using the "
                        + "Skyforge Development world type and inspect near x=" + INSPECTION_X
                        + ", y=" + INSPECTION_Y
                        + ", z=" + INSPECTION_Z
                        + ". Base-world structures and biome decoration must complete before the floating island is "
                        + "written. Development data forces one nearby woodland-mansion candidate; under this model "
                        + "it remains native-ground-owned beneath the island rather than being lifted onto it. A "
                        + "successful origin proof emits 'SF-IMP-0052 BASE WORLD ISOLATED'.");
    }

    static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    /**
     * Captures every completed native position that Skyforge does not own immediately before
     * Skyforge realization.
     *
     * <p>The proof deliberately excludes only positions where the deterministic Skyforge
     * materialization contributes a solid. Those cells represent explicit physical composition:
     * an island is allowed to occupy them even when native generation happened to place something
     * there first. Every other position in the chunk interval is protected byte-for-byte.
     */
    static Proof captureBeforeSkyforge(ChunkAccess chunk) {
        if (!isProofChunk(chunk)) {
            return Proof.inactive();
        }
        MinecraftChunkMaterialization ownershipMask = SkyforgeNeoForge1211SurfaceStage.materializeOccupancy(chunk)
                .orElseThrow(() -> new IllegalStateException(
                        "SF-IMP-0052 fixture invalid: no Skyforge materialization is bound"));
        BlockPos islandSample = new BlockPos(INSPECTION_X, ISLAND_PROOF_Y, INSPECTION_Z);
        if (chunk.getBlockState(islandSample).isAir() == false) {
            throw new IllegalStateException(
                    "SF-IMP-0052 fixture invalid: Skyforge-height sample is already occupied before island realization: "
                            + chunk.getBlockState(islandSample));
        }
        int sampleLocalX = islandSample.getX() - chunk.getPos().getMinBlockX();
        int sampleLocalZ = islandSample.getZ() - chunk.getPos().getMinBlockZ();
        if (SkyforgeMinecraftBlockPalette.AIR.equals(
                ownershipMask.blockKeyAt(sampleLocalX, islandSample.getY(), sampleLocalZ))) {
            throw new IllegalStateException(
                    "SF-IMP-0052 fixture invalid: expected proof sample is not owned by the Skyforge materialization");
        }
        ProtectedSnapshot snapshot = fingerprintProtectedWorld(chunk, ownershipMask);
        return new Proof(true, snapshot.fingerprint(), snapshot.positionCount(), ownershipMask);
    }

    /** Requires Skyforge realization to leave every non-Skyforge-owned native position stable. */
    static void verifyAfterSkyforge(ChunkAccess chunk, Proof proof) {
        if (!proof.active()) {
            return;
        }
        ProtectedSnapshot after = fingerprintProtectedWorld(chunk, proof.ownershipMask());
        if (after.positionCount() != proof.protectedPositionCount()) {
            throw new IllegalStateException("SF-IMP-0052 isolation proof changed its protected-position set");
        }
        if (after.fingerprint() != proof.protectedFingerprint()) {
            throw new IllegalStateException(
                    "SF-IMP-0052 isolation failure: Skyforge realization changed a completed base-world position "
                            + "outside Skyforge-owned solid occupancy");
        }
        BlockPos islandSample = new BlockPos(INSPECTION_X, ISLAND_PROOF_Y, INSPECTION_Z);
        if (chunk.getBlockState(islandSample).isAir()) {
            throw new IllegalStateException(
                    "SF-IMP-0052 fixture invalid: expected elevated Skyforge solid was not realized");
        }
        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0052 BASE WORLD ISOLATED: chunk=" + chunk.getPos()
                        + ", protectedPositions=" + after.positionCount()
                        + ", protectedFingerprint=" + Long.toUnsignedString(after.fingerprint())
                        + ", skyforgeSolidPositions=" + proof.ownershipMask().solidBlockCount()
                        + ", islandSample=" + islandSample);
    }

    private static boolean isProofChunk(ChunkAccess chunk) {
        return enabled()
                && chunk.getPos().x == PROOF_CHUNK_X
                && chunk.getPos().z == PROOF_CHUNK_Z;
    }

    private static ProtectedSnapshot fingerprintProtectedWorld(
            ChunkAccess chunk,
            MinecraftChunkMaterialization ownershipMask) {
        if (!chunk.getPos().equals(ownershipMask.chunkPos())) {
            throw new IllegalArgumentException("ownership mask does not belong to proof chunk");
        }
        int minimumX = chunk.getPos().getMinBlockX();
        int minimumZ = chunk.getPos().getMinBlockZ();
        long maximumYExclusive = (long) ownershipMask.minimumY() + ownershipMask.height();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        long hash = FNV_OFFSET_BASIS;
        int positionCount = 0;
        for (int worldY = ownershipMask.minimumY(); worldY < maximumYExclusive; worldY++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                for (int localX = 0; localX < 16; localX++) {
                    if (!SkyforgeMinecraftBlockPalette.AIR.equals(
                            ownershipMask.blockKeyAt(localX, worldY, localZ))) {
                        continue;
                    }
                    var state = chunk.getBlockState(cursor.set(
                            minimumX + localX,
                            worldY,
                            minimumZ + localZ));
                    hash ^= state.toString().hashCode();
                    hash *= FNV_PRIME;
                    positionCount++;
                }
            }
        }
        return new ProtectedSnapshot(hash, positionCount);
    }

    private record ProtectedSnapshot(long fingerprint, int positionCount) {}

    record Proof(
            boolean active,
            long protectedFingerprint,
            int protectedPositionCount,
            MinecraftChunkMaterialization ownershipMask) {
        static Proof inactive() {
            return new Proof(false, 0L, 0, null);
        }
    }
}
