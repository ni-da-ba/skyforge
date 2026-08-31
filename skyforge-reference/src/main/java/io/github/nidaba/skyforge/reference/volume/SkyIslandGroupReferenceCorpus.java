package io.github.nidaba.skyforge.reference.volume;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyFamily;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyProviderBlend;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProvider;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviderRegistry;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviders;
import io.github.nidaba.skyforge.recipes.skyisland.group.ProviderBlendMorphologySpec;
import io.github.nidaba.skyforge.recipes.skyisland.group.ProviderMorphologySpec;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupLayout;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupRequest;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandMorphologySpec;
import io.github.nidaba.skyforge.reference.provider.ReferenceCrescentMorphologyProvider;
import java.util.List;

/** Stable mixed-provider requests for the first group-scale Skyforge realization proof. */
public final class SkyIslandGroupReferenceCorpus {
    public static final long SKYFORGE_SEED = 0x534b59464f524745L;
    public static final long[] ACCEPTANCE_SEEDS = {Long.MIN_VALUE, 0L, SKYFORGE_SEED};

    public static final double NOMINAL_RADIUS = 192.0;
    public static final double RESERVED_RADIUS = 256.0;
    public static final double MINIMUM_GAP = 96.0;

    private SkyIslandGroupReferenceCorpus() {}

    /** Registry containing all accepted built-ins plus the genuine external reference provider. */
    public static SkyIslandMorphologyProviderRegistry registry() {
        SkyIslandMorphologyProviderRegistry.Builder builder = SkyIslandMorphologyProviderRegistry.builder();
        for (SkyIslandMorphologyProvider provider : SkyIslandMorphologyProviders.builtInRegistry().providers()) {
            builder.register(provider);
        }
        return builder.register(new ReferenceCrescentMorphologyProvider()).build();
    }

    /** Seven-member curved chain with single providers and cross-provider blends. */
    public static SkyIslandGroupRequest chain(long seed) {
        return new SkyIslandGroupRequest(
                seed,
                template(),
                RESERVED_RADIUS,
                MINIMUM_GAP,
                44.0,
                chainMorphologies(),
                new SkyIslandGroupLayout.Chain(
                        Math.PI / 9.0,
                        720.0,
                        0.08,
                        86.0,
                        210.0,
                        Math.PI / 14.0));
    }

    /** Nine-member organic cluster with the same mixed-provider vocabulary. */
    public static SkyIslandGroupRequest cluster(long seed) {
        return new SkyIslandGroupRequest(
                seed,
                template(),
                RESERVED_RADIUS,
                MINIMUM_GAP,
                52.0,
                clusterMorphologies(),
                new SkyIslandGroupLayout.Cluster(
                        680.0,
                        Math.PI / 7.0,
                        0.16,
                        Math.PI / 10.0));
    }

    private static SkyIslandVolumeDescriptor template() {
        return new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                0L,
                0.0,
                0.0,
                320.0,
                NOMINAL_RADIUS,
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

    private static List<SkyIslandMorphologySpec> chainMorphologies() {
        var massif = id(MorphologyFamily.MASSIF);
        var tableland = id(MorphologyFamily.TABLELAND);
        var spine = id(MorphologyFamily.SPINE);
        var basin = id(MorphologyFamily.BASIN);
        var lobed = id(MorphologyFamily.LOBED);
        var crescent = ReferenceCrescentMorphologyProvider.ID;
        return List.of(
                ProviderMorphologySpec.full(massif),
                ProviderMorphologySpec.full(crescent),
                ProviderBlendMorphologySpec.full(new MorphologyProviderBlend(massif, spine, 0.50)),
                ProviderMorphologySpec.full(tableland),
                ProviderBlendMorphologySpec.full(new MorphologyProviderBlend(crescent, basin, 0.35)),
                ProviderMorphologySpec.full(lobed),
                ProviderMorphologySpec.full(spine));
    }

    private static List<SkyIslandMorphologySpec> clusterMorphologies() {
        var massif = id(MorphologyFamily.MASSIF);
        var tableland = id(MorphologyFamily.TABLELAND);
        var spine = id(MorphologyFamily.SPINE);
        var basin = id(MorphologyFamily.BASIN);
        var lobed = id(MorphologyFamily.LOBED);
        var crescent = ReferenceCrescentMorphologyProvider.ID;
        return List.of(
                ProviderMorphologySpec.full(basin),
                ProviderMorphologySpec.full(crescent),
                ProviderBlendMorphologySpec.full(new MorphologyProviderBlend(tableland, lobed, 0.45)),
                ProviderMorphologySpec.full(massif),
                ProviderBlendMorphologySpec.full(new MorphologyProviderBlend(spine, crescent, 0.40)),
                ProviderMorphologySpec.full(tableland),
                ProviderMorphologySpec.full(lobed),
                ProviderBlendMorphologySpec.full(new MorphologyProviderBlend(massif, basin, 0.50)),
                ProviderMorphologySpec.full(crescent));
    }

    private static io.github.nidaba.skyforge.recipes.skyisland.MorphologyProviderId id(
            MorphologyFamily family) {
        return SkyIslandMorphologyProviders.builtInId(family);
    }
}
