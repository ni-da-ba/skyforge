package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
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
import java.util.Map;

/** Fixed production-stack specimen for rapid hydrology-overhaul visual iteration. */
final class SkyforgeHydrologyReferenceReviewFixture {
    static final String ENABLE_PROPERTY = "skyforge.dev.hydrologyReferenceReview";
    static final String HEADLESS_BOOTSTRAP_PROPERTY =
            "skyforge.dev.hydrologyReferenceReviewHeadlessBootstrap";
    static final long ISLAND_KEY = 287L;
    static final double SUSPENSION_Y = 220.0;

    private static final long WORLD_SEED = 0x534B59464F524745L;
    private static final long PROVINCE_KEY = 8L;
    private static final long CLUSTER_KEY = 81L;
    private static final long PHYSICAL_SEED = 880287L;

    private SkyforgeHydrologyReferenceReviewFixture() {}

    static RuntimeFixture create() {
        SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(WORLD_SEED, PROVINCE_KEY, CLUSTER_KEY, ISLAND_KEY));
        double radius = descriptor.nominalRadius();
        SkyIslandVolumeDescriptor physical = SkyIslandVolumeDescriptor.schema2(
                PHYSICAL_SEED,
                0.0,
                0.0,
                SUSPENSION_Y,
                radius,
                58.0,
                82.0,
                Math.min(54.0, radius * 0.18),
                0.0,
                0.24,
                0.62,
                0.0,
                descriptor.morphologyFamily(),
                0.10,
                28.0,
                0.18);
        var compiled = new SemanticSkyIslandVolumeRecipe().compile(physical);
        SkyIslandWorldVolumeId id = new SkyIslandWorldVolumeId(
                WORLD_SEED,
                "hydrology-reference-review",
                0,
                0,
                PHYSICAL_SEED);
        SkyIslandWorldVolume volume = new SkyIslandWorldVolume(
                id,
                new WorldBounds(
                        -radius * 1.08,
                        radius * 1.08,
                        SUSPENSION_Y - 110.0,
                        SUSPENSION_Y + 80.0,
                        -radius * 1.08,
                        radius * 1.08),
                compiled);
        var caves = SkyIslandExteriorConnectedCaveVolumeField.create(descriptor);
        return new RuntimeFixture(
                descriptor,
                caves,
                volume,
                new SkyIslandWorldCatalog(WORLD_SEED, List.of(volume)),
                Map.of(id, descriptor));
    }

    record RuntimeFixture(
            SkyIslandDescriptor descriptor,
            SkyIslandExteriorConnectedCaveVolumeField caveField,
            SkyIslandWorldVolume volume,
            SkyIslandWorldCatalog catalog,
            Map<SkyIslandWorldVolumeId, SkyIslandDescriptor> descriptorsByVolumeId) {
        RuntimeFixture {
            descriptorsByVolumeId = Map.copyOf(descriptorsByVolumeId);
        }
    }
}
