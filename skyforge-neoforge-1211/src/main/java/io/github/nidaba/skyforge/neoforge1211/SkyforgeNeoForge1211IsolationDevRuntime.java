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
    private static final int LOWER_FINGERPRINT_MAX_Y = 160;
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

    /** Captures the completed native lower world immediately before Skyforge realization. */
    static Proof captureBeforeSkyforge(ChunkAccess chunk) {
        if (!isProofChunk(chunk)) {
            return Proof.inactive();
        }
        BlockPos islandSample = new BlockPos(INSPECTION_X, ISLAND_PROOF_Y, INSPECTION_Z);
        if (!chunk.getBlockState(islandSample).isAir()) {
            throw new IllegalStateException(
                    "SF-IMP-0052 fixture invalid: Skyforge-height sample is already occupied before island realization: "
                            + chunk.getBlockState(islandSample));
        }
        return new Proof(true, fingerprintLowerWorld(chunk));
    }

    /** Requires Skyforge realization to leave the already-completed lower native world byte-for-byte stable. */
    static void verifyAfterSkyforge(ChunkAccess chunk, Proof proof) {
        if (!proof.active()) {
            return;
        }
        long after = fingerprintLowerWorld(chunk);
        if (after != proof.lowerFingerprint()) {
            throw new IllegalStateException(
                    "SF-IMP-0052 isolation failure: Skyforge realization changed completed base-world blocks below y="
                            + LOWER_FINGERPRINT_MAX_Y);
        }
        BlockPos islandSample = new BlockPos(INSPECTION_X, ISLAND_PROOF_Y, INSPECTION_Z);
        if (chunk.getBlockState(islandSample).isAir()) {
            throw new IllegalStateException(
                    "SF-IMP-0052 fixture invalid: expected elevated Skyforge solid was not realized");
        }
        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0052 BASE WORLD ISOLATED: chunk=" + chunk.getPos()
                        + ", lowerFingerprint=" + Long.toUnsignedString(after)
                        + ", islandSample=" + islandSample);
    }

    private static boolean isProofChunk(ChunkAccess chunk) {
        return enabled()
                && chunk.getPos().x == PROOF_CHUNK_X
                && chunk.getPos().z == PROOF_CHUNK_Z;
    }

    private static long fingerprintLowerWorld(ChunkAccess chunk) {
        int maximumY = Math.min(
                LOWER_FINGERPRINT_MAX_Y,
                chunk.getMinBuildHeight() + chunk.getHeight() - 1);
        int minimumX = chunk.getPos().getMinBlockX();
        int minimumZ = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        long hash = FNV_OFFSET_BASIS;
        for (int worldY = chunk.getMinBuildHeight(); worldY <= maximumY; worldY++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                for (int localX = 0; localX < 16; localX++) {
                    var state = chunk.getBlockState(cursor.set(
                            minimumX + localX,
                            worldY,
                            minimumZ + localZ));
                    hash ^= state.toString().hashCode();
                    hash *= FNV_PRIME;
                }
            }
        }
        return hash;
    }

    record Proof(boolean active, long lowerFingerprint) {
        static Proof inactive() {
            return new Proof(false, 0L);
        }
    }
}
