package io.github.nidaba.skyforge.world.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import io.github.nidaba.skyforge.world.SkyIslandBaseMetalKind;
import io.github.nidaba.skyforge.world.SkyIslandBaseMetalOpportunityProfile;
import io.github.nidaba.skyforge.world.SkyIslandBaseMetalOpportunityProfiler;
import io.github.nidaba.skyforge.world.SkyIslandDescriptorGenerator;
import java.util.Arrays;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

final class SkyIslandBaseMetalContentPolicyTest {
    private static final long WORLD = 0x434f4e54454e5432L;

    @Test
    void exhaustivelyMapsEveryAcceptedAuth0093Metal() {
        EnumSet<SkyIslandBaseMetalKind> seen = EnumSet.noneOf(SkyIslandBaseMetalKind.class);
        for (SkyIslandBaseMetalKind kind : SkyIslandBaseMetalKind.values()) {
            SkyIslandBaseMetalContentPolicy.Entry entry =
                    SkyIslandBaseMetalContentPolicy.policyFor(kind);
            assertEquals(kind, entry.kind());
            assertEquals(
                    SkyIslandBaseMetalContentPolicy.AvailabilityClass.COMMON_REGIONAL,
                    entry.availabilityClass());
            assertEquals(
                    SkyIslandBaseMetalContentPolicy.AlternativeAccess.BOUNDED_TRADE_OR_SALVAGE,
                    entry.alternativeAccess());
            seen.add(entry.kind());
        }
        assertEquals(EnumSet.allOf(SkyIslandBaseMetalKind.class), seen);
    }

    @Test
    void ironOwnsBootstrapGuaranteeWhileCopperAndZincRemainPostFlight() {
        SkyIslandBaseMetalContentPolicy.Entry iron =
                SkyIslandBaseMetalContentPolicy.policyFor(SkyIslandBaseMetalKind.IRON);
        SkyIslandBaseMetalContentPolicy.Entry copper =
                SkyIslandBaseMetalContentPolicy.policyFor(SkyIslandBaseMetalKind.COPPER);
        SkyIslandBaseMetalContentPolicy.Entry zinc =
                SkyIslandBaseMetalContentPolicy.policyFor(SkyIslandBaseMetalKind.ZINC);

        assertTrue(iron.firstFlightCritical());
        assertEquals(
                SkyIslandBaseMetalContentPolicy.GuaranteeScope.STARTING_CLUSTER,
                iron.guaranteeScope());

        assertFalse(copper.firstFlightCritical());
        assertFalse(zinc.firstFlightCritical());
        assertEquals(
                SkyIslandBaseMetalContentPolicy.GuaranteeScope.POST_FLIGHT_PROVINCE,
                copper.guaranteeScope());
        assertEquals(
                SkyIslandBaseMetalContentPolicy.GuaranteeScope.POST_FLIGHT_PROVINCE,
                zinc.guaranteeScope());
    }

    @Test
    void eligibilityAndRankingConsumeAuth0093WithoutInventedThresholds() {
        SkyIslandBaseMetalOpportunityProfile profile =
                new SkyIslandBaseMetalOpportunityProfiler().profile(descriptor(20001L));

        for (SkyIslandBaseMetalKind kind : SkyIslandBaseMetalKind.values()) {
            boolean expectedEligible = profile.peakOpportunity(kind) > 0.0;
            assertEquals(
                    expectedEligible,
                    SkyIslandBaseMetalContentPolicy.geologicallyEligible(profile, kind));
            assertEquals(
                    expectedEligible ? profile.meanOpportunity(kind) : 0.0,
                    SkyIslandBaseMetalContentPolicy.candidateRankScore(profile, kind),
                    0.0);
        }
    }

    @Test
    void fixedAvailabilityPolicyDoesNotDependOnOpportunityMagnitude() {
        SkyIslandBaseMetalOpportunityProfiler profiler = new SkyIslandBaseMetalOpportunityProfiler();
        SkyIslandBaseMetalOpportunityProfile first = profiler.profile(descriptor(20002L));
        SkyIslandBaseMetalOpportunityProfile second = profiler.profile(descriptor(20003L));

        assertTrue(Arrays.stream(SkyIslandBaseMetalKind.values()).anyMatch(kind ->
                Double.doubleToLongBits(first.meanOpportunity(kind))
                        != Double.doubleToLongBits(second.meanOpportunity(kind))));

        for (SkyIslandBaseMetalKind kind : SkyIslandBaseMetalKind.values()) {
            SkyIslandBaseMetalContentPolicy.Entry a =
                    SkyIslandBaseMetalContentPolicy.policyFor(kind);
            SkyIslandBaseMetalContentPolicy.Entry b =
                    SkyIslandBaseMetalContentPolicy.policyFor(kind);
            assertEquals(a, b);
        }
    }

    private static SkyIslandDescriptor descriptor(long islandKey) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(WORLD, 20L, 20L, islandKey));
    }
}
