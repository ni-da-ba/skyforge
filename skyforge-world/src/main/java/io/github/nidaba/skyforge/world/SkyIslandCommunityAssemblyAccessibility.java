package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * One parameterized community-assembly accessibility result.
 *
 * <p>This record binds exact raw assembly evidence to the exact dispersal profile that interpreted it.
 * Accessibility remains separate from local community suitability and does not imply occupancy.
 */
public record SkyIslandCommunityAssemblyAccessibility(
        SkyIslandCommunityAssemblyEvidence evidence,
        SkyIslandCommunityDispersalProfile dispersalProfile,
        OptionalDouble accessibility) {

    public SkyIslandCommunityAssemblyAccessibility {
        evidence = Objects.requireNonNull(evidence, "evidence");
        dispersalProfile = Objects.requireNonNull(dispersalProfile, "dispersalProfile");
        accessibility = Objects.requireNonNull(accessibility, "accessibility");
        if (accessibility.isPresent()) {
            double value = accessibility.orElseThrow();
            if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
                throw new IllegalArgumentException(
                        "accessibility must be finite and normalized when present");
            }
        }
    }

    /** Evaluates one explicit dispersal profile against exact raw assembly evidence. */
    public static SkyIslandCommunityAssemblyAccessibility evaluate(
            SkyIslandCommunityAssemblyEvidence evidence,
            SkyIslandCommunityDispersalProfile dispersalProfile) {
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(dispersalProfile, "dispersalProfile");
        return new SkyIslandCommunityAssemblyAccessibility(
                evidence,
                dispersalProfile,
                dispersalProfile.accessibility(evidence));
    }

    public boolean resolved() {
        return accessibility.isPresent();
    }
}
