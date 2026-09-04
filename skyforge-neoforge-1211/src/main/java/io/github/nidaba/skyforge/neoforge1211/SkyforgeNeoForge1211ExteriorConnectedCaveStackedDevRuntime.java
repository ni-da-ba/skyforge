package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.SemanticSkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.world.SkyIslandCompiledVolumeColumnField;
import io.github.nidaba.skyforge.world.SkyIslandRealizedExteriorConnectedCaveVolumeField;
import io.github.nidaba.skyforge.world.SkyIslandWorldCatalog;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import io.github.nidaba.skyforge.world.WorldBounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Same-X/Z stacked SF-IMP-0066 proof for AUTH-0030 exterior-connected caves. */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID)
final class SkyforgeNeoForge1211ExteriorConnectedCaveStackedDevRuntime {
    static final String ENABLE_PROPERTY = "skyforge.dev.exteriorConnectedCaveStacked";

    private static final double LOWER_SUSPENSION_Y = 132.0;
    private static final double UPPER_SUSPENSION_Y = 232.0;
    private static final long LOWER_SEED = 660166L;
    private static final long UPPER_SEED = 660266L;
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211ExteriorConnectedCaveStackedDevRuntime.class.getName());

    private static final Fixture FIXTURE = fixture();
    private static AutoCloseable persistentTerrainBinding;
    private static boolean proofComplete;

    private SkyforgeNeoForge1211ExteriorConnectedCaveStackedDevRuntime() {}

    static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    static synchronized void installFromSystemProperty() {
        if (!enabled() || persistentTerrainBinding != null) {
            return;
        }
        if (SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()
                || SkyforgePhysicalVolumeAdmissionStage.active()) {
            throw new IllegalStateException("SF-IMP-0066 stacked proof requires isolated terrain binding");
        }
        persistentTerrainBinding = SkyforgeNeoForge1211SurfaceStage.install(
                new SkyforgeNeoForge1211ChunkAdapter(
                        FIXTURE.catalog(),
                        io.github.nidaba.skyforge.world.SkyIslandTerrainProfile.reference(),
                        new SkyforgeMinecraftBlockPalette()),
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()));
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (!enabled() || proofComplete) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (level.dimension().equals(Level.OVERWORLD)) {
                observeLoaded(level);
            }
        }
    }

    private static synchronized void observeLoaded(ServerLevel level) {
        if (proofComplete) {
            return;
        }
        List<LevelChunk> chunks = loadedProofChunks(level);
        if (chunks.isEmpty()) {
            return;
        }
        prove(level, chunks);
    }

    private static void prove(
            ServerLevel level,
            List<LevelChunk> chunks) {
        BlockPos upperCaveAnchor = caveAnchor(FIXTURE.upper());
        if (level.getBlockState(upperCaveAnchor).isAir()) {
            throw new IllegalStateException(
                    "SF-IMP-0066 stacked upper cave anchor was AIR before lower realization");
        }

        Aggregate lower = realize(level, FIXTURE.lower(), chunks);
        if (!lower.valid()) {
            throw new IllegalStateException("SF-IMP-0066 stacked lower exterior cave failed: " + lower);
        }
        if (!level.getBlockState(lower.mouth()).isAir()
                || !level.getBlockState(lower.outward()).isAir()) {
            throw new IllegalStateException("SF-IMP-0066 stacked lower mouth did not reach exterior AIR");
        }
        if (level.getBlockState(upperCaveAnchor).isAir()) {
            throw new IllegalStateException(
                    "SF-IMP-0066 lower realization contaminated vertically stacked upper volume");
        }

        Aggregate upper = realize(level, FIXTURE.upper(), chunks);
        if (!upper.valid()) {
            throw new IllegalStateException("SF-IMP-0066 stacked upper exterior cave failed: " + upper);
        }
        if (!level.getBlockState(upper.mouth()).isAir()
                || !level.getBlockState(upper.outward()).isAir()) {
            throw new IllegalStateException("SF-IMP-0066 stacked upper mouth did not reach exterior AIR");
        }
        if (!level.getBlockState(lower.mouth()).isAir()) {
            throw new IllegalStateException(
                    "SF-IMP-0066 upper realization damaged persisted lower authored mouth");
        }
        var lowerMouthSample = mouthSample(FIXTURE.lower(), lower.mouth());
        var upperMouthSample = mouthSample(FIXTURE.upper(), upper.mouth());
        if (lower.mouth().getY() == upper.mouth().getY()
                || lowerMouthSample.sourceKind()
                        != io.github.nidaba.skyforge.world.SkyIslandExteriorConnectedCaveVolumeSample.SourceKind.EXPOSURE_CONNECTION
                || upperMouthSample.sourceKind()
                        != io.github.nidaba.skyforge.world.SkyIslandExteriorConnectedCaveVolumeSample.SourceKind.EXPOSURE_CONNECTION
                || lowerMouthSample.exposureSide() != upperMouthSample.exposureSide()
                || lowerMouthSample.exposureSide() != FIXTURE.base().connection().side()) {
            throw new IllegalStateException(
                    "SF-IMP-0066 stacked mouths lost common AUTH-0030 exposure provenance: lower="
                            + lower.mouth() + " -> " + lowerMouthSample
                            + ", upper=" + upper.mouth() + " -> " + upperMouthSample);
        }
        int horizontalMouthOffset = Math.abs(lower.mouth().getX() - upper.mouth().getX())
                + Math.abs(lower.mouth().getZ() - upper.mouth().getZ());

        proofComplete = true;
        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0066 EXTERIOR CAVE STACKED PASS: lowerMouth=" + lower.mouth()
                        + ", upperMouth=" + upper.mouth()
                        + ", lowerChanged=" + lower.changed()
                        + ", upperChanged=" + upper.changed()
                        + ", lowerExposure=" + lower.exposure()
                        + ", upperExposure=" + upper.exposure()
                        + ", unsafeLower=0, unsafeUpper=0"
                        + ", horizontalMouthOffset=" + horizontalMouthOffset
                        + ", sameAuthoredExposure=true"
                        + ", stackedXZDomainIndependent=true, foreignVolumePreserved=true.");

        SkyforgeAutomatedAcceptanceHarness.completeServerCase(
                level.getServer(),
                java.util.Map.ofEntries(
                        java.util.Map.entry("lowerMouthY", lower.mouth().getY()),
                        java.util.Map.entry("upperMouthY", upper.mouth().getY()),
                        java.util.Map.entry("lowerChanged", lower.changed()),
                        java.util.Map.entry("upperChanged", upper.changed()),
                        java.util.Map.entry("lowerExposure", lower.exposure()),
                        java.util.Map.entry("upperExposure", upper.exposure()),
                        java.util.Map.entry("unsafeLower", 0),
                        java.util.Map.entry("unsafeUpper", 0),
                        java.util.Map.entry("horizontalMouthOffset", horizontalMouthOffset),
                        java.util.Map.entry("sameAuthoredExposure", true),
                        java.util.Map.entry("sameXZIndependent", true),
                        java.util.Map.entry("foreignVolumePreserved", true)));
    }

    private static Aggregate realize(
            ServerLevel level,
            SkyIslandWorldVolume volume,
            List<LevelChunk> chunks) {
        int changed = 0;
        int base = 0;
        int exposure = 0;
        int mouthCells = 0;
        int unsafe = 0;
        BlockPos mouth = null;
        io.github.nidaba.skyforge.world.SkyIslandCaveExposureSide side = null;

        for (LevelChunk chunk : chunks) {
            var result = SkyforgeExteriorConnectedCaveRealizer.realize(
                    level,
                    volume,
                    FIXTURE.base().field(),
                    chunk);
            changed = Math.addExact(changed, result.changedBlocks());
            base = Math.addExact(base, result.basePositiveSamples());
            exposure = Math.addExact(exposure, result.exposurePositiveSamples());
            mouthCells = Math.addExact(mouthCells, result.mouthCells());
            unsafe = Math.addExact(unsafe, result.unsafePositiveSamples());
            if (mouth == null && result.firstMouthPosition() != null) {
                mouth = result.firstMouthPosition();
                side = result.firstMouthSide();
            }
        }

        BlockPos outward = mouth == null || side == null
                ? null
                : side == io.github.nidaba.skyforge.world.SkyIslandCaveExposureSide.UPPER_SURFACE
                        ? mouth.above()
                        : mouth.below();
        return new Aggregate(
                changed,
                base,
                exposure,
                mouthCells,
                unsafe,
                mouth,
                outward);
    }

    private static io.github.nidaba.skyforge.world.SkyIslandExteriorConnectedCaveVolumeSample mouthSample(
            SkyIslandWorldVolume volume,
            BlockPos mouth) {
        var realized = new SkyIslandRealizedExteriorConnectedCaveVolumeField(
                FIXTURE.base().field(),
                new SkyIslandCompiledVolumeColumnField(volume.compiledVolume()));
        var descriptor = volume.compiledVolume().descriptor();
        var local = new io.github.nidaba.skyforge.world.SkyIslandLocalPosition(
                mouth.getX() - descriptor.centerX(),
                mouth.getZ() - descriptor.centerZ());
        return realized.sample(
                new io.github.nidaba.skyforge.world.SkyIslandRealizedSubsurfacePosition(
                        local,
                        mouth.getY()));
    }

    private static BlockPos caveAnchor(SkyIslandWorldVolume volume) {
        var realized = new SkyIslandRealizedExteriorConnectedCaveVolumeField(
                FIXTURE.base().field(),
                new SkyIslandCompiledVolumeColumnField(volume.compiledVolume()));
        var physical = realized.transform()
                .toPhysical(FIXTURE.base().connection().caveSidePoint().position())
                .orElseThrow(() -> new IllegalStateException(
                        "SF-IMP-0066 stacked cave anchor has no physical realization"));
        return new BlockPos(
                (int) Math.round(volume.compiledVolume().descriptor().centerX()
                        + physical.horizontalPosition().x()),
                (int) Math.round(physical.physicalY()),
                (int) Math.round(volume.compiledVolume().descriptor().centerZ()
                        + physical.horizontalPosition().z()));
    }

    private static List<LevelChunk> loadedProofChunks(ServerLevel level) {
        List<LevelChunk> result = new ArrayList<>();
        for (long packed : FIXTURE.base().proofChunks()) {
            var pos = new net.minecraft.world.level.ChunkPos(packed);
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
            if (chunk == null) {
                return List.of();
            }
            result.add(chunk);
        }
        return List.copyOf(result);
    }

    private static Fixture fixture() {
        var base = SkyforgeNeoForge1211ExteriorConnectedCaveDevRuntime.fixtureDefinition();
        double centerX = base.volume().compiledVolume().descriptor().centerX();
        double centerZ = base.volume().compiledVolume().descriptor().centerZ();
        double radius = base.descriptor().nominalRadius();

        SkyIslandWorldVolume lower = volume(
                base, centerX, centerZ, radius, LOWER_SUSPENSION_Y, LOWER_SEED, "lower");
        SkyIslandWorldVolume upper = volume(
                base, centerX, centerZ, radius, UPPER_SUSPENSION_Y, UPPER_SEED, "upper");
        SkyIslandWorldCatalog catalog =
                new SkyIslandWorldCatalog(base.catalog().rootSeed(), List.of(lower, upper));
        return new Fixture(base, lower, upper, catalog);
    }

    private static SkyIslandWorldVolume volume(
            SkyforgeNeoForge1211ExteriorConnectedCaveDevRuntime.Fixture base,
            double centerX,
            double centerZ,
            double radius,
            double suspensionY,
            long seed,
            String suffix) {
        SkyIslandVolumeDescriptor descriptor = SkyIslandVolumeDescriptor.schema2(
                seed,
                centerX,
                centerZ,
                suspensionY,
                radius,
                30.0,
                38.0,
                Math.min(48.0, radius * 0.16),
                0.0,
                0.24,
                0.62,
                0.0,
                base.descriptor().morphologyFamily(),
                0.10,
                28.0,
                0.18);
        var compiled = new SemanticSkyIslandVolumeRecipe().compile(descriptor);
        var id = new SkyIslandWorldVolumeId(
                base.catalog().rootSeed(),
                "sf-imp-0066-exterior-connected-cave-stacked/" + suffix,
                0,
                0,
                seed);
        var bounds = new WorldBounds(
                centerX - radius * 1.08,
                centerX + radius * 1.08,
                suspensionY - 55.0,
                suspensionY + 45.0,
                centerZ - radius * 1.08,
                centerZ + radius * 1.08);
        return new SkyIslandWorldVolume(id, bounds, compiled);
    }

    private record Fixture(
            SkyforgeNeoForge1211ExteriorConnectedCaveDevRuntime.Fixture base,
            SkyIslandWorldVolume lower,
            SkyIslandWorldVolume upper,
            SkyIslandWorldCatalog catalog) {}

    private record Aggregate(
            int changed,
            int base,
            int exposure,
            int mouthCells,
            int unsafe,
            BlockPos mouth,
            BlockPos outward) {
        boolean valid() {
            return changed > 0
                    && base > 0
                    && exposure > 0
                    && mouthCells > 0
                    && unsafe == 0
                    && mouth != null
                    && outward != null;
        }
    }
}
