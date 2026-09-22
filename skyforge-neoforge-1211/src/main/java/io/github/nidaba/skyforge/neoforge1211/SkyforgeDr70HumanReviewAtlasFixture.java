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
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;

/** Deterministic 10x10 placement of the frozen DR-70 human-review corpus. */
final class SkyforgeDr70HumanReviewAtlasFixture {
    static final String ENABLE_PROPERTY = "skyforge.dev.dr70HumanReviewAtlas";
    static final int GRID_WIDTH = 10;
    static final double GRID_SPACING = 2048.0;
    static final double SUSPENSION_Y = 220.0;

    private static final long WORLD_SEED = 0x534B59464F524745L;
    private static final long PROVINCE_KEY = 8L;
    private static final long CLUSTER_KEY = 81L;
    private static final long PHYSICAL_SEED_BASE = 780000L;
    private static final double HALF_GRID = (GRID_WIDTH - 1) * 0.5;

    private static final List<Spec> SPECS = List.of(
            new Spec("unfiltered", 811L),
            new Spec("unfiltered", 1904L),
            new Spec("unfiltered", 2997L),
            new Spec("unfiltered", 4090L),
            new Spec("unfiltered", 1087L),
            new Spec("unfiltered", 2180L),
            new Spec("unfiltered", 3273L),
            new Spec("unfiltered", 270L),
            new Spec("unfiltered", 1363L),
            new Spec("unfiltered", 2456L),
            new Spec("unfiltered", 3549L),
            new Spec("unfiltered", 546L),
            new Spec("unfiltered", 1639L),
            new Spec("unfiltered", 2732L),
            new Spec("unfiltered", 3825L),
            new Spec("unfiltered", 822L),
            new Spec("unfiltered", 1915L),
            new Spec("unfiltered", 3008L),
            new Spec("unfiltered", 5L),
            new Spec("unfiltered", 1098L),
            new Spec("unfiltered", 2191L),
            new Spec("unfiltered", 3284L),
            new Spec("unfiltered", 281L),
            new Spec("unfiltered", 1374L),
            new Spec("unfiltered", 2467L),
            new Spec("unfiltered", 3560L),
            new Spec("unfiltered", 557L),
            new Spec("unfiltered", 1650L),
            new Spec("unfiltered", 2743L),
            new Spec("unfiltered", 3836L),
            new Spec("unfiltered", 833L),
            new Spec("unfiltered", 1926L),
            new Spec("unfiltered", 3019L),
            new Spec("unfiltered", 16L),
            new Spec("unfiltered", 1109L),
            new Spec("unfiltered", 2202L),
            new Spec("unfiltered", 3295L),
            new Spec("unfiltered", 292L),
            new Spec("unfiltered", 1385L),
            new Spec("unfiltered", 2478L),
            new Spec("unfiltered", 3571L),
            new Spec("unfiltered", 568L),
            new Spec("unfiltered", 1661L),
            new Spec("unfiltered", 2754L),
            new Spec("unfiltered", 3847L),
            new Spec("unfiltered", 844L),
            new Spec("unfiltered", 1937L),
            new Spec("unfiltered", 3030L),
            new Spec("unfiltered", 27L),
            new Spec("unfiltered", 1120L),
            new Spec("unfiltered", 2213L),
            new Spec("unfiltered", 3306L),
            new Spec("substantial-channel", 2083L),
            new Spec("substantial-channel", 2150L),
            new Spec("substantial-channel", 649L),
            new Spec("substantial-channel", 1776L),
            new Spec("retained-water", 1434L),
            new Spec("retained-water", 2297L),
            new Spec("retained-water", 2600L),
            new Spec("retained-water", 1390L),
            new Spec("cave-sealed", 1337L),
            new Spec("cave-sealed", 2132L),
            new Spec("cave-sealed", 298L),
            new Spec("cave-sealed", 2417L),
            new Spec("cave-upper", 3694L),
            new Spec("cave-upper", 2712L),
            new Spec("cave-upper", 329L),
            new Spec("cave-upper", 1856L),
            new Spec("cave-underside", 1552L),
            new Spec("cave-underside", 674L),
            new Spec("cave-underside", 2827L),
            new Spec("cave-underside", 2941L),
            new Spec("channel-and-cave", 2325L),
            new Spec("channel-and-cave", 1397L),
            new Spec("channel-and-cave", 2666L),
            new Spec("channel-and-cave", 4065L),
            new Spec("channel-and-upper", 1224L),
            new Spec("channel-and-upper", 1407L),
            new Spec("channel-and-upper", 245L),
            new Spec("channel-and-upper", 420L),
            new Spec("channel-and-underside", 2201L),
            new Spec("channel-and-underside", 528L),
            new Spec("channel-and-underside", 3962L),
            new Spec("channel-and-underside", 3598L),
            new Spec("channel-water-cave", 632L),
            new Spec("channel-water-cave", 3426L),
            new Spec("channel-water-cave", 2975L),
            new Spec("channel-water-cave", 609L),
            new Spec("wet-large-relief-channel", 3610L),
            new Spec("wet-large-relief-channel", 938L),
            new Spec("wet-large-relief-channel", 1679L),
            new Spec("wet-large-relief-channel", 1176L),
            new Spec("deterministic-fill", 303L),
            new Spec("deterministic-fill", 1396L),
            new Spec("deterministic-fill", 2489L),
            new Spec("deterministic-fill", 3582L),
            new Spec("deterministic-fill", 579L),
            new Spec("deterministic-fill", 1672L),
            new Spec("deterministic-fill", 2765L),
            new Spec("deterministic-fill", 3858L));

    static {
        if (SPECS.size() != GRID_WIDTH * GRID_WIDTH) {
            throw new IllegalStateException("DR-70 review corpus must exactly fill the 10x10 atlas");
        }
        if (2.16 * SkyIslandDescriptorGenerator.MAX_NOMINAL_RADIUS >= GRID_SPACING) {
            throw new IllegalStateException("DR-70 atlas spacing cannot isolate maximum legal island radius");
        }
    }

    private SkyforgeDr70HumanReviewAtlasFixture() {}

    static int size() {
        return SPECS.size();
    }

    static List<Integer> reviewOrder() {
        return ReviewOrderHolder.ORDER;
    }

    static Member member(int oneBasedIndex) {
        if (oneBasedIndex < 1 || oneBasedIndex > SPECS.size()) {
            throw new IllegalArgumentException("DR-70 review index must be in [1,100]");
        }
        int ordinal = oneBasedIndex - 1;
        Spec spec = SPECS.get(ordinal);
        int row = ordinal / GRID_WIDTH;
        int column = ordinal % GRID_WIDTH;
        double centerX = (column - HALF_GRID) * GRID_SPACING;
        double centerZ = (row - HALF_GRID) * GRID_SPACING;
        SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(WORLD_SEED, PROVINCE_KEY, CLUSTER_KEY, spec.islandKey()));
        return new Member(
                oneBasedIndex,
                spec.bucket(),
                spec.islandKey(),
                row,
                column,
                centerX,
                centerZ,
                descriptor);
    }

    static RuntimeFixture runtimeFixture(int oneBasedIndex) {
        Member member = member(oneBasedIndex);
        SkyIslandExteriorConnectedCaveVolumeField caves =
                SkyIslandExteriorConnectedCaveVolumeField.create(member.descriptor());
        SkyIslandWorldVolume volume = physicalVolume(member);
        return new RuntimeFixture(
                member,
                caves,
                volume,
                new SkyIslandWorldCatalog(WORLD_SEED, List.of(volume)),
                Map.of(volume.id(), member.descriptor()));
    }

    private static SkyIslandWorldVolume physicalVolume(Member member) {
        SkyIslandDescriptor descriptor = member.descriptor();
        double radius = descriptor.nominalRadius();
        long seed = Math.addExact(PHYSICAL_SEED_BASE, descriptor.identity().islandKey());
        SkyIslandVolumeDescriptor physical = SkyIslandVolumeDescriptor.schema2(
                seed,
                member.centerX(),
                member.centerZ(),
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
                "dr-70-human-review-atlas",
                0,
                member.reviewIndex() - 1,
                seed);
        WorldBounds bounds = new WorldBounds(
                member.centerX() - radius * 1.08,
                member.centerX() + radius * 1.08,
                SUSPENSION_Y - 110.0,
                SUSPENSION_Y + 80.0,
                member.centerZ() - radius * 1.08,
                member.centerZ() + radius * 1.08);
        return new SkyIslandWorldVolume(id, bounds, compiled);
    }

    record Member(
            int reviewIndex,
            String selectionBucket,
            long islandKey,
            int row,
            int column,
            double centerX,
            double centerZ,
            SkyIslandDescriptor descriptor) {
        Member {
            Objects.requireNonNull(selectionBucket, "selectionBucket");
            Objects.requireNonNull(descriptor, "descriptor");
        }
    }

    record RuntimeFixture(
            Member member,
            SkyIslandExteriorConnectedCaveVolumeField caveField,
            SkyIslandWorldVolume volume,
            SkyIslandWorldCatalog catalog,
            Map<SkyIslandWorldVolumeId, SkyIslandDescriptor> descriptorsByVolumeId) {
        RuntimeFixture {
            Objects.requireNonNull(member, "member");
            Objects.requireNonNull(caveField, "caveField");
            Objects.requireNonNull(volume, "volume");
            Objects.requireNonNull(catalog, "catalog");
            descriptorsByVolumeId = Map.copyOf(descriptorsByVolumeId);
        }
    }

    private record Spec(String bucket, long islandKey) {
        Spec {
            Objects.requireNonNull(bucket, "bucket");
        }
    }

    private static final class ReviewOrderHolder {
        private static final List<String> SEMANTIC_COVERAGE_BUCKETS = List.of(
                "substantial-channel",
                "retained-water",
                "cave-sealed",
                "cave-upper",
                "cave-underside",
                "channel-and-cave",
                "channel-and-upper",
                "channel-and-underside",
                "channel-water-cave",
                "wet-large-relief-channel");

        private static final List<Integer> ORDER = build();

        private static List<Integer> build() {
            List<Member> members = IntStream.rangeClosed(1, SPECS.size())
                    .mapToObj(SkyforgeDr70HumanReviewAtlasFixture::member)
                    .toList();
            var order = new LinkedHashSet<Integer>();

            // First-wave coverage: every built-in morphology at compact, middle and large scales.
            for (int sizeBand = 0; sizeBand < 3; sizeBand++) {
                for (var morphology : io.github.nidaba.skyforge.model.skyisland.SkyIslandMorphologyFamily.values()) {
                    final int requiredBand = sizeBand;
                    members.stream()
                            .filter(member -> sizeBand(member.descriptor().nominalRadius()) == requiredBand)
                            .filter(member -> member.descriptor().morphologyFamily() == morphology)
                            .min(Comparator
                                    .comparingDouble((Member member) -> member.descriptor().nominalRadius())
                                    .thenComparingInt(Member::reviewIndex))
                            .ifPresent(member -> order.add(member.reviewIndex()));
                }
            }

            // Then guarantee that each explicitly frozen semantic stress stratum appears early.
            for (String bucket : SEMANTIC_COVERAGE_BUCKETS) {
                members.stream()
                        .filter(member -> member.selectionBucket().equals(bucket))
                        .min(Comparator
                                .comparingDouble((Member member) -> member.descriptor().nominalRadius())
                                .thenComparingInt(Member::reviewIndex))
                        .ifPresent(member -> order.add(member.reviewIndex()));
            }

            // Remaining corpus stays cost-aware so background preparation remains economical.
            members.stream()
                    .sorted(Comparator
                            .comparingDouble((Member member) -> member.descriptor().nominalRadius())
                            .thenComparingInt(Member::reviewIndex))
                    .map(Member::reviewIndex)
                    .forEach(order::add);
            return List.copyOf(order);
        }

        private static int sizeBand(double nominalRadius) {
            if (nominalRadius < 160.0) {
                return 0;
            }
            if (nominalRadius < 320.0) {
                return 1;
            }
            return 2;
        }
    }
}
