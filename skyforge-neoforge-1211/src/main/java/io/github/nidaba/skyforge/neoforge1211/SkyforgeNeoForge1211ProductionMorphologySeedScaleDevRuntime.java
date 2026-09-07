package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.recipes.skyisland.MorphologyFamily;
import io.github.nidaba.skyforge.world.SkyIslandTerrainInterpreter;
import io.github.nidaba.skyforge.world.SkyIslandTerrainProfile;
import io.github.nidaba.skyforge.world.WorldBounds;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
 * SF-IMP-0083 exact Minecraft carrier for one four-member AUTH-0083 built-in family seed/scale atlas.
 *
 * <p>The selected family is represented by four vertically stacked, independently admitted exact
 * volumes in the development-only tall review dimension. No cave/ecology/interior mutation is
 * installed so issue #214 can compare morphology across seeds and scale without obscuration.
 */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID)
final class SkyforgeNeoForge1211ProductionMorphologySeedScaleDevRuntime {
    static final String FAMILY_PROPERTY = "skyforge.dev.productionMorphologySeedScaleFamily";

    private static final int SURFACE_SAMPLE_STRIDE = 8;
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211ProductionMorphologySeedScaleDevRuntime.class.getName());

    private static AutoCloseable persistentTerrainBinding;
    private static AutoCloseable persistentAdmissionBinding;
    private static boolean proofComplete;

    private SkyforgeNeoForge1211ProductionMorphologySeedScaleDevRuntime() {}

    static boolean enabled() {
        String value = System.getProperty(FAMILY_PROPERTY);
        return value != null && !value.isBlank();
    }

    static MorphologyFamily selectedFamily() {
        String value = System.getProperty(FAMILY_PROPERTY);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("SF-IMP-0083 seed/scale family property is not configured");
        }
        return SkyforgeProductionMorphologySeedScaleMatrixFixture.family(value);
    }

    static SkyforgeProductionMorphologySeedScaleMatrixFixture.FamilyFixture selectedFixture() {
        return FixtureHolder.FIXTURE;
    }

    private static final class FixtureHolder {
        private static final SkyforgeProductionMorphologySeedScaleMatrixFixture.FamilyFixture FIXTURE =
                SkyforgeProductionMorphologySeedScaleMatrixFixture.buildFamily(selectedFamily());
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
                    "SF-IMP-0083 seed/scale carrier requires an inert terrain/admission/population start");
        }

        var fixture = selectedFixture();
        SkyforgeAutomatedAcceptanceHarness.installWarmupChunkKeys(fixture.footprintChunkKeys());
        persistentTerrainBinding = SkyforgeNeoForge1211SurfaceStage.installNativeSurfaceAdapted(
                new SkyforgeNeoForge1211ChunkAdapter(
                        fixture.catalog(),
                        SkyIslandTerrainProfile.reference(),
                        new SkyforgeMinecraftBlockPalette()),
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()));
        LinkedHashMap<io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId, Set<Long>> requiredFootprints =
                new LinkedHashMap<>();
        for (var member : fixture.members()) {
            requiredFootprints.put(member.volumeId(), member.footprintChunkKeys());
        }
        persistentAdmissionBinding =
                SkyforgePhysicalVolumeAdmissionStage.install(fixture.catalog(), requiredFootprints);

        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0083 seed/scale family carrier enabled: family="
                        + fixture.family().identifier()
                        + ", members=" + fixture.members().stream().map(member -> member.member().id()).toList()
                        + ", reviewHeight=" + SkyforgeProductionMorphologySeedScaleMatrixFixture.REVIEW_DIMENSION_HEIGHT
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
        if (level.getMinBuildHeight()
                        != SkyforgeProductionMorphologySeedScaleMatrixFixture.REVIEW_DIMENSION_MIN_Y
                || level.getHeight()
                        != SkyforgeProductionMorphologySeedScaleMatrixFixture.REVIEW_DIMENSION_HEIGHT) {
            SkyforgeAutomatedAcceptanceHarness.fail(
                    event.getServer(),
                    "SF-IMP-0083 family atlas loaded in the wrong dimension interval: minY="
                            + level.getMinBuildHeight() + ", height=" + level.getHeight());
            return;
        }
        if (SkyforgeNativeSurfacePopulationStage.hasActiveBinding()
                || SkyforgeComposedCaveStage.active()
                || SkyforgeNativeInteriorPopulationStage.active()) {
            SkyforgeAutomatedAcceptanceHarness.fail(
                    event.getServer(),
                    "SF-IMP-0083 family atlas unexpectedly enabled cave/ecology/interior mutation");
            return;
        }

        LinkedHashMap<String, SurfaceEvidence> surfaces = new LinkedHashMap<>();
        for (var member : fixture.members()) {
            var admission = SkyforgePhysicalVolumeAdmissionStage.snapshot(member.volumeId());
            if (admission.state() == SkyforgePhysicalVolumeAdmissionState.REJECTED) {
                SkyforgeAutomatedAcceptanceHarness.fail(
                        event.getServer(),
                        "SF-IMP-0083 " + member.member().id()
                                + " collided with native terrain: " + admission);
                return;
            }
            if (admission.state() != SkyforgePhysicalVolumeAdmissionState.ADMITTED
                    || !SkyforgePhysicalVolumeAdmissionStage
                            .pendingCatchupChunks(member.volumeId())
                            .isEmpty()) {
                return;
            }
            if (admission.observedChunks() != admission.requiredChunks()
                    || !SkyforgePhysicalVolumeAdmissionStage
                            .requiredChunkKeys(member.volumeId())
                            .equals(member.footprintChunkKeys())) {
                SkyforgeAutomatedAcceptanceHarness.fail(
                        event.getServer(),
                        "SF-IMP-0083 " + member.member().id()
                                + " reached admission without exact finite-footprint evidence: " + admission);
                return;
            }

            Optional<SurfaceEvidence> optional = scanLoadedSurface(level, member);
            if (optional.isEmpty()) {
                return;
            }
            SurfaceEvidence surface = optional.orElseThrow();
            if (!surface.valid()) {
                SkyforgeAutomatedAcceptanceHarness.fail(
                        event.getServer(),
                        "SF-IMP-0083 " + member.member().id()
                                + " final surface evidence was incomplete: " + surface);
                return;
            }
            surfaces.put(member.member().id(), surface);
        }

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("family", fixture.family().identifier());
        evidence.put("memberCount", fixture.members().size());
        evidence.put("reviewDimensionMinY", level.getMinBuildHeight());
        evidence.put("reviewDimensionHeight", level.getHeight());
        evidence.put("familyFootprintChunks", fixture.footprintChunkKeys().size());

        for (int index = 0; index < fixture.members().size(); index++) {
            var member = fixture.members().get(index);
            var admission = SkyforgePhysicalVolumeAdmissionStage.snapshot(member.volumeId());
            var surface = surfaces.get(member.member().id());
            String prefix = "member" + index;
            evidence.put(prefix + "Id", member.member().id());
            evidence.put(prefix + "Family", member.member().family().identifier());
            evidence.put(prefix + "Scale", member.member().scaleId());
            evidence.put(prefix + "Seed", Long.toUnsignedString(member.member().seed()));
            evidence.put(prefix + "Morphology", member.morphologyIdentifier());
            evidence.put(prefix + "VerticalTranslation", member.verticalTranslation());
            evidence.put(prefix + "MinimumY",
                    SkyforgeProductionMorphologySeedScaleMatrixFixture.toExactInt(
                            member.exactSupport().bounds().minimumY()));
            evidence.put(prefix + "MaximumY",
                    SkyforgeProductionMorphologySeedScaleMatrixFixture.toExactInt(
                            member.exactSupport().bounds().maximumY()));
            evidence.put(prefix + "FootprintChunks", member.footprintChunkKeys().size());
            evidence.put(prefix + "SupportOccupiedColumns", member.exactSupport().occupiedColumns());
            evidence.put(prefix + "SupportScannedColumns", member.exactSupport().scannedColumns());
            evidence.put(prefix + "State", admission.state());
            evidence.put(prefix + "Observed", admission.observedChunks());
            evidence.put(prefix + "Required", admission.requiredChunks());
            evidence.put(prefix + "PendingCatchup", 0);
            evidence.put(prefix + "SampledClaims", surface.claimedColumns());
            evidence.put(prefix + "StoredTop", surface.storedTopBlocks());
            evidence.put(prefix + "AirAbove", surface.airAboveColumns());
            evidence.put(prefix + "StoredUnderside", surface.storedUndersideBlocks());
            evidence.put(prefix + "AirBelow", surface.airBelowColumns());
            evidence.put(prefix + "HeightMismatches", surface.heightMismatches());
            evidence.put(prefix + "LandTop", surface.landTopBlocks());
            evidence.put(prefix + "GrassTop", surface.grassTopBlocks());
            evidence.put(prefix + "SurfaceDigest", Long.toUnsignedString(surface.digest()));
        }

        evidence.putAll(SkyforgeRuntimePerformanceMetrics.evidence());
        proofComplete = true;
        SkyforgeAutomatedAcceptanceHarness.completeServerCase(event.getServer(), evidence);
        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0083 SEED/SCALE FAMILY PASS: family=" + fixture.family().identifier()
                        + ", members=" + surfaces);
    }

    static Optional<SurfaceEvidence> scanLoadedSurface(
            ServerLevel level,
            SkyforgeProductionMorphologySeedScaleMatrixFixture.MemberFixture fixture) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(fixture, "fixture");
        WorldBounds bounds = fixture.exactSupport().bounds();
        int minimumX = SkyforgeProductionMorphologySeedScaleMatrixFixture.toExactInt(bounds.minimumX());
        int maximumX = SkyforgeProductionMorphologySeedScaleMatrixFixture.toExactInt(bounds.maximumX());
        int minimumZ = SkyforgeProductionMorphologySeedScaleMatrixFixture.toExactInt(bounds.minimumZ());
        int maximumZ = SkyforgeProductionMorphologySeedScaleMatrixFixture.toExactInt(bounds.maximumZ());
        SkyIslandTerrainInterpreter interpreter = new SkyIslandTerrainInterpreter(
                fixture.worldVolume().compiledVolume(),
                SkyIslandTerrainProfile.reference());

        int claimedColumns = 0;
        int storedTopBlocks = 0;
        int airAboveColumns = 0;
        int storedUndersideBlocks = 0;
        int airBelowColumns = 0;
        int heightMismatches = 0;
        int landTopBlocks = 0;
        int grassTopBlocks = 0;
        String firstMissingBoundary = "none";
        int minimumSurfaceY = Integer.MAX_VALUE;
        int maximumSurfaceY = Integer.MIN_VALUE;
        long digest = 0xcbf29ce484222325L;

        for (int x = minimumX; x <= maximumX; x += SURFACE_SAMPLE_STRIDE) {
            for (int z = minimumZ; z <= maximumZ; z += SURFACE_SAMPLE_STRIDE) {
                Optional<SkyforgeExactVoxelSupportBounds.ColumnRange> expectedRange =
                        SkyforgeExactVoxelSupportBounds.integerSolidRange(interpreter, x, z);
                if (expectedRange.isEmpty()) {
                    continue;
                }

                int chunkX = Math.floorDiv(x, 16);
                int chunkZ = Math.floorDiv(z, 16);
                if (level.getChunkSource().getChunkNow(chunkX, chunkZ) == null) {
                    return Optional.empty();
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

                if ((top.isAir() || underside.isAir()) && "none".equals(firstMissingBoundary)) {
                    var topKey = BuiltInRegistries.BLOCK.getKey(top.getBlock());
                    var undersideKey = BuiltInRegistries.BLOCK.getKey(underside.getBlock());
                    firstMissingBoundary = "x=" + x
                            + ",z=" + z
                            + ",minY=" + range.minimumY()
                            + ",maxY=" + range.maximumY()
                            + ",thickness=" + (range.maximumY() - range.minimumY() + 1)
                            + ",firstFreeY=" + firstFreeY
                            + ",top=" + topKey
                            + ",underside=" + undersideKey;
                }

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
                firstMissingBoundary,
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
            String firstMissingBoundary,
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
                throw new IllegalArgumentException("negative SF-IMP-0083 surface evidence");
            }
            Objects.requireNonNull(firstMissingBoundary, "firstMissingBoundary");
        }

        static SurfaceEvidence empty() {
            return new SurfaceEvidence(
                    0, 0, 0, 0, 0, 0, 0, 0, "none", 0, 0, 0xcbf29ce484222325L);
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
