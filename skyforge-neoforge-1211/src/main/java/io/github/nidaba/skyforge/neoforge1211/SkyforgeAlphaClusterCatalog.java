package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyFamily;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviders;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoLayout;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoPlanner;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoRequest;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandGroupRole;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandGroupTemplate;
import io.github.nidaba.skyforge.recipes.skyisland.group.ProviderMorphologySpec;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupLayout;
import io.github.nidaba.skyforge.world.SkyIslandWorldCatalog;
import io.github.nidaba.skyforge.world.SkyIslandWorldCatalogCompiler;
import io.github.nidaba.skyforge.world.SkyIslandWorldVerticalReservation;
import java.util.List;

/** Deterministic two-island cluster specimen for the private-alpha terrain/ecology review. */
final class SkyforgeAlphaClusterCatalog {
    private static final String MASSIF_GROUP_ID = "sf-imp-1526-massif-anchor";
    private static final String BASIN_GROUP_ID = "sf-imp-1526-basin-satellite";
    private static final double MEMBER_RADIUS = 160.0;
    private static final double MEMBER_GAP = 72.0;

    private SkyforgeAlphaClusterCatalog() {}

    static SkyIslandWorldCatalog compile(long worldSeed) {
        SkyIslandVolumeDescriptor member = new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                worldSeed,
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
        SkyIslandGroupTemplate massif = new SkyIslandGroupTemplate(
                MASSIF_GROUP_ID,
                SkyIslandGroupRole.ANCHOR,
                member,
                MEMBER_RADIUS,
                MEMBER_GAP,
                0.0,
                List.of(
                        ProviderMorphologySpec.full(
                                SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF))),
                new SkyIslandGroupLayout.Cluster(392.0, 0.0, 0.0, 0.0),
                MEMBER_RADIUS);
        SkyIslandGroupTemplate basin = new SkyIslandGroupTemplate(
                BASIN_GROUP_ID,
                SkyIslandGroupRole.SATELLITE,
                member,
                MEMBER_RADIUS,
                MEMBER_GAP,
                0.0,
                List.of(
                        ProviderMorphologySpec.full(
                                SkyIslandMorphologyProviders.builtInId(MorphologyFamily.BASIN))),
                new SkyIslandGroupLayout.Cluster(392.0, 0.0, 0.0, 0.0),
                MEMBER_RADIUS);
        var request = new SkyIslandArchipelagoRequest(
                worldSeed,
                196.0,
                0.0,
                224.0,
                MEMBER_GAP,
                List.of(massif, basin),
                new SkyIslandArchipelagoLayout.Arc(0.0, 392.0, 0.0, 0.0, 0.0, 0.0, 52.0));
        var plan = new SkyIslandArchipelagoPlanner().plan(request);
        return new SkyIslandWorldCatalogCompiler().compile(
                plan,
                SkyIslandMorphologyProviders.builtInRegistry(),
                new SkyIslandWorldVerticalReservation(144.0, 112.0));
    }
}
