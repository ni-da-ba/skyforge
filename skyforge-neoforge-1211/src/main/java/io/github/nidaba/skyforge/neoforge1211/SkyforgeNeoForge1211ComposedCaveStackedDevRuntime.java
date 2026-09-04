package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.SemanticSkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.world.SkyIslandCompiledVolumeColumnField;
import io.github.nidaba.skyforge.world.SkyIslandExteriorConnectedCaveVolumeField;
import io.github.nidaba.skyforge.world.SkyIslandLocalPosition;
import io.github.nidaba.skyforge.world.SkyIslandRealizedExteriorConnectedCaveVolumeField;
import io.github.nidaba.skyforge.world.SkyIslandRealizedSubsurfacePosition;
import io.github.nidaba.skyforge.world.SkyIslandWorldCatalog;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import io.github.nidaba.skyforge.world.WorldBounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Same-X/Z stacked proof for SF-IMP-0067 native-first/authored-last cave composition. */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID)
final class SkyforgeNeoForge1211ComposedCaveStackedDevRuntime {
    static final String ENABLE_PROPERTY = "skyforge.dev.composedCaveStacked";

    private static final ChunkPos TARGET_CHUNK = new ChunkPos(-2, -2);
    private static final int INTERIOR_MARGIN = 8;
    private static final double LOWER_SUSPENSION_Y = 132.0;
    private static final double UPPER_SUSPENSION_Y = 232.0;
    private static final long LOWER_SEED = 670167L;
    private static final long UPPER_SEED = 670267L;
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211ComposedCaveStackedDevRuntime.class.getName());

    private static final Fixture FIXTURE = fixture();
    private static AutoCloseable persistentTerrainBinding;
    private static boolean proofComplete;

    private SkyforgeNeoForge1211ComposedCaveStackedDevRuntime() {}

    static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    static Fixture fixtureDefinition() {
        return FIXTURE;
    }

    static synchronized void installFromSystemProperty() {
        if (!enabled() || persistentTerrainBinding != null) {
            return;
        }
        if (SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()
                || SkyforgePhysicalVolumeAdmissionStage.active()) {
            throw new IllegalStateException("SF-IMP-0067 stacked proof requires isolated terrain binding");
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
            if (!level.dimension().equals(Level.OVERWORLD)) {
                continue;
            }
            LevelChunk chunk = level.getChunkSource().getChunkNow(TARGET_CHUNK.x, TARGET_CHUNK.z);
            if (chunk != null) {
                prove(level, chunk);
            }
        }
    }

    private static synchronized void prove(
            ServerLevel level,
            LevelChunk chunk) {
        if (proofComplete) {
            return;
        }
        if (!(level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator generator)) {
            throw new IllegalStateException("SF-IMP-0067 stacked proof requires noise generator");
        }

        BlockPos lowerAuthoredAnchor =
                firstDiscreteAuthoredPositive(FIXTURE.lower(), FIXTURE.base().field(), chunk);
        BlockPos upperAuthoredAnchor =
                firstDiscreteAuthoredPositive(FIXTURE.upper(), FIXTURE.base().field(), chunk);
        if (lowerAuthoredAnchor == null || upperAuthoredAnchor == null
                || lowerAuthoredAnchor.getY() == upperAuthoredAnchor.getY()) {
            throw new IllegalStateException(
                    "SF-IMP-0067 stacked proof could not resolve distinct discrete AUTH-0030 anchors");
        }
        if (level.getBlockState(lowerAuthoredAnchor).isAir()
                || level.getBlockState(upperAuthoredAnchor).isAir()) {
            throw new IllegalStateException(
                    "SF-IMP-0067 stacked discrete authored anchors were AIR before composition");
        }

        Aggregate lower = compose(level, generator, FIXTURE.lower(), chunk);
        if (!lower.valid()) {
            throw new IllegalStateException("SF-IMP-0067 lower stacked composition failed: " + lower);
        }
        if (!level.getBlockState(lower.nativeOnly()).isAir()
                || !level.getBlockState(lowerAuthoredAnchor).isAir()) {
            throw new IllegalStateException(
                    "SF-IMP-0067 lower union did not retain native-only and authored AIR");
        }
        if (level.getBlockState(upperAuthoredAnchor).isAir()) {
            throw new IllegalStateException(
                    "SF-IMP-0067 lower composition contaminated vertically stacked upper volume");
        }

        Aggregate upper = compose(level, generator, FIXTURE.upper(), chunk);
        if (!upper.valid()) {
            throw new IllegalStateException("SF-IMP-0067 upper stacked composition failed: " + upper);
        }
        if (!level.getBlockState(upper.nativeOnly()).isAir()
                || !level.getBlockState(upperAuthoredAnchor).isAir()) {
            throw new IllegalStateException(
                    "SF-IMP-0067 upper union did not retain native-only and authored AIR");
        }
        if (!level.getBlockState(lower.nativeOnly()).isAir()
                || !level.getBlockState(lowerAuthoredAnchor).isAir()) {
            throw new IllegalStateException(
                    "SF-IMP-0067 upper composition damaged persisted lower union");
        }

        proofComplete = true;
        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0067 COMPOSED CAVE STACKED PASS: targetChunk=" + TARGET_CHUNK
                        + ", lowerNativeChanged=" + lower.nativeChanged()
                        + ", upperNativeChanged=" + upper.nativeChanged()
                        + ", lowerNativeOnlyAir=" + lower.nativeOnlyAir()
                        + ", upperNativeOnlyAir=" + upper.nativeOnlyAir()
                        + ", lowerAuthoredPositive=" + lower.authoredPositive()
                        + ", upperAuthoredPositive=" + upper.authoredPositive()
                        + ", lowerAnchor=" + lowerAuthoredAnchor
                        + ", upperAnchor=" + upperAuthoredAnchor
                        + ", unsafeLower=0, unsafeUpper=0"
                        + ", discreteAuthoredAnchors=true"
                        + ", stackedXZDomainIndependent=true, foreignVolumePreserved=true.");

        SkyforgeAutomatedAcceptanceHarness.completeServerCase(
                level.getServer(),
                java.util.Map.ofEntries(
                        java.util.Map.entry("targetChunk", TARGET_CHUNK.toLong()),
                        java.util.Map.entry("lowerNativeChanged", lower.nativeChanged()),
                        java.util.Map.entry("upperNativeChanged", upper.nativeChanged()),
                        java.util.Map.entry("lowerNativeOnlyAir", lower.nativeOnlyAir()),
                        java.util.Map.entry("upperNativeOnlyAir", upper.nativeOnlyAir()),
                        java.util.Map.entry("lowerAuthoredPositive", lower.authoredPositive()),
                        java.util.Map.entry("upperAuthoredPositive", upper.authoredPositive()),
                        java.util.Map.entry("lowerAnchorY", lowerAuthoredAnchor.getY()),
                        java.util.Map.entry("upperAnchorY", upperAuthoredAnchor.getY()),
                        java.util.Map.entry("unsafeLower", 0),
                        java.util.Map.entry("unsafeUpper", 0),
                        java.util.Map.entry("sameXZIndependent", true),
                        java.util.Map.entry("discreteAuthoredAnchors", true),
                        java.util.Map.entry("foreignVolumePreserved", true)));
    }

    private static Aggregate compose(
            ServerLevel level,
            NoiseBasedChunkGenerator generator,
            SkyIslandWorldVolume volume,
            LevelChunk chunk) {
        OwnerSpan span = widestOwnerSpan(volume, chunk);
        if (span == null
                || span.maximumY() - span.minimumY() <= INTERIOR_MARGIN * 2) {
            return Aggregate.invalid();
        }

        List<Long> outsideAuthored = captureInitialOwnerOutsideAuthored(
                level, volume, FIXTURE.base().field(), chunk);
        var resolver = (SkyforgeExactVolumeBiomeResolver) (candidateId, x, y, z) -> Biomes.TAIGA;
        var result = SkyforgeComposedCaveRealizer.realize(
                level,
                generator,
                resolver,
                volume,
                FIXTURE.base().field(),
                chunk,
                new BlockPos(span.x(), (span.minimumY() + span.maximumY()) / 2, span.z()),
                span.minimumY() + INTERIOR_MARGIN,
                span.maximumY() - INTERIOR_MARGIN);

        int nativeOnlyAir = 0;
        BlockPos nativeOnly = null;
        for (long packed : outsideAuthored) {
            BlockPos position = BlockPos.of(packed);
            if (level.getBlockState(position).isAir()) {
                nativeOnlyAir++;
                if (nativeOnly == null) {
                    nativeOnly = position.immutable();
                }
            }
        }

        return new Aggregate(
                result.nativeResult().changedBlocks(),
                nativeOnlyAir,
                result.authoredResult().positiveSamples(),
                result.authoredResult().unsafePositiveSamples(),
                nativeOnly);
    }

    private static BlockPos firstDiscreteAuthoredPositive(
            SkyIslandWorldVolume volume,
            SkyIslandExteriorConnectedCaveVolumeField field,
            LevelChunk chunk) {
        int minimumY = Math.max(
                chunk.getMinBuildHeight(),
                (int) Math.ceil(volume.bounds().minimumY()));
        int maximumY = Math.min(
                chunk.getMaxBuildHeight() - 1,
                (int) Math.floor(volume.bounds().maximumY()));
        var realized = new SkyIslandRealizedExteriorConnectedCaveVolumeField(
                field,
                new SkyIslandCompiledVolumeColumnField(volume.compiledVolume()));
        var descriptor = volume.compiledVolume().descriptor();

        for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++) {
            for (int z = chunk.getPos().getMinBlockZ(); z <= chunk.getPos().getMaxBlockZ(); z++) {
                var local = new SkyIslandLocalPosition(
                        x - descriptor.centerX(),
                        z - descriptor.centerZ());
                for (int y = minimumY; y <= maximumY; y++) {
                    if (!ownerSolid(volume, x, y, z)) {
                        continue;
                    }
                    var sample = realized.sample(
                            new SkyIslandRealizedSubsurfacePosition(local, y));
                    if (sample.inside()) {
                        return new BlockPos(x, y, z);
                    }
                }
            }
        }
        return null;
    }

    private static OwnerSpan widestOwnerSpan(
            SkyIslandWorldVolume volume,
            LevelChunk chunk) {
        int minimumY = Math.max(chunk.getMinBuildHeight(), (int) Math.ceil(volume.bounds().minimumY()));
        int maximumY = Math.min(chunk.getMaxBuildHeight() - 1, (int) Math.floor(volume.bounds().maximumY()));
        OwnerSpan best = null;
        for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++) {
            for (int z = chunk.getPos().getMinBlockZ(); z <= chunk.getPos().getMaxBlockZ(); z++) {
                int first = Integer.MAX_VALUE;
                int last = Integer.MIN_VALUE;
                for (int y = minimumY; y <= maximumY; y++) {
                    if (ownerSolid(volume, x, y, z)) {
                        if (first == Integer.MAX_VALUE) {
                            first = y;
                        }
                        last = y;
                    }
                }
                if (first == Integer.MAX_VALUE) {
                    continue;
                }
                OwnerSpan candidate = new OwnerSpan(x, z, first, last);
                if (best == null
                        || candidate.maximumY() - candidate.minimumY()
                                > best.maximumY() - best.minimumY()) {
                    best = candidate;
                }
            }
        }
        return best;
    }

    private static List<Long> captureInitialOwnerOutsideAuthored(
            ServerLevel level,
            SkyIslandWorldVolume volume,
            SkyIslandExteriorConnectedCaveVolumeField field,
            LevelChunk chunk) {
        int minimumY = Math.max(chunk.getMinBuildHeight(), (int) Math.ceil(volume.bounds().minimumY()));
        int maximumY = Math.min(chunk.getMaxBuildHeight() - 1, (int) Math.floor(volume.bounds().maximumY()));
        var realized = new SkyIslandRealizedExteriorConnectedCaveVolumeField(
                field,
                new SkyIslandCompiledVolumeColumnField(volume.compiledVolume()));
        var descriptor = volume.compiledVolume().descriptor();
        List<Long> result = new ArrayList<>();

        for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++) {
            for (int z = chunk.getPos().getMinBlockZ(); z <= chunk.getPos().getMaxBlockZ(); z++) {
                var local = new SkyIslandLocalPosition(
                        x - descriptor.centerX(),
                        z - descriptor.centerZ());
                for (int y = minimumY; y <= maximumY; y++) {
                    if (!ownerSolid(volume, x, y, z)) {
                        continue;
                    }
                    BlockPos position = new BlockPos(x, y, z);
                    if (level.getBlockState(position).isAir()) {
                        continue;
                    }
                    if (!realized.sample(new SkyIslandRealizedSubsurfacePosition(local, y)).inside()) {
                        result.add(position.asLong());
                    }
                }
            }
        }
        return List.copyOf(result);
    }

    private static boolean ownerSolid(
            SkyIslandWorldVolume volume,
            int x,
            int y,
            int z) {
        return SkyforgeNeoForge1211SurfaceStage.isSolidOwnedBy(volume.id(), x, y, z)
                .orElseThrow();
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
        return new Fixture(
                base,
                lower,
                upper,
                new SkyIslandWorldCatalog(base.catalog().rootSeed(), List.of(lower, upper)));
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
                "sf-imp-0067-composed-cave-stacked/" + suffix,
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

    record Fixture(
            SkyforgeNeoForge1211ExteriorConnectedCaveDevRuntime.Fixture base,
            SkyIslandWorldVolume lower,
            SkyIslandWorldVolume upper,
            SkyIslandWorldCatalog catalog) {}

    private record OwnerSpan(int x, int z, int minimumY, int maximumY) {}

    private record Aggregate(
            int nativeChanged,
            int nativeOnlyAir,
            int authoredPositive,
            int authoredUnsafe,
            BlockPos nativeOnly) {
        static Aggregate invalid() {
            return new Aggregate(0, 0, 0, 1, null);
        }

        boolean valid() {
            return nativeChanged > 0
                    && nativeOnlyAir > 0
                    && authoredPositive > 0
                    && authoredUnsafe == 0
                    && nativeOnly != null;
        }
    }
}
