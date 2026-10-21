package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandTerrainProfile;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Final SF-IMP-0060 proof that exact-column LOCAL_MODIFICATIONS frames remain independent for
 * vertically stacked Skyforge volumes.
 *
 * <p>The actual registered amethyst-geode realization is proven by the physically admitted 0060
 * runtime. This fixture isolates the new coordinate/ownership contract itself: the same native
 * height sample at the same X/Z is mapped independently into each accepted 0054 stacked volume's
 * exact solid owner column, and the generic write preflight must reject the other volume in both
 * directions.
 */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID)
final class SkyforgeNeoForge1211LocalModificationsStackedDevRuntime {
    static final String ENABLE_PROPERTY = "skyforge.dev.localModificationsStacked";

    private static final ChunkPos PROOF_CHUNK = new ChunkPos(0, 0);
    private static final int PROOF_X = 8;
    private static final int PROOF_Z = 8;
    private static final int NATIVE_PROBE_Y = 30;
    private static final ResourceLocation FRAME_PROBE_KEY =
            ResourceLocation.fromNamespaceAndPath("skyforge", "sf_imp_0060_stacked_frame_probe");
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211LocalModificationsStackedDevRuntime.class.getName());

    private static AutoCloseable persistentBinding;
    private static boolean proofStarted;
    private static boolean proofComplete;

    private SkyforgeNeoForge1211LocalModificationsStackedDevRuntime() {}

    static synchronized void installFromSystemProperty() {
        if (!enabled() || persistentBinding != null) {
            return;
        }
        if (SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()) {
            throw new IllegalStateException("cannot install SF-IMP-0060 stacked proof over another Skyforge binding");
        }
        persistentBinding = SkyforgeNeoForge1211SurfaceStage.install(
                new SkyforgeNeoForge1211ChunkAdapter(
                        SkyforgeNeoForge1211BiomePopulationDevRuntime.catalog(),
                        SkyIslandTerrainProfile.reference(),
                        new SkyforgeMinecraftBlockPalette()),
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()));

        LOGGER.log(
                System.Logger.Level.INFO,
                "Skyforge SF-IMP-0060 stacked LOCAL_MODIFICATIONS specimen enabled. Create a NEW disposable "
                        + "Skyforge Development world. The accepted SF-IMP-0054 forest/taiga tablelands share X/Z "
                        + "but occupy independent Y domains; this proof maps one native local-modification height "
                        + "into each exact solid column and requires bidirectional foreign-owner veto.");
    }

    private static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
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
            observeLoaded(level);
        }
    }

    private static synchronized void observeLoaded(ServerLevel level) {
        if (proofStarted || proofComplete) {
            return;
        }
        if (level.getChunkSource().getChunkNow(PROOF_CHUNK.x, PROOF_CHUNK.z) == null) {
            return;
        }

        var volumes = SkyforgeNeoForge1211BiomePopulationDevRuntime.catalog().volumes();
        if (volumes.size() != 2) {
            throw new IllegalStateException("SF-IMP-0060 stacked proof requires exactly two accepted 0054 volumes");
        }
        SkyIslandWorldVolume lowerVolume = volumes.get(0);
        SkyIslandWorldVolume upperVolume = volumes.get(1);
        int lowerSurfaceY = surfaceY(level, lowerVolume.id());
        int upperSurfaceY = surfaceY(level, upperVolume.id());
        if (lowerSurfaceY == upperSurfaceY) {
            throw new IllegalStateException("SF-IMP-0060 stacked fixture did not resolve distinct vertical surfaces");
        }

        BlockPos lowerOwnerSample = requireOwnerSample(level, lowerVolume, lowerSurfaceY);
        BlockPos upperOwnerSample = requireOwnerSample(level, upperVolume, upperSurfaceY);
        proofStarted = true;

        VolumeProof lower = proveIndependentFrame(
                level,
                lowerVolume,
                upperVolume,
                lowerOwnerSample,
                upperOwnerSample);
        VolumeProof upper = proveIndependentFrame(
                level,
                upperVolume,
                lowerVolume,
                upperOwnerSample,
                lowerOwnerSample);

        if (lower.mappedY() == upper.mappedY()) {
            throw new IllegalStateException(
                    "SF-IMP-0060 stacked LOCAL_MODIFICATIONS mapped two distinct vertical owners to the same Y");
        }

        proofComplete = true;
        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0060 LOCAL MODIFICATIONS STACKED PASS: nativeProbe={x=" + PROOF_X
                        + ", y=" + NATIVE_PROBE_Y + ", z=" + PROOF_Z + "}, lower={volume="
                        + lower.volumeId().path()
                        + ", mappedY=" + lower.mappedY()
                        + ", ownerSurfaceY=" + lower.ownerSurfaceY()
                        + ", mappedOwnerSolid=true, mappedForeignSolid=false, ownerPreflight=true, foreignVeto=true}, "
                        + "upper={volume=" + upper.volumeId().path()
                        + ", mappedY=" + upper.mappedY()
                        + ", ownerSurfaceY=" + upper.ownerSurfaceY()
                        + ", mappedOwnerSolid=true, mappedForeignSolid=false, ownerPreflight=true, foreignVeto=true}. "
                        + "The same native LOCAL_MODIFICATIONS height sample at identical X/Z resolved independently "
                        + "into each exact solid owner column; the other vertically stacked island was rejected by "
                        + "the generic exact-volume write preflight in both directions.");
    }

    private static VolumeProof proveIndependentFrame(
            ServerLevel level,
            SkyIslandWorldVolume ownerVolume,
            SkyIslandWorldVolume foreignVolume,
            BlockPos ownerSample,
            BlockPos foreignSample) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(ownerVolume, "ownerVolume");
        Objects.requireNonNull(foreignVolume, "foreignVolume");
        SkyIslandWorldVolumeId ownerId = ownerVolume.id();
        SkyIslandWorldVolumeId foreignId = foreignVolume.id();
        int ownerSurfaceY = surfaceY(level, ownerId);

        var operation = SkyforgePopulationOperation.create(
                ownerId,
                PROOF_CHUNK,
                FRAME_PROBE_KEY,
                GenerationStep.Decoration.LOCAL_MODIFICATIONS.ordinal(),
                0);

        BlockPos mapped;
        try (var domain = SkyforgeGenerationDomainStage.openIsland(ownerId);
                var execution = SkyforgePopulationExecutionStage.open(operation, 0);
                var verticalFrame = SkyforgeVerticalPlacementFrame.open(level, operation)) {
            domain.requireActive();
            execution.requireActive();
            verticalFrame.requireActive();

            BlockPos nativePosition = new BlockPos(PROOF_X, NATIVE_PROBE_Y, PROOF_Z);
            mapped = SkyforgeVerticalPlacementFrame.mapHeightRangePosition(nativePosition);
            if (mapped.getX() != PROOF_X || mapped.getZ() != PROOF_Z) {
                throw new IllegalStateException("SF-IMP-0060 stacked frame changed native X/Z: " + mapped);
            }
            if (!SkyforgeNeoForge1211SurfaceStage.isSolidOwnedBy(
                            ownerId, mapped.getX(), mapped.getY(), mapped.getZ())
                    .orElse(false)) {
                throw new IllegalStateException(
                        "SF-IMP-0060 stacked frame mapped outside owner-solid terrain: owner="
                                + ownerId.path() + ", mapped=" + mapped);
            }
            if (SkyforgeNeoForge1211SurfaceStage.isSolidOwnedBy(
                            foreignId, mapped.getX(), mapped.getY(), mapped.getZ())
                    .orElse(false)) {
                throw new IllegalStateException(
                        "SF-IMP-0060 stacked frame mapped into foreign owner terrain: owner="
                                + ownerId.path() + ", foreign=" + foreignId.path() + ", mapped=" + mapped);
            }

            if (!SkyforgeWorldGenRegionDomainBridge.canWrite(mapped)
                    || !SkyforgeWorldGenRegionDomainBridge.canWrite(ownerSample)) {
                throw new IllegalStateException(
                        "SF-IMP-0060 stacked owner preflight rejected exact owner terrain for " + ownerId.path());
            }
            if (SkyforgeWorldGenRegionDomainBridge.canWrite(foreignSample)
                    || SkyforgeWorldGenRegionDomainBridge.acceptWrite(foreignSample)) {
                throw new IllegalStateException(
                        "SF-IMP-0060 stacked owner preflight admitted foreign volume " + foreignId.path()
                                + " while " + ownerId.path() + " was active");
            }
        }

        return new VolumeProof(ownerId, mapped.getY(), ownerSurfaceY);
    }

    private static int surfaceY(ServerLevel level, SkyIslandWorldVolumeId volumeId) {
        return SkyforgeNeoForge1211SurfaceStage.queryBaseHeightClaim(
                        volumeId,
                        PROOF_X,
                        PROOF_Z,
                        Heightmap.Types.WORLD_SURFACE_WG,
                        level.getMinBuildHeight(),
                        level.getHeight())
                .orElseThrow(() -> new IllegalStateException(
                        "stacked local-modifications proof volume has no origin surface: " + volumeId.path()))
                .height();
    }

    private static BlockPos requireOwnerSample(
            ServerLevel level,
            SkyIslandWorldVolume volume,
            int surfaceY) {
        for (int y = surfaceY - 1; y >= Math.ceil(volume.bounds().minimumY()); y--) {
            BlockPos candidate = new BlockPos(PROOF_X, y, PROOF_Z);
            if (SkyforgeNeoForge1211SurfaceStage.isSolidOwnedBy(
                            volume.id(), candidate.getX(), candidate.getY(), candidate.getZ())
                    .orElse(false)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "stacked local-modifications proof found no owner-solid sample for " + volume.id().path());
    }

    private record VolumeProof(
            SkyIslandWorldVolumeId volumeId,
            int mappedY,
            int ownerSurfaceY) {}
}
