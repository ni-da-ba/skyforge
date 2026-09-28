package io.github.nidaba.skyforge.world.content;

import io.github.nidaba.skyforge.world.SkyIslandCommunityArchetype;
import io.github.nidaba.skyforge.world.SkyIslandCommunityRealizationProfile;
import io.github.nidaba.skyforge.world.SkyIslandCommunityStructureCapacity;
import io.github.nidaba.skyforge.world.SkyIslandFunctionalGroupStructuralAffinity;
import io.github.nidaba.skyforge.world.SkyIslandLinearSupportRealizationTransform;
import io.github.nidaba.skyforge.world.SkyIslandSurfaceSiteCapabilityCell;
import io.github.nidaba.skyforge.world.SkyIslandVegetationFunctionalGroup;
import io.github.nidaba.skyforge.world.SkyIslandVegetationFunctionalGroupProfile;
import io.github.nidaba.skyforge.world.SkyIslandWeightedMeanFunctionalGroupNicheTransform;
import java.util.Objects;
import java.util.Optional;

/**
 * CONTENT C28 production vegetation policy over accepted backend-neutral ecology contracts.
 *
 * <p>This policy supplies visible structural intent and functional-role affinities. It does not
 * select concrete species, assert occupancy or abundance, define carrying capacity, tune final
 * population density, choose candidate spacing, or name backend blocks/features.
 */
public final class SkyIslandVegetationContentPolicy {

    /** Version of this explicit Content policy. */
    public static final int VERSION = 1;

    /** Raw hydrologic evidence required before one vegetation role may be realized. */
    public enum HydrologicRequirement {
        NONE,
        RETAINED_WATERBODY
    }

    /** Explicit structural-capacity policy for one accepted community archetype. */
    public record CommunityPolicy(
            SkyIslandCommunityArchetype community,
            SkyIslandCommunityStructureCapacity capacity) {

        public CommunityPolicy {
            community = Objects.requireNonNull(community, "community");
            capacity = Objects.requireNonNull(capacity, "capacity");
        }

        /** Creates the accepted neutral realization profile for this Content policy. */
        public SkyIslandCommunityRealizationProfile realizationProfile() {
            return new SkyIslandCommunityRealizationProfile(
                    capacity,
                    SkyIslandLinearSupportRealizationTransform.INSTANCE);
        }
    }

    /** Explicit structural-role policy for one accepted vegetation functional group. */
    public record FunctionalGroupPolicy(
            SkyIslandVegetationFunctionalGroup functionalGroup,
            SkyIslandFunctionalGroupStructuralAffinity structuralAffinity,
            HydrologicRequirement hydrologicRequirement) {

        public FunctionalGroupPolicy {
            functionalGroup = Objects.requireNonNull(functionalGroup, "functionalGroup");
            structuralAffinity =
                    Objects.requireNonNull(structuralAffinity, "structuralAffinity");
            hydrologicRequirement =
                    Objects.requireNonNull(hydrologicRequirement, "hydrologicRequirement");
        }

        /**
         * Returns whether accepted raw AUTH-0096 evidence satisfies this role's hydrologic
         * requirement.
         *
         * <p>Missing evidence fails closed only for hydrology-dependent roles.
         */
        public boolean hydrologicallyEligible(
                Optional<SkyIslandSurfaceSiteCapabilityCell> surfaceEvidence) {
            surfaceEvidence = Objects.requireNonNull(surfaceEvidence, "surfaceEvidence");
            return switch (hydrologicRequirement) {
                case NONE -> true;
                case RETAINED_WATERBODY ->
                        surfaceEvidence.map(SkyIslandSurfaceSiteCapabilityCell::retainedWaterbody)
                                .orElse(false);
            };
        }

        /**
         * Creates the accepted neutral niche profile only when this Content role is eligible.
         *
         * <p>This keeps the hydrologic requirement on the production-facing profile path rather
         * than relying on callers to remember a separate advisory check.
         */
        public Optional<SkyIslandVegetationFunctionalGroupProfile> nicheProfile(
                Optional<SkyIslandSurfaceSiteCapabilityCell> surfaceEvidence) {
            if (!hydrologicallyEligible(surfaceEvidence)) {
                return Optional.empty();
            }
            return Optional.of(new SkyIslandVegetationFunctionalGroupProfile(
                    functionalGroup,
                    structuralAffinity,
                    SkyIslandWeightedMeanFunctionalGroupNicheTransform.INSTANCE));
        }
    }

    private static final CommunityPolicy CLOSED_WOODLAND = new CommunityPolicy(
            SkyIslandCommunityArchetype.CLOSED_WOODLAND,
            new SkyIslandCommunityStructureCapacity(
                    0.90, 0.85, 0.80, 0.60, 0.50, 0.85, 0.45, 0.65, 0.55));

    private static final CommunityPolicy OPEN_HERBACEOUS = new CommunityPolicy(
            SkyIslandCommunityArchetype.OPEN_HERBACEOUS,
            new SkyIslandCommunityStructureCapacity(
                    0.75, 0.10, 0.10, 0.25, 0.90, 0.55, 0.40, 0.30, 0.15));

    private static final CommunityPolicy SATURATED_WETLAND = new CommunityPolicy(
            SkyIslandCommunityArchetype.SATURATED_WETLAND,
            new SkyIslandCommunityStructureCapacity(
                    0.85, 0.35, 0.30, 0.75, 0.85, 0.75, 0.55, 0.75, 0.50));

    private static final CommunityPolicy ALPINE_TUNDRA = new CommunityPolicy(
            SkyIslandCommunityArchetype.ALPINE_TUNDRA,
            new SkyIslandCommunityStructureCapacity(
                    0.45, 0.02, 0.03, 0.20, 0.70, 0.30, 0.50, 0.25, 0.10));

    private static final CommunityPolicy XERIC_SCRUB = new CommunityPolicy(
            SkyIslandCommunityArchetype.XERIC_SCRUB,
            new SkyIslandCommunityStructureCapacity(
                    0.50, 0.08, 0.12, 0.60, 0.50, 0.35, 0.55, 0.12, 0.18));

    private static final FunctionalGroupPolicy TALL_CANOPY_TREE = new FunctionalGroupPolicy(
            SkyIslandVegetationFunctionalGroup.TALL_CANOPY_TREE,
            affinity(1.0, 1.0, 1.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0),
            HydrologicRequirement.NONE);

    private static final FunctionalGroupPolicy SMALL_TREE = new FunctionalGroupPolicy(
            SkyIslandVegetationFunctionalGroup.SMALL_TREE,
            affinity(1.0, 1.0, 0.5, 0.5, 0.0, 0.5, 0.0, 0.0, 0.0),
            HydrologicRequirement.NONE);

    private static final FunctionalGroupPolicy SHRUB = new FunctionalGroupPolicy(
            SkyIslandVegetationFunctionalGroup.SHRUB,
            affinity(0.5, 0.0, 0.0, 1.0, 0.5, 0.0, 0.5, 0.0, 0.0),
            HydrologicRequirement.NONE);

    private static final FunctionalGroupPolicy GRASS_FORB = new FunctionalGroupPolicy(
            SkyIslandVegetationFunctionalGroup.GRASS_FORB,
            affinity(0.5, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0),
            HydrologicRequirement.NONE);

    private static final FunctionalGroupPolicy FERN_GROUNDCOVER = new FunctionalGroupPolicy(
            SkyIslandVegetationFunctionalGroup.FERN_GROUNDCOVER,
            affinity(0.0, 0.0, 0.0, 1.0, 1.0, 0.0, 0.0, 0.5, 0.0),
            HydrologicRequirement.NONE);

    private static final FunctionalGroupPolicy AQUATIC_PLANT = new FunctionalGroupPolicy(
            SkyIslandVegetationFunctionalGroup.AQUATIC_PLANT,
            affinity(0.5, 0.0, 0.0, 0.0, 1.0, 0.5, 0.0, 0.0, 0.0),
            HydrologicRequirement.RETAINED_WATERBODY);

    private static final FunctionalGroupPolicy DECOMPOSER_FUNGUS = new FunctionalGroupPolicy(
            SkyIslandVegetationFunctionalGroup.DECOMPOSER_FUNGUS,
            affinity(0.0, 0.0, 0.0, 0.0, 0.0, 0.25, 0.0, 1.0, 1.0),
            HydrologicRequirement.NONE);

    private SkyIslandVegetationContentPolicy() {}

    /** Returns the complete Content policy for one accepted community archetype. */
    public static CommunityPolicy communityPolicy(SkyIslandCommunityArchetype community) {
        Objects.requireNonNull(community, "community");
        return switch (community) {
            case CLOSED_WOODLAND -> CLOSED_WOODLAND;
            case OPEN_HERBACEOUS -> OPEN_HERBACEOUS;
            case SATURATED_WETLAND -> SATURATED_WETLAND;
            case ALPINE_TUNDRA -> ALPINE_TUNDRA;
            case XERIC_SCRUB -> XERIC_SCRUB;
        };
    }

    /** Returns the complete Content policy for one accepted vegetation functional group. */
    public static FunctionalGroupPolicy functionalGroupPolicy(
            SkyIslandVegetationFunctionalGroup functionalGroup) {
        Objects.requireNonNull(functionalGroup, "functionalGroup");
        return switch (functionalGroup) {
            case TALL_CANOPY_TREE -> TALL_CANOPY_TREE;
            case SMALL_TREE -> SMALL_TREE;
            case SHRUB -> SHRUB;
            case GRASS_FORB -> GRASS_FORB;
            case FERN_GROUNDCOVER -> FERN_GROUNDCOVER;
            case AQUATIC_PLANT -> AQUATIC_PLANT;
            case DECOMPOSER_FUNGUS -> DECOMPOSER_FUNGUS;
        };
    }

    /**
     * Returns whether accepted raw AUTH-0096 evidence satisfies this functional group's explicit
     * hydrologic requirement.
     *
     * <p>Missing evidence fails closed for hydrology-dependent roles. No interpolation, tolerance,
     * water-distance threshold, or combined wetness scalar is introduced.
     */
    public static boolean hydrologicallyEligible(
            SkyIslandVegetationFunctionalGroup functionalGroup,
            Optional<SkyIslandSurfaceSiteCapabilityCell> surfaceEvidence) {
        return functionalGroupPolicy(functionalGroup).hydrologicallyEligible(surfaceEvidence);
    }

    private static SkyIslandFunctionalGroupStructuralAffinity affinity(
            double vegetationDensity,
            double canopyCover,
            double canopyHeight,
            double understoryDensity,
            double groundCover,
            double biomass,
            double patchiness,
            double organicSurface,
            double deadwood) {
        return new SkyIslandFunctionalGroupStructuralAffinity(
                vegetationDensity,
                canopyCover,
                canopyHeight,
                understoryDensity,
                groundCover,
                biomass,
                patchiness,
                organicSurface,
                deadwood);
    }
}
