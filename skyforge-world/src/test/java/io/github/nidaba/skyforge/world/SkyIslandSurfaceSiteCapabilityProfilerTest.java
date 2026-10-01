package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class SkyIslandSurfaceSiteCapabilityProfilerTest {
    private static final long AUTHORED_WORLD = 0x4155544830303936L;
    private static final long REALIZATION_ROOT = 0x5355524653495445L;

    @Test
    void profileRetainsExactAssociationAndWatershedIdentity() {
        SkyIslandAuthoredRealizationAssociation association =
                association(96001L, 420.0, -275.0);
        SkyIslandSurfaceSiteCapabilityProfile profile =
                new SkyIslandSurfaceSiteCapabilityProfiler().profile(association);
        SkyIslandWatershedPlan direct =
                SkyIslandWatershedPlanner.plan(association.authoredDescriptor());

        assertEquals(association, profile.association());
        assertEquals(direct, profile.watershed());
        assertEquals(direct.cells().size(), profile.cells().size());
        assertEquals(49, profile.gridSize());
        assertEquals(
                1.0 / 24.0,
                profile.spacing() / association.authoredDescriptor().nominalRadius(),
                1.0e-15);

        for (int ordinal = 0; ordinal < profile.cells().size(); ordinal++) {
            assertEquals(
                    direct.cells().get(ordinal).index(),
                    profile.cells().get(ordinal).watershedCellIndex());
            assertEquals(
                    direct.cells().get(ordinal).position(),
                    profile.cells().get(ordinal).position());
        }
    }

    @Test
    void repeatedProfilingIsBitStableForAllLocalMeasurements() {
        SkyIslandAuthoredRealizationAssociation association =
                association(96002L, -180.0, 615.0);
        SkyIslandSurfaceSiteCapabilityProfiler profiler =
                new SkyIslandSurfaceSiteCapabilityProfiler();

        var first = profiler.profile(association);
        var second = profiler.profile(association);

        assertEquals(first.watershed(), second.watershed());
        assertEquals(first.visibleHydrology(), second.visibleHydrology());
        assertEquals(first.cells(), second.cells());
    }

    @Test
    void hydrologyEvidenceEqualsAcceptedSourcePlansExactly() {
        SkyIslandAuthoredRealizationAssociation association =
                association(96003L, 900.0, 325.0);
        var profile = new SkyIslandSurfaceSiteCapabilityProfiler().profile(association);
        var hydrology = profile.visibleHydrology();

        Map<Integer, SkyIslandWaterbodyFootprintCell> water = new HashMap<>();
        for (SkyIslandWaterbodyFootprint footprint : hydrology.waterbodies().footprints()) {
            for (SkyIslandWaterbodyFootprintCell cell : footprint.cells()) {
                water.merge(
                        cell.watershedCellIndex(),
                        cell,
                        (a, b) -> a.waterDepthPotential() >= b.waterDepthPotential() ? a : b);
            }
        }
        Map<Integer, Double> margins = new HashMap<>();
        for (SkyIslandWaterbodyMargin margin : hydrology.waterbodyMargins().margins()) {
            for (SkyIslandWaterbodyMarginCell cell : margin.cells()) {
                margins.merge(cell.watershedCellIndex(), cell.marginPotential(), Math::max);
            }
        }
        Map<Integer, Double> riparian = new HashMap<>();
        for (SkyIslandRiparianCell cell : hydrology.coherentHydrology().riparian().cells()) {
            riparian.merge(cell.watershedCellIndex(), cell.riparianPotential(), Math::max);
        }
        Map<Integer, Double> channels = new HashMap<>();
        for (SkyIslandChannelProfile channel : hydrology.coherentHydrology().channels().profiles()) {
            SkyIslandChannelSegment segment = channel.segment();
            channels.merge(segment.sourceCellIndex(), segment.relativeDischarge(), Math::max);
            channels.merge(segment.downstreamCellIndex(), segment.relativeDischarge(), Math::max);
        }
        Map<Integer, SkyIslandHydrologicTerrainSurfaceCell> terrain = new HashMap<>();
        for (SkyIslandHydrologicTerrainSurfaceCell cell :
                hydrology.coherentHydrology().terrainSurface().cells()) {
            terrain.put(cell.watershedCellIndex(), cell);
        }

        for (SkyIslandSurfaceSiteCapabilityCell cell : profile.cells()) {
            int index = cell.watershedCellIndex();
            SkyIslandWaterbodyFootprintCell sourceWater = water.get(index);
            assertEquals(sourceWater != null, cell.retainedWaterbody(), "water " + index);
            assertEquals(
                    sourceWater != null && sourceWater.shoreline(),
                    cell.shoreline(),
                    "shoreline " + index);
            assertEquals(
                    sourceWater == null ? 0.0 : sourceWater.waterDepthPotential(),
                    cell.waterDepthPotential(),
                    0.0,
                    "water depth " + index);
            assertEquals(
                    margins.getOrDefault(index, 0.0),
                    cell.waterbodyMarginPotential(),
                    0.0,
                    "margin " + index);
            assertEquals(
                    riparian.getOrDefault(index, 0.0),
                    cell.riparianPotential(),
                    0.0,
                    "riparian " + index);
            assertEquals(
                    channels.getOrDefault(index, 0.0),
                    cell.channelRelativeDischarge(),
                    0.0,
                    "channel " + index);
            assertEquals(
                    Math.abs(terrain.get(index).netAdjustment()),
                    cell.hydrologicAdjustmentMagnitude(),
                    0.0,
                    "terrain " + index);
        }
    }

    @Test
    void physicalEvidenceIsFiniteAndCarriesNoImplicitSiteAcceptance() {
        SkyIslandAuthoredRealizationAssociation association =
                association(96004L, 0.0, 0.0);
        var profile = new SkyIslandSurfaceSiteCapabilityProfiler().profile(association);

        assertTrue(profile.physicalSurfaceCellCount() > 0);
        for (SkyIslandSurfaceSiteCapabilityCell cell : profile.cells()) {
            assertTrue(cell.authoredInteriority() >= 0.0 && cell.authoredInteriority() <= 1.0);
            assertFraction(cell.support3x3Fraction());
            assertFraction(cell.support5x5Fraction());
            assertFraction(cell.support9x9Fraction());
            assertFraction(cell.normalizedFlowAccumulation());
            assertFraction(cell.waterDepthPotential());
            assertFraction(cell.waterbodyMarginPotential());
            assertFraction(cell.riparianPotential());
            assertFraction(cell.channelRelativeDischarge());
            assertFraction(cell.hydrologicAdjustmentMagnitude());

            if (cell.physicalSurfacePresent()) {
                assertTrue(cell.upperSurfaceOffsetNormalized().isPresent());
                assertTrue(cell.relief3x3Normalized().orElseThrow() >= 0.0);
                assertTrue(cell.relief5x5Normalized().orElseThrow() >= 0.0);
                assertTrue(cell.relief9x9Normalized().orElseThrow() >= 0.0);
                assertTrue(cell.support3x3Fraction() > 0.0);
            } else {
                assertTrue(cell.upperSurfaceOffsetNormalized().isEmpty());
                assertTrue(cell.relief3x3Normalized().isEmpty());
                assertTrue(cell.relief5x5Normalized().isEmpty());
                assertTrue(cell.relief9x9Normalized().isEmpty());
            }
        }

        List<String> forbidden =
                List.of("accepted", "buildable", "walkable", "village", "airfield", "plateau");
        for (Method method : SkyIslandSurfaceSiteCapabilityProfile.class.getDeclaredMethods()) {
            String name = method.getName().toLowerCase(java.util.Locale.ROOT);
            assertFalse(forbidden.stream().anyMatch(name::contains), method.getName());
        }
    }

    @Test
    void authoredAndHydrologicEvidenceRemainStableAcrossDistinctExactRealizations() {
        SkyIslandDescriptor authored = authored(96005L);
        var first = SkyIslandAuthoredRealizationAssociation.of(
                authored, realized(authored, 0, 0, 77123L, 125.0, -310.0));
        var second = SkyIslandAuthoredRealizationAssociation.of(
                authored, realized(authored, 1, 0, 77123L, 2_125.0, 1_690.0));
        var profiler = new SkyIslandSurfaceSiteCapabilityProfiler();

        var a = profiler.profile(first);
        var b = profiler.profile(second);

        assertEquals(a.watershed(), b.watershed());
        assertEquals(a.visibleHydrology(), b.visibleHydrology());
        assertEquals(a.cells().size(), b.cells().size());

        for (int index = 0; index < a.cells().size(); index++) {
            SkyIslandSurfaceSiteCapabilityCell left = a.cells().get(index);
            SkyIslandSurfaceSiteCapabilityCell right = b.cells().get(index);
            assertEquals(left.watershedCellIndex(), right.watershedCellIndex());
            assertEquals(left.position(), right.position());
            assertEquals(left.authoredInteriority(), right.authoredInteriority(), 0.0);
            assertEquals(left.normalizedFlowAccumulation(), right.normalizedFlowAccumulation(), 0.0);
            assertEquals(left.retainedWaterbody(), right.retainedWaterbody());
            assertEquals(left.shoreline(), right.shoreline());
            assertEquals(left.waterDepthPotential(), right.waterDepthPotential(), 0.0);
            assertEquals(left.waterbodyMarginPotential(), right.waterbodyMarginPotential(), 0.0);
            assertEquals(left.riparianPotential(), right.riparianPotential(), 0.0);
            assertEquals(left.channelRelativeDischarge(), right.channelRelativeDischarge(), 0.0);
            assertEquals(
                    left.hydrologicAdjustmentMagnitude(),
                    right.hydrologicAdjustmentMagnitude(),
                    0.0);
        }

        assertFalse(first.realizedVolumeId().equals(second.realizedVolumeId()));
        // Physical metrics intentionally remain tied to each exact realization. The accepted
        // PlanarValueSignal samples world X/Z, so AUTH-0096 must not infer translation invariance.
    }

    @Test
    void publicProfilerAcceptsOnlyOneExactAssociation() {
        Method[] publicProfiles = Arrays.stream(
                        SkyIslandSurfaceSiteCapabilityProfiler.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> method.getName().equals("profile"))
                .toArray(Method[]::new);

        assertEquals(1, publicProfiles.length);
        assertEquals(
                List.of(SkyIslandAuthoredRealizationAssociation.class),
                List.of(publicProfiles[0].getParameterTypes()));
        assertEquals(
                SkyIslandSurfaceSiteCapabilityProfile.class,
                publicProfiles[0].getReturnType());
    }

    private static void assertFraction(double value) {
        assertTrue(Double.isFinite(value));
        assertTrue(value >= 0.0 && value <= 1.0, "fraction " + value);
    }

    private static void assertOptionalClose(
            java.util.OptionalDouble first,
            java.util.OptionalDouble second) {
        assertEquals(first.isPresent(), second.isPresent());
        if (first.isPresent()) {
            assertEquals(first.orElseThrow(), second.orElseThrow(), 1.0e-10);
        }
    }

    private static SkyIslandAuthoredRealizationAssociation association(
            long islandKey,
            double centerX,
            double centerZ) {
        SkyIslandDescriptor authored = authored(islandKey);
        return SkyIslandAuthoredRealizationAssociation.of(
                authored,
                realized(authored, 0, 0, 76000L + islandKey, centerX, centerZ));
    }

    private static SkyIslandDescriptor authored(long islandKey) {
        SkyIslandDescriptor base = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(AUTHORED_WORLD, 9L, 96L, islandKey));
        return new SkyIslandDescriptor(
                base.schemaVersion(),
                base.identity(),
                base.authorshipSeed(),
                base.morphologyFamily(),
                base.nominalRadius(),
                base.reliefBudget(),
                0.67,
                0.74,
                base.temperatureTendency(),
                0.76,
                base.exposureTendency(),
                0.68,
                0.82,
                base.ecologicalPotential());
    }

    private static SkyIslandWorldVolume realized(
            SkyIslandDescriptor authored,
            int groupOrdinal,
            int memberOrdinal,
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
                0.46 * radius,
                0.62 * radius,
                Math.min(0.20 * radius, 36.0),
                0.43,
                0.62,
                0.57,
                0.18,
                morphology,
                0.22,
                0.20 * radius,
                0.31);
        CompiledSkyIslandVolume compiled =
                new SemanticSkyIslandVolumeRecipe().compile(physical);
        SkyIslandWorldVolumeId id =
                new SkyIslandWorldVolumeId(
                        REALIZATION_ROOT,
                        "auth96-" + groupOrdinal,
                        groupOrdinal,
                        memberOrdinal,
                        geometrySeed);
        WorldBounds bounds =
                new WorldBounds(
                        centerX - radius * 2.0,
                        centerX + radius * 2.0,
                        0.0,
                        640.0,
                        centerZ - radius * 2.0,
                        centerZ + radius * 2.0);
        return new SkyIslandWorldVolume(id, bounds, compiled);
    }
}
