package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.recipes.skyisland.SemanticSkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationAssociation;
import io.github.nidaba.skyforge.world.SkyIslandComponentFluvialTerrainCandidatePlan;
import io.github.nidaba.skyforge.world.SkyIslandComponentFluvialTerrainCandidatePlanner;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandFluvialVoxelColumn;
import io.github.nidaba.skyforge.world.SkyIslandHydrologyRuntimeAuthorization;
import io.github.nidaba.skyforge.world.SkyIslandTerrainProfile;
import io.github.nidaba.skyforge.world.SkyIslandWorldCatalog;
import io.github.nidaba.skyforge.world.SkyIslandWorldHeadRefinedTerrainPlan;
import io.github.nidaba.skyforge.world.SkyIslandWorldHeadRefinedTerrainPlanner;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import io.github.nidaba.skyforge.world.SkyIslandWorldWaterHeadRefinementPlan;
import io.github.nidaba.skyforge.world.SkyIslandWorldWaterHeadRefinementPlanner;
import io.github.nidaba.skyforge.world.SkyIslandWorldWaterProjectionQualificationPlan;
import io.github.nidaba.skyforge.world.SkyIslandWorldWaterProjectionQualificationPlanner;
import io.github.nidaba.skyforge.world.SkyIslandFluvialVoxelQuantizationPlanner;
import io.github.nidaba.skyforge.world.WorldBounds;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Disposable, opt-in F4K visual-review binding.
 *
 * <p>This fixture is deliberately separate from the retained SF-IMP legacy specimen. It derives
 * the fixed ordinary (8, 81, 77) authored identity, the accepted F4H direct/refined field, and
 * the exact runtime authorization before installing the Minecraft adapter. No production runtime
 * enables it unless the dedicated ModDev run property is set.
 */
final class SkyforgeF4KHydrologyReviewDevRuntime {
    static final String ENABLE_PROPERTY = "skyforge.dev.f4kHydrologyReview";
    static final String VIEWER_PROPERTY = "skyforge.dev.f4kHydrologyReviewViewer";
    static final String REVIEW_WORLD = "Skyforge F4K Hydrology Review";
    static final int INSPECTION_X = 0;
    static final int INSPECTION_Y = 320;
    static final int INSPECTION_Z = 0;

    private static final long DESCRIPTOR_SEED = 0x534B59464F524745L;
    private static final long REALIZATION_ROOT = 0x5245414C495A4552L;
    private static final long PROVINCE = 8L;
    private static final long CLUSTER = 81L;
    private static final long KEY = 77L;
    private static final long GEOMETRY_SEED = 910_077L;

    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeF4KHydrologyReviewDevRuntime.class.getName());
    private static AutoCloseable persistentBinding;
    private static Fixture reviewFixture;
    private static boolean preparationComplete;
    private static int lastLoggedPersistedWaterHeads = -1;

    private SkyforgeF4KHydrologyReviewDevRuntime() {}

    /** Installs only for the dedicated local visual-review run. */
    static synchronized void installFromSystemProperty() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY) || persistentBinding != null) {
            return;
        }
        if (SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()) {
            throw new IllegalStateException(
                    "F4K hydrology review cannot install over another Skyforge surface binding");
        }

        Fixture fixture = fixture();
        reviewFixture = fixture;
        if (SkyforgeAutomatedAcceptanceHarness.serverMode()) {
            SkyforgeAutomatedAcceptanceHarness.installWarmupChunkKeys(
                    requiredWaterHeadChunkKeys(fixture));
        }
        SkyforgeNeoForge1211ChunkAdapter adapter = new SkyforgeNeoForge1211ChunkAdapter(
                fixture.catalog(),
                SkyIslandTerrainProfile.reference(),
                new SkyforgeMinecraftBlockPalette(),
                Map.of(fixture.volume().id(), fixture.descriptor()));
        persistentBinding = SkyforgeNeoForge1211SurfaceStage.installAuthorizedHydrology(
                adapter,
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()),
                fixture.authorization());

        String anchor = fixture.firstWetAnchor()
                .map(SkyforgeF4KHydrologyReviewDevRuntime::formatAnchor)
                .orElse("none");
        LOGGER.log(
                System.Logger.Level.INFO,
                REVIEW_WORLD
                        + " enabled. New disposable Skyforge Development world only. "
                        + "F4H authorized columns=" + fixture.authorization().quantization().authorizedColumns().size()
                        + ", exact removed blocks=" + fixture.authorization().quantization().totalRemovedSolidBlocks()
                        + ", refined reaches=" + fixture.refined().reaches().size()
                        + ", first wet anchor=" + anchor
                        + ". Inspect overview near x=" + INSPECTION_X
                        + ", y=" + INSPECTION_Y
                        + ", z=" + INSPECTION_Z
                        + "; the generated water must remain inside the authored channel and exact island owner.");
    }

    static boolean viewerEnabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY) || Boolean.getBoolean(VIEWER_PROPERTY);
    }

    @SubscribeEvent
    static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!viewerEnabled() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        player.setGameMode(GameType.SPECTATOR);
        player.teleportTo(INSPECTION_X, INSPECTION_Y, INSPECTION_Z);
        player.setYRot(180.0f);
        player.setXRot(16.0f);
        player.sendSystemMessage(Component.literal(
                "[Skyforge F4K] Persisted F4H hydrology specimen loaded at the overview. "
                        + "The viewer does not reinstall the hydrology mutation binding."));
        player.sendSystemMessage(Component.literal(
                "[Skyforge F4K] Inspect the channel for water constrained to its carved, island-owned bed; "
                        + "use spectator flight to follow it downstream."));
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)
                || !SkyforgeAutomatedAcceptanceHarness.serverMode()
                || preparationComplete) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (level.dimension().equals(Level.OVERWORLD)) {
                verifyPreparedWorld(level, event);
            }
        }
    }

    private static synchronized void verifyPreparedWorld(
            ServerLevel level,
            ServerTickEvent.Post event) {
        if (preparationComplete) {
            return;
        }
        Fixture fixture = reviewFixture;
        if (fixture == null) {
            SkyforgeAutomatedAcceptanceHarness.fail(
                    event.getServer(),
                    "F4K preparation fixture was not retained after installing its review binding");
            return;
        }

        List<BlockPos> expectedWaterHeads = expectedWaterHeadPositions(fixture);
        if (expectedWaterHeads.isEmpty()) {
            SkyforgeAutomatedAcceptanceHarness.fail(
                    event.getServer(),
                    "F4K preparation produced no realizable F4H water-head columns");
            return;
        }

        int persistedWaterHeads = 0;
        for (BlockPos position : expectedWaterHeads) {
            ChunkPos chunkPos = new ChunkPos(position);
            var chunk = level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);
            if (chunk == null) {
                SkyforgeAutomatedAcceptanceHarness.fail(
                        event.getServer(),
                        "F4K acceptance warmup omitted expected water head "
                                + position + " in chunk " + chunkPos);
                return;
            }
            if (chunk.getBlockState(position).is(Blocks.WATER)) {
                persistedWaterHeads++;
            }
        }

        if (persistedWaterHeads != expectedWaterHeads.size()) {
            if (persistedWaterHeads != lastLoggedPersistedWaterHeads) {
                BlockPos firstMissing = expectedWaterHeads.stream()
                        .filter(position -> {
                            var chunkPos = new ChunkPos(position);
                            var chunk = level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);
                            return chunk != null && !chunk.getBlockState(position).is(Blocks.WATER);
                        })
                        .findFirst()
                        .orElse(null);
                LOGGER.log(
                        System.Logger.Level.INFO,
                        "F4K preparation waiting for persisted water heads: observed="
                                + persistedWaterHeads + "/" + expectedWaterHeads.size()
                                + ", firstMissing=" + firstMissing);
                lastLoggedPersistedWaterHeads = persistedWaterHeads;
            }
            return;
        }

        preparationComplete = true;
        SkyforgeAutomatedAcceptanceHarness.completeServerCase(
                event.getServer(),
                Map.of(
                        "f4hAuthorizedColumns", fixture.authorization().quantization().authorizedColumns().size(),
                        "f4dRemovedSolidBlocks", fixture.authorization().quantization().totalRemovedSolidBlocks(),
                        "f4hRefinedReaches", fixture.refined().reaches().size(),
                        "persistedWaterHeads", persistedWaterHeads,
                        "surfaceBindingActive", SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()));
        LOGGER.log(
                System.Logger.Level.INFO,
                "F4K HYDROLOGY PREPARATION PASS: persisted " + persistedWaterHeads
                        + " exact F4H water heads after F4D authorized terrain removal.");
    }

    static List<BlockPos> expectedWaterHeadPositions(Fixture fixture) {
        Objects.requireNonNull(fixture, "fixture");
        return fixture.authorization().quantization().authorizedColumns().stream()
                .filter(column -> column.projection().semanticSample().wet() && column.mutatesTerrain())
                .filter(column -> Math.min(
                                waterMaximumY(column, fixture),
                                column.originalSupport().maximumSolidY())
                        >= column.targetMaximumSolidY() + 1)
                .map(column -> new BlockPos(
                        column.worldX(),
                        column.targetMaximumSolidY() + 1,
                        column.worldZ()))
                .toList();
    }

    static Set<Long> requiredWaterHeadChunkKeys(Fixture fixture) {
        Set<Long> chunkKeys = new LinkedHashSet<>();
        for (BlockPos position : expectedWaterHeadPositions(fixture)) {
            chunkKeys.add(new ChunkPos(position).toLong());
        }
        return Set.copyOf(chunkKeys);
    }

    private static int waterMaximumY(SkyIslandFluvialVoxelColumn column, Fixture fixture) {
        var projection = column.projection();
        var semantic = projection.semanticSample();
        double waterHeadWorld = projection.originalUpperSurfaceWorldY()
                + (semantic.waterSurfacePotential() - semantic.originalTerrainPotential())
                        * fixture.descriptor().reliefBudget();
        return (int) Math.ceil(waterHeadWorld) - 1;
    }

    private static String formatAnchor(SkyIslandFluvialVoxelColumn column) {
        var projection = column.projection();
        return "(" + column.worldX() + ", authorized-water-band, " + column.worldZ()
                + ", targetMaxSolidY=" + column.targetMaximumSolidY()
                + ", wet=" + projection.semanticSample().wet() + ")";
    }
    static Fixture fixture() {
        SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(DESCRIPTOR_SEED, PROVINCE, CLUSTER, KEY));
        SkyIslandVolumeDescriptor physical = SkyIslandVolumeDescriptor.schema2(
                GEOMETRY_SEED,
                0.0,
                0.0,
                256.0,
                descriptor.nominalRadius(),
                72.0,
                104.0,
                Math.min(32.0, descriptor.nominalRadius()),
                0.43,
                0.62,
                0.57,
                0.18,
                descriptor.morphologyFamily(),
                0.22,
                38.0,
                0.31);
        CompiledSkyIslandVolume compiled = new SemanticSkyIslandVolumeRecipe().compile(physical);
        SkyIslandWorldVolume volume = new SkyIslandWorldVolume(
                new SkyIslandWorldVolumeId(
                        REALIZATION_ROOT,
                        "f4k-hydrology-review",
                        0,
                        0,
                        GEOMETRY_SEED),
                new WorldBounds(
                        -descriptor.nominalRadius(),
                        descriptor.nominalRadius(),
                        64.0,
                        448.0,
                        -descriptor.nominalRadius(),
                        descriptor.nominalRadius()),
                compiled);
        SkyIslandAuthoredRealizationAssociation association =
                SkyIslandAuthoredRealizationAssociation.of(descriptor, volume);
        SkyIslandComponentFluvialTerrainCandidatePlan candidate =
                SkyIslandComponentFluvialTerrainCandidatePlanner.plan(descriptor);
        var voxel = SkyIslandFluvialVoxelQuantizationPlanner.plan(association, candidate);
        SkyIslandWorldWaterProjectionQualificationPlan direct =
                SkyIslandWorldWaterProjectionQualificationPlanner.plan(voxel);
        SkyIslandWorldWaterHeadRefinementPlan head =
                SkyIslandWorldWaterHeadRefinementPlanner.plan(direct);
        SkyIslandWorldHeadRefinedTerrainPlan refined =
                SkyIslandWorldHeadRefinedTerrainPlanner.plan(head);
        SkyIslandHydrologyRuntimeAuthorization authorization =
                SkyIslandHydrologyRuntimeAuthorization.fromF4H(
                        association,
                        candidate,
                        refined.terrainField());
        return new Fixture(
                descriptor,
                volume,
                new SkyIslandWorldCatalog(REALIZATION_ROOT, List.of(volume)),
                direct,
                head,
                refined,
                authorization);
    }

    static record Fixture(
            SkyIslandDescriptor descriptor,
            SkyIslandWorldVolume volume,
            SkyIslandWorldCatalog catalog,
            SkyIslandWorldWaterProjectionQualificationPlan direct,
            SkyIslandWorldWaterHeadRefinementPlan head,
            SkyIslandWorldHeadRefinedTerrainPlan refined,
            SkyIslandHydrologyRuntimeAuthorization authorization) {
        Fixture {
            if (direct.terrainVoxelPlan().association() != authorization.association()
                    || head.directQualification() != direct
                    || refined.headRefinement() != head) {
                throw new IllegalArgumentException("F4K review fixture lost exact F4H plan identity");
            }
        }

        private java.util.Optional<SkyIslandFluvialVoxelColumn> firstWetAnchor() {
            return authorization.quantization().authorizedColumns().stream()
                    .filter(column -> column.projection().semanticSample().wet()
                            && column.mutatesTerrain())
                    .findFirst();
        }
    }
}
