package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

final class SkyIslandCaveSiteCapabilityProfilerTest {
    private static final long SEED = 0x534B59464F524745L;

    @Test
    void profileRetainsExactAcceptedCaveAndMaterialSources() {
        SkyIslandDescriptor descriptor = descriptor(653L);
        SkyIslandCaveSiteCapabilityProfile profile =
                new SkyIslandCaveSiteCapabilityProfiler().profile(descriptor);

        assertFalse(profile.systems().isEmpty());
        assertEquals(descriptor, profile.descriptor());
        assertEquals(profile.topology(), profile.geometry().topology());
        assertEquals(profile.geometry(), profile.exposure().geometry());
        assertEquals(
                SkyIslandMaterialFamilyPlanner.plan(descriptor),
                profile.materialPlan());
        assertEquals(profile.topology().systems().size(), profile.systems().size());

        for (int ordinal = 0; ordinal < profile.systems().size(); ordinal++) {
            SkyIslandCaveSiteCapabilitySystem system = profile.systems().get(ordinal);
            assertEquals(profile.topology().systems().get(ordinal), system.sourceSystem());
            assertEquals(system.sourceSystem().systemId(), system.sourceGeometry().systemId());
            assertEquals(system.sourceSystem().nodes().size(), system.nodeEvidence().size());
        }
    }

    @Test
    void nodeEvidenceReconstructsExactGeologyAndNearestAuth0033Host() {
        SkyIslandDescriptor descriptor = descriptor(1051L);
        SkyIslandCaveSiteCapabilityProfile profile =
                new SkyIslandCaveSiteCapabilityProfiler().profile(descriptor);
        SkyIslandGeologyFieldSet geology = SkyIslandGeologyFieldSet.create(descriptor);

        for (SkyIslandCaveSiteCapabilitySystem system : profile.systems()) {
            for (SkyIslandCaveSiteNodeEvidence evidence : system.nodeEvidence()) {
                assertEquals(geology.sample(evidence.sourceNode().position()), evidence.geology());

                SkyIslandMaterialFamilyCell expected =
                        nearestHost(descriptor, profile.materialPlan(), evidence.sourceNode().position());
                assertEquals(expected, evidence.nearestHostCell());
                assertEquals(
                        normalizedDistance(
                                descriptor,
                                evidence.sourceNode().position(),
                                expected.position()),
                        evidence.normalizedHostDistance(),
                        0.0);
            }
        }
    }

    @Test
    void exposureEvidenceIsExactAndSealedSystemsRemainEmpty() {
        SkyIslandDescriptor descriptor = descriptor(1439L);
        SkyIslandCaveSiteCapabilityProfile profile =
                new SkyIslandCaveSiteCapabilityProfiler().profile(descriptor);
        Map<Integer, SkyIslandCaveExposureIntent> intents = new HashMap<>();
        for (SkyIslandCaveExposureIntent intent : profile.exposure().intents()) {
            intents.put(intent.systemId(), intent);
        }

        long observedExposed = 0L;
        long observedSealed = 0L;
        for (SkyIslandCaveSiteCapabilitySystem system : profile.systems()) {
            SkyIslandCaveExposureIntent expected = intents.get(system.systemId());
            if (expected == null) {
                assertTrue(system.exteriorExposure().isEmpty());
                observedSealed++;
            } else {
                assertEquals(expected, system.exteriorExposure().orElseThrow());
                assertTrue(system.hasAcceptedExteriorExposure());
                observedExposed++;
            }
        }

        assertEquals(profile.exposure().intents().size(), observedExposed);
        assertEquals(profile.systems().size() - observedExposed, observedSealed);
        assertEquals(observedExposed, profile.exposedSystemCount());
    }

    @Test
    void summaryMetricsReconstructFromExactSources() {
        SkyIslandCaveSiteCapabilityProfile profile =
                new SkyIslandCaveSiteCapabilityProfiler().profile(descriptor(3670L));

        for (SkyIslandCaveSiteCapabilitySystem system : profile.systems()) {
            double radius = profile.descriptor().nominalRadius();

            assertEquals(
                    system.sourceSystem().nodes().stream()
                            .mapToDouble(node -> node.position().depthFraction())
                            .min()
                            .orElse(0.0),
                    system.minimumNodeDepth(),
                    0.0);
            assertEquals(
                    system.sourceSystem().nodes().stream()
                            .mapToDouble(node -> node.position().depthFraction())
                            .average()
                            .orElse(0.0),
                    system.meanNodeDepth(),
                    0.0);
            assertEquals(
                    system.sourceGeometry().chambers().stream()
                            .mapToDouble(chamber -> chamber.horizontalRadius() / radius)
                            .max()
                            .orElse(0.0),
                    system.maximumNormalizedChamberHorizontalRadius(),
                    0.0);
            assertEquals(
                    system.nodeEvidence().stream()
                            .mapToDouble(evidence -> evidence.geology().fractureIntensity())
                            .average()
                            .orElse(0.0),
                    system.meanFractureIntensity(),
                    0.0);
            assertEquals(
                    system.nodeEvidence().stream()
                            .mapToDouble(evidence ->
                                    evidence.nearestHostCell().mineralBearingStructuralHost())
                            .max()
                            .orElse(0.0),
                    system.peakMineralBearingHostSupport(),
                    0.0);
        }
    }

    @Test
    void caveFreeDescriptorsDoNotGainSyntheticCapabilitySystems() {
        for (long key : new long[] {2332L, 2211L}) {
            SkyIslandCaveSiteCapabilityProfile profile =
                    new SkyIslandCaveSiteCapabilityProfiler().profile(descriptor(key));
            assertTrue(profile.topology().systems().isEmpty());
            assertTrue(profile.geometry().systems().isEmpty());
            assertTrue(profile.exposure().intents().isEmpty());
            assertTrue(profile.systems().isEmpty());
            assertEquals(0, profile.systemCount());
            assertEquals(0L, profile.exposedSystemCount());
        }
    }

    @Test
    void repeatedProfileIsDeterministic() {
        SkyIslandDescriptor descriptor = descriptor(653L);
        SkyIslandCaveSiteCapabilityProfiler profiler =
                new SkyIslandCaveSiteCapabilityProfiler();

        assertEquals(profiler.profile(descriptor), profiler.profile(descriptor));
    }

    @Test
    void radiusOnlyScalingPreservesNormalizedCapabilityAndSystemIdentity() {
        SkyIslandDescriptor small = descriptor(1051L);
        SkyIslandDescriptor large = withRadius(small, small.nominalRadius() * 2.0);

        SkyIslandCaveSiteCapabilityProfile first =
                new SkyIslandCaveSiteCapabilityProfiler().profile(small);
        SkyIslandCaveSiteCapabilityProfile second =
                new SkyIslandCaveSiteCapabilityProfiler().profile(large);

        assertEquals(first.systems().size(), second.systems().size());
        for (int ordinal = 0; ordinal < first.systems().size(); ordinal++) {
            SkyIslandCaveSiteCapabilitySystem a = first.systems().get(ordinal);
            SkyIslandCaveSiteCapabilitySystem b = second.systems().get(ordinal);
            assertEquals(a.systemId(), b.systemId());
            assertEquals(a.nodeCount(), b.nodeCount());
            assertEquals(a.linkCount(), b.linkCount());
            assertEquals(a.chamberCount(), b.chamberCount());
            assertEquals(a.passageCount(), b.passageCount());
            assertEquals(a.minimumNodeDepth(), b.minimumNodeDepth(), 1.0e-12);
            assertEquals(a.meanNodeDepth(), b.meanNodeDepth(), 1.0e-12);
            assertEquals(a.maximumNodeDepth(), b.maximumNodeDepth(), 1.0e-12);
            assertEquals(
                    a.meanNormalizedChamberHorizontalRadius(),
                    b.meanNormalizedChamberHorizontalRadius(),
                    1.0e-12);
            assertEquals(
                    a.maximumNormalizedChamberHorizontalRadius(),
                    b.maximumNormalizedChamberHorizontalRadius(),
                    1.0e-12);
            assertEquals(a.meanFractureIntensity(), b.meanFractureIntensity(), 1.0e-12);
            assertEquals(
                    a.meanMineralBearingHostSupport(),
                    b.meanMineralBearingHostSupport(),
                    1.0e-12);
            assertEquals(a.nodeEvidence().size(), b.nodeEvidence().size());
            for (int node = 0; node < a.nodeEvidence().size(); node++) {
                assertEquals(
                        a.nodeEvidence().get(node).nearestHostCell().index(),
                        b.nodeEvidence().get(node).nearestHostCell().index());
                assertEquals(
                        a.nodeEvidence().get(node).normalizedHostDistance(),
                        b.nodeEvidence().get(node).normalizedHostDistance(),
                        1.0e-12);
            }
        }
    }

    @Test
    void publicProfilerDoesNotPublishUsefulnessOrStructurePolicy() {
        Method[] publicProfiles = Arrays.stream(
                        SkyIslandCaveSiteCapabilityProfiler.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> method.getName().equals("profile"))
                .toArray(Method[]::new);

        assertEquals(1, publicProfiles.length);
        assertEquals(
                List.of(SkyIslandDescriptor.class),
                List.of(publicProfiles[0].getParameterTypes()));

        List<String> forbidden =
                List.of("useful", "minecapable", "dungeon", "progression", "structuretier", "loot");
        for (Class<?> type : List.of(
                SkyIslandCaveSiteCapabilityProfile.class,
                SkyIslandCaveSiteCapabilitySystem.class,
                SkyIslandCaveSiteNodeEvidence.class)) {
            for (Method method : type.getDeclaredMethods()) {
                String name = method.getName().toLowerCase(java.util.Locale.ROOT);
                assertFalse(
                        forbidden.stream().anyMatch(name::contains),
                        type.getSimpleName() + "." + method.getName());
            }
        }
    }

    private static SkyIslandMaterialFamilyCell nearestHost(
            SkyIslandDescriptor descriptor,
            SkyIslandMaterialFamilyPlan plan,
            SkyIslandSubsurfacePosition position) {
        return plan.cells().stream()
                .min((first, second) -> {
                    double a = normalizedDistance(descriptor, position, first.position());
                    double b = normalizedDistance(descriptor, position, second.position());
                    int distance = Double.compare(a, b);
                    return distance != 0
                            ? distance
                            : Integer.compare(first.index(), second.index());
                })
                .orElseThrow();
    }

    private static double normalizedDistance(
            SkyIslandDescriptor descriptor,
            SkyIslandSubsurfacePosition first,
            SkyIslandSubsurfacePosition second) {
        double radius = descriptor.nominalRadius();
        double dx = (second.x() - first.x()) / radius;
        double dz = (second.z() - first.z()) / radius;
        double dd = second.depthFraction() - first.depthFraction();
        return Math.sqrt(dx * dx + dz * dz + dd * dd);
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 8L, 81L, key));
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
