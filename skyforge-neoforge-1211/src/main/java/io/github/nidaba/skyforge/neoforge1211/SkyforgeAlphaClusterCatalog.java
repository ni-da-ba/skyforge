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

/** Deterministic three-member cluster specimen for the private-alpha terrain/ecology review. */
final class SkyforgeAlphaClusterCatalog {
    private static final String GROUP_ID = "sf-imp-1526-alpha-cluster";
    private static final double MEMBER_RADIUS = 160.0;
    private static final double MEMBER_GAP = 72.0;

    private SkyforgeAlphaClusterCatalog() {}

    static SkyIslandWorldCatalog compile(long worldSeed) {
        SkyIslandVolumeDescriptor member = new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                worldSeed,
                192.0,
                0.0,
                224.0,
                112.0,
                44.0,
                72.0,
                24.0,
                Math.PI / 6.0,
                0.65,
                0.60,
                0.25,
                0.0,
                20.0);
        SkyIslandGroupTemplate cluster = new SkyIslandGroupTemplate(
                GROUP_ID,
                SkyIslandGroupRole.ANCHOR,
                member,
                MEMBER_RADIUS,
                MEMBER_GAP,
                52.0,
                List.of(
                        ProviderMorphologySpec.full(
                                SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF)),
                        ProviderMorphologySpec.full(
                                SkyIslandMorphologyProviders.builtInId(MorphologyFamily.BASIN))),
                new SkyIslandGroupLayout.Cluster(392.0, 0.0, 0.08, 0.18),
                512.0);
        var request = new SkyIslandArchipelagoRequest(
                worldSeed,
                0.0,
                0.0,
                224.0,
                MEMBER_GAP,
                List.of(cluster),
                new SkyIslandArchipelagoLayout.Hub(640.0, 0.0, 0.0, 0.0, 0.0));
        var plan = new SkyIslandArchipelagoPlanner().plan(request);
        return new SkyIslandWorldCatalogCompiler().compile(
                plan,
                SkyIslandMorphologyProviders.builtInRegistry(),
                new SkyIslandWorldVerticalReservation(144.0, 112.0));
    }
}
