package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyFamily;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviders;
import io.github.nidaba.skyforge.recipes.skyisland.group.ProviderMorphologySpec;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandMorphologySpecCompiler;
import io.github.nidaba.skyforge.world.SkyIslandTerrainInterpreter;
import io.github.nidaba.skyforge.world.SkyIslandTerrainProfile;
import io.github.nidaba.skyforge.world.SkyIslandWorldCatalog;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import io.github.nidaba.skyforge.world.WorldBounds;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * SF-IMP-0081 first Minecraft carrier for the AUTH-0083 production morphology atlas.
 *
 * <p>The specimen is exactly AUTH-0083 member {@value #MEMBER_ID}: canonical Skyforge seed,
 * SMALL scale, built-in MASSIF provider, full bounded detail, and full secondary morphology through
 * the provider-neutral production compiler. Minecraft changes only the suspension elevation by an
 * integer translation so the same discrete morphology fits inside the 1.21.1 build range.
 *
 * <p>This first #214 tranche intentionally excludes caves and native ecology. SF-IMP-0080 already
 * established those lifecycle hooks; the purpose here is to expose macro/meso/underside form
 * without vegetation or caves obscuring the morphology decision.
 */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID)
final class SkyforgeNeoForge1211ProductionMorphologyMassifDevRuntime {
    static final String ENABLE_PROPERTY = "skyforge.dev.productionMorphologyMassif";
    static final String MEMBER_ID = "builtin-massif-small-seed-skyforge";
    static final long GEOMETRY_SEED = 0x534b59464f524745L;

    private static final long ROOT_SEED = 0x5346494d50303081L;
    private static final double SOURCE_SUSPENSION = 512.0;
    private static final int TARGET_MINIMUM_SOLID_Y = 96;
    private static final int SURFACE_SAMPLE_STRIDE = 8;
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211ProductionMorphologyMassifDevRuntime.class.getName());

    private static AutoCloseable persistentTerrainBinding;
    private static AutoCloseable persistentAdmissionBinding;
    private static boolean proofComplete;

    private SkyforgeNeoForge1211ProductionMorphologyMassifDevRuntime() {}

    static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    static synchronized void installFromSystemProperty() {
        if (!enabled() || persistentTerrainBinding != null || persistentAdmissionBinding != null) {
            return;
        }
        if (SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()
                || SkyforgePhysicalVolumeAdmissionStage.active()
                || SkyforgeNativeSurfacePopulationStage.hasActiveBinding()) {
            throw new IllegalStateException(
                    "SF-IMP-0081 production morphology specimen requires an inert terrain/admission/population start");
        }

        Fixture fixture = fixture();
        persistentTerrainBinding = SkyforgeNeoForge1211SurfaceStage.installNativeSurfaceAdapted(
                new SkyforgeNeoForge1211ChunkAdapter(
                        fixture.catalog(),
                        SkyIslandTerrainProfile.reference(),
                        new SkyforgeMinecraftBlockPalette()),
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()));
        persistentAdmissionBinding = SkyforgePhysicalVolumeAdmissionStage.install(fixture.catalog());

        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0081 production morphology specimen enabled: member=" + MEMBER_ID
                        + ", translatedSuspension=" + fixture.translatedDescriptor().suspensionElevation()
                        + ", exactBounds=" + fixture.exactSupport().bounds()
                        + ", requiredChunks=" + footprintChunkKeys(fixture.exactSupport().bounds()).size()
                        + ". No cave/ecology mutation is installed so #214 can judge the production form directly.");
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (!enabled() || proofComplete) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (!level.dimension().equals(Level.OVERWORLD)) {
                continue;
            }
            evaluate(level, event);
        }
    }

    private static synchronized void evaluate(
            ServerLevel level,
            ServerTickEvent.Post event) {
        if (proofComplete) {
            return;
        }
        Fixture fixture = fixture();
        var admission = SkyforgePhysicalVolumeAdmissionStage.snapshot(fixture.volumeId());
        if (admission.state() == SkyforgePhysicalVolumeAdmissionState.REJECTED) {
            SkyforgeAutomatedAcceptanceHarness.fail(
                    event.getServer(),
                    "SF-IMP-0081 production Massif collided with native terrain: " + admission);
            return;
        }
        if (admission.state() != SkyforgePhysicalVolumeAdmissionState.ADMITTED
                || !SkyforgePhysicalVolumeAdmissionStage.pendingCatchupChunks(fixture.volumeId()).isEmpty()) {
            return;
        }
        if (admission.observedChunks() != admission.requiredChunks()) {
            SkyforgeAutomatedAcceptanceHarness.fail(
                    event.getServer(),
                    "SF-IMP-0081 admitted before complete physical evidence: " + admission);
            return;
        }
        if (SkyforgeNativeSurfacePopulationStage.hasActiveBinding()
                || SkyforgeComposedCaveStage.active()
                || SkyforgeNativeInteriorPopulationStage.active()) {
            SkyforgeAutomatedAcceptanceHarness.fail(
                    event.getServer(),
                    "SF-IMP-0081 morphology carrier unexpectedly enabled cave/ecology mutation");
            return;
        }

        Optional<SurfaceEvidence> optional = scanLoadedSurface(level);
        if (optional.isEmpty()) {
            return;
        }
        SurfaceEvidence surface = optional.orElseThrow();
        if (!surface.valid()) {
            SkyforgeAutomatedAcceptanceHarness.fail(
                    event.getServer(),
                    "SF-IMP-0081 persisted production morphology surface evidence was incomplete: " + surface);
            return;
        }

        proofComplete = true;
        SkyforgeAutomatedAcceptanceHarness.completeServerCase(
                event.getServer(),
                java.util.Map.ofEntries(
                        java.util.Map.entry("memberId", MEMBER_ID),
                        java.util.Map.entry("geometrySeed", Long.toUnsignedString(GEOMETRY_SEED)),
                        java.util.Map.entry(
                                "translatedSuspension",
                                fixture.translatedDescriptor().suspensionElevation()),
                        java.util.Map.entry("certificateKind", fixture.exactSupport().certificateKind()),
                        java.util.Map.entry("minimumX", (int) fixture.exactSupport().bounds().minimumX()),
                        java.util.Map.entry("maximumX", (int) fixture.exactSupport().bounds().maximumX()),
                        java.util.Map.entry("minimumY", (int) fixture.exactSupport().bounds().minimumY()),
                        java.util.Map.entry("maximumY", (int) fixture.exactSupport().bounds().maximumY()),
                        java.util.Map.entry("minimumZ", (int) fixture.exactSupport().bounds().minimumZ()),
                        java.util.Map.entry("maximumZ", (int) fixture.exactSupport().bounds().maximumZ()),
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
                "SF-IMP-0081 PRODUCTION MORPHOLOGY MASSIF PASS: member=" + MEMBER_ID
                        + ", admission=" + admission.observedChunks() + "/" + admission.requiredChunks()
                        + ", exactBounds=" + fixture.exactSupport().bounds()
                        + ", surface=" + surface);
    }

    static Fixture fixture() {
        return FixtureHolder.FIXTURE;
    }

    private static final class FixtureHolder {
        private static final Fixture FIXTURE = buildFixture();
    }

    private static Fixture buildFixture() {
        ProviderMorphologySpec morphology = ProviderMorphologySpec.full(
                SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF));
        var compiler = new SkyIslandMorphologySpecCompiler();
        var registry = SkyIslandMorphologyProviders.builtInRegistry();
        SkyIslandTerrainProfile profile = SkyIslandTerrainProfile.reference();

        SkyIslandVolumeDescriptor sourceDescriptor = descriptor(SOURCE_SUSPENSION);
        var sourceCompilation = compiler.compileWithSupport(sourceDescriptor, morphology, registry);
        var sourceCertificate = sourceCompilation.supportEnvelope()
                .orElseThrow(() -> new IllegalStateException(
                        "AUTH-0083 built-in Massif unexpectedly lost its certified support envelope"));
        var sourceSupport = SkyforgeExactVoxelSupportBounds.derive(
                sourceCompilation.volume(),
                sourceCertificate,
                profile);

        int verticalTranslation = Math.subtractExact(
                TARGET_MINIMUM_SOLID_Y,
                toExactInt(sourceSupport.bounds().minimumY()));
        SkyIslandVolumeDescriptor translatedDescriptor =
                descriptor(SOURCE_SUSPENSION + verticalTranslation);
        var translatedCompilation =
                compiler.compileWithSupport(translatedDescriptor, morphology, registry);
        var translatedCertificate = translatedCompilation.supportEnvelope().orElseThrow();
        var translatedSupport = SkyforgeExactVoxelSupportBounds.derive(
                translatedCompilation.volume(),
                translatedCertificate,
                profile);

        requirePureIntegerTranslation(sourceSupport.bounds(), translatedSupport.bounds(), verticalTranslation);
        if (toExactInt(translatedSupport.bounds().minimumY()) != TARGET_MINIMUM_SOLID_Y
                || translatedSupport.bounds().maximumY() >= 320.0) {
            throw new IllegalStateException(
                    "translated AUTH-0083 Massif does not fit the Minecraft build interval: "
                            + translatedSupport.bounds());
        }

        SkyIslandWorldVolumeId volumeId =
                new SkyIslandWorldVolumeId(ROOT_SEED, "sf-imp-0081-morphology", 0, 0, GEOMETRY_SEED);
        SkyIslandWorldVolume worldVolume = new SkyIslandWorldVolume(
                volumeId,
                translatedSupport.bounds(),
                translatedCompilation.volume());
        SkyIslandWorldCatalog catalog = new SkyIslandWorldCatalog(ROOT_SEED, java.util.List.of(worldVolume));

        Set<Long> footprint = footprintChunkKeys(translatedSupport.bounds());
        for (long key : footprint) {
            if (Math.abs(ChunkPos.getX(key)) > 12 || Math.abs(ChunkPos.getZ(key)) > 12) {
                throw new IllegalStateException(
                        "first production morphology specimen exceeds the bounded acceptance harness: "
                                + new ChunkPos(ChunkPos.getX(key), ChunkPos.getZ(key)));
            }
        }

        return new Fixture(
                MEMBER_ID,
                morphology.stableIdentifier(),
                sourceDescriptor,
                translatedDescriptor,
                verticalTranslation,
                sourceSupport,
                translatedSupport,
                catalog,
                volumeId);
    }

    private static SkyIslandVolumeDescriptor descriptor(double suspensionElevation) {
        return new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                GEOMETRY_SEED,
                0.0,
                0.0,
                suspensionElevation,
                160.0,
                60.0,
                80.0,
                40.0,
                Math.PI / 6.0,
                0.65,
                0.60,
                0.25,
                0.0,
                20.0);
    }

    static Set<Long> footprintChunkKeys(WorldBounds bounds) {
        Objects.requireNonNull(bounds, "bounds");
        int minimumChunkX = Math.floorDiv((int) Math.floor(bounds.minimumX()), 16);
        int maximumChunkX = Math.floorDiv((int) Math.floor(bounds.maximumX()), 16);
        int minimumChunkZ = Math.floorDiv((int) Math.floor(bounds.minimumZ()), 16);
        int maximumChunkZ = Math.floorDiv((int) Math.floor(bounds.maximumZ()), 16);
        Set<Long> result = new LinkedHashSet<>();
        for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
            for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                result.add(new ChunkPos(chunkX, chunkZ).toLong());
            }
        }
        return Set.copyOf(result);
    }

    static Optional<SurfaceEvidence> scanLoadedSurface(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        Fixture fixture = fixture();
        WorldBounds bounds = fixture.exactSupport().bounds();
        int minimumX = toExactInt(bounds.minimumX());
        int maximumX = toExactInt(bounds.maximumX());
        int minimumZ = toExactInt(bounds.minimumZ());
        int maximumZ = toExactInt(bounds.maximumZ());
        SkyIslandTerrainInterpreter interpreter =
                new SkyIslandTerrainInterpreter(
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

    private static void requirePureIntegerTranslation(
            WorldBounds source,
            WorldBounds translated,
            int deltaY) {
        if (source.minimumX() != translated.minimumX()
                || source.maximumX() != translated.maximumX()
                || source.minimumZ() != translated.minimumZ()
                || source.maximumZ() != translated.maximumZ()
                || source.minimumY() + deltaY != translated.minimumY()
                || source.maximumY() + deltaY != translated.maximumY()) {
            throw new IllegalStateException(
                    "Minecraft translation changed AUTH-0083 discrete morphology support");
        }
    }

    private static int toExactInt(double value) {
        int integer = (int) value;
        if (integer != value) {
            throw new IllegalArgumentException("expected exact integer coordinate, found " + value);
        }
        return integer;
    }

    private static long mix(long digest, long value) {
        long mixed = digest;
        for (int shift = 0; shift < Long.SIZE; shift += Byte.SIZE) {
            mixed ^= (value >>> shift) & 0xffL;
            mixed *= 0x100000001b3L;
        }
        return mixed;
    }

    record Fixture(
            String memberId,
            String morphologyIdentifier,
            SkyIslandVolumeDescriptor sourceDescriptor,
            SkyIslandVolumeDescriptor translatedDescriptor,
            int verticalTranslation,
            SkyforgeExactVoxelSupportBounds.Result sourceSupport,
            SkyforgeExactVoxelSupportBounds.Result exactSupport,
            SkyIslandWorldCatalog catalog,
            SkyIslandWorldVolumeId volumeId) {
        Fixture {
            Objects.requireNonNull(memberId, "memberId");
            Objects.requireNonNull(morphologyIdentifier, "morphologyIdentifier");
            Objects.requireNonNull(sourceDescriptor, "sourceDescriptor");
            Objects.requireNonNull(translatedDescriptor, "translatedDescriptor");
            Objects.requireNonNull(sourceSupport, "sourceSupport");
            Objects.requireNonNull(exactSupport, "exactSupport");
            Objects.requireNonNull(catalog, "catalog");
            Objects.requireNonNull(volumeId, "volumeId");
        }
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
                throw new IllegalArgumentException("negative SF-IMP-0081 surface evidence");
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
