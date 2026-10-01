package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.SemanticSkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.world.SkyIslandCompiledVolumeColumnField;
import io.github.nidaba.skyforge.world.SkyIslandLocalPosition;
import io.github.nidaba.skyforge.world.SkyIslandRealizedCaveVolumeField;
import io.github.nidaba.skyforge.world.SkyIslandWorldCatalog;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import io.github.nidaba.skyforge.world.WorldBounds;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Same-X/Z stacked SF-IMP-0065 proof for authored cave realization. */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID)
final class SkyforgeNeoForge1211AuthoredCaveStackedDevRuntime {
    static final String ENABLE_PROPERTY = "skyforge.dev.authoredCaveStacked";

    private static final double LOWER_SUSPENSION_Y = 132.0;
    private static final double UPPER_SUSPENSION_Y = 232.0;
    private static final long LOWER_SEED = 650165L;
    private static final long UPPER_SEED = 650265L;
    private static final ChunkPos TARGET_CHUNK = new ChunkPos(0, 0);
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211AuthoredCaveStackedDevRuntime.class.getName());

    private static final Fixture FIXTURE = fixture();
    private static AutoCloseable persistentTerrainBinding;
    private static boolean proofStarted;
    private static boolean proofComplete;

    private SkyforgeNeoForge1211AuthoredCaveStackedDevRuntime() {}

    static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    static synchronized void installFromSystemProperty() {
        if (!enabled() || persistentTerrainBinding != null) {
            return;
        }
        if (SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()
                || SkyforgePhysicalVolumeAdmissionStage.active()) {
            throw new IllegalStateException("SF-IMP-0065 stacked proof requires isolated terrain binding");
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
        if (!enabled() || proofStarted || proofComplete) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (!level.dimension().equals(Level.OVERWORLD)) {
                continue;
            }
            LevelChunk chunk = level.getChunkSource().getChunkNow(TARGET_CHUNK.x, TARGET_CHUNK.z);
            if (chunk != null) {
                prove(level, chunk);
            }
        }
    }

    private static synchronized void prove(ServerLevel level, LevelChunk chunk) {
        if (proofStarted || proofComplete) {
            return;
        }
        proofStarted = true;

        BlockPos lowerCenter = chamberCenter(FIXTURE.lower());
        BlockPos upperCenter = chamberCenter(FIXTURE.upper());
        if (lowerCenter.getX() != upperCenter.getX()
                || lowerCenter.getZ() != upperCenter.getZ()
                || lowerCenter.getY() == upperCenter.getY()) {
            throw new IllegalStateException(
                    "SF-IMP-0065 stacked authored cave centers do not share X/Z with distinct Y: lower="
                            + lowerCenter + ", upper=" + upperCenter);
        }

        if (level.getBlockState(lowerCenter).isAir() || level.getBlockState(upperCenter).isAir()) {
            throw new IllegalStateException(
                    "SF-IMP-0065 stacked authored cave center already AIR before realization");
        }

        var lowerResult = SkyforgeAuthoredCaveRealizer.realize(
                level, FIXTURE.lower(), FIXTURE.caveField(), chunk);
        if (!lowerResult.sealedAndAccepted()
                || lowerResult.positiveAuthoredSamples() <= 0
                || lowerResult.changedBlocks() <= 0
                || lowerResult.unsafePositiveSamples() != 0) {
            throw new IllegalStateException("SF-IMP-0065 stacked lower cave realization failed: " + lowerResult);
        }
        if (!level.getBlockState(lowerCenter).isAir()) {
            throw new IllegalStateException("SF-IMP-0065 stacked lower chamber center did not become AIR");
        }
        if (level.getBlockState(upperCenter).isAir()) {
            throw new IllegalStateException(
                    "SF-IMP-0065 lower authored cave contaminated vertically stacked upper volume");
        }

        var upperResult = SkyforgeAuthoredCaveRealizer.realize(
                level, FIXTURE.upper(), FIXTURE.caveField(), chunk);
        if (!upperResult.sealedAndAccepted()
                || upperResult.positiveAuthoredSamples() <= 0
                || upperResult.changedBlocks() <= 0
                || upperResult.unsafePositiveSamples() != 0) {
            throw new IllegalStateException("SF-IMP-0065 stacked upper cave realization failed: " + upperResult);
        }
        if (!level.getBlockState(upperCenter).isAir() || !level.getBlockState(lowerCenter).isAir()) {
            throw new IllegalStateException(
                    "SF-IMP-0065 stacked authored caves did not persist independently after both realizations");
        }

        proofComplete = true;
        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0065 AUTHORED CAVE STACKED PASS: lowerCenter=" + lowerCenter
                        + ", upperCenter=" + upperCenter
                        + ", lowerChanged=" + lowerResult.changedBlocks()
                        + ", upperChanged=" + upperResult.changedBlocks()
                        + ", lowerPositive=" + lowerResult.positiveAuthoredSamples()
                        + ", upperPositive=" + upperResult.positiveAuthoredSamples()
                        + ", unsafeLower=0, unsafeUpper=0"
                        + ", sameXZIndependent=true, foreignVolumePreserved=true.");

        SkyforgeAutomatedAcceptanceHarness.completeServerCase(
                level.getServer(),
                java.util.Map.ofEntries(
                        java.util.Map.entry("lowerCenterY", lowerCenter.getY()),
                        java.util.Map.entry("upperCenterY", upperCenter.getY()),
                        java.util.Map.entry("lowerChanged", lowerResult.changedBlocks()),
                        java.util.Map.entry("upperChanged", upperResult.changedBlocks()),
                        java.util.Map.entry("lowerPositive", lowerResult.positiveAuthoredSamples()),
                        java.util.Map.entry("upperPositive", upperResult.positiveAuthoredSamples()),
                        java.util.Map.entry("unsafeLower", lowerResult.unsafePositiveSamples()),
                        java.util.Map.entry("unsafeUpper", upperResult.unsafePositiveSamples()),
                        java.util.Map.entry("sameXZIndependent", true),
                        java.util.Map.entry("foreignVolumePreserved", true)));
    }

    private static BlockPos chamberCenter(SkyIslandWorldVolume volume) {
        var realized = new SkyIslandRealizedCaveVolumeField(
                FIXTURE.caveField(),
                new SkyIslandCompiledVolumeColumnField(volume.compiledVolume()));
        var physical = realized.transform()
                .toPhysical(FIXTURE.chamber().center())
                .orElseThrow(() -> new IllegalStateException(
                        "SF-IMP-0065 stacked chamber center has no physical realization"));
        return new BlockPos(
                (int) Math.round(volume.compiledVolume().descriptor().centerX()
                        + physical.horizontalPosition().x()),
                (int) Math.round(physical.physicalY()),
                (int) Math.round(volume.compiledVolume().descriptor().centerZ()
                        + physical.horizontalPosition().z()));
    }

    private static Fixture fixture() {
        var base = SkyforgeNeoForge1211AuthoredCaveDevRuntime.fixtureDefinition();
        double radius = base.authoredDescriptor().nominalRadius();
        double centerX = -base.chamber().center().x();
        double centerZ = -base.chamber().center().z();

        var lower = volume(
                base,
                radius,
                centerX,
                centerZ,
                LOWER_SUSPENSION_Y,
                LOWER_SEED,
                "sf-imp-0065-authored-cave-stacked/lower");
        var upper = volume(
                base,
                radius,
                centerX,
                centerZ,
                UPPER_SUSPENSION_Y,
                UPPER_SEED,
                "sf-imp-0065-authored-cave-stacked/upper");
        var catalog = new SkyIslandWorldCatalog(
                base.catalog().rootSeed(),
                List.of(lower, upper));
        return new Fixture(base.caveField(), base.chamber(), lower, upper, catalog);
    }

    private static SkyIslandWorldVolume volume(
            SkyforgeNeoForge1211AuthoredCaveDevRuntime.Fixture base,
            double radius,
            double centerX,
            double centerZ,
            double suspensionY,
            long seed,
            String path) {
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
                base.authoredDescriptor().morphologyFamily(),
                0.10,
                28.0,
                0.18);
        var compiled = new SemanticSkyIslandVolumeRecipe().compile(descriptor);
        var id = new SkyIslandWorldVolumeId(
                base.catalog().rootSeed(),
                path,
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
            io.github.nidaba.skyforge.world.SkyIslandCaveVolumeField caveField,
            io.github.nidaba.skyforge.world.SkyIslandCaveChamberGeometry chamber,
            SkyIslandWorldVolume lower,
            SkyIslandWorldVolume upper,
            SkyIslandWorldCatalog catalog) {}
}
