package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandTerrainProfile;
import io.github.nidaba.skyforge.world.SkyIslandWorldCatalog;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import io.github.nidaba.skyforge.world.WorldBounds;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** DEV/CI-only DR-30 qualification of the production exact-volume native-structure lifecycle. */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID)
final class SkyforgeDr30NativeStructureAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.dr30NativeStructureAcceptance";
    static final String MODE_PROPERTY = "skyforge.dev.dr30NativeStructureMode";
    static final String EXPECTED_RESULT_PROPERTY = "skyforge.dev.dr30NativeStructureExpectedResultFile";

    private static final String MODE_SINGLE = "single";
    private static final String MODE_RELOAD = "reload";
    private static final String MODE_STACKED = "stacked";
    private static final long ROOT_SEED = 0x445233304e415449L;
    private static final String GROUP = "dr-30-native-structure-probe";
    private static final double LOWER_ELEVATION = 196.0;
    private static final double UPPER_ELEVATION = 264.0;
    private static final double HORIZONTAL_RADIUS = 64.0;
    private static final int PROOF_RADIUS_CHUNKS = 4;

    private static AutoCloseable terrainBinding;
    private static AutoCloseable admissionBinding;
    private static boolean complete;

    private SkyforgeDr30NativeStructureAcceptance() {}

    static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    static synchronized void installFromSystemProperty() {
        if (!enabled() || terrainBinding != null || admissionBinding != null) {
            return;
        }
        String mode = mode();
        if (MODE_RELOAD.equals(mode)) {
            return;
        }
        if (SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()) {
            throw new IllegalStateException("DR-30 acceptance cannot replace an active terrain binding");
        }
        if (SkyforgePhysicalVolumeAdmissionStage.active()) {
            throw new IllegalStateException("DR-30 acceptance cannot replace an active admission binding");
        }
        SkyIslandWorldCatalog catalog = catalog(MODE_STACKED.equals(mode));
        terrainBinding = SkyforgeNeoForge1211SurfaceStage.installNativeSurfaceAdapted(
                new SkyforgeNeoForge1211ChunkAdapter(
                        catalog,
                        SkyIslandTerrainProfile.reference(),
                        new SkyforgeMinecraftBlockPalette()),
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()));
        admissionBinding = SkyforgePhysicalVolumeAdmissionStage.install(catalog);
    }

    @SubscribeEvent
    static synchronized void onServerTick(ServerTickEvent.Post event) {
        if (!enabled() || complete || !SkyforgeAutomatedAcceptanceHarness.serverMode()) {
            return;
        }
        ServerLevel level = event.getServer().getLevel(Level.OVERWORLD);
        if (level == null) {
            return;
        }
        switch (mode()) {
            case MODE_SINGLE -> observeSingle(level);
            case MODE_RELOAD -> observeReload(level);
            case MODE_STACKED -> observeStacked(level);
            default -> throw new IllegalStateException("unsupported DR-30 acceptance mode: " + mode());
        }
    }

    private static void observeSingle(ServerLevel level) {
        SkyIslandWorldVolumeId volumeId = lowerId();
        if (!ready(volumeId)) {
            return;
        }
        var identity = completedOriginIdentity(level, volumeId);
        if (identity == null) {
            return;
        }
        BlockPos structureBlock = findMansionBlock(level, identity);
        if (structureBlock == null) {
            return;
        }
        level.setBlockAndUpdate(structureBlock, Blocks.DIAMOND_BLOCK.defaultBlockState());

        Map<String, Object> evidence = commonEvidence("single");
        evidence.put("volume", volumeId.path());
        evidence.put("structure", identity.structureId());
        evidence.put("structureMinY", identity.minY());
        evidence.put("structureMaxY", identity.maxY());
        evidence.put("mutationX", structureBlock.getX());
        evidence.put("mutationY", structureBlock.getY());
        evidence.put("mutationZ", structureBlock.getZ());
        evidence.put("mutationBlock", "minecraft:diamond_block");
        evidence.put("physicalStructureBlockObserved", true);
        complete = true;
        SkyforgeAutomatedAcceptanceHarness.completeServerCase(level.getServer(), evidence);
    }

    private static void observeReload(ServerLevel level) {
        Properties previous = previousResult();
        int x = integer(previous, "mutationX");
        int y = integer(previous, "mutationY");
        int z = integer(previous, "mutationZ");
        BlockPos mutation = new BlockPos(x, y, z);
        if (!level.hasChunkAt(mutation)) {
            level.getChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
        }
        if (!level.getBlockState(mutation).is(Blocks.DIAMOND_BLOCK)) {
            throw new IllegalStateException("DR-30 reload lost the post-placement mutation at " + mutation);
        }
        var saved = SkyforgeNativeStructurePlacementSavedData.forLevel(level);
        var identities = saved.ownedFor(lowerId(), ChunkPos.asLong(0, 0));
        if (identities.isEmpty() || identities.stream().anyMatch(identity -> !saved.completed(identity))) {
            throw new IllegalStateException("DR-30 reload did not restore durable completed placement identity");
        }

        Map<String, Object> evidence = commonEvidence("reload");
        evidence.put("volume", lowerId().path());
        evidence.put("mutationX", x);
        evidence.put("mutationY", y);
        evidence.put("mutationZ", z);
        evidence.put("mutationPreserved", true);
        evidence.put("durableCompletedStarts", identities.size());
        evidence.put("secondProcessingDisposition", "SKIP_COMPLETED_IDENTITY");
        complete = true;
        SkyforgeAutomatedAcceptanceHarness.completeServerCase(level.getServer(), evidence);
    }

    private static void observeStacked(ServerLevel level) {
        SkyIslandWorldVolumeId lower = lowerId();
        SkyIslandWorldVolumeId upper = upperId();
        if (!ready(lower) || !ready(upper)) {
            return;
        }
        var lowerIdentity = completedOriginIdentity(level, lower);
        var upperIdentity = completedOriginIdentity(level, upper);
        if (lowerIdentity == null || upperIdentity == null) {
            return;
        }
        BlockPos lowerBlock = findMansionBlock(level, lowerIdentity);
        BlockPos upperBlock = findMansionBlock(level, upperIdentity);
        if (lowerBlock == null || upperBlock == null) {
            return;
        }
        if (lowerIdentity.volumeId().equals(upperIdentity.volumeId())
                || lowerIdentity.minY() == upperIdentity.minY()
                || lowerBlock.getY() == upperBlock.getY()) {
            throw new IllegalStateException("DR-30 stacked structure identities were not physically distinct");
        }

        Map<String, Object> evidence = commonEvidence("stacked");
        evidence.put("lowerVolume", lower.path());
        evidence.put("upperVolume", upper.path());
        evidence.put("lowerStructureMinY", lowerIdentity.minY());
        evidence.put("upperStructureMinY", upperIdentity.minY());
        evidence.put("lowerPhysicalBlockY", lowerBlock.getY());
        evidence.put("upperPhysicalBlockY", upperBlock.getY());
        evidence.put("exactVolumeIdentitiesDistinct", true);
        evidence.put("physicalPlacementsDistinct", true);
        complete = true;
        SkyforgeAutomatedAcceptanceHarness.completeServerCase(level.getServer(), evidence);
    }

    private static boolean ready(SkyIslandWorldVolumeId volumeId) {
        var snapshot = SkyforgePhysicalVolumeAdmissionStage.snapshot(volumeId);
        return snapshot.state() == SkyforgePhysicalVolumeAdmissionState.ADMITTED
                && SkyforgePhysicalVolumeAdmissionStage.pendingCatchupChunks(volumeId).isEmpty();
    }

    private static SkyforgeNativeStructurePlacementSavedData.PlacementIdentity completedOriginIdentity(
            ServerLevel level,
            SkyIslandWorldVolumeId volumeId) {
        var saved = SkyforgeNativeStructurePlacementSavedData.forLevel(level);
        return saved.ownedFor(volumeId, ChunkPos.asLong(0, 0)).stream()
                .filter(saved::completed)
                .findFirst()
                .orElse(null);
    }

    private static BlockPos findMansionBlock(
            ServerLevel level,
            SkyforgeNativeStructurePlacementSavedData.PlacementIdentity identity) {
        int minX = Math.max(identity.minX(), 0);
        int maxX = Math.min(identity.maxX(), 15);
        int minZ = Math.max(identity.minZ(), 0);
        int maxZ = Math.min(identity.maxZ(), 15);
        int minY = Math.max(identity.minY(), level.getMinBuildHeight());
        int maxY = Math.min(identity.maxY(), level.getMaxBuildHeight() - 1);
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.is(Blocks.DARK_OAK_PLANKS)
                            || state.is(Blocks.DARK_OAK_LOG)
                            || state.is(Blocks.COBBLESTONE)
                            || state.is(Blocks.GLASS_PANE)) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    private static Map<String, Object> commonEvidence(String phase) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("milestone", "DR-30");
        evidence.put("phase", phase);
        evidence.put("contentRole", "FREIGHT_TRANSFER_EDGE");
        evidence.put("fixtureAuthority", "GENERIC_NATIVE_MACHINE_PROBE");
        evidence.put("nativeRepresentative", "minecraft:mansion");
        return evidence;
    }

    private static Properties previousResult() {
        String configured = System.getProperty(EXPECTED_RESULT_PROPERTY);
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("DR-30 reload requires " + EXPECTED_RESULT_PROPERTY);
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(Path.of(configured))) {
            properties.load(input);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to load DR-30 prior acceptance result", exception);
        }
        if (!"PASS".equals(properties.getProperty("status"))) {
            throw new IllegalStateException("DR-30 prior placement result is not PASS");
        }
        return properties;
    }

    private static int integer(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null) {
            throw new IllegalStateException("DR-30 prior result missing " + key);
        }
        return Integer.parseInt(value);
    }

    private static String mode() {
        return System.getProperty(MODE_PROPERTY, MODE_SINGLE);
    }

    static int proofRadiusChunks() {
        return PROOF_RADIUS_CHUNKS;
    }

    static SkyIslandWorldCatalog catalog(boolean stacked) {
        SkyIslandWorldVolume lower = volume(lowerId(), LOWER_ELEVATION);
        if (!stacked) {
            return new SkyIslandWorldCatalog(ROOT_SEED, List.of(lower));
        }
        return new SkyIslandWorldCatalog(ROOT_SEED, List.of(lower, volume(upperId(), UPPER_ELEVATION)));
    }

    private static SkyIslandWorldVolume volume(SkyIslandWorldVolumeId id, double elevation) {
        return new SkyIslandWorldVolume(
                id,
                new WorldBounds(-64.0, 79.0, elevation - 40.0, elevation + 32.0, -64.0, 79.0),
                SkyforgeNeoForge1211PhysicalAdmissionDevRuntime.compileTableland(
                        id.geometrySeed(), 8.0, 8.0, elevation, HORIZONTAL_RADIUS, HORIZONTAL_RADIUS));
    }

    private static SkyIslandWorldVolumeId lowerId() {
        return new SkyIslandWorldVolumeId(ROOT_SEED, GROUP, 0, 0, ROOT_SEED ^ 0x4c4f574552L);
    }

    private static SkyIslandWorldVolumeId upperId() {
        return new SkyIslandWorldVolumeId(ROOT_SEED, GROUP, 0, 1, ROOT_SEED ^ 0x5550504552L);
    }
}
