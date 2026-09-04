package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandTerrainProfile;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Development-only SF-IMP-0061 save/reload verifier.
 *
 * <p>This mode deliberately installs no Skyforge terrain/admission/carver binding. It reopens the
 * already-saved disposable world, reconstructs only the immutable compiled fixture geometry, and
 * proves that positions which should be owner-solid still contain the cave air/lava written by the
 * prior native-carver run. The logical client then verifies one of those exact persisted states from
 * its own ClientLevel.
 */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID)
final class SkyforgeNeoForge1211CarverReloadDevRuntime {
    static final String ENABLE_PROPERTY = "skyforge.dev.nativeCarverReload";

    private static final ChunkPos PROOF_CHUNK = new ChunkPos(0, 0);
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211CarverReloadDevRuntime.class.getName());

    private static volatile ClientExpectation clientExpectation;
    private static boolean serverProofStarted;
    private static boolean serverProofComplete;

    private SkyforgeNeoForge1211CarverReloadDevRuntime() {}

    static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    static synchronized void installFromSystemProperty() {
        if (!enabled()) {
            return;
        }
        if (SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()
                || SkyforgePhysicalVolumeAdmissionStage.active()) {
            throw new IllegalStateException(
                    "SF-IMP-0061 reload proof must not install over a live Skyforge realization binding");
        }
        LOGGER.log(
                System.Logger.Level.INFO,
                "Skyforge SF-IMP-0061 reload verifier enabled. Open the SAME disposable world saved by "
                        + "nativeCarverRepeatClient. No island/carver realization binding is installed in this mode; "
                        + "the verifier reads persisted origin-chunk blocks against immutable compiled fixture "
                        + "ownership and then requires the logical client to observe the same persisted state.");
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (!enabled() || serverProofStarted || serverProofComplete) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (!level.dimension().equals(Level.OVERWORLD) || level.players().isEmpty()) {
                continue;
            }
            LevelChunkView view = loadedOrigin(level);
            if (view == null) {
                continue;
            }
            verifyServerPersistence(level, view);
        }
    }

    private static synchronized void verifyServerPersistence(ServerLevel level, LevelChunkView view) {
        if (serverProofStarted || serverProofComplete) {
            return;
        }
        serverProofStarted = true;

        var catalog = SkyforgeNeoForge1211CarverDevRuntime.catalog();
        if (catalog.volumes().size() != 1) {
            throw new IllegalStateException("SF-IMP-0061 reload proof requires exactly one carver fixture volume");
        }
        SkyIslandWorldVolume volume = catalog.volumes().getFirst();
        var adapter = new SkyforgeNeoForge1211ChunkAdapter(
                catalog,
                SkyIslandTerrainProfile.reference(),
                new SkyforgeMinecraftBlockPalette());

        int minimumY = Math.max(
                level.getMinBuildHeight(),
                (int) Math.ceil(volume.bounds().minimumY()));
        int maximumY = Math.min(
                level.getMinBuildHeight() + level.getHeight() - 1,
                (int) Math.floor(volume.bounds().maximumY()));

        int persistedAir = 0;
        int persistedLava = 0;
        int persistedCarved = 0;
        long digest = 0xcbf29ce484222325L;
        ClientExpectation first = null;

        for (int x = PROOF_CHUNK.getMinBlockX(); x <= PROOF_CHUNK.getMaxBlockX(); x++) {
            for (int z = PROOF_CHUNK.getMinBlockZ(); z <= PROOF_CHUNK.getMaxBlockZ(); z++) {
                for (int y = minimumY; y <= maximumY; y++) {
                    if (!adapter.isSolidOwnedBy(volume.id(), x, y, z)) {
                        continue;
                    }
                    BlockPos position = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(position);
                    if (!state.isAir() && !state.is(Blocks.LAVA)) {
                        continue;
                    }
                    persistedCarved++;
                    if (state.isAir()) {
                        persistedAir++;
                    } else {
                        persistedLava++;
                    }
                    long packed = position.asLong();
                    digest ^= packed;
                    digest *= 0x100000001b3L;
                    digest ^= state.isAir() ? 1L : 2L;
                    digest *= 0x100000001b3L;
                    if (first == null) {
                        first = new ClientExpectation(packed, state);
                    }
                }
            }
        }

        if (persistedCarved <= 0 || persistedAir <= 0 || first == null) {
            throw new IllegalStateException(
                    "SF-IMP-0061 reload proof found no persisted cave air inside compiled owner terrain: "
                            + "carved=" + persistedCarved + ", air=" + persistedAir + ", lava=" + persistedLava);
        }

        clientExpectation = first;
        serverProofComplete = true;
        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0061 RELOAD SERVER PASS: volume=" + volume.id().path()
                        + ", persistedCarved=" + persistedCarved
                        + ", persistedAir=" + persistedAir
                        + ", persistedLava=" + persistedLava
                        + ", persistenceDigest=" + Long.toUnsignedString(digest, 16)
                        + ", clientSample=" + BlockPos.of(first.position())
                        + ", clientExpectedState=" + first.expectedState()
                        + ". Persisted cave state survived a full save/stop/reload without reinstalling "
                        + "the Skyforge terrain, admission, or carver mutation pipeline.");
    }

    static ClientExpectation clientExpectation() {
        return clientExpectation;
    }

    private static LevelChunkView loadedOrigin(ServerLevel level) {
        var chunk = level.getChunkSource().getChunkNow(PROOF_CHUNK.x, PROOF_CHUNK.z);
        return chunk == null ? null : new LevelChunkView();
    }

    record ClientExpectation(long position, BlockState expectedState) {
        ClientExpectation {
            Objects.requireNonNull(expectedState, "expectedState");
        }
    }

    /** Presence token: getChunkNow above proves the origin LevelChunk is already loaded. */
    private record LevelChunkView() {}
}
