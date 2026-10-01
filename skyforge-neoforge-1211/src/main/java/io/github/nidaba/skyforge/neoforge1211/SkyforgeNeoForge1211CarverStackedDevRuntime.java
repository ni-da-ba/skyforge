package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandTerrainProfile;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Development-only SF-IMP-0061 proof that carver vertical frames and direct-write ownership remain
 * independent for two Skyforge volumes sharing X/Z but occupying distinct Y domains.
 */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID)
final class SkyforgeNeoForge1211CarverStackedDevRuntime {
    static final String ENABLE_PROPERTY = "skyforge.dev.nativeCarverStacked";

    private static final ChunkPos PROOF_CHUNK = new ChunkPos(0, 0);
    private static final int PROOF_X = 8;
    private static final int PROOF_Z = 8;
    private static final int NATIVE_SAMPLE_Y = 30;
    private static final int FRAME_MARGIN = 2;
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211CarverStackedDevRuntime.class.getName());

    private static AutoCloseable persistentBinding;
    private static boolean proofStarted;
    private static boolean proofComplete;

    private SkyforgeNeoForge1211CarverStackedDevRuntime() {}

    static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    static synchronized void installFromSystemProperty() {
        if (!enabled() || persistentBinding != null) {
            return;
        }
        if (SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()) {
            throw new IllegalStateException("cannot install SF-IMP-0061 stacked proof over another Skyforge binding");
        }
        persistentBinding = SkyforgeNeoForge1211SurfaceStage.install(
                new SkyforgeNeoForge1211ChunkAdapter(
                        SkyforgeNeoForge1211BiomePopulationDevRuntime.catalog(),
                        SkyIslandTerrainProfile.reference(),
                        new SkyforgeMinecraftBlockPalette()),
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()));

        LOGGER.log(
                System.Logger.Level.INFO,
                "Skyforge SF-IMP-0061 stacked-carver specimen enabled. Create a NEW disposable Skyforge "
                        + "Development world. The accepted stacked forest/taiga fixture shares X/Z but has two "
                        + "independent owner-solid Y domains; one identical native carver sample is mapped into "
                        + "each frame and the direct carver fence must reject the other island in both directions.");
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (!enabled() || proofStarted || proofComplete) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (!level.dimension().equals(Level.OVERWORLD)) {
                continue;
            }
            if (level.getChunkSource().getChunkNow(PROOF_CHUNK.x, PROOF_CHUNK.z) == null) {
                continue;
            }
            prove(level);
        }
    }

    private static synchronized void prove(ServerLevel level) {
        if (proofStarted || proofComplete) {
            return;
        }
        var volumes = SkyforgeNeoForge1211BiomePopulationDevRuntime.catalog().volumes();
        if (volumes.size() != 2) {
            throw new IllegalStateException("SF-IMP-0061 stacked proof requires exactly two fixture volumes");
        }
        SkyIslandWorldVolume lower = volumes.get(0);
        SkyIslandWorldVolume upper = volumes.get(1);
        OwnerSpan lowerSpan = ownerSpan(lower.id());
        OwnerSpan upperSpan = ownerSpan(upper.id());
        proofStarted = true;

        VolumeProof lowerProof = proveVolume(level, lower.id(), upper.id(), lowerSpan, upperSpan);
        VolumeProof upperProof = proveVolume(level, upper.id(), lower.id(), upperSpan, lowerSpan);

        if (lowerProof.mappedY() == upperProof.mappedY()) {
            throw new IllegalStateException(
                    "SF-IMP-0061 stacked carver frames collapsed two independent Y domains to one Y");
        }

        proofComplete = true;
        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0061 NATIVE CARVER STACKED PASS: nativeSample={x=" + PROOF_X
                        + ", y=" + NATIVE_SAMPLE_Y + ", z=" + PROOF_Z + "}, lower={volume="
                        + lowerProof.volumeId().path()
                        + ", ownerSpanY=[" + lowerSpan.minimumY() + "," + lowerSpan.maximumY() + "]"
                        + ", mappedY=" + lowerProof.mappedY()
                        + ", mappedOwnerSolid=true, mappedForeignSolid=false, ownerWriteAccepted=true, "
                        + "foreignWriteRejected=true}, upper={volume="
                        + upperProof.volumeId().path()
                        + ", ownerSpanY=[" + upperSpan.minimumY() + "," + upperSpan.maximumY() + "]"
                        + ", mappedY=" + upperProof.mappedY()
                        + ", mappedOwnerSolid=true, mappedForeignSolid=false, ownerWriteAccepted=true, "
                        + "foreignWriteRejected=true}. Same X/Z and same native carver Y sample remained "
                        + "independent across vertically stacked exact volumes.");

        SkyforgeAutomatedAcceptanceHarness.completeServerCase(
                level.getServer(),
                java.util.Map.of(
                        "lowerMappedY", lowerProof.mappedY(),
                        "upperMappedY", upperProof.mappedY(),
                        "foreignWriteRejected", true,
                        "ownerWriteAccepted", true));
    }

    private static VolumeProof proveVolume(
            ServerLevel level,
            SkyIslandWorldVolumeId ownerId,
            SkyIslandWorldVolumeId foreignId,
            OwnerSpan ownerSpan,
            OwnerSpan foreignSpan) {
        int targetMinimumY = ownerSpan.minimumY() + FRAME_MARGIN;
        int targetMaximumY = ownerSpan.maximumY() - FRAME_MARGIN;
        if (targetMaximumY < targetMinimumY) {
            throw new IllegalStateException("SF-IMP-0061 stacked owner has no interior frame: " + ownerId.path());
        }

        BlockPos foreignSample = new BlockPos(
                PROOF_X,
                (foreignSpan.minimumY() + foreignSpan.maximumY()) / 2,
                PROOF_Z);

        int mappedY;
        try (var domain = SkyforgeGenerationDomainStage.openIsland(ownerId);
                var execution = SkyforgeCarverExecutionStage.openForTest(
                        ownerId,
                        PROOF_CHUNK,
                        position -> SkyforgeNeoForge1211SurfaceStage.isSolidOwnedBy(
                                        ownerId, position.getX(), position.getY(), position.getZ())
                                .orElseThrow(),
                        position -> SkyforgeNeoForge1211SurfaceStage.isSolidOwnedBy(
                                        foreignId, position.getX(), position.getY(), position.getZ())
                                .orElseThrow());
                var vertical = SkyforgeCarverVerticalFrame.open(
                        level, ownerId, targetMinimumY, targetMaximumY)) {
            domain.requireActive();
            execution.requireActive();
            vertical.requireActive();

            mappedY = SkyforgeCarverVerticalFrame.mapSampledY(NATIVE_SAMPLE_Y);
            BlockPos mapped = new BlockPos(PROOF_X, mappedY, PROOF_Z);
            if (!SkyforgeNeoForge1211SurfaceStage.isSolidOwnedBy(
                            ownerId, mapped.getX(), mapped.getY(), mapped.getZ())
                    .orElse(false)) {
                throw new IllegalStateException(
                        "SF-IMP-0061 stacked frame mapped outside owner-solid terrain: " + mapped);
            }
            if (SkyforgeNeoForge1211SurfaceStage.isSolidOwnedBy(
                            foreignId, mapped.getX(), mapped.getY(), mapped.getZ())
                    .orElse(false)) {
                throw new IllegalStateException(
                        "SF-IMP-0061 stacked frame mapped into foreign owner terrain: " + mapped);
            }
            if (!execution.authorizeForTest(mapped)) {
                throw new IllegalStateException(
                        "SF-IMP-0061 exact carver fence rejected its own mapped owner terrain: " + mapped);
            }
            if (execution.authorizeForTest(foreignSample)) {
                throw new IllegalStateException(
                        "SF-IMP-0061 exact carver fence admitted foreign stacked terrain: " + foreignSample);
            }
        }

        return new VolumeProof(ownerId, mappedY);
    }

    private static OwnerSpan ownerSpan(SkyIslandWorldVolumeId volumeId) {
        var volume = SkyforgeNeoForge1211BiomePopulationDevRuntime.catalog().volumes().stream()
                .filter(candidate -> candidate.id().equals(volumeId))
                .findFirst()
                .orElseThrow();
        int minimumY = (int) Math.ceil(volume.bounds().minimumY());
        int maximumY = (int) Math.floor(volume.bounds().maximumY());
        int first = Integer.MAX_VALUE;
        int last = Integer.MIN_VALUE;
        for (int y = minimumY; y <= maximumY; y++) {
            if (SkyforgeNeoForge1211SurfaceStage.isSolidOwnedBy(volumeId, PROOF_X, y, PROOF_Z)
                    .orElse(false)) {
                first = Math.min(first, y);
                last = y;
            }
        }
        if (first == Integer.MAX_VALUE) {
            throw new IllegalStateException(
                    "SF-IMP-0061 stacked fixture found no owner-solid proof column for " + volumeId.path());
        }
        return new OwnerSpan(first, last);
    }

    private record OwnerSpan(int minimumY, int maximumY) {}

    private record VolumeProof(SkyIslandWorldVolumeId volumeId, int mappedY) {}
}
