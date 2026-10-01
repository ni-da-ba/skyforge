package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SkyIslandBaseMetalOpportunityProfilerTest {
    private static final long WORLD = 0x4155544830303933L;

    @Test
    void preservesExactAuth0033ProvenanceDeterministically() {
        SkyIslandDescriptor descriptor = mineralDescriptor(93001L, 180.0);
        SkyIslandBaseMetalOpportunityProfiler profiler =
                new SkyIslandBaseMetalOpportunityProfiler();

        SkyIslandBaseMetalOpportunityProfile first = profiler.profile(descriptor);
        SkyIslandBaseMetalOpportunityProfile second = profiler.profile(descriptor);

        assertEquals(descriptor, first.descriptor());
        assertEquals(SkyIslandMaterialDomainPlanner.GRID_SIZE, first.gridSize());
        assertEquals(SkyIslandMaterialDomainPlanner.DEPTH_SAMPLES, first.depthSamples());
        assertEquals(first.sourcePlan(), second.sourcePlan());
        assertEquals(first.cells(), second.cells());
        assertEquals(first.sourcePlan().cells(),
                first.cells().stream().map(SkyIslandBaseMetalOpportunityCell::sourceCell).toList());
    }

    @Test
    void opportunityIsStrictlySubordinateToAcceptedMineralStructure() {
        SkyIslandBaseMetalOpportunityProfile profile =
                new SkyIslandBaseMetalOpportunityProfiler()
                        .profile(mineralDescriptor(93002L, 180.0));

        assertTrue(profile.mineralBearingCellCount() > 0);
        for (SkyIslandBaseMetalOpportunityCell cell : profile.cells()) {
            double support = cell.sourceCell().mineralBearingStructuralHost();
            for (SkyIslandBaseMetalKind kind : SkyIslandBaseMetalKind.values()) {
                assertTrue(cell.opportunity(kind) >= 0.0);
                assertTrue(cell.opportunity(kind) <= support);
                if (support == 0.0) {
                    assertEquals(0.0, cell.opportunity(kind), 0.0);
                }
            }
        }
    }

    @Test
    void relativeSharesNormalizeWithoutCreatingAvailabilityClasses() {
        SkyIslandBaseMetalOpportunityProfile profile =
                new SkyIslandBaseMetalOpportunityProfiler()
                        .profile(mineralDescriptor(93003L, 180.0));

        double sum = Arrays.stream(SkyIslandBaseMetalKind.values())
                .mapToDouble(profile::relativeOpportunityShare)
                .sum();
        assertEquals(1.0, sum, 1.0e-12);
        for (SkyIslandBaseMetalKind kind : SkyIslandBaseMetalKind.values()) {
            assertTrue(profile.meanOpportunity(kind) > 0.0);
            assertTrue(profile.peakOpportunity(kind) >= profile.meanOpportunity(kind));
            assertTrue(profile.relativeOpportunityShare(kind) > 0.0);
            assertTrue(profile.relativeOpportunityShare(kind) < 1.0);
        }
    }

    @Test
    void radiusOnlyScalingPreservesNormalizedOpportunityAndCellIdentity() {
        SkyIslandDescriptor small = mineralDescriptor(93004L, 160.0);
        SkyIslandDescriptor large = withRadius(small, 320.0);

        SkyIslandBaseMetalOpportunityProfile first =
                new SkyIslandBaseMetalOpportunityProfiler().profile(small);
        SkyIslandBaseMetalOpportunityProfile second =
                new SkyIslandBaseMetalOpportunityProfiler().profile(large);

        assertEquals(first.cells().size(), second.cells().size());
        for (int index = 0; index < first.cells().size(); index++) {
            SkyIslandBaseMetalOpportunityCell a = first.cells().get(index);
            SkyIslandBaseMetalOpportunityCell b = second.cells().get(index);
            assertEquals(a.sourceCell().index(), b.sourceCell().index());
            for (SkyIslandBaseMetalKind kind : SkyIslandBaseMetalKind.values()) {
                assertEquals(a.opportunity(kind), b.opportunity(kind), 1.0e-12);
            }
        }
    }

    @Test
    void elementalOpportunityDoesNotCollapseToOneIdenticalField() {
        SkyIslandBaseMetalOpportunityProfile profile =
                new SkyIslandBaseMetalOpportunityProfiler()
                        .profile(mineralDescriptor(93005L, 180.0));

        assertTrue(profile.cells().stream().anyMatch(cell ->
                cell.sourceCell().mineralBearingStructuralHost() > 0.0
                        && (Double.doubleToLongBits(cell.ironOpportunity())
                                        != Double.doubleToLongBits(cell.copperOpportunity())
                                || Double.doubleToLongBits(cell.ironOpportunity())
                                        != Double.doubleToLongBits(cell.zincOpportunity()))));
    }

    @Test
    void publicProfilerAcceptsOnlyOneAuthoredDescriptor() {
        Method[] publicProfiles = Arrays.stream(
                        SkyIslandBaseMetalOpportunityProfiler.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> method.getName().equals("profile"))
                .toArray(Method[]::new);

        assertEquals(1, publicProfiles.length);
        assertEquals(
                List.of(SkyIslandDescriptor.class),
                List.of(publicProfiles[0].getParameterTypes()));
        assertEquals(
                SkyIslandBaseMetalOpportunityProfile.class,
                publicProfiles[0].getReturnType());
    }

    private static SkyIslandDescriptor mineralDescriptor(long islandKey, double radius) {
        SkyIslandDescriptor base = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(WORLD, 9L, 93L, islandKey));
        return new SkyIslandDescriptor(
                base.schemaVersion(),
                base.identity(),
                base.authorshipSeed(),
                base.morphologyFamily(),
                radius,
                base.reliefBudget(),
                0.68,
                0.76,
                base.temperatureTendency(),
                0.72,
                base.exposureTendency(),
                0.66,
                0.74,
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
