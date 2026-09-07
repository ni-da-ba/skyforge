package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandEcologyRegime;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import org.junit.jupiter.api.Test;

final class SkyIslandEcologicalOpportunityProfilerTest {
    @Test
    void deterministicProfileCoversEveryRegimeAndNormalizesComposition() {
        SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(89001L, 8L, 89L, 1L));
        SkyIslandEcologicalOpportunityProfiler profiler =
                new SkyIslandEcologicalOpportunityProfiler();

        SkyIslandEcologicalOpportunityProfile first = profiler.profile(descriptor);
        SkyIslandEcologicalOpportunityProfile second = profiler.profile(descriptor);

        assertEquals(first, second);
        assertEquals(SkyIslandEcologicalOpportunityProfiler.SAMPLES_PER_AXIS, first.samplesPerAxis());
        assertTrue(first.ownedCellCount() > 0L);
        assertTrue(first.horizontalOwnedAreaEstimate() > 0.0);
        assertTrue(first.horizontalOwnedAreaEstimate()
                <= 4.0 * descriptor.nominalRadius() * descriptor.nominalRadius());

        double sum = 0.0;
        for (SkyIslandEcologyRegime regime : SkyIslandEcologyRegime.values()) {
            double fraction = first.regimeFraction(regime);
            assertTrue(fraction >= 0.0 && fraction <= 1.0);
            sum += fraction;
        }
        assertEquals(1.0, sum, 1.0e-12);
    }

    @Test
    void doublingOnlyRadiusPreservesNormalizedEcologyAndQuadruplesPlanningArea() {
        SkyIslandDescriptor base = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(89002L, 8L, 89L, 2L));
        SkyIslandDescriptor doubled = withRadius(base, base.nominalRadius() * 2.0);

        SkyIslandEcologicalOpportunityProfiler profiler =
                new SkyIslandEcologicalOpportunityProfiler();
        SkyIslandEcologicalOpportunityProfile small = profiler.profile(base);
        SkyIslandEcologicalOpportunityProfile large = profiler.profile(doubled);

        assertEquals(small.ownedCellCount(), large.ownedCellCount());
        assertEquals(
                small.horizontalOwnedAreaEstimate() * 4.0,
                large.horizontalOwnedAreaEstimate(),
                Math.ulp(large.horizontalOwnedAreaEstimate()) * 8.0);
        assertEquals(
                small.meanVegetationPotential(),
                large.meanVegetationPotential(),
                1.0e-12);
        assertEquals(
                small.meanSaturationPotential(),
                large.meanSaturationPotential(),
                1.0e-12);
        assertEquals(
                small.meanThermalSuitability(),
                large.meanThermalSuitability(),
                1.0e-12);
        for (SkyIslandEcologyRegime regime : SkyIslandEcologyRegime.values()) {
            assertEquals(small.regimeFraction(regime), large.regimeFraction(regime), 1.0e-12);
        }
    }

    @Test
    void authoredClimateVariationChangesIslandScaleOpportunityProfile() {
        SkyIslandEcologicalOpportunityProfiler profiler =
                new SkyIslandEcologicalOpportunityProfiler();
        SkyIslandEcologicalOpportunityProfile first = profiler.profile(
                SkyIslandDescriptorGenerator.derive(
                        SkyIslandIdentity.of(89003L, 8L, 89L, 10L)));
        SkyIslandEcologicalOpportunityProfile second = profiler.profile(
                SkyIslandDescriptorGenerator.derive(
                        SkyIslandIdentity.of(89003L, 8L, 89L, 11L)));

        assertNotEquals(first.descriptor().identity(), second.descriptor().identity());
        boolean continuousDiffers =
                Math.abs(first.meanVegetationPotential() - second.meanVegetationPotential()) > 1.0e-9
                        || Math.abs(first.meanSaturationPotential() - second.meanSaturationPotential())
                                > 1.0e-9
                        || Math.abs(first.meanThermalSuitability() - second.meanThermalSuitability())
                                > 1.0e-9;
        boolean compositionDiffers = false;
        for (SkyIslandEcologyRegime regime : SkyIslandEcologyRegime.values()) {
            compositionDiffers |=
                    Math.abs(first.regimeFraction(regime) - second.regimeFraction(regime)) > 1.0e-9;
        }
        assertTrue(continuousDiffers || compositionDiffers);
    }

    @Test
    void horizontalAreaIsPlanningEvidenceNotPhysicalSurfaceAuthority() {
        SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(89004L, 8L, 89L, 4L));
        SkyIslandEcologicalOpportunityProfile profile =
                new SkyIslandEcologicalOpportunityProfiler().profile(descriptor);

        double rasterCellWidth =
                (2.0 * descriptor.nominalRadius())
                        / SkyIslandEcologicalOpportunityProfiler.SAMPLES_PER_AXIS;
        assertEquals(
                profile.ownedCellCount() * rasterCellWidth * rasterCellWidth,
                profile.horizontalOwnedAreaEstimate(),
                0.0);
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
