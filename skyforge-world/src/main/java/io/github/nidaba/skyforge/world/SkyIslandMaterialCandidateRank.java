package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * AUTH-0041 backend-neutral semantic rank for one AUTH-0040-compatible capability profile.
 *
 * <p>Higher values are preferred for every component. Candidate/backend identity is deliberately
 * absent. Exact semantic ties remain ties for a downstream adapter-owned stable identity rule.
 */
public record SkyIslandMaterialCandidateRank(
        SkyIslandMaterialBindingRequest request,
        SkyIslandMaterialCapabilityProfile profile,
        double minimumRequiredHeadroom,
        double meanRequiredHeadroom,
        double specializationPurity,
        double requestAffinity) {

    public SkyIslandMaterialCandidateRank {
        request = Objects.requireNonNull(request, "request");
        profile = Objects.requireNonNull(profile, "profile");
        requireNormalized("minimumRequiredHeadroom", minimumRequiredHeadroom);
        requireNormalized("meanRequiredHeadroom", meanRequiredHeadroom);
        requireNormalized("specializationPurity", specializationPurity);
        requireNormalized("requestAffinity", requestAffinity);
        if (!SkyIslandMaterialCompatibilityEvaluator.evaluate(request, profile).compatible()) {
            throw new IllegalArgumentException(
                    "material candidate rank requires an AUTH-0040-compatible profile");
        }
    }

    public boolean semanticallyTiedWith(SkyIslandMaterialCandidateRank other) {
        Objects.requireNonNull(other, "other");
        requireSameRequest(other);
        return same(minimumRequiredHeadroom, other.minimumRequiredHeadroom)
                && same(meanRequiredHeadroom, other.meanRequiredHeadroom)
                && same(specializationPurity, other.specializationPurity)
                && same(requestAffinity, other.requestAffinity);
    }

    public void requireSameRequest(SkyIslandMaterialCandidateRank other) {
        if (!request.equals(other.request())) {
            throw new IllegalArgumentException(
                    "material candidate ranks can only be compared for the same request");
        }
    }

    private static boolean same(double first, double second) {
        return Math.abs(first - second) <= 1.0e-12;
    }

    private static void requireNormalized(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
    }
}
