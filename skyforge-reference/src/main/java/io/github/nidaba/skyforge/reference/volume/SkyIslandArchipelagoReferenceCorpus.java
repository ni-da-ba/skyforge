package io.github.nidaba.skyforge.reference.volume;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyFamily;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyProviderBlend;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyProviderId;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviderRegistry;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviders;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoLayout;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoRequest;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandGroupRole;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandGroupTemplate;
import io.github.nidaba.skyforge.recipes.skyisland.group.ProviderBlendMorphologySpec;
import io.github.nidaba.skyforge.recipes.skyisland.group.ProviderMorphologySpec;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupLayout;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandMorphologySpec;
import io.github.nidaba.skyforge.reference.provider.ReferenceCrescentMorphologyProvider;
import java.util.List;

/** Stable regional scenes for the first hierarchical Skyforge archipelago proof. */
public final class SkyIslandArchipelagoReferenceCorpus {
    public static final long SKYFORGE_SEED = 0x534b59464f524745L;
    public static final long[] ACCEPTANCE_SEEDS = {Long.MIN_VALUE, 0L, SKYFORGE_SEED};

    public static final double MEMBER_RESERVED_RADIUS = 256.0;
    public static final double MEMBER_GAP = 96.0;
    public static final double HUB_GROUP_GAP = 900.0;
    public static final double ARC_GROUP_GAP = 1200.0;

    private SkyIslandArchipelagoReferenceCorpus() {}

    public static SkyIslandMorphologyProviderRegistry registry() {
        return SkyIslandGroupReferenceCorpus.registry();
    }

    /** Four formations organized around one deliberately dominant central cluster. */
    public static SkyIslandArchipelagoRequest hub(long seed) {
        return new SkyIslandArchipelagoRequest(
                seed,
                0.0,
                0.0,
                336.0,
                HUB_GROUP_GAP,
                List.of(
                        anchorCluster(),
                        secondaryChain(),
                        satelliteCluster(),
                        outlierPair()),
                new SkyIslandArchipelagoLayout.Hub(
                        5200.0,
                        Math.PI / 11.0,
                        0.10,
                        Math.PI / 16.0,
                        72.0));
    }

    /** Four distinct formations following a broad regional corridor with substantial empty sky. */
    public static SkyIslandArchipelagoRequest arc(long seed) {
        return new SkyIslandArchipelagoRequest(
                seed,
                0.0,
                0.0,
                336.0,
                ARC_GROUP_GAP,
                List.of(
                        arcCluster(),
                        arcChain(),
                        arcSatellite(),
                        arcOutlier()),
                new SkyIslandArchipelagoLayout.Arc(
                        Math.PI / 8.0,
                        5000.0,
                        0.08,
                        340.0,
                        1250.0,
                        Math.PI / 18.0,
                        84.0));
    }

    private static SkyIslandGroupTemplate anchorCluster() {
        return group(
                "anchor-cluster",
                SkyIslandGroupRole.ANCHOR,
                List.of(
                        provider(MorphologyFamily.BASIN),
                        provider(MorphologyFamily.MASSIF),
                        provider(ReferenceCrescentMorphologyProvider.ID),
                        blend(MorphologyFamily.TABLELAND, MorphologyFamily.LOBED, 0.45),
                        blend(id(MorphologyFamily.SPINE), ReferenceCrescentMorphologyProvider.ID, 0.40),
                        provider(MorphologyFamily.TABLELAND),
                        blend(MorphologyFamily.MASSIF, MorphologyFamily.BASIN, 0.50)),
                new SkyIslandGroupLayout.Cluster(660.0, Math.PI / 9.0, 0.12, Math.PI / 12.0),
                2300.0,
                52.0);
    }

    private static SkyIslandGroupTemplate secondaryChain() {
        return group(
                "secondary-chain",
                SkyIslandGroupRole.SECONDARY,
                List.of(
                        provider(MorphologyFamily.SPINE),
                        provider(ReferenceCrescentMorphologyProvider.ID),
                        blend(MorphologyFamily.MASSIF, MorphologyFamily.SPINE, 0.50),
                        provider(MorphologyFamily.LOBED),
                        blend(id(MorphologyFamily.BASIN), ReferenceCrescentMorphologyProvider.ID, 0.35)),
                new SkyIslandGroupLayout.Chain(0.0, 690.0, 0.06, 70.0, 160.0, Math.PI / 16.0),
                1900.0,
                46.0);
    }

    private static SkyIslandGroupTemplate satelliteCluster() {
        return group(
                "satellite-cluster",
                SkyIslandGroupRole.SATELLITE,
                List.of(
                        provider(MorphologyFamily.TABLELAND),
                        provider(MorphologyFamily.LOBED),
                        provider(ReferenceCrescentMorphologyProvider.ID)),
                new SkyIslandGroupLayout.Cluster(650.0, Math.PI / 5.0, 0.10, Math.PI / 14.0),
                1400.0,
                38.0);
    }

    private static SkyIslandGroupTemplate outlierPair() {
        return group(
                "outlier-pair",
                SkyIslandGroupRole.OUTLIER,
                List.of(
                        provider(MorphologyFamily.SPINE),
                        blend(id(MorphologyFamily.MASSIF), ReferenceCrescentMorphologyProvider.ID, 0.55)),
                new SkyIslandGroupLayout.Chain(0.0, 720.0, 0.04, 38.0, 70.0, Math.PI / 20.0),
                900.0,
                30.0);
    }

    private static SkyIslandGroupTemplate arcCluster() {
        return group(
                "arc-major-cluster",
                SkyIslandGroupRole.SECONDARY,
                List.of(
                        provider(MorphologyFamily.MASSIF),
                        provider(MorphologyFamily.BASIN),
                        provider(ReferenceCrescentMorphologyProvider.ID),
                        blend(MorphologyFamily.TABLELAND, MorphologyFamily.LOBED, 0.50),
                        provider(MorphologyFamily.SPINE)),
                new SkyIslandGroupLayout.Cluster(660.0, Math.PI / 8.0, 0.12, Math.PI / 12.0),
                1800.0,
                46.0);
    }

    private static SkyIslandGroupTemplate arcChain() {
        return group(
                "arc-long-chain",
                SkyIslandGroupRole.SECONDARY,
                List.of(
                        provider(MorphologyFamily.SPINE),
                        blend(id(MorphologyFamily.MASSIF), ReferenceCrescentMorphologyProvider.ID, 0.45),
                        provider(MorphologyFamily.TABLELAND),
                        provider(MorphologyFamily.LOBED)),
                new SkyIslandGroupLayout.Chain(0.0, 700.0, 0.06, 78.0, 190.0, Math.PI / 15.0),
                1600.0,
                42.0);
    }

    private static SkyIslandGroupTemplate arcSatellite() {
        return group(
                "arc-satellite",
                SkyIslandGroupRole.SATELLITE,
                List.of(
                        provider(ReferenceCrescentMorphologyProvider.ID),
                        provider(MorphologyFamily.BASIN),
                        blend(MorphologyFamily.MASSIF, MorphologyFamily.SPINE, 0.50)),
                new SkyIslandGroupLayout.Cluster(640.0, Math.PI / 6.0, 0.10, Math.PI / 14.0),
                1350.0,
                38.0);
    }

    private static SkyIslandGroupTemplate arcOutlier() {
        return group(
                "arc-outlier",
                SkyIslandGroupRole.OUTLIER,
                List.of(
                        provider(MorphologyFamily.LOBED),
                        provider(ReferenceCrescentMorphologyProvider.ID)),
                new SkyIslandGroupLayout.Chain(0.0, 730.0, 0.03, 30.0, 60.0, Math.PI / 20.0),
                900.0,
                28.0);
    }

    private static SkyIslandGroupTemplate group(
            String identifier,
            SkyIslandGroupRole role,
            List<SkyIslandMorphologySpec> morphologies,
            SkyIslandGroupLayout layout,
            double reservedGroupRadius,
            double elevationJitter) {
        return new SkyIslandGroupTemplate(
                identifier,
                role,
                template(),
                MEMBER_RESERVED_RADIUS,
                MEMBER_GAP,
                elevationJitter,
                morphologies,
                layout,
                reservedGroupRadius);
    }

    private static SkyIslandVolumeDescriptor template() {
        return new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                0L,
                0.0,
                0.0,
                336.0,
                192.0,
                76.0,
                100.0,
                48.0,
                Math.PI / 6.0,
                0.65,
                0.60,
                0.25,
                0.0,
                28.0);
    }

    private static SkyIslandMorphologySpec provider(MorphologyFamily family) {
        return provider(id(family));
    }

    private static SkyIslandMorphologySpec provider(MorphologyProviderId provider) {
        return ProviderMorphologySpec.full(provider);
    }

    private static SkyIslandMorphologySpec blend(
            MorphologyFamily first, MorphologyFamily second, double secondWeight) {
        return blend(id(first), id(second), secondWeight);
    }

    private static SkyIslandMorphologySpec blend(
            MorphologyProviderId first, MorphologyProviderId second, double secondWeight) {
        return ProviderBlendMorphologySpec.full(
                new MorphologyProviderBlend(first, second, secondWeight));
    }

    private static MorphologyProviderId id(MorphologyFamily family) {
        return SkyIslandMorphologyProviders.builtInId(family);
    }
}
