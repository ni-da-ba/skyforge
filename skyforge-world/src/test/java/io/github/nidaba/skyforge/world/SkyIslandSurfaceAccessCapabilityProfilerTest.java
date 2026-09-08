package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandMorphologyFamily;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.recipes.skyisland.SemanticSkyIslandVolumeRecipe;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

final class SkyIslandSurfaceAccessCapabilityProfilerTest {
    private static final long AUTHORED_WORLD = 0x4155544830303937L;
    private static final long REALIZATION_ROOT = 0x4143434553533039L;

    @Test
    void retainsExactAuth0096SourceAndCanonicalDirectionOrder() {
        SkyIslandSurfaceSiteCapabilityProfile source = source(97001L, 460.0, -210.0);
        SkyIslandSurfaceAccessCapabilityProfile profile =
                new SkyIslandSurfaceAccessCapabilityProfiler().profile(source);

        assertEquals(source, profile.sourceProfile());
        assertEquals(source.association(), profile.association());
        assertEquals(source.gridSize(), profile.gridSize());
        assertEquals(source.spacing(), profile.spacing(), 0.0);
        assertEquals(source.cells().size(), profile.cells().size());

        for (int ordinal = 0; ordinal < profile.cells().size(); ordinal++) {
            SkyIslandSurfaceAccessCapabilityCell cell = profile.cells().get(ordinal);
            assertEquals(source.cells().get(ordinal), cell.sourceCell());
            if (cell.sourceCell().physicalSurfacePresent()) {
                assertEquals(
                        Arrays.asList(SkyIslandSurfaceAccessDirection.values()),
                        cell.rays().stream().map(SkyIslandSurfaceAccessRay::direction).toList());
                assertEquals(8, cell.rays().size());
            } else {
                assertTrue(cell.rays().isEmpty());
            }
        }

        SkyIslandSurfaceAccessCapabilityCell first =
                profile.cells().stream().filter(cell -> !cell.rays().isEmpty()).findFirst().orElseThrow();
        double cardinal = 1.0 / 24.0;
        double diagonal = Math.sqrt(2.0) / 24.0;
        for (SkyIslandSurfaceAccessRay ray : first.rays()) {
            double expected = ray.direction().xStep() != 0 && ray.direction().zStep() != 0
                    ? diagonal
                    : cardinal;
            assertEquals(expected, ray.stepDistanceNormalized(), 1.0e-15);
        }
    }

    @Test
    void repeatedProfilingIsBitStable() {
        SkyIslandSurfaceSiteCapabilityProfile source = source(97002L, -725.0, 315.0);
        SkyIslandSurfaceAccessCapabilityProfiler profiler =
                new SkyIslandSurfaceAccessCapabilityProfiler();

        assertEquals(profiler.profile(source), profiler.profile(source));
    }

    @Test
    void everyRayMatchesIndependentExactPhysicalReconstruction() {
        SkyIslandSurfaceSiteCapabilityProfile source = source(97003L, 1_025.0, 880.0);
        SkyIslandSurfaceAccessCapabilityProfile profile =
                new SkyIslandSurfaceAccessCapabilityProfiler().profile(source);
        SkyIslandCompiledVolumeColumnField physical =
                new SkyIslandCompiledVolumeColumnField(
                        source.association().realizedVolume().compiledVolume());

        double radius = source.association().authoredDescriptor().nominalRadius();
        int gridSize = source.gridSize();
        double spacing = source.spacing();

        long rayCount = 0L;
        for (SkyIslandSurfaceAccessCapabilityCell cell : profile.cells()) {
            if (cell.rays().isEmpty()) {
                continue;
            }
            int index = cell.watershedCellIndex();
            int anchorX = index % gridSize;
            int anchorZ = index / gridSize;
            SkyIslandVerticalColumn anchor =
                    physical.columnAt(position(anchorX, anchorZ, gridSize, radius, spacing)).orElseThrow();

            for (SkyIslandSurfaceAccessRay ray : cell.rays()) {
                rayCount++;
                RayExpected expected = reconstruct(
                        physical,
                        anchor,
                        anchorX,
                        anchorZ,
                        gridSize,
                        radius,
                        spacing,
                        ray.direction());
                assertEquals(expected.availableSteps(), ray.availableStepCount());
                assertEquals(expected.stepDistanceNormalized(), ray.stepDistanceNormalized(), 1.0e-15);
                assertEquals(expected.consecutiveSupported(), ray.consecutiveSupportedStepCount());
                assertOptionalEquals(expected.firstOpenDistance(), ray.firstOpenDistanceNormalized());
                assertEquals(
                        expected.furthestSupportedDistance(),
                        ray.furthestSupportedDistanceNormalized(),
                        1.0e-15);
                assertEquals(expected.openTailSteps(), ray.boundaryOpenTailStepCount());
                assertEquals(
                        expected.openTailDistance(),
                        ray.boundaryOpenTailDistanceNormalized(),
                        1.0e-15);
                assertEquals(expected.transitions(), ray.supportTransitionCount());
                assertEquals(expected.maxRise(), ray.maximumRiseFromAnchorNormalized(), 1.0e-15);
                assertEquals(expected.maxFall(), ray.maximumFallFromAnchorNormalized(), 1.0e-15);
                assertOptionalEquals(expected.meanGrade(), ray.meanAdjacentSupportedGrade());
            }
        }
        assertTrue(rayCount > 0L);
    }

    @Test
    void unsupportedAnchorCannotCarryDirectionalEvidence() {
        SkyIslandSurfaceSiteCapabilityCell unsupported =
                new SkyIslandSurfaceSiteCapabilityCell(
                        17,
                        new SkyIslandLocalPosition(12.0, -4.0),
                        false,
                        OptionalDouble.empty(),
                        0.2,
                        0.0,
                        0.0,
                        0.0,
                        OptionalDouble.empty(),
                        OptionalDouble.empty(),
                        OptionalDouble.empty(),
                        OptionalDouble.empty(),
                        0.0,
                        false,
                        false,
                        0.0,
                        0.0,
                        0.0,
                        0.0,
                        0.0);
        assertEquals(
                unsupported,
                new SkyIslandSurfaceAccessCapabilityCell(unsupported, List.of()).sourceCell());

        SkyIslandSurfaceAccessRay synthetic =
                new SkyIslandSurfaceAccessRay(
                        SkyIslandSurfaceAccessDirection.POSITIVE_X,
                        0,
                        1.0 / 24.0,
                        0,
                        OptionalDouble.empty(),
                        0.0,
                        0,
                        0.0,
                        0,
                        0.0,
                        0.0,
                        OptionalDouble.empty());
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandSurfaceAccessCapabilityCell(unsupported, List.of(synthetic)));
    }

    @Test
    void publicProfilerConsumesOnlyExactAuth0096ProfileAndPublishesNoRoleThreshold() {
        Method[] publicProfiles = Arrays.stream(
                        SkyIslandSurfaceAccessCapabilityProfiler.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> method.getName().equals("profile"))
                .toArray(Method[]::new);

        assertEquals(1, publicProfiles.length);
        assertEquals(
                List.of(SkyIslandSurfaceSiteCapabilityProfile.class),
                List.of(publicProfiles[0].getParameterTypes()));
        assertEquals(
                SkyIslandSurfaceAccessCapabilityProfile.class,
                publicProfiles[0].getReturnType());

        List<String> forbidden =
                List.of("airfield", "runway", "dock", "buildable", "walkable", "accepted", "validsite");
        for (Class<?> type : List.of(
                SkyIslandSurfaceAccessCapabilityProfile.class,
                SkyIslandSurfaceAccessCapabilityCell.class,
                SkyIslandSurfaceAccessRay.class)) {
            for (Method method : type.getDeclaredMethods()) {
                String name = method.getName().toLowerCase(java.util.Locale.ROOT);
                assertFalse(forbidden.stream().anyMatch(name::contains), type.getSimpleName() + "." + method.getName());
            }
        }
    }

    private static RayExpected reconstruct(
            SkyIslandCompiledVolumeColumnField physical,
            SkyIslandVerticalColumn anchor,
            int anchorX,
            int anchorZ,
            int gridSize,
            double radius,
            double spacing,
            SkyIslandSurfaceAccessDirection direction) {
        int available = availableSteps(gridSize, anchorX, anchorZ, direction);
        double physicalStepDistance = spacing * direction.latticeStepLength();
        double stepNormalized = physicalStepDistance / radius;

        boolean[] supported = new boolean[available + 1];
        supported[0] = true;
        boolean previousSupported = true;
        SkyIslandVerticalColumn previousColumn = anchor;
        int consecutive = 0;
        OptionalDouble firstOpen = OptionalDouble.empty();
        double furthest = 0.0;
        int transitions = 0;
        double maxRise = 0.0;
        double maxFall = 0.0;
        double gradeSum = 0.0;
        int gradeCount = 0;

        for (int step = 1; step <= available; step++) {
            int x = anchorX + direction.xStep() * step;
            int z = anchorZ + direction.zStep() * step;
            SkyIslandVerticalColumn column =
                    physical.columnAt(position(x, z, gridSize, radius, spacing)).orElse(null);
            boolean current = column != null;
            supported[step] = current;

            if (current != previousSupported) {
                transitions++;
            }
            if (firstOpen.isEmpty() && !current) {
                firstOpen = OptionalDouble.of(step * stepNormalized);
            }
            if (firstOpen.isEmpty() && current) {
                consecutive++;
            }
            if (current) {
                furthest = step * stepNormalized;
                double delta = (column.upperY() - anchor.upperY()) / radius;
                maxRise = Math.max(maxRise, delta);
                maxFall = Math.max(maxFall, -delta);
                if (previousSupported && previousColumn != null) {
                    gradeSum += Math.abs(column.upperY() - previousColumn.upperY())
                            / physicalStepDistance;
                    gradeCount++;
                }
            }
            previousSupported = current;
            previousColumn = column;
        }

        int openTail = 0;
        for (int step = available; step >= 1 && !supported[step]; step--) {
            openTail++;
        }

        return new RayExpected(
                available,
                stepNormalized,
                consecutive,
                firstOpen,
                furthest,
                openTail,
                openTail * stepNormalized,
                transitions,
                maxRise,
                maxFall,
                gradeCount == 0 ? OptionalDouble.empty() : OptionalDouble.of(gradeSum / gradeCount));
    }

    private static int availableSteps(
            int gridSize,
            int x,
            int z,
            SkyIslandSurfaceAccessDirection direction) {
        int xSteps = direction.xStep() > 0
                ? gridSize - 1 - x
                : direction.xStep() < 0 ? x : Integer.MAX_VALUE;
        int zSteps = direction.zStep() > 0
                ? gridSize - 1 - z
                : direction.zStep() < 0 ? z : Integer.MAX_VALUE;
        return Math.min(xSteps, zSteps);
    }

    private static SkyIslandLocalPosition position(
            int x,
            int z,
            int gridSize,
            double radius,
            double spacing) {
        return new SkyIslandLocalPosition(
                x == gridSize - 1 ? radius : -radius + x * spacing,
                z == gridSize - 1 ? radius : -radius + z * spacing);
    }

    private static void assertOptionalEquals(OptionalDouble expected, OptionalDouble actual) {
        assertEquals(expected.isPresent(), actual.isPresent());
        if (expected.isPresent()) {
            assertEquals(expected.orElseThrow(), actual.orElseThrow(), 1.0e-15);
        }
    }

    private static SkyIslandSurfaceSiteCapabilityProfile source(
            long islandKey,
            double centerX,
            double centerZ) {
        SkyIslandDescriptor authored = authored(islandKey);
        SkyIslandAuthoredRealizationAssociation association =
                SkyIslandAuthoredRealizationAssociation.of(
                        authored,
                        realized(authored, islandKey ^ 0x5a5a5a5aL, centerX, centerZ));
        return new SkyIslandSurfaceSiteCapabilityProfiler().profile(association);
    }

    private static SkyIslandDescriptor authored(long islandKey) {
        SkyIslandDescriptor base = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(AUTHORED_WORLD, 9L, 97L, islandKey));
        return new SkyIslandDescriptor(
                base.schemaVersion(),
                base.identity(),
                base.authorshipSeed(),
                base.morphologyFamily(),
                base.nominalRadius(),
                base.reliefBudget(),
                0.70,
                0.71,
                base.temperatureTendency(),
                0.73,
                base.exposureTendency(),
                0.64,
                0.79,
                base.ecologicalPotential());
    }

    private static SkyIslandWorldVolume realized(
            SkyIslandDescriptor authored,
            long geometrySeed,
            double centerX,
            double centerZ) {
        double radius = authored.nominalRadius();
        SkyIslandMorphologyFamily morphology = authored.morphologyFamily();
        SkyIslandVolumeDescriptor physical = SkyIslandVolumeDescriptor.schema2(
                geometrySeed,
                centerX,
                centerZ,
                320.0,
                radius,
                0.44 * radius,
                0.60 * radius,
                Math.min(0.18 * radius, 34.0),
                0.37,
                0.61,
                0.55,
                0.20,
                morphology,
                0.24,
                0.19 * radius,
                0.29);
        CompiledSkyIslandVolume compiled =
                new SemanticSkyIslandVolumeRecipe().compile(physical);
        SkyIslandWorldVolumeId id =
                new SkyIslandWorldVolumeId(
                        REALIZATION_ROOT,
                        "auth97",
                        0,
                        0,
                        geometrySeed);
        WorldBounds bounds = new WorldBounds(
                centerX - radius * 2.0,
                centerX + radius * 2.0,
                0.0,
                640.0,
                centerZ - radius * 2.0,
                centerZ + radius * 2.0);
        return new SkyIslandWorldVolume(id, bounds, compiled);
    }

    private record RayExpected(
            int availableSteps,
            double stepDistanceNormalized,
            int consecutiveSupported,
            OptionalDouble firstOpenDistance,
            double furthestSupportedDistance,
            int openTailSteps,
            double openTailDistance,
            int transitions,
            double maxRise,
            double maxFall,
            OptionalDouble meanGrade) {}
}
