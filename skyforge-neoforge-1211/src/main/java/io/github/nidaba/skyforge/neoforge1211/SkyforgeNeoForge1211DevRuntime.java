package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.EnrichedProviderMorphologySkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyFamily;
import io.github.nidaba.skyforge.recipes.skyisland.ProviderMorphologyEnrichment;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviders;
import io.github.nidaba.skyforge.world.SkyIslandTerrainProfile;
import io.github.nidaba.skyforge.world.SkyIslandWorldCatalog;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import io.github.nidaba.skyforge.world.WorldBounds;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

/**
 * Explicitly development-only runtime used for the first visual Minecraft smoke test.
 *
 * <p>The production mod does not install a world binding by default. ModDevGradle's SF-IMP-0034
 * client run opts into this class with {@value #ENABLE_PROPERTY}. The specimen is intentionally
 * finite, deterministic, Overworld-only, and centered at a documented coordinate so a developer
 * can inspect it without introducing a premature user-facing configuration system.
 */
final class SkyforgeNeoForge1211DevRuntime {
    static final String ENABLE_PROPERTY = "skyforge.dev.specimen";
    static final long ROOT_SEED = 0x534b59464f524745L;
    static final int INSPECTION_X = 0;
    static final int INSPECTION_Y = 300;
    static final int INSPECTION_Z = 0;

    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211DevRuntime.class.getName());
    private static AutoCloseable persistentBinding;

    private SkyforgeNeoForge1211DevRuntime() {}

    /** Installs the specimen only when the development JVM property is enabled. */
    static synchronized void installFromSystemProperty() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY) || persistentBinding != null) {
            return;
        }
        if (SkyforgeNeoForge1211ChunkLifecycle.hasActiveBinding()) {
            throw new IllegalStateException(
                    "cannot install the Skyforge development specimen over an existing runtime binding");
        }

        persistentBinding = installSpecimen();
        LOGGER.log(
                System.Logger.Level.INFO,
                "Skyforge development specimen enabled. Create a NEW disposable Overworld and inspect near "
                        + "x=" + INSPECTION_X
                        + ", y=" + INSPECTION_Y
                        + ", z=" + INSPECTION_Z
                        + ".");
    }

    /** Installs one disposable specimen binding and returns the lifecycle cleanup handle. */
    static AutoCloseable installSpecimen() {
        return SkyforgeNeoForge1211ChunkLifecycle.install(
                SkyforgeNeoForge1211DevRuntime::isTargetLevel,
                adapter(),
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()));
    }

    static SkyforgeNeoForge1211ChunkAdapter adapter() {
        return new SkyforgeNeoForge1211ChunkAdapter(
                catalog(),
                SkyIslandTerrainProfile.reference(),
                new SkyforgeMinecraftBlockPalette());
    }

    static SkyIslandWorldCatalog catalog() {
        var compiled = compiledMassif();
        var id = new SkyIslandWorldVolumeId(ROOT_SEED, "sf-imp-0034-dev-massif", 0, 0, ROOT_SEED);
        var worldVolume = new SkyIslandWorldVolume(
                id,
                new WorldBounds(-160.0, 160.0, 96.0, 304.0, -160.0, 160.0),
                compiled);
        return new SkyIslandWorldCatalog(ROOT_SEED, List.of(worldVolume));
    }

    private static boolean isTargetLevel(LevelAccessor level) {
        return level instanceof ServerLevel serverLevel
                && Level.OVERWORLD.equals(serverLevel.dimension());
    }

    private static io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume compiledMassif() {
        SkyIslandVolumeDescriptor descriptor = new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                ROOT_SEED,
                0.0,
                0.0,
                224.0,
                96.0,
                42.0,
                72.0,
                28.0,
                Math.PI / 6.0,
                0.65,
                0.60,
                0.25,
                0.0,
                20.0);
        var provider = SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF);
        return new EnrichedProviderMorphologySkyIslandVolumeRecipe().compile(
                descriptor,
                new ProviderMorphologyEnrichment(provider, 0.0, 0.0),
                SkyIslandMorphologyProviders.builtInRegistry());
    }
}
