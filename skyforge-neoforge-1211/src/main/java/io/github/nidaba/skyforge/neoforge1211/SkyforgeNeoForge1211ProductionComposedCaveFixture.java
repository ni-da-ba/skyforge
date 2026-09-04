package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.SemanticSkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandExteriorConnectedCaveVolumeField;
import io.github.nidaba.skyforge.world.SkyIslandWorldCatalog;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import io.github.nidaba.skyforge.world.WorldBounds;
import java.util.List;

/**
 * Compact accepted AUTH-0030 physical fixtures used only by SF-IMP-0068 production acceptance.
 *
 * <p>Key 3670 is one of the two AUTH-0030 canonical representatives with an accepted exposure
 * connection. Unlike the large key-653 implementation specimen, its nominal radius is small enough
 * to exercise the complete physical-admission/cave obligation footprint in bounded CI.
 */
final class SkyforgeNeoForge1211ProductionComposedCaveFixture {
    private static final long WORLD_SEED = 0x534B59464F524745L;
    private static final long PROVINCE_KEY = 8L;
    private static final long CLUSTER_KEY = 81L;
    private static final long ISLAND_KEY = 3670L;
    private static final long SINGLE_PHYSICAL_SEED = 680068L;
    private static final long LOWER_PHYSICAL_SEED = 680168L;
    private static final long UPPER_PHYSICAL_SEED = 680268L;
    private static final double SINGLE_SUSPENSION_Y = 220.0;
    private static final double LOWER_SUSPENSION_Y = 170.0;
    private static final double UPPER_SUSPENSION_Y = 260.0;

    private static final io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor DESCRIPTOR =
            SkyIslandDescriptorGenerator.derive(
                    SkyIslandIdentity.of(WORLD_SEED, PROVINCE_KEY, CLUSTER_KEY, ISLAND_KEY));
    private static final SkyIslandExteriorConnectedCaveVolumeField FIELD =
            SkyIslandExteriorConnectedCaveVolumeField.create(DESCRIPTOR);
    private static final Single SINGLE = singleFixture();
    private static final Stacked STACKED = stackedFixture();

    private SkyforgeNeoForge1211ProductionComposedCaveFixture() {}

    static Single single() {
        return SINGLE;
    }

    static Stacked stacked() {
        return STACKED;
    }

    private static Single singleFixture() {
        if (FIELD.exposureGeometry().connectionCount() != 1) {
            throw new IllegalStateException(
                    "SF-IMP-0068 canonical key 3670 must retain one accepted AUTH-0030 connection");
        }
        double radius = DESCRIPTOR.nominalRadius();
        SkyIslandWorldVolume volume = volume(
                SINGLE_PHYSICAL_SEED,
                "sf-imp-0068-production-composed-cave",
                SINGLE_SUSPENSION_Y,
                radius,
                58.0,
                82.0,
                Math.min(54.0, radius * 0.18),
                110.0,
                80.0);
        return new Single(
                ISLAND_KEY,
                DESCRIPTOR,
                FIELD,
                volume,
                new SkyIslandWorldCatalog(WORLD_SEED, List.of(volume)));
    }

    private static Stacked stackedFixture() {
        double radius = DESCRIPTOR.nominalRadius();
        SkyIslandWorldVolume lower = volume(
                LOWER_PHYSICAL_SEED,
                "sf-imp-0068-production-composed-cave-stacked/lower",
                LOWER_SUSPENSION_Y,
                radius,
                30.0,
                38.0,
                Math.min(48.0, radius * 0.16),
                55.0,
                45.0);
        SkyIslandWorldVolume upper = volume(
                UPPER_PHYSICAL_SEED,
                "sf-imp-0068-production-composed-cave-stacked/upper",
                UPPER_SUSPENSION_Y,
                radius,
                30.0,
                38.0,
                Math.min(48.0, radius * 0.16),
                55.0,
                45.0);
        return new Stacked(
                ISLAND_KEY,
                DESCRIPTOR,
                FIELD,
                lower,
                upper,
                new SkyIslandWorldCatalog(WORLD_SEED, List.of(lower, upper)));
    }

    private static SkyIslandWorldVolume volume(
            long seed,
            String path,
            double suspensionY,
            double radius,
            double upperThickness,
            double lowerThickness,
            double rimDepth,
            double lowerBounds,
            double upperBounds) {
        SkyIslandVolumeDescriptor physicalDescriptor = SkyIslandVolumeDescriptor.schema2(
                seed,
                0.0,
                0.0,
                suspensionY,
                radius,
                upperThickness,
                lowerThickness,
                rimDepth,
                0.0,
                0.24,
                0.62,
                0.0,
                DESCRIPTOR.morphologyFamily(),
                0.10,
                28.0,
                0.18);
        var compiled = new SemanticSkyIslandVolumeRecipe().compile(physicalDescriptor);
        var id = new SkyIslandWorldVolumeId(WORLD_SEED, path, 0, 0, seed);
        var bounds = new WorldBounds(
                -radius * 1.08,
                radius * 1.08,
                suspensionY - lowerBounds,
                suspensionY + upperBounds,
                -radius * 1.08,
                radius * 1.08);
        return new SkyIslandWorldVolume(id, bounds, compiled);
    }

    record Single(
            long islandKey,
            io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor descriptor,
            SkyIslandExteriorConnectedCaveVolumeField field,
            SkyIslandWorldVolume volume,
            SkyIslandWorldCatalog catalog) {}

    record Stacked(
            long islandKey,
            io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor descriptor,
            SkyIslandExteriorConnectedCaveVolumeField field,
            SkyIslandWorldVolume lower,
            SkyIslandWorldVolume upper,
            SkyIslandWorldCatalog catalog) {}
}
