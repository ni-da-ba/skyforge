package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandTerrainInterpreter;
import io.github.nidaba.skyforge.world.SkyIslandTerrainProfile;
import io.github.nidaba.skyforge.world.WorldBounds;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * SF-IMP-0082 reusable Minecraft carrier for the remaining AUTH-0083 built-in family atlas.
 *
 * <p>One member is selected per ModDev process. The runtime shares the same objective realization
 * contract for Tableland, Spine, Basin, and Lobed and deliberately installs no cave/ecology/interior
 * mutation so #214 can inspect the morphology itself.
 */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID)
final class SkyforgeNeoForge1211ProductionMorphologyAtlasDevRuntime {
    static final String MEMBER_PROPERTY = "skyforge.dev.productionMorphologyAtlasMember";
    private static final int SURFACE_SAMPLE_STRIDE = 8;
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211ProductionMorphologyAtlasDevRuntime.class.getName());

    private static AutoCloseable persistentTerrainBinding;
    private static AutoCloseable persistentAdmissionBinding;
    private static boolean proofComplete;

    private SkyforgeNeoForge1211ProductionMorphologyAtlasDevRuntime() {}

    static boolean enabled() {
        String value = System.getProperty(MEMBER_PROPERTY);
        return value != null && !value.isBlank();
    }

    static SkyforgeProductionMorphologyAtlasFixture.Member selectedMember() {
        String value = System.getProperty(MEMBER_PROPERTY);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("SF-IMP-0082 atlas member property is not configured");
        }
        return SkyforgeProductionMorphologyAtlasFixture.member(value);
    }

    static SkyforgeProductionMorphologyAtlasFixture.Fixture selectedFixture() {
        return SkyforgeProductionMorphologyAtlasFixture.fixture(selectedMember());
    }

    static synchronized void installFromSystemProperty() {
        if (!enabled() || persistentTerrainBinding != null || persistentAdmissionBinding != null) {
            return;
        }
        if (SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()
                || SkyforgePhysicalVolumeAdmissionStage.active()
                || SkyforgeNativeSurfacePopulationStage.hasActiveBinding()
                || SkyforgeComposedCaveStage.active()
                || SkyforgeNativeInteriorPopulationStage.active()) {
            throw new IllegalStateException(
                    "SF-IMP-0082 atlas carrier requires an inert terrain/admission/population start");
        }

        var fixture = selectedFixture();
        SkyforgeAutomatedAcceptanceHarness.installWarmupChunkKeys(fixture.footprintChunkKeys());
        persistentTerrainBinding = SkyforgeNeoForge1211SurfaceStage.installNativeSurfaceAdapted(
                new SkyforgeNeoForge1211ChunkAdapter(
                        fixture.catalog(),
                        SkyIslandTerrainProfile.reference(),
                        new SkyforgeMinecraftBlockPalette()),
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()));
        persistentAdmissionBinding = SkyforgePhysicalVolumeAdmissionStage.install(fixture.catalog());

        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0082 morphology atlas carrier enabled: member=" + fixture.member().id()
                        + ", family=" + fixture.member().family().identifier()
                        + ", translatedSuspension=" + fixture.translatedDescriptor().suspensionElevation()
                        + ", exactBounds=" + fixture.exactSupport().bounds()
                        + ", footprintChunks=" + fixture.footprintChunkKeys().size()
                        + ". Cave/ecology/interior mutation remains intentionally absent.");
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (!enabled() || proofComplete) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (level.dimension().equals(Level.OVERWORLD)) {
                evaluate(level, event);
            }
        }
    }

    private static synchronized void evaluate(ServerLevel level, ServerTickEvent.Post event) {
        if (proofComplete) {
            return;
        }
        var fixture = selectedFixture();
        var admission = SkyforgePhysicalVolumeAdmissionStage.snapshot(fixture.volumeId());
        if (admission.state() == SkyforgePhysicalVolumeAdmissionState.REJECTED) {
            SkyforgeAutomatedAcceptanceHarness.fail(
                    event.getServer(),
                    "SF-IMP-0082 " + fixture.member().id()
                            + " collided with native terrain: " + admission);
            return;
        }
        if (admission.state() != SkyforgePhysicalVolumeAdmissionState.ADMITTED
                || !SkyforgePhysicalVolumeAdmissionStage.pendingCatchupChunks(fixture.volumeId()).isEmpty()) {
            return;
        }
        if (admission.observedChunks() != admission.requiredChunks()) {
            SkyforgeAutomatedAcceptanceHarness.fail(
                    event.getServer(),
                    "SF-IMP-0082 admitted before complete physical evidence: " + admission);
            return;
        }
        if (SkyforgeNativeSurfacePopulationStage.hasActiveBinding()
                || SkyforgeComposedCaveStage.active()
                || SkyforgeNativeInteriorPopulationStage.active()) {
            SkyforgeAutomatedAcceptanceHarness.fail(
                    event.getServer(),
                    "SF-IMP-0082 atlas carrier unexpectedly enabled cave/ecology/interior mutation");
            return;
        }

        Optional<SurfaceEvidence> optional = scanLoadedSurface(level, fixture);
        if (optional.isEmpty()) {
            return;
        }
        SurfaceEvidence surface = optional.orElseThrow();
        if (!surface.valid()) {
            SkyforgeAutomatedAcceptanceHarness.fail(
                    event.getServer(),
                    "SF-IMP-0082 " + fixture.member().id()
                            + " final surface evidence was incomplete: " + surface);
            return;
        }

        proofComplete = true;
        SkyforgeAutomatedAcceptanceHarness.completeServerCase(
                event.getServer(),
                java.util.Map.ofEntries(
                        java.util.Map.entry("memberId", fixture.member().id()),
                        java.util.Map.entry("family", fixture.member().family().identifier()),
                        java.util.Map.entry(
                                "geometrySeed",
                                Long.toUnsignedString(SkyforgeProductionMorphologyAtlasFixture.GEOMETRY_SEED)),
                        java.util.Map.entry(
                                "translatedSuspension",
                                fixture.translatedDescriptor().suspensionElevation()),
                        java.util.Map.entry("verticalTranslation", fixture.verticalTranslation()),
                        java.util.Map.entry("certificateKind", fixture.exactSupport().certificateKind()),
                        java.util.Map.entry(
                                "minimumX",
                                SkyforgeProductionMorphologyAtlasFixture.toExactInt(
                                        fixture.exactSupport().bounds().minimumX())),
                        java.util.Map.entry(
                                "maximumX",
                                SkyforgeProductionMorphologyAtlasFixture.toExactInt(
                                        fixture.exactSupport().bounds().maximumX())),
                        java.util.Map.entry(
                                "minimumY",
                                SkyforgeProductionMorphologyAtlasFixture.toExactInt(
                                        fixture.exactSupport().bounds().minimumY())),
                        java.util.Map.entry(
                                "maximumY",
                                SkyforgeProductionMorphologyAtlasFixture.toExactInt(
                                        fixture.exactSupport().bounds().maximumY())),
                        java.util.Map.entry(
                                "minimumZ",
                                SkyforgeProductionMorphologyAtlasFixture.toExactInt(
                                        fixture.exactSupport().bounds().minimumZ())),
                        java.util.Map.entry(
                                "maximumZ",
                                SkyforgeProductionMorphologyAtlasFixture.toExactInt(
                                        fixture.exactSupport().bounds().maximumZ())),
                        java.util.Map.entry("footprintChunks", fixture.footprintChunkKeys().size()),
                        java.util.Map.entry("supportOccupiedColumns", fixture.exactSupport().occupiedColumns()),
                        java.util.Map.entry("supportScannedColumns", fixture.exactSupport().scannedColumns()),
                        java.util.Map.entry("state", admission.state()),
                        java.util.Map.entry("observed", admission.observedChunks()),
                        java.util.Map.entry("required", admission.requiredChunks()),
                        java.util.Map.entry("pendingCatchup", 0),
                        java.util.Map.entry("sampledClaims", surface.claimedColumns()),
                        java.util.Map.entry("storedTop", surface.storedTopBlocks()),
                        java.util.Map.entry("airAbove", surface.airAboveColumns()),
                        java.util.Map.entry("storedUnderside", surface.storedUndersideBlocks()),
                        java.util.Map.entry("airBelow", surface.airBelowColumns()),
                        java.util.Map.entry("heightMismatches", surface.heightMismatches()),
                        java.util.Map.entry("landTop", surface.landTopBlocks()),
                        java.util.Map.entry("grassTop", surface.grassTopBlocks()),
                        java.util.Map.entry("minimumSampleSurfaceY", surface.minimumSurfaceY()),
                        java.util.Map.entry("maximumSampleSurfaceY", surface.maximumSurfaceY()),
                        java.util.Map.entry("surfaceDigest", Long.toUnsignedString(surface.digest()))));

        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0082 MORPHOLOGY ATLAS PASS: member=" + fixture.member().id()
                        + ", admission=" + admission.observedChunks() + "/" + admission.requiredChunks()
                        + ", exactBounds=" + fixture.exactSupport().bounds()
                        + ", surface=" + surface);
    }

    static Optional<SurfaceEvidence> scanLoadedSurface(
            ServerLevel level,
            SkyforgeProductionMorphologyAtlasFixture.Fixture fixture) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(fixture, "fixture");
        WorldBounds bounds = fixture.exactSupport().bounds();
        int minimumX = SkyforgeProductionMorphologyAtlasFixture.toExactInt(bounds.minimumX());
        int maximumX = SkyforgeProductionMorphologyAtlasFixture.toExactInt(bounds.maximumX());
        int minimumZ = SkyforgeProductionMorphologyAtlasFixture.toExactInt(bounds.minimumZ());
        int maximumZ = SkyforgeProductionMorphologyAtlasFixture.toExactInt(bounds.maximumZ());
        SkyIslandTerrainInterpreter interpreter = new SkyIslandTerrainInterpreter(
                fixture.catalog().volumes().getFirst().compiledVolume(),
                SkyIslandTerrainProfile.reference());

        int claimedColumns = 0;
        int storedTopBlocks = 0;
        int airAboveColumns = 0;
        int storedUndersideBlocks = 0;
        int airBelowColumns = 0;
        int heightMismatches = 0;
        int landTopBlocks = 0;
        int grassTopBlocks = 0;
        int minimumSurfaceY = Integer.MAX_VALUE;
        int maximumSurfaceY = Integer.MIN_VALUE;
        long digest = 0xcbf29ce484222325L;

        for (int x = minimumX; x <= maximumX; x += SURFACE_SAMPLE_STRIDE) {
            for (int z = minimumZ; z <= maximumZ; z += SURFACE_SAMPLE_STRIDE) {
                int chunkX = Math.floorDiv(x, 16);
                int chunkZ = Math.floorDiv(z, 16);
                if (level.getChunkSource().getChunkNow(chunkX, chunkZ) == null) {
                    return Optional.empty();
                }

                Optional<SkyforgeExactVoxelSupportBounds.ColumnRange> expectedRange =
                        SkyforgeExactVoxelSupportBounds.integerSolidRange(interpreter, x, z);
                if (expectedRange.isEmpty()) {
                    continue;
                }
                var claim = SkyforgeNeoForge1211SurfaceStage.queryBaseHeightClaim(
                        fixture.volumeId(),
                        x,
                        z,
                        Heightmap.Types.WORLD_SURFACE_WG,
                        level.getMinBuildHeight(),
                        level.getHeight());
                if (claim.isEmpty()) {
                    heightMismatches++;
                    continue;
                }

                var range = expectedRange.orElseThrow();
                int firstFreeY = claim.orElseThrow().height();
                claimedColumns++;
                if (firstFreeY != range.maximumY() + 1) {
                    heightMismatches++;
                }
                minimumSurfaceY = Math.min(minimumSurfaceY, firstFreeY);
                maximumSurfaceY = Math.max(maximumSurfaceY, firstFreeY);

                BlockState top = level.getBlockState(new BlockPos(x, firstFreeY - 1, z));
                BlockState above = level.getBlockState(new BlockPos(x, firstFreeY, z));
                BlockState underside = level.getBlockState(new BlockPos(x, range.minimumY(), z));
                BlockState below = level.getBlockState(new BlockPos(x, range.minimumY() - 1, z));

                if (!top.isAir()) {
                    storedTopBlocks++;
                }
                if (above.isAir()) {
                    airAboveColumns++;
                }
                if (!underside.isAir()) {
                    storedUndersideBlocks++;
                }
                if (below.isAir()) {
                    airBelowColumns++;
                }
                if (isLandSubstrate(top)) {
                    landTopBlocks++;
                }
                if (top.is(Blocks.GRASS_BLOCK)) {
                    grassTopBlocks++;
                }

                digest = mix(digest, x);
                digest = mix(digest, z);
                digest = mix(digest, firstFreeY);
                digest = mix(digest, range.minimumY());
                var blockKey = BuiltInRegistries.BLOCK.getKey(top.getBlock());
                digest = mix(digest, blockKey == null ? 0 : blockKey.toString().hashCode());
            }
        }

        if (claimedColumns == 0) {
            return Optional.of(SurfaceEvidence.empty());
        }
        return Optional.of(new SurfaceEvidence(
                claimedColumns,
                storedTopBlocks,
                airAboveColumns,
                storedUndersideBlocks,
                airBelowColumns,
                heightMismatches,
                landTopBlocks,
                grassTopBlocks,
                minimumSurfaceY,
                maximumSurfaceY,
                digest));
    }

    private static boolean isLandSubstrate(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.PODZOL)
                || state.is(Blocks.ROOTED_DIRT)
                || state.is(Blocks.MUD);
    }

    private static long mix(long digest, long value) {
        long mixed = digest;
        for (int shift = 0; shift < Long.SIZE; shift += Byte.SIZE) {
            mixed ^= (value >>> shift) & 0xffL;
            mixed *= 0x100000001b3L;
        }
        return mixed;
    }

    record SurfaceEvidence(
            int claimedColumns,
            int storedTopBlocks,
            int airAboveColumns,
            int storedUndersideBlocks,
            int airBelowColumns,
            int heightMismatches,
            int landTopBlocks,
            int grassTopBlocks,
            int minimumSurfaceY,
            int maximumSurfaceY,
            long digest) {
        SurfaceEvidence {
            if (claimedColumns < 0
                    || storedTopBlocks < 0
                    || airAboveColumns < 0
                    || storedUndersideBlocks < 0
                    || airBelowColumns < 0
                    || heightMismatches < 0
                    || landTopBlocks < 0
                    || grassTopBlocks < 0) {
                throw new IllegalArgumentException("negative SF-IMP-0082 surface evidence");
            }
        }

        static SurfaceEvidence empty() {
            return new SurfaceEvidence(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0xcbf29ce484222325L);
        }

        boolean valid() {
            return claimedColumns > 0
                    && storedTopBlocks == claimedColumns
                    && airAboveColumns == claimedColumns
                    && storedUndersideBlocks == claimedColumns
                    && airBelowColumns == claimedColumns
                    && heightMismatches == 0
                    && landTopBlocks > 0
                    && grassTopBlocks > 0
                    && minimumSurfaceY <= maximumSurfaceY;
        }
    }
}
