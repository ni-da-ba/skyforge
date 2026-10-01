package io.github.nidaba.skyforge.world;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/**
 * AUTH-0041 semantic ranking for AUTH-0040-compatible backend-neutral capability profiles.
 *
 * <p>Ranking is stable for one AUTH-0039 request and capability profile. It never depends on local
 * sample position, traversal order, candidate encounter order, or backend material identity.
 */
public final class SkyIslandMaterialCandidateRanker {
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private static final Comparator<SkyIslandMaterialCandidateRank> BEST_FIRST =
            Comparator.comparingDouble(
                            SkyIslandMaterialCandidateRank::minimumRequiredHeadroom)
                    .reversed()
                    .thenComparing(
                            Comparator.comparingDouble(
                                            SkyIslandMaterialCandidateRank::meanRequiredHeadroom)
                                    .reversed())
                    .thenComparing(
                            Comparator.comparingDouble(
                                            SkyIslandMaterialCandidateRank::specializationPurity)
                                    .reversed())
                    .thenComparing(
                            Comparator.comparingDouble(
                                            SkyIslandMaterialCandidateRank::requestAffinity)
                                    .reversed());

    private SkyIslandMaterialCandidateRanker() {}

    public static SkyIslandMaterialCandidateRank rank(
            SkyIslandMaterialBindingRequest request,
            SkyIslandMaterialCapabilityProfile profile) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(profile, "profile");

        SkyIslandMaterialCompatibilityAssessment compatibility =
                SkyIslandMaterialCompatibilityEvaluator.evaluate(request, profile);
        if (!compatibility.compatible()) {
            throw new IllegalArgumentException(
                    "AUTH-0041 cannot rank an AUTH-0040-incompatible profile");
        }

        double minimumHeadroom = 1.0;
        double sumHeadroom = 0.0;
        EnumSet<SkyIslandMaterialCapability> required =
                EnumSet.noneOf(SkyIslandMaterialCapability.class);

        for (SkyIslandMaterialCapabilityEvaluation evaluation :
                compatibility.evaluations()) {
            double headroom =
                    normalizedHeadroom(
                            evaluation.requiredMinimum(), evaluation.advertised());
            minimumHeadroom = Math.min(minimumHeadroom, headroom);
            sumHeadroom += headroom;
            required.add(evaluation.capability());
        }

        double meanHeadroom =
                sumHeadroom / compatibility.evaluations().size();

        double unrequiredTotal = 0.0;
        int unrequiredCount = 0;
        for (SkyIslandMaterialCapability capability : SkyIslandMaterialCapability.values()) {
            if (!required.contains(capability)) {
                unrequiredTotal += profile.capability(capability);
                unrequiredCount++;
            }
        }
        double specializationPurity =
                unrequiredCount == 0
                        ? 1.0
                        : 1.0 - unrequiredTotal / unrequiredCount;

        return new SkyIslandMaterialCandidateRank(
                request,
                profile,
                minimumHeadroom,
                meanHeadroom,
                specializationPurity,
                requestAffinity(request, profile));
    }

    public static List<SkyIslandMaterialCandidateRank> rankCompatible(
            SkyIslandMaterialBindingRequest request,
            List<SkyIslandMaterialCapabilityProfile> profiles) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(profiles, "profiles");

        List<SkyIslandMaterialCandidateRank> ranks = new ArrayList<>();
        for (SkyIslandMaterialCapabilityProfile profile : profiles) {
            Objects.requireNonNull(profile, "profile");
            if (SkyIslandMaterialCompatibilityEvaluator.evaluate(request, profile).compatible()) {
                ranks.add(rank(request, profile));
            }
        }
        ranks.sort(BEST_FIRST);
        return List.copyOf(ranks);
    }

    public static int compareBestFirst(
            SkyIslandMaterialCandidateRank first,
            SkyIslandMaterialCandidateRank second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        first.requireSameRequest(second);
        return BEST_FIRST.compare(first, second);
    }

    public static double requestAffinity(
            SkyIslandMaterialBindingRequest request,
            SkyIslandMaterialCapabilityProfile profile) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(profile, "profile");

        long hash = FNV_OFFSET_BASIS;
        for (byte value :
                request.bindingKey().canonicalToken().getBytes(StandardCharsets.UTF_8)) {
            hash = fnv(hash, value & 0xFFL);
        }
        for (SkyIslandMaterialCapability capability : SkyIslandMaterialCapability.values()) {
            long bits = Double.doubleToLongBits(profile.capability(capability));
            for (int shift = 0; shift < Long.SIZE; shift += Byte.SIZE) {
                hash = fnv(hash, (bits >>> shift) & 0xFFL);
            }
        }
        long mantissa = hash >>> 11;
        return mantissa * 0x1.0p-53;
    }

    private static double normalizedHeadroom(double required, double advertised) {
        if (required >= 1.0) {
            return advertised >= 1.0 ? 1.0 : 0.0;
        }
        double value = (advertised - required) / (1.0 - required);
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static long fnv(long hash, long value) {
        return (hash ^ value) * FNV_PRIME;
    }
}
