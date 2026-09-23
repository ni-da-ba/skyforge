package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import org.junit.jupiter.api.Test;

class SkyIslandHydrologicFeaturePlannerTest {
    @Test
    void featurePlanIsDeterministicAndContainsNetworkFeatures() {
        SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(0x534B59464F524745L, 6L, 61L, 77L));
        SkyIslandHydrologicFeaturePlan a = SkyIslandHydrologicFeaturePlanner.plan(descriptor);
        SkyIslandHydrologicFeaturePlan b = SkyIslandHydrologicFeaturePlanner.plan(descriptor);
        assertEquals(a, b);
        assertTrue(a.count(SkyIslandHydrologicFeatureKind.CHANNEL) > 0);
        assertTrue(a.count(SkyIslandHydrologicFeatureKind.EDGE_WATERFALL) > 0);
    }

    @Test
    void corpusIncludesRetainedWaterCandidates() {
        boolean found = false;
        for (long key = 0; key < 1024 && !found; key++) {
            SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                    SkyIslandIdentity.of(0x534B59464F524745L, 6L, 61L, key));
            found = SkyIslandHydrologicFeaturePlanner.plan(descriptor)
                    .count(SkyIslandHydrologicFeatureKind.RETAINED_WATER) > 0;
        }
        assertTrue(found);
    }


    @Test
    void visibleChannelsNeverRequireRawTerrainAscent() {
        for (long key : new long[] {77L, 287L, 649L, 811L, 2083L, 2266L}) {
            SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                    SkyIslandIdentity.of(0x534B59464F524745L, 8L, 81L, key));
            SkyIslandWatershedPlan watershed = SkyIslandWatershedPlanner.plan(descriptor);
            var cells = watershed.cells().stream().collect(
                    java.util.stream.Collectors.toMap(SkyIslandWatershedCell::index, cell -> cell));
            for (SkyIslandHydrologicFeature feature : SkyIslandHydrologicFeaturePlanner.plan(descriptor).features()) {
                if (feature.kind() != SkyIslandHydrologicFeatureKind.CHANNEL) {
                    continue;
                }
                SkyIslandWatershedCell source = cells.get(feature.sourceCellIndex());
                SkyIslandWatershedCell downstream = cells.get(feature.downstreamCellIndex());
                assertTrue(source != null && downstream != null);
                assertTrue(
                        downstream.surfacePotential() <= source.surfacePotential() + 1.0e-10,
                        "visible channel cannot represent filled-depression uphill transport");
            }
        }
    }

    @Test
    void weaklyDifferentiatedWatershedCannotBecomeChannelCarpet() {
        SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(0x534B59464F524745L, 6L, 61L, 811L));
        SkyIslandWatershedPlan watershed = SkyIslandWatershedPlanner.plan(descriptor);
        SkyIslandHydrologicFeaturePlan features = SkyIslandHydrologicFeaturePlanner.plan(descriptor);
        long channelCount = features.count(SkyIslandHydrologicFeatureKind.CHANNEL);
        assertTrue(channelCount > 0);
        assertTrue(channelCount < watershed.cells().size() * 0.08);
    }
}
