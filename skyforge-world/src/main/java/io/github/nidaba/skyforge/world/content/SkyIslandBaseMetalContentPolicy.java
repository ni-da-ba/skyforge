package io.github.nidaba.skyforge.world.content;

import io.github.nidaba.skyforge.world.SkyIslandBaseMetalKind;
import io.github.nidaba.skyforge.world.SkyIslandBaseMetalOpportunityProfile;
import java.util.Objects;

/**
 * CONTENT C20 backend-neutral policy over accepted AUTH-0093 base-metal opportunity.
 *
 * <p>This class owns gameplay availability and guarantee semantics only. It must not reinterpret
 * AUTH-0093 normalized opportunity as ore grade, reserves, deposit count/volume, accessibility, or
 * Minecraft placement policy.
 */
public final class SkyIslandBaseMetalContentPolicy {
    public enum AvailabilityClass {
        UBIQUITOUS,
        COMMON_REGIONAL,
        SPECIALIZED_REGIONAL,
        STRATEGIC_NODE,
        EXCEPTIONAL
    }

    public enum GuaranteeScope {
        STARTING_CLUSTER,
        POST_FLIGHT_PROVINCE
    }

    public enum AlternativeAccess {
        BOUNDED_TRADE_OR_SALVAGE
    }

    public record Entry(
            SkyIslandBaseMetalKind kind,
            AvailabilityClass availabilityClass,
            GuaranteeScope guaranteeScope,
            boolean firstFlightCritical,
            AlternativeAccess alternativeAccess) {
        public Entry {
            kind = Objects.requireNonNull(kind, "kind");
            availabilityClass = Objects.requireNonNull(availabilityClass, "availabilityClass");
            guaranteeScope = Objects.requireNonNull(guaranteeScope, "guaranteeScope");
            alternativeAccess = Objects.requireNonNull(alternativeAccess, "alternativeAccess");
        }
    }

    private static final Entry IRON = new Entry(
            SkyIslandBaseMetalKind.IRON,
            AvailabilityClass.COMMON_REGIONAL,
            GuaranteeScope.STARTING_CLUSTER,
            true,
            AlternativeAccess.BOUNDED_TRADE_OR_SALVAGE);

    private static final Entry COPPER = new Entry(
            SkyIslandBaseMetalKind.COPPER,
            AvailabilityClass.COMMON_REGIONAL,
            GuaranteeScope.POST_FLIGHT_PROVINCE,
            false,
            AlternativeAccess.BOUNDED_TRADE_OR_SALVAGE);

    private static final Entry ZINC = new Entry(
            SkyIslandBaseMetalKind.ZINC,
            AvailabilityClass.COMMON_REGIONAL,
            GuaranteeScope.POST_FLIGHT_PROVINCE,
            false,
            AlternativeAccess.BOUNDED_TRADE_OR_SALVAGE);

    private SkyIslandBaseMetalContentPolicy() {}

    /** Returns the fixed Content policy for one accepted AUTH-0093 element family. */
    public static Entry policyFor(SkyIslandBaseMetalKind kind) {
        Objects.requireNonNull(kind, "kind");
        return switch (kind) {
            case IRON -> IRON;
            case COPPER -> COPPER;
            case ZINC -> ZINC;
        };
    }

    /**
     * Returns whether AUTH-0093 supplies any accepted geological opportunity for this metal.
     *
     * <p>Zero opportunity is a hard fail-closed geological boundary. C20 does not inject a resource
     * into zero-opportunity geology to satisfy a gameplay guarantee; the owning planner must select
     * or re-plan an eligible authored scope instead.
     */
    public static boolean geologicallyEligible(
            SkyIslandBaseMetalOpportunityProfile profile,
            SkyIslandBaseMetalKind kind) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(kind, "kind");
        return profile.peakOpportunity(kind) > 0.0;
    }

    /**
     * Returns the unchanged AUTH-0093 mean opportunity for ordinal ranking among eligible
     * candidates.
     *
     * <p>This is not a rarity threshold, quantity estimate, deposit scale, grade, or placement
     * instruction. Zero-opportunity candidates remain ineligible and therefore rank at zero.
     */
    public static double candidateRankScore(
            SkyIslandBaseMetalOpportunityProfile profile,
            SkyIslandBaseMetalKind kind) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(kind, "kind");
        if (!geologicallyEligible(profile, kind)) {
            return 0.0;
        }
        return profile.meanOpportunity(kind);
    }
}
