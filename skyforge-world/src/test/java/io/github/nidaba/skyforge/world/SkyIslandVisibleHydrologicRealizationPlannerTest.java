package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.*;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SkyIslandVisibleHydrologicRealizationPlannerTest {
    private static final long SEED = 0x534B59464F524745L;
    private static final List<Long> KEYS = List.of(77L, 118L, 241L, 512L, 811L, 83L);

    @Test
    void deterministicallyProjectsEveryAcceptedVisibleHydrologySourceExactlyOnce() {
        for (long key : KEYS) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandVisibleHydrologicRealizationPlan first =
                    SkyIslandVisibleHydrologicRealizationPlanner.plan(descriptor);
            SkyIslandVisibleHydrologicRealizationPlan second =
                    SkyIslandVisibleHydrologicRealizationPlanner.plan(descriptor);

            assertEquals(first, second);
            assertEquals(
                    first.coherentHydrology().naturalizedChannels().paths().size(),
                    first.channels().size());
            assertEquals(first.waterbodies().footprints().size(), first.retainedWater().size());
            assertEquals(first.coherentHydrology().drops().drops().size(), first.drops().size());
            assertEquals(
                    first.channels().size() + first.retainedWater().size() + first.drops().size(),
                    first.totalIntentCount());
        }
    }

    @Test
    void channelIntentRetainsExactPathAndRiparianRelationship() {
        for (long key : KEYS) {
            SkyIslandVisibleHydrologicRealizationPlan plan =
                    SkyIslandVisibleHydrologicRealizationPlanner.plan(descriptor(key));
            for (int i = 0; i < plan.channels().size(); i++) {
                SkyIslandVisibleChannelWaterIntent intent = plan.channels().get(i);
                SkyIslandNaturalizedChannelPath source =
                        plan.coherentHydrology().naturalizedChannels().paths().get(i);
                assertEquals(source, intent.path());
                assertEquals(SkyIslandVisibleHydrologicRealizationKind.CHANNEL_WATER, intent.kind());

                SkyIslandChannelSegment segment = source.profile().segment();
                List<SkyIslandRiparianCell> expected = plan.coherentHydrology().riparian().cells().stream()
                        .filter(cell -> cell.channelSourceCellIndex() == segment.sourceCellIndex()
                                && cell.channelDownstreamCellIndex() == segment.downstreamCellIndex())
                        .toList();
                assertEquals(expected, intent.riparianCells());
            }
        }
    }

    @Test
    void retainedWaterIntentRetainsExactFootprintAndDryMargin() {
        for (long key : KEYS) {
            SkyIslandVisibleHydrologicRealizationPlan plan =
                    SkyIslandVisibleHydrologicRealizationPlanner.plan(descriptor(key));
            for (int i = 0; i < plan.retainedWater().size(); i++) {
                SkyIslandVisibleRetainedWaterIntent intent = plan.retainedWater().get(i);
                assertEquals(plan.waterbodies().footprints().get(i), intent.footprint());
                assertEquals(plan.waterbodyMargins().margins().get(i), intent.margin());
                assertEquals(SkyIslandVisibleHydrologicRealizationKind.RETAINED_WATER, intent.kind());
            }
        }
    }

    @Test
    void dropIntentMapsAcceptedDropKindWithoutReclassificationThresholds() {
        for (long key : KEYS) {
            SkyIslandVisibleHydrologicRealizationPlan plan =
                    SkyIslandVisibleHydrologicRealizationPlanner.plan(descriptor(key));
            for (int i = 0; i < plan.drops().size(); i++) {
                SkyIslandVisibleDropWaterIntent intent = plan.drops().get(i);
                SkyIslandChannelDrop source = plan.coherentHydrology().drops().drops().get(i);
                assertEquals(source, intent.drop());
                assertEquals(switch (source.kind()) {
                    case CASCADE_STEP -> SkyIslandVisibleHydrologicRealizationKind.CASCADE;
                    case WATERFALL -> SkyIslandVisibleHydrologicRealizationKind.WATERFALL;
                    case EDGE_FALL -> SkyIslandVisibleHydrologicRealizationKind.EDGE_DISCHARGE;
                }, intent.kind());
            }
        }
    }

    @Test
    void planRejectsCrossIslandSourceComposition() {
        SkyIslandVisibleHydrologicRealizationPlan a =
                SkyIslandVisibleHydrologicRealizationPlanner.plan(descriptor(77L));
        SkyIslandVisibleHydrologicRealizationPlan b =
                SkyIslandVisibleHydrologicRealizationPlanner.plan(descriptor(83L));

        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandVisibleHydrologicRealizationPlan(
                        a.descriptor(),
                        b.coherentHydrology(),
                        b.waterbodies(),
                        b.waterbodyMargins(),
                        b.channels(),
                        b.retainedWater(),
                        b.drops()));
    }

    @Test
    void planRejectsOmittedAcceptedSourceIntent() {
        for (long key : KEYS) {
            SkyIslandVisibleHydrologicRealizationPlan plan =
                    SkyIslandVisibleHydrologicRealizationPlanner.plan(descriptor(key));
            if (!plan.channels().isEmpty()) {
                List<SkyIslandVisibleChannelWaterIntent> truncated =
                        new ArrayList<>(plan.channels());
                truncated.removeLast();
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new SkyIslandVisibleHydrologicRealizationPlan(
                                plan.descriptor(),
                                plan.coherentHydrology(),
                                plan.waterbodies(),
                                plan.waterbodyMargins(),
                                truncated,
                                plan.retainedWater(),
                                plan.drops()));
                return;
            }
        }
        fail("representative corpus contains no accepted visible channel");
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 9L, 86L, key));
    }
}
