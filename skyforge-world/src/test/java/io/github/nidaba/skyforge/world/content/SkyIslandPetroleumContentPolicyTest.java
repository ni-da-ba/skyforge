package io.github.nidaba.skyforge.world.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import io.github.nidaba.skyforge.world.SkyIslandPetroleumSystemOpportunityProfile;
import io.github.nidaba.skyforge.world.SkyIslandPetroleumSystemOpportunityProfiler;
import org.junit.jupiter.api.Test;

final class SkyIslandPetroleumContentPolicyTest {
    private static final long WORLD = 0x434f4e54454e5433L;

    @Test
    void fixesPetroleumToStrategicNodeMatureIndustryWithoutOrdinaryProvinceGuarantee() {
        SkyIslandPetroleumContentPolicy.Entry entry = SkyIslandPetroleumContentPolicy.policy();

        assertEquals(
                SkyIslandPetroleumContentPolicy.AvailabilityClass.STRATEGIC_NODE,
                entry.availabilityClass());
        assertEquals(
                SkyIslandPetroleumContentPolicy.ProgressionBand.R3_MATURE_INDUSTRY,
                entry.progressionBand());
        assertEquals(
                SkyIslandPetroleumContentPolicy.GuaranteePolicy.NO_ORDINARY_PROVINCE_HARD_GUARANTEE,
                entry.guaranteePolicy());
        assertFalse(entry.firstFlightCritical());
        assertEquals(
                SkyIslandPetroleumContentPolicy.AlternativeAccess.BOUNDED_TRADE_OR_SALVAGE,
                entry.alternativeAccess());
        assertEquals(
                SkyIslandPetroleumContentPolicy.IndustrialSupplyPolicy.PRIMARY_EXTRACTION_OR_LOGISTICS_REQUIRED,
                entry.industrialSupplyPolicy());
    }

    @Test
    void requestsCanonicalRegionalInventoryForIntentionalStrategicNodeProvinceSelection() {
        assertEquals(
                SkyIslandPetroleumContentPolicy.RegionalSelectionEvidence
                        .CANONICAL_AUTH_0098_REGIONAL_INVENTORY_REQUIRED,
                SkyIslandPetroleumContentPolicy.policy().regionalSelectionEvidence());
    }

    @Test
    void eligibilityAndRankingConsumeAuth0098WithoutInventedThresholds() {
        SkyIslandPetroleumSystemOpportunityProfiler profiler =
                new SkyIslandPetroleumSystemOpportunityProfiler();

        for (long islandKey = 22001L; islandKey <= 22008L; islandKey++) {
            SkyIslandPetroleumSystemOpportunityProfile profile =
                    profiler.profile(descriptor(islandKey));
            boolean expectedEligible = profile.peakSystemOpportunity() > 0.0;

            assertEquals(
                    expectedEligible,
                    SkyIslandPetroleumContentPolicy.geologicallyEligible(profile));
            assertEquals(
                    expectedEligible ? profile.meanSystemOpportunity() : 0.0,
                    SkyIslandPetroleumContentPolicy.candidateRankScore(profile),
                    0.0);
        }
    }

    @Test
    void fixedGameplayPolicyDoesNotAcceptOpportunityMagnitudeAsPolicyInput() {
        assertEquals(
                SkyIslandPetroleumContentPolicy.policy(),
                SkyIslandPetroleumContentPolicy.policy());
    }

    private static SkyIslandDescriptor descriptor(long islandKey) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(WORLD, 22L, 22L, islandKey));
    }
}
