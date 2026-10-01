package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class SkyIslandFreshwaterHabitatOpportunityProfilerTest {
    private static final long SEED = 0x534B59464F524745L;

    @Test
    void wetIslandRetainsExactAcceptedFreshwaterProvenanceAndMetrics() {
        SkyIslandDescriptor descriptor = descriptor(83L);
        SkyIslandFreshwaterHabitatOpportunityProfiler profiler =
                new SkyIslandFreshwaterHabitatOpportunityProfiler();

        SkyIslandFreshwaterHabitatOpportunityProfile first =
                profiler.profile(descriptor);
        SkyIslandFreshwaterHabitatOpportunityProfile second =
                profiler.profile(descriptor);

        assertEquals(first.descriptor(), second.descriptor());
        assertEquals(first.watershed(), second.watershed());
        assertEquals(first.footprintPlan(), second.footprintPlan());
        assertEquals(first.sourceCandidateCount(), second.sourceCandidateCount());
        assertEquals(first.inundatedCellCount(), second.inundatedCellCount());
        assertEquals(
                first.coarseHorizontalInundatedAreaEstimate(),
                second.coarseHorizontalInundatedAreaEstimate(),
                0.0);
        assertEquals(first.shorelineCellCount(), second.shorelineCellCount());
        assertEquals(first.meanWaterDepthPotential(), second.meanWaterDepthPotential(), 0.0);
        assertEquals(first.maxWaterDepthPotential(), second.maxWaterDepthPotential(), 0.0);
        assertEquals(first.sourceKindCounts(), second.sourceKindCounts());

        assertTrue(first.hasRetainedFreshwater());
        assertEquals(1, first.footprintCount());
        assertEquals(2L, first.sourceCandidateCount());

        Set<Integer> unique = new HashSet<>();
        long shoreline = 0L;
        double depthSum = 0.0;
        double maxDepth = 0.0;
        for (SkyIslandWaterbodyFootprint footprint : first.footprintPlan().footprints()) {
            for (SkyIslandWaterbodyFootprintCell cell : footprint.cells()) {
                if (unique.add(cell.watershedCellIndex())) {
                    if (cell.shoreline()) {
                        shoreline++;
                    }
                    depthSum += cell.waterDepthPotential();
                    maxDepth = Math.max(maxDepth, cell.waterDepthPotential());
                }
            }
        }

        assertEquals(unique.size(), first.inundatedCellCount());
        assertEquals(shoreline, first.shorelineCellCount());
        assertEquals(depthSum / unique.size(), first.meanWaterDepthPotential(), 1.0e-12);
        assertEquals(maxDepth, first.maxWaterDepthPotential(), 0.0);

        double cellArea = first.watershed().spacing() * first.watershed().spacing();
        assertEquals(
                first.inundatedCellCount() * cellArea,
                first.coarseHorizontalInundatedAreaEstimate(),
                0.0);

        long kindSum = 0L;
        for (SkyIslandWaterbodyKind kind : SkyIslandWaterbodyKind.values()) {
            kindSum += first.sourceCount(kind);
        }
        assertEquals(first.sourceCandidateCount(), kindSum);
    }

    @Test
    void dryIslandProducesValidAllZeroFreshwaterOpportunity() {
        SkyIslandFreshwaterHabitatOpportunityProfile dry =
                new SkyIslandFreshwaterHabitatOpportunityProfiler()
                        .profile(descriptor(77L));

        assertFalse(dry.hasRetainedFreshwater());
        assertEquals(0, dry.footprintCount());
        assertEquals(0L, dry.sourceCandidateCount());
        assertEquals(0L, dry.inundatedCellCount());
        assertEquals(0.0, dry.coarseHorizontalInundatedAreaEstimate(), 0.0);
        assertEquals(0L, dry.shorelineCellCount());
        assertEquals(0.0, dry.meanWaterDepthPotential(), 0.0);
        assertEquals(0.0, dry.maxWaterDepthPotential(), 0.0);
        for (SkyIslandWaterbodyKind kind : SkyIslandWaterbodyKind.values()) {
            assertEquals(0L, dry.sourceCount(kind));
        }
    }

    @Test
    void doublingOnlyRadiusPreservesFreshwaterTopologyAndDepthWhileAreaQuadruples() {
        SkyIslandDescriptor smallDescriptor = descriptor(83L);
        SkyIslandDescriptor largeDescriptor =
                withRadius(smallDescriptor, smallDescriptor.nominalRadius() * 2.0);

        SkyIslandFreshwaterHabitatOpportunityProfiler profiler =
                new SkyIslandFreshwaterHabitatOpportunityProfiler();
        SkyIslandFreshwaterHabitatOpportunityProfile small =
                profiler.profile(smallDescriptor);
        SkyIslandFreshwaterHabitatOpportunityProfile large =
                profiler.profile(largeDescriptor);

        assertEquals(small.watershed().gridSize(), large.watershed().gridSize());
        assertEquals(small.watershed().spacing() * 2.0, large.watershed().spacing(), 1.0e-12);
        assertEquals(small.footprintCount(), large.footprintCount());
        assertEquals(small.sourceCandidateCount(), large.sourceCandidateCount());
        assertEquals(small.inundatedCellCount(), large.inundatedCellCount());
        assertEquals(small.shorelineCellCount(), large.shorelineCellCount());
        assertEquals(small.sourceKindCounts(), large.sourceKindCounts());
        assertEquals(small.meanWaterDepthPotential(), large.meanWaterDepthPotential(), 1.0e-12);
        assertEquals(small.maxWaterDepthPotential(), large.maxWaterDepthPotential(), 1.0e-12);
        assertEquals(
                small.coarseHorizontalInundatedAreaEstimate() * 4.0,
                large.coarseHorizontalInundatedAreaEstimate(),
                Math.ulp(large.coarseHorizontalInundatedAreaEstimate()) * 8.0);

        assertEquals(
                footprintIndices(small),
                footprintIndices(large));
    }

    @Test
    void publicProfilerUsesOnlyTheAcceptedDescriptorDrivenPlannerStack() {
        Method[] publicProfiles = Arrays.stream(
                        SkyIslandFreshwaterHabitatOpportunityProfiler.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> method.getName().equals("profile"))
                .toArray(Method[]::new);

        assertEquals(1, publicProfiles.length);
        assertEquals(
                List.of(SkyIslandDescriptor.class),
                List.of(publicProfiles[0].getParameterTypes()));
        assertEquals(
                SkyIslandFreshwaterHabitatOpportunityProfile.class,
                publicProfiles[0].getReturnType());
    }

    private static Set<Integer> footprintIndices(
            SkyIslandFreshwaterHabitatOpportunityProfile profile) {
        Set<Integer> result = new HashSet<>();
        for (SkyIslandWaterbodyFootprint footprint : profile.footprintPlan().footprints()) {
            for (SkyIslandWaterbodyFootprintCell cell : footprint.cells()) {
                result.add(cell.watershedCellIndex());
            }
        }
        return result;
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 6L, 61L, key));
    }

    private static SkyIslandDescriptor withRadius(
            SkyIslandDescriptor source,
            double radius) {
        return new SkyIslandDescriptor(
                source.schemaVersion(),
                source.identity(),
                source.authorshipSeed(),
                source.morphologyFamily(),
                radius,
                source.reliefBudget(),
                source.rockCompetence(),
                source.permeability(),
                source.temperatureTendency(),
                source.moistureTendency(),
                source.exposureTendency(),
                source.erosionMaturity(),
                source.hydrologicalPotential(),
                source.ecologicalPotential());
    }
}
