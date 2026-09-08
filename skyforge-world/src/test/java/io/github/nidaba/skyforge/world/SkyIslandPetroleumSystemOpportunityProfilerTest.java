package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class SkyIslandPetroleumSystemOpportunityProfilerTest {
    private static final long WORLD = 0x4155544830303938L;

    @Test
    void preservesExactAuth0033ProvenanceDeterministically() {
        SkyIslandDescriptor descriptor = petroleumDescriptor(98001L, 180.0);
        SkyIslandPetroleumSystemOpportunityProfiler profiler =
                new SkyIslandPetroleumSystemOpportunityProfiler();

        SkyIslandPetroleumSystemOpportunityProfile first = profiler.profile(descriptor);
        SkyIslandPetroleumSystemOpportunityProfile second = profiler.profile(descriptor);

        assertEquals(descriptor, first.descriptor());
        assertEquals(SkyIslandMaterialDomainPlanner.GRID_SIZE, first.gridSize());
        assertEquals(SkyIslandMaterialDomainPlanner.DEPTH_SAMPLES, first.depthSamples());
        assertEquals(first.sourcePlan(), second.sourcePlan());
        assertEquals(first.cells(), second.cells());
        assertEquals(
                first.sourcePlan().cells(),
                first.cells().stream()
                        .map(SkyIslandPetroleumSystemOpportunityCell::sourceCell)
                        .toList());
    }

    @Test
    void componentPotentialsRemainSubordinateToAcceptedHostAndGeology() {
        SkyIslandDescriptor descriptor = petroleumDescriptor(98002L, 180.0);
        SkyIslandPetroleumSystemOpportunityProfile profile =
                new SkyIslandPetroleumSystemOpportunityProfiler().profile(descriptor);
        SkyIslandGeologyFieldSet geology = SkyIslandGeologyFieldSet.create(descriptor);

        boolean observedSource = false;
        boolean observedReservoir = false;
        boolean observedSeal = false;

        for (SkyIslandPetroleumSystemOpportunityCell cell : profile.cells()) {
            SkyIslandGeologySample geologic = geology.sample(cell.sourceCell().position());
            double layered = cell.sourceCell().layeredFabricRichHost();
            double permeability = geologic.connectedPermeability();
            double lowPermeability = 1.0 - permeability;

            assertTrue(cell.sourcePotential() >= 0.0 && cell.sourcePotential() <= layered);
            assertTrue(cell.reservoirPotential() >= 0.0
                    && cell.reservoirPotential() <= permeability + 1.0e-15);
            assertTrue(cell.sealPotential() >= 0.0
                    && cell.sealPotential() <= lowPermeability + 1.0e-15);

            observedSource |= cell.sourcePotential() > 0.0;
            observedReservoir |= cell.reservoirPotential() > 0.0;
            observedSeal |= cell.sealPotential() > 0.0;
        }

        assertTrue(observedSource);
        assertTrue(observedReservoir);
        assertTrue(observedSeal);
    }

    @Test
    void verticalSupportsReconstructExactlyFromTheAuth0033Column() {
        SkyIslandPetroleumSystemOpportunityProfile profile =
                new SkyIslandPetroleumSystemOpportunityProfiler()
                        .profile(petroleumDescriptor(98003L, 180.0));

        Map<Integer, SkyIslandPetroleumSystemOpportunityCell> byIndex = new HashMap<>();
        for (SkyIslandPetroleumSystemOpportunityCell cell : profile.cells()) {
            byIndex.put(cell.sourceCell().index(), cell);
        }

        int gridSize = profile.gridSize();
        int depthSamples = profile.depthSamples();
        for (SkyIslandPetroleumSystemOpportunityCell cell : profile.cells()) {
            SkyIslandMaterialFamilyCell source = cell.sourceCell();

            double expectedDeeperSource = 0.0;
            for (int depth = source.depthIndex() + 1; depth < depthSamples; depth++) {
                int index = index(
                        source.xIndex(), depth, source.zIndex(), gridSize, depthSamples);
                SkyIslandPetroleumSystemOpportunityCell deeper = byIndex.get(index);
                if (deeper != null) {
                    int distance = depth - source.depthIndex();
                    expectedDeeperSource =
                            Math.max(expectedDeeperSource, deeper.sourcePotential() / distance);
                }
            }

            double expectedShallowerSeal = 0.0;
            for (int depth = source.depthIndex() - 1; depth >= 0; depth--) {
                int index = index(
                        source.xIndex(), depth, source.zIndex(), gridSize, depthSamples);
                SkyIslandPetroleumSystemOpportunityCell shallower = byIndex.get(index);
                if (shallower != null) {
                    int distance = source.depthIndex() - depth;
                    expectedShallowerSeal =
                            Math.max(expectedShallowerSeal, shallower.sealPotential() / distance);
                }
            }

            assertEquals(expectedDeeperSource, cell.deeperSourceSupport(), 0.0);
            assertEquals(expectedShallowerSeal, cell.shallowerSealSupport(), 0.0);
            assertEquals(
                    cell.reservoirPotential()
                            * cell.deeperSourceSupport()
                            * cell.shallowerSealSupport(),
                    cell.systemOpportunity(),
                    0.0);
        }
    }

    @Test
    void systemOpportunityRequiresAllThreeVerticalComponents() {
        SkyIslandMaterialFamilyCell sourceCell = new SkyIslandMaterialFamilyCell(
                1,
                1,
                1,
                1,
                new SkyIslandSubsurfacePosition(0.0, 0.0, 0.5),
                0.8,
                0.7,
                0.2,
                0.4,
                0.0);

        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandPetroleumSystemOpportunityCell(
                        sourceCell,
                        0.5,
                        0.0,
                        0.6,
                        0.7,
                        0.8,
                        0.1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandPetroleumSystemOpportunityCell(
                        sourceCell,
                        0.5,
                        0.6,
                        0.6,
                        0.0,
                        0.8,
                        0.1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandPetroleumSystemOpportunityCell(
                        sourceCell,
                        0.5,
                        0.6,
                        0.6,
                        0.7,
                        0.0,
                        0.1));

        SkyIslandMaterialFamilyCell noLayeredHost = new SkyIslandMaterialFamilyCell(
                2,
                1,
                1,
                1,
                new SkyIslandSubsurfacePosition(0.0, 0.0, 0.5),
                0.9,
                0.0,
                0.2,
                0.4,
                0.0);
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandPetroleumSystemOpportunityCell(
                        noLayeredHost,
                        0.1,
                        0.6,
                        0.6,
                        0.7,
                        0.8,
                        0.1));
    }

    @Test
    void radiusOnlyScalingPreservesNormalizedOpportunityAndCellIdentity() {
        SkyIslandDescriptor small = petroleumDescriptor(98004L, 160.0);
        SkyIslandDescriptor large = withRadius(small, 320.0);

        SkyIslandPetroleumSystemOpportunityProfile first =
                new SkyIslandPetroleumSystemOpportunityProfiler().profile(small);
        SkyIslandPetroleumSystemOpportunityProfile second =
                new SkyIslandPetroleumSystemOpportunityProfiler().profile(large);

        assertEquals(first.cells().size(), second.cells().size());
        for (int ordinal = 0; ordinal < first.cells().size(); ordinal++) {
            SkyIslandPetroleumSystemOpportunityCell a = first.cells().get(ordinal);
            SkyIslandPetroleumSystemOpportunityCell b = second.cells().get(ordinal);
            assertEquals(a.sourceCell().index(), b.sourceCell().index());
            assertEquals(a.sourcePotential(), b.sourcePotential(), 1.0e-12);
            assertEquals(a.reservoirPotential(), b.reservoirPotential(), 1.0e-12);
            assertEquals(a.sealPotential(), b.sealPotential(), 1.0e-12);
            assertEquals(a.deeperSourceSupport(), b.deeperSourceSupport(), 1.0e-12);
            assertEquals(a.shallowerSealSupport(), b.shallowerSealSupport(), 1.0e-12);
            assertEquals(a.systemOpportunity(), b.systemOpportunity(), 1.0e-12);
        }
    }

    @Test
    void representativeProfileContainsNonzeroPetroleumSystemOpportunity() {
        SkyIslandPetroleumSystemOpportunityProfile profile =
                new SkyIslandPetroleumSystemOpportunityProfiler()
                        .profile(petroleumDescriptor(98005L, 180.0));

        assertTrue(profile.meanSourcePotential() > 0.0);
        assertTrue(profile.meanReservoirPotential() > 0.0);
        assertTrue(profile.meanSealPotential() > 0.0);
        assertTrue(profile.nonzeroSystemCellCount() > 0L);
        assertTrue(profile.peakSystemOpportunity() >= profile.meanSystemOpportunity());
        assertTrue(profile.peakSystemOpportunity() > 0.0);
    }

    @Test
    void publicProfilerAcceptsOnlyOneAuthoredDescriptor() {
        Method[] publicProfiles = Arrays.stream(
                        SkyIslandPetroleumSystemOpportunityProfiler.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> method.getName().equals("profile"))
                .toArray(Method[]::new);

        assertEquals(1, publicProfiles.length);
        assertEquals(
                List.of(SkyIslandDescriptor.class),
                List.of(publicProfiles[0].getParameterTypes()));
        assertEquals(
                SkyIslandPetroleumSystemOpportunityProfile.class,
                publicProfiles[0].getReturnType());
    }

    private static int index(
            int x,
            int depth,
            int z,
            int gridSize,
            int depthSamples) {
        return (z * depthSamples + depth) * gridSize + x;
    }

    private static SkyIslandDescriptor petroleumDescriptor(long islandKey, double radius) {
        SkyIslandDescriptor base = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(WORLD, 9L, 98L, islandKey));
        return new SkyIslandDescriptor(
                base.schemaVersion(),
                base.identity(),
                base.authorshipSeed(),
                base.morphologyFamily(),
                radius,
                base.reliefBudget(),
                0.72,
                0.68,
                0.70,
                0.70,
                base.exposureTendency(),
                0.60,
                0.76,
                base.ecologicalPotential());
    }

    private static SkyIslandDescriptor withRadius(
            SkyIslandDescriptor descriptor,
            double radius) {
        return new SkyIslandDescriptor(
                descriptor.schemaVersion(),
                descriptor.identity(),
                descriptor.authorshipSeed(),
                descriptor.morphologyFamily(),
                radius,
                descriptor.reliefBudget(),
                descriptor.rockCompetence(),
                descriptor.permeability(),
                descriptor.temperatureTendency(),
                descriptor.moistureTendency(),
                descriptor.exposureTendency(),
                descriptor.erosionMaturity(),
                descriptor.hydrologicalPotential(),
                descriptor.ecologicalPotential());
    }
}
