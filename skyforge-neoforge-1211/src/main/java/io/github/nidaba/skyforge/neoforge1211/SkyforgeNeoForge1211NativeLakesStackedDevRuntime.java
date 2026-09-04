package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandTerrainProfile;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** SF-IMP-0064 stacked-volume proof for whole-footprint LAKES admission. */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID)
final class SkyforgeNeoForge1211NativeLakesStackedDevRuntime {
    static final String ENABLE_PROPERTY = "skyforge.dev.nativeLakesStacked";

    private static final int ORIGIN_X = 0;
    private static final int ORIGIN_Z = 0;
    private static final ChunkPos PROOF_CHUNK = new ChunkPos(0, 0);
    private static final ResourceLocation PROBE_KEY =
            ResourceLocation.fromNamespaceAndPath("skyforge", "sf_imp_0064_stacked_lake_probe");
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211NativeLakesStackedDevRuntime.class.getName());

    private static AutoCloseable persistentBinding;
    private static boolean proofStarted;
    private static boolean proofComplete;

    private SkyforgeNeoForge1211NativeLakesStackedDevRuntime() {}

    static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    static synchronized void installFromSystemProperty() {
        if (!enabled() || persistentBinding != null) {
            return;
        }
        if (SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()) {
            throw new IllegalStateException("cannot install SF-IMP-0064 stacked proof over another Skyforge binding");
        }
        persistentBinding = SkyforgeNeoForge1211SurfaceStage.install(
                new SkyforgeNeoForge1211ChunkAdapter(
                        SkyforgeNeoForge1211BiomePopulationDevRuntime.catalog(),
                        SkyIslandTerrainProfile.reference(),
                        new SkyforgeMinecraftBlockPalette()),
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()));
        LOGGER.log(
                System.Logger.Level.INFO,
                "Skyforge SF-IMP-0064 stacked native-LAKES whole-footprint specimen enabled.");
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (!enabled() || proofStarted || proofComplete) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (level.dimension().equals(Level.OVERWORLD)
                    && level.getChunkSource().getChunkNow(PROOF_CHUNK.x, PROOF_CHUNK.z) != null) {
                prove(level);
            }
        }
    }

    private static synchronized void prove(ServerLevel level) {
        if (proofStarted || proofComplete) {
            return;
        }
        var volumes = SkyforgeNeoForge1211BiomePopulationDevRuntime.catalog().volumes();
        if (volumes.size() != 2) {
            throw new IllegalStateException("SF-IMP-0064 stacked proof requires exactly two fixture volumes");
        }
        proofStarted = true;

        SkyIslandWorldVolume lower = volumes.get(0);
        SkyIslandWorldVolume upper = volumes.get(1);
        BlockPos lowerOrigin = findAdmittedOrigin(lower);
        BlockPos upperOrigin = findAdmittedOrigin(upper);

        requireOwnerAdmission(lower, lowerOrigin);
        requireOwnerAdmission(upper, upperOrigin);
        requireForeignRejection(lower, upperOrigin);
        requireForeignRejection(upper, lowerOrigin);
        proveProvenanceIsolation(level, lower.id(), upperOrigin, lowerOrigin, 3);
        proveProvenanceIsolation(level, upper.id(), lowerOrigin, upperOrigin, 4);

        if (lowerOrigin.getY() == upperOrigin.getY()) {
            throw new IllegalStateException("SF-IMP-0064 stacked lake origins collapsed distinct Y domains");
        }

        proofComplete = true;
        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0064 NATIVE LAKES STACKED PASS: lowerOrigin=" + lowerOrigin
                        + ", upperOrigin=" + upperOrigin
                        + ", ownerWholeFootprintAccepted=true"
                        + ", foreignWholeFootprintRejected=true"
                        + ", provenanceVolumeIsolation=true"
                        + ", sameXZIndependent=true.");

        SkyforgeAutomatedAcceptanceHarness.completeServerCase(
                level.getServer(),
                java.util.Map.of(
                        "lowerOriginY", lowerOrigin.getY(),
                        "upperOriginY", upperOrigin.getY(),
                        "ownerWholeFootprintAccepted", true,
                        "foreignWholeFootprintRejected", true,
                        "provenanceVolumeIsolation", true,
                        "sameXZIndependent", true));
    }

    private static BlockPos findAdmittedOrigin(SkyIslandWorldVolume volume) {
        int minimumY = (int) Math.ceil(volume.bounds().minimumY());
        int maximumY = (int) Math.floor(volume.bounds().maximumY());
        var operation = operation(volume.id(), 0);
        for (int y = minimumY; y <= maximumY; y++) {
            BlockPos origin = new BlockPos(ORIGIN_X, y, ORIGIN_Z);
            var snapshot = SkyforgeNativeLakeAdmissionStage.probe(operation, origin);
            if (snapshot.admitted() == 1 && snapshot.rejected() == 0) {
                return origin;
            }
        }
        throw new IllegalStateException(
                "SF-IMP-0064 stacked proof found no 16x8x16 owner-local lake footprint for "
                        + volume.id().path());
    }

    private static void requireOwnerAdmission(
            SkyIslandWorldVolume volume,
            BlockPos origin) {
        var snapshot = SkyforgeNativeLakeAdmissionStage.probe(operation(volume.id(), 1), origin);
        if (snapshot.admitted() != 1 || snapshot.rejected() != 0) {
            throw new IllegalStateException(
                    "SF-IMP-0064 stacked owner rejected its own whole lake footprint: "
                            + volume.id().path() + " origin=" + origin);
        }
    }

    private static void requireForeignRejection(
            SkyIslandWorldVolume owner,
            BlockPos foreignOrigin) {
        var snapshot = SkyforgeNativeLakeAdmissionStage.probe(operation(owner.id(), 2), foreignOrigin);
        if (snapshot.admitted() != 0 || snapshot.rejected() != 1) {
            throw new IllegalStateException(
                    "SF-IMP-0064 stacked owner admitted foreign-volume lake footprint: owner="
                            + owner.id().path() + ", foreignOrigin=" + foreignOrigin);
        }
    }

    private static void proveProvenanceIsolation(
            ServerLevel level,
            SkyIslandWorldVolumeId ownerId,
            BlockPos foreignOrigin,
            BlockPos ownerOrigin,
            int ordinal) {
        var operation = operation(ownerId, ordinal);
        try (var capture = SkyforgeGeneratedFluidPropagationStage.openPopulation(level, operation)) {
            capture.requireActive();
            SkyforgeGeneratedFluidPropagationStage.observeScheduledTick(ownerOrigin, Fluids.LAVA);
        }

        boolean tracked = SkyforgeGeneratedFluidPropagationStage.trackedFluids(level, ownerId).stream()
                .anyMatch(fluid -> fluid.position() == ownerOrigin.asLong());
        if (!tracked) {
            throw new IllegalStateException(
                    "SF-IMP-0064 stacked proof failed to persist owner-local LAKES provenance");
        }

        SkyforgeGeneratedFluidPropagationStage.beginFluidTick(
                level,
                ownerOrigin,
                Fluids.LAVA.defaultFluidState());
        try {
            if (!SkyforgeGeneratedFluidPropagationStage.propagationActive()
                    || !SkyforgeGeneratedFluidPropagationStage.isVisible(ownerOrigin)
                    || !SkyforgeGeneratedFluidPropagationStage.acceptWrite(ownerOrigin)) {
                throw new IllegalStateException(
                        "SF-IMP-0064 stacked LAKES provenance rejected owner terrain");
            }
            if (SkyforgeGeneratedFluidPropagationStage.isVisible(foreignOrigin)
                    || SkyforgeGeneratedFluidPropagationStage.acceptWrite(foreignOrigin)) {
                throw new IllegalStateException(
                        "SF-IMP-0064 stacked LAKES provenance admitted a foreign volume");
            }
        } finally {
            SkyforgeGeneratedFluidPropagationStage.endFluidTick();
        }
    }

    private static SkyforgePopulationOperation operation(
            SkyIslandWorldVolumeId volumeId,
            int ordinal) {
        return SkyforgePopulationOperation.create(
                volumeId,
                PROOF_CHUNK,
                PROBE_KEY,
                GenerationStep.Decoration.LAKES.ordinal(),
                ordinal);
    }
}
