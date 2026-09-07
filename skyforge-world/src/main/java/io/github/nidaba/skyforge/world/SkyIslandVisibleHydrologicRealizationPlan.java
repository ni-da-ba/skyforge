package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.List;
import java.util.Objects;

/** Exact backend-neutral projection of accepted authored hydrology into visible-water intents. */
public record SkyIslandVisibleHydrologicRealizationPlan(
        SkyIslandDescriptor descriptor,
        SkyIslandCoherentHydrologicRealizationPlan coherentHydrology,
        SkyIslandWaterbodyFootprintPlan waterbodies,
        SkyIslandWaterbodyMarginPlan waterbodyMargins,
        List<SkyIslandVisibleChannelWaterIntent> channels,
        List<SkyIslandVisibleRetainedWaterIntent> retainedWater,
        List<SkyIslandVisibleDropWaterIntent> drops) {

    public SkyIslandVisibleHydrologicRealizationPlan {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        coherentHydrology = Objects.requireNonNull(coherentHydrology, "coherentHydrology");
        waterbodies = Objects.requireNonNull(waterbodies, "waterbodies");
        waterbodyMargins = Objects.requireNonNull(waterbodyMargins, "waterbodyMargins");
        channels = List.copyOf(channels);
        retainedWater = List.copyOf(retainedWater);
        drops = List.copyOf(drops);

        if (!coherentHydrology.descriptor().equals(descriptor)
                || !coherentHydrology.channels().descriptor().equals(descriptor)
                || !coherentHydrology.riparian().descriptor().equals(descriptor)
                || !coherentHydrology.drops().descriptor().equals(descriptor)
                || !coherentHydrology.terrainInfluence().descriptor().equals(descriptor)
                || !coherentHydrology.terrainSurface().descriptor().equals(descriptor)
                || !coherentHydrology.naturalizedChannels().descriptor().equals(descriptor)
                || !waterbodies.descriptor().equals(descriptor)
                || !waterbodyMargins.descriptor().equals(descriptor)) {
            throw new IllegalArgumentException(
                    "visible hydrologic realization sources must belong to one exact island");
        }

        requireExactChannelProjection(coherentHydrology, channels);
        requireExactRetainedWaterProjection(waterbodies, waterbodyMargins, retainedWater);
        requireExactDropProjection(coherentHydrology, drops);
    }

    public int totalIntentCount() {
        return channels.size() + retainedWater.size() + drops.size();
    }

    public long count(SkyIslandVisibleHydrologicRealizationKind kind) {
        Objects.requireNonNull(kind, "kind");
        return switch (kind) {
            case CHANNEL_WATER -> channels.size();
            case RETAINED_WATER -> retainedWater.size();
            case CASCADE, WATERFALL, EDGE_DISCHARGE ->
                    drops.stream().filter(intent -> intent.kind() == kind).count();
        };
    }

    private static void requireExactChannelProjection(
            SkyIslandCoherentHydrologicRealizationPlan coherentHydrology,
            List<SkyIslandVisibleChannelWaterIntent> channels) {
        List<SkyIslandNaturalizedChannelPath> sourcePaths =
                coherentHydrology.naturalizedChannels().paths();
        if (channels.size() != sourcePaths.size()) {
            throw new IllegalArgumentException(
                    "visible channel projection must cover every accepted naturalized path exactly once");
        }
        for (int i = 0; i < sourcePaths.size(); i++) {
            SkyIslandNaturalizedChannelPath sourcePath = sourcePaths.get(i);
            SkyIslandVisibleChannelWaterIntent intent =
                    Objects.requireNonNull(channels.get(i), "channel intent");
            if (!intent.path().equals(sourcePath)) {
                throw new IllegalArgumentException(
                        "visible channel projection must preserve exact source ordering and identity");
            }
            SkyIslandChannelSegment segment = sourcePath.profile().segment();
            List<SkyIslandRiparianCell> expectedRiparian = coherentHydrology.riparian().cells().stream()
                    .filter(cell -> cell.channelSourceCellIndex() == segment.sourceCellIndex()
                            && cell.channelDownstreamCellIndex() == segment.downstreamCellIndex())
                    .toList();
            if (!intent.riparianCells().equals(expectedRiparian)) {
                throw new IllegalArgumentException(
                        "visible channel projection must retain the exact authored riparian relationship");
            }
        }
    }

    private static void requireExactRetainedWaterProjection(
            SkyIslandWaterbodyFootprintPlan waterbodies,
            SkyIslandWaterbodyMarginPlan waterbodyMargins,
            List<SkyIslandVisibleRetainedWaterIntent> retainedWater) {
        if (waterbodies.footprints().size() != waterbodyMargins.margins().size()
                || retainedWater.size() != waterbodies.footprints().size()) {
            throw new IllegalArgumentException(
                    "visible retained-water projection must cover every footprint and margin exactly once");
        }
        for (int i = 0; i < retainedWater.size(); i++) {
            SkyIslandVisibleRetainedWaterIntent intent =
                    Objects.requireNonNull(retainedWater.get(i), "retained-water intent");
            if (!intent.footprint().equals(waterbodies.footprints().get(i))
                    || !intent.margin().equals(waterbodyMargins.margins().get(i))) {
                throw new IllegalArgumentException(
                        "visible retained-water projection must preserve exact source ordering and identity");
            }
        }
    }

    private static void requireExactDropProjection(
            SkyIslandCoherentHydrologicRealizationPlan coherentHydrology,
            List<SkyIslandVisibleDropWaterIntent> drops) {
        List<SkyIslandChannelDrop> sourceDrops = coherentHydrology.drops().drops();
        if (drops.size() != sourceDrops.size()) {
            throw new IllegalArgumentException(
                    "visible drop projection must cover every accepted drop exactly once");
        }
        for (int i = 0; i < sourceDrops.size(); i++) {
            SkyIslandVisibleDropWaterIntent intent =
                    Objects.requireNonNull(drops.get(i), "drop intent");
            if (!intent.drop().equals(sourceDrops.get(i))) {
                throw new IllegalArgumentException(
                        "visible drop projection must preserve exact source ordering and identity");
            }
        }
    }
}
