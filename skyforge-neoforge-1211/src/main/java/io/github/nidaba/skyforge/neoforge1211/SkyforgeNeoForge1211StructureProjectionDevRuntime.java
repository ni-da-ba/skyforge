package io.github.nidaba.skyforge.neoforge1211;

import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Development-only lifecycle probe for SF-IMP-0051.
 *
 * <p>The dedicated run combines the accepted SF-IMP-0056 physical-admission fixture with an
 * isolated fixed-biome world preset that forces one terrain-matching jigsaw structure through the
 * real GravityProcessor path. The bridge reports whether any such query occurred after a vertically
 * foreign Skyforge surface had become physically present. That determines whether the original
 * SF-IMP-0050 projection race still survives the accepted SF-IMP-0052/0056 ordering.
 */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID)
final class SkyforgeNeoForge1211StructureProjectionDevRuntime {
    static final String ENABLE_PROPERTY = "skyforge.dev.structureProjection";

    private static final int PROOF_X = 8;
    private static final int PROOF_Z = 8;
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211StructureProjectionDevRuntime.class.getName());
    private static final AtomicLong GRAVITY_QUERIES = new AtomicLong();
    private static final AtomicLong SKYFORGE_CLAIM_QUERIES = new AtomicLong();
    private static final AtomicLong PHYSICAL_SKYFORGE_QUERIES = new AtomicLong();
    private static final AtomicLong CONSTRAINED_QUERIES = new AtomicLong();

    private static boolean proofComplete;

    private SkyforgeNeoForge1211StructureProjectionDevRuntime() {}

    static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    static void installFromSystemProperty() {
        if (!enabled()) {
            return;
        }
        if (!Boolean.getBoolean(SkyforgeNeoForge1211PhysicalAdmissionDevRuntime.ENABLE_PROPERTY)) {
            throw new IllegalStateException(
                    "SF-IMP-0051 projection probe requires the accepted SF-IMP-0056 physical-admission fixture");
        }
        LOGGER.log(
                System.Logger.Level.INFO,
                "Skyforge SF-IMP-0051 structure-projection lifecycle probe enabled. Create a NEW disposable world "
                        + "using the 'Skyforge Structure Projection Probe (SF-IMP-0051)' world type. The fixed "
                        + "minecraft:the_void biome makes a development-only origin jigsaw street candidate eligible "
                        + "without altering any other Skyforge development preset. The run records every real "
                        + "GravityProcessor height query and reports whether any query occurred after an unrelated "
                        + "upper Skyforge surface was physically present.");
    }

    static void recordGravityQuery(
            int liveHeight,
            int domainHeight,
            OptionalInt skyforgeHeight,
            boolean physicalSkyforgePresent,
            int selectedHeight) {
        if (!enabled()) {
            return;
        }
        GRAVITY_QUERIES.incrementAndGet();
        if (skyforgeHeight.isPresent()) {
            SKYFORGE_CLAIM_QUERIES.incrementAndGet();
        }
        if (physicalSkyforgePresent) {
            PHYSICAL_SKYFORGE_QUERIES.incrementAndGet();
        }
        if (selectedHeight != liveHeight) {
            CONSTRAINED_QUERIES.incrementAndGet();
        }

        // Fail loudly if the resolver ever raises a BASE_WORLD projection above the live answer.
        if (selectedHeight > liveHeight) {
            throw new IllegalStateException("SF-IMP-0051 projection resolver raised live terrain height: live="
                    + liveHeight + ", domain=" + domainHeight + ", selected=" + selectedHeight
                    + ", skyforge=" + (skyforgeHeight.isPresent() ? skyforgeHeight.getAsInt() : "none"));
        }
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (!enabled() || proofComplete || !SkyforgePhysicalVolumeAdmissionStage.active()) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (!level.dimension().equals(Level.OVERWORLD)) {
                continue;
            }
            observe(level);
        }
    }

    private static synchronized void observe(ServerLevel level) {
        if (proofComplete) {
            return;
        }
        if (!level.getBiome(new BlockPos(PROOF_X, 64, PROOF_Z)).is(Biomes.THE_VOID)) {
            throw new IllegalStateException(
                    "SF-IMP-0051 fixture invalid: select 'Skyforge Structure Projection Probe (SF-IMP-0051)' "
                            + "instead of the ordinary Skyforge Development world type");
        }

        var volumes = SkyforgeNeoForge1211PhysicalAdmissionDevRuntime.catalog().volumes();
        var lowerId = volumes.get(0).id();
        var upperId = volumes.get(1).id();
        var lower = SkyforgePhysicalVolumeAdmissionStage.snapshot(lowerId);
        var upper = SkyforgePhysicalVolumeAdmissionStage.snapshot(upperId);
        if (lower.state() != SkyforgePhysicalVolumeAdmissionState.REJECTED
                || upper.state() != SkyforgePhysicalVolumeAdmissionState.ADMITTED
                || !SkyforgePhysicalVolumeAdmissionStage.pendingCatchupChunks(upperId).isEmpty()) {
            return;
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
                .orElseThrow(() -> new IllegalStateException("SF-IMP-0051 upper proof volume has no origin surface"))
                .height();
        if (originChunk.getBlockState(new BlockPos(PROOF_X, upperSurfaceY - 1, PROOF_Z)).isAir()) {
            return;
        }

        long gravityQueries = GRAVITY_QUERIES.get();
        long claimQueries = SKYFORGE_CLAIM_QUERIES.get();
        long physicalQueries = PHYSICAL_SKYFORGE_QUERIES.get();
        long constrainedQueries = CONSTRAINED_QUERIES.get();
        if (gravityQueries == 0L) {
            throw new IllegalStateException(
                    "SF-IMP-0051 fixture invalid: forced terrain-matching jigsaw structure produced no GravityProcessor height queries");
        }
        if (claimQueries == 0L) {
            throw new IllegalStateException(
                    "SF-IMP-0051 fixture invalid: GravityProcessor probe did not overlap the planned upper Skyforge columns");
        }

        String outcome = constrainedQueries == 0L
                ? "ORDERING_ELIMINATES_BASE_WORLD_RACE"
                : "RACE_SURVIVES_REQUIRES_DOMAIN_HEIGHT_CONSTRAINT";
        proofComplete = true;
        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0051 PROJECTION LIFECYCLE PROBE: outcome=" + outcome
                        + ", gravityQueries=" + gravityQueries
                        + ", skyforgeClaimQueries=" + claimQueries
                        + ", physicalSkyforgeQueries=" + physicalQueries
                        + ", constrainedQueries=" + constrainedQueries
                        + ", lower=" + lower.state()
                        + ", upper=" + upper.state()
                        + ", pendingCatchup=0"
                        + ", upperOriginSurfaceY=" + upperSurfaceY
                        + ". A zero constrained-query count means every terrain-matching BASE_WORLD query completed "
                        + "before the unrelated upper Skyforge surface became physically visible; a nonzero count "
                        + "proves the cross-volume race still exists and requires the domain-height constraint.");
    }
}
