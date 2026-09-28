package io.github.nidaba.skyforge.world.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.world.SkyIslandCommunityArchetype;
import io.github.nidaba.skyforge.world.SkyIslandLinearSupportRealizationTransform;
import io.github.nidaba.skyforge.world.SkyIslandLocalPosition;
import io.github.nidaba.skyforge.world.SkyIslandSurfaceSiteCapabilityCell;
import io.github.nidaba.skyforge.world.SkyIslandVegetationFunctionalGroup;
import io.github.nidaba.skyforge.world.SkyIslandWeightedMeanFunctionalGroupNicheTransform;
import java.util.EnumSet;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

final class SkyIslandVegetationContentPolicyTest {

    @Test
    void exhaustivelyDefinesEveryAcceptedCommunityArchetype() {
        EnumSet<SkyIslandCommunityArchetype> seen =
                EnumSet.noneOf(SkyIslandCommunityArchetype.class);

        for (SkyIslandCommunityArchetype community : SkyIslandCommunityArchetype.values()) {
            var policy = SkyIslandVegetationContentPolicy.communityPolicy(community);
            assertEquals(community, policy.community());
            assertSame(
                    SkyIslandLinearSupportRealizationTransform.INSTANCE,
                    policy.realizationProfile().transform());
            assertEquals(policy.capacity(), policy.realizationProfile().capacity());
            seen.add(policy.community());
        }

        assertEquals(EnumSet.allOf(SkyIslandCommunityArchetype.class), seen);
    }

    @Test
    void structuralPolicyExpressesDistinctCommunityFormsWithoutBackendIdentity() {
        var woodland = SkyIslandVegetationContentPolicy
                .communityPolicy(SkyIslandCommunityArchetype.CLOSED_WOODLAND)
                .capacity();
        var open = SkyIslandVegetationContentPolicy
                .communityPolicy(SkyIslandCommunityArchetype.OPEN_HERBACEOUS)
                .capacity();
        var wetland = SkyIslandVegetationContentPolicy
                .communityPolicy(SkyIslandCommunityArchetype.SATURATED_WETLAND)
                .capacity();
        var alpine = SkyIslandVegetationContentPolicy
                .communityPolicy(SkyIslandCommunityArchetype.ALPINE_TUNDRA)
                .capacity();
        var xeric = SkyIslandVegetationContentPolicy
                .communityPolicy(SkyIslandCommunityArchetype.XERIC_SCRUB)
                .capacity();

        assertTrue(woodland.canopyCoverCapacity() > open.canopyCoverCapacity());
        assertTrue(woodland.canopyHeightPotential() > open.canopyHeightPotential());
        assertTrue(open.groundCoverCapacity() > woodland.groundCoverCapacity());
        assertTrue(
                wetland.organicSurfaceAccumulationPotential()
                        > open.organicSurfaceAccumulationPotential());
        assertTrue(wetland.understoryDensityCapacity() > open.understoryDensityCapacity());
        assertTrue(alpine.canopyHeightPotential() < xeric.canopyHeightPotential());
        assertTrue(xeric.patchinessPotential() > woodland.patchinessPotential());
    }

    @Test
    void exhaustivelyDefinesEveryAcceptedVegetationFunctionalGroup() {
        EnumSet<SkyIslandVegetationFunctionalGroup> seen =
                EnumSet.noneOf(SkyIslandVegetationFunctionalGroup.class);

        for (SkyIslandVegetationFunctionalGroup group :
                SkyIslandVegetationFunctionalGroup.values()) {
            var policy = SkyIslandVegetationContentPolicy.functionalGroupPolicy(group);
            var evidence = group == SkyIslandVegetationFunctionalGroup.AQUATIC_PLANT
                    ? Optional.of(cell(true))
                    : Optional.<SkyIslandSurfaceSiteCapabilityCell>empty();
            var nicheProfile = policy.nicheProfile(evidence).orElseThrow();
            assertEquals(group, policy.functionalGroup());
            assertEquals(group, nicheProfile.functionalGroup());
            assertSame(
                    SkyIslandWeightedMeanFunctionalGroupNicheTransform.INSTANCE,
                    nicheProfile.transform());
            assertEquals(policy.structuralAffinity(), nicheProfile.structuralAffinity());
            seen.add(policy.functionalGroup());
        }

        assertEquals(EnumSet.allOf(SkyIslandVegetationFunctionalGroup.class), seen);
    }

    @Test
    void functionalRolesExposeDistinctStructuralDependencies() {
        var canopy = SkyIslandVegetationContentPolicy
                .functionalGroupPolicy(SkyIslandVegetationFunctionalGroup.TALL_CANOPY_TREE)
                .structuralAffinity();
        var grass = SkyIslandVegetationContentPolicy
                .functionalGroupPolicy(SkyIslandVegetationFunctionalGroup.GRASS_FORB)
                .structuralAffinity();
        var decomposer = SkyIslandVegetationContentPolicy
                .functionalGroupPolicy(SkyIslandVegetationFunctionalGroup.DECOMPOSER_FUNGUS)
                .structuralAffinity();

        assertTrue(canopy.canopyCoverWeight() > 0.0);
        assertTrue(canopy.canopyHeightPotentialWeight() > 0.0);
        assertEquals(0.0, grass.canopyCoverWeight(), 0.0);
        assertTrue(grass.groundCoverWeight() > 0.0);
        assertTrue(decomposer.organicSurfaceAccumulationPotentialWeight() > 0.0);
        assertTrue(decomposer.deadwoodPotentialWeight() > 0.0);
    }

    @Test
    void aquaticPlantAloneRequiresExactRetainedWaterbodyEvidence() {
        for (SkyIslandVegetationFunctionalGroup group :
                SkyIslandVegetationFunctionalGroup.values()) {
            var expected = group == SkyIslandVegetationFunctionalGroup.AQUATIC_PLANT
                    ? SkyIslandVegetationContentPolicy.HydrologicRequirement.RETAINED_WATERBODY
                    : SkyIslandVegetationContentPolicy.HydrologicRequirement.NONE;
            assertEquals(
                    expected,
                    SkyIslandVegetationContentPolicy
                            .functionalGroupPolicy(group)
                            .hydrologicRequirement());
        }

        assertFalse(SkyIslandVegetationContentPolicy.hydrologicallyEligible(
                SkyIslandVegetationFunctionalGroup.AQUATIC_PLANT,
                Optional.empty()));
        assertFalse(SkyIslandVegetationContentPolicy.hydrologicallyEligible(
                SkyIslandVegetationFunctionalGroup.AQUATIC_PLANT,
                Optional.of(cell(false))));
        assertTrue(SkyIslandVegetationContentPolicy.hydrologicallyEligible(
                SkyIslandVegetationFunctionalGroup.AQUATIC_PLANT,
                Optional.of(cell(true))));
        assertTrue(SkyIslandVegetationContentPolicy.hydrologicallyEligible(
                SkyIslandVegetationFunctionalGroup.TALL_CANOPY_TREE,
                Optional.empty()));

        var aquatic = SkyIslandVegetationContentPolicy.functionalGroupPolicy(
                SkyIslandVegetationFunctionalGroup.AQUATIC_PLANT);
        assertTrue(aquatic.nicheProfile(Optional.empty()).isEmpty());
        assertTrue(aquatic.nicheProfile(Optional.of(cell(false))).isEmpty());
        assertTrue(aquatic.nicheProfile(Optional.of(cell(true))).isPresent());

        var canopy = SkyIslandVegetationContentPolicy.functionalGroupPolicy(
                SkyIslandVegetationFunctionalGroup.TALL_CANOPY_TREE);
        assertTrue(canopy.nicheProfile(Optional.empty()).isPresent());
    }

    private static SkyIslandSurfaceSiteCapabilityCell cell(boolean retainedWaterbody) {
        return new SkyIslandSurfaceSiteCapabilityCell(
                0,
                new SkyIslandLocalPosition(0.0, 0.0),
                true,
                OptionalDouble.of(0.0),
                0.8,
                1.0,
                1.0,
                1.0,
                OptionalDouble.of(0.1),
                OptionalDouble.of(0.1),
                OptionalDouble.of(0.1),
                OptionalDouble.of(0.05),
                0.0,
                retainedWaterbody,
                false,
                retainedWaterbody ? 0.3 : 0.0,
                0.0,
                0.0,
                0.0,
                0.0);
    }
}
