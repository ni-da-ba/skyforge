package io.github.nidaba.skyforge.reference.evidence;

import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupMemberPlan;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * AUTH-0084 measurement-only diagnostics for regional morphology review.
 *
 * <p>No value in this record is an aesthetic pass/fail threshold. The metrics expose negative
 * space, spacing, layering, repetition, and hierarchy for correlation with reference/Minecraft
 * visual review under issue #214.
 */
public record ProductionRegionalMorphologyDiagnostics(
        int groupCount,
        int islandCount,
        double horizontalCoverageFraction,
        double minimumIslandSeparationRadiusSum,
        double nearestNeighborSpacingCoefficientOfVariation,
        double elevationSpanVerticalScale,
        double distinctMorphologyFraction,
        double dominantMorphologyShare,
        double nearestNeighborMorphologyRepeatFraction,
        double horizontalAspectRatio,
        double dominantGroupSolidShare) {

    /** Measures one realized single-group context. */
    public static ProductionRegionalMorphologyDiagnostics measure(
            SkyIslandGroupEvidence evidence) {
        Objects.requireNonNull(evidence, "evidence");
        return measure(
                1,
                evidence.plan().members(),
                occupiedFraction(evidence.ownerByHorizontalSample()),
                boundsAspectRatio(
                        evidence.metrics().bounds().minimumX(),
                        evidence.metrics().bounds().maximumX(),
                        evidence.metrics().bounds().minimumZ(),
                        evidence.metrics().bounds().maximumZ()),
                1.0);
    }

    /** Measures one realized hierarchical archipelago context. */
    public static ProductionRegionalMorphologyDiagnostics measure(
            SkyIslandArchipelagoEvidence evidence) {
        Objects.requireNonNull(evidence, "evidence");
        ArrayList<SkyIslandGroupMemberPlan> members =
                new ArrayList<>(evidence.plan().totalMemberCount());
        for (var group : evidence.plan().groups()) {
            members.addAll(group.groupPlan().members());
        }
        return measure(
                evidence.plan().groupCount(),
                members,
                occupiedFraction(evidence.horizontalGroupOwner()),
                boundsAspectRatio(
                        evidence.metrics().bounds().minimumX(),
                        evidence.metrics().bounds().maximumX(),
                        evidence.metrics().bounds().minimumZ(),
                        evidence.metrics().bounds().maximumZ()),
                dominantShare(evidence.metrics().groupSolidSampleCounts()));
    }

    private static ProductionRegionalMorphologyDiagnostics measure(
            int groupCount,
            List<SkyIslandGroupMemberPlan> members,
            double horizontalCoverageFraction,
            double aspectRatio,
            double dominantGroupSolidShare) {
        if (members.isEmpty()) {
            throw new IllegalArgumentException("regional diagnostics require islands");
        }

        Pairwise pairwise = pairwise(members);
        double meanVerticalScale =
                members.stream()
                        .mapToDouble(
                                member ->
                                        member.descriptor().upperElevation()
                                                + member.descriptor().undersideDepth())
                        .average()
                        .orElseThrow();
        double minimumElevation =
                members.stream()
                        .mapToDouble(member -> member.descriptor().suspensionElevation())
                        .min()
                        .orElseThrow();
        double maximumElevation =
                members.stream()
                        .mapToDouble(member -> member.descriptor().suspensionElevation())
                        .max()
                        .orElseThrow();

        Map<String, Integer> morphologyCounts = new HashMap<>();
        for (SkyIslandGroupMemberPlan member : members) {
            morphologyCounts.merge(member.morphology().stableIdentifier(), 1, Integer::sum);
        }
        int dominantMorphologyCount =
                morphologyCounts.values().stream().mapToInt(Integer::intValue).max().orElseThrow();

        return new ProductionRegionalMorphologyDiagnostics(
                groupCount,
                members.size(),
                horizontalCoverageFraction,
                pairwise.minimumSeparationRadiusSum(),
                coefficientOfVariation(pairwise.nearestNeighborDistances()),
                (maximumElevation - minimumElevation) / meanVerticalScale,
                (double) morphologyCounts.size() / members.size(),
                (double) dominantMorphologyCount / members.size(),
                pairwise.nearestNeighborMorphologyRepeatFraction(),
                aspectRatio,
                dominantGroupSolidShare);
    }

    private static Pairwise pairwise(List<SkyIslandGroupMemberPlan> members) {
        if (members.size() == 1) {
            return new Pairwise(
                    Double.POSITIVE_INFINITY,
                    new double[] {0.0},
                    0.0);
        }

        double minimumSeparation = Double.POSITIVE_INFINITY;
        double[] nearestDistances = new double[members.size()];
        int repeatedNearest = 0;

        for (int first = 0; first < members.size(); first++) {
            SkyIslandGroupMemberPlan a = members.get(first);
            double nearest = Double.POSITIVE_INFINITY;
            int nearestIndex = -1;
            for (int second = 0; second < members.size(); second++) {
                if (first == second) {
                    continue;
                }
                SkyIslandGroupMemberPlan b = members.get(second);
                double distance =
                        Math.hypot(
                                a.descriptor().centerX() - b.descriptor().centerX(),
                                a.descriptor().centerZ() - b.descriptor().centerZ());
                double radiusSum =
                        a.descriptor().nominalRadius()
                                + b.descriptor().nominalRadius();
                minimumSeparation =
                        Math.min(minimumSeparation, distance / radiusSum);
                if (distance < nearest) {
                    nearest = distance;
                    nearestIndex = second;
                }
            }
            nearestDistances[first] = nearest;
            if (nearestIndex >= 0
                    && a.morphology()
                            .stableIdentifier()
                            .equals(
                                    members.get(nearestIndex)
                                            .morphology()
                                            .stableIdentifier())) {
                repeatedNearest++;
            }
        }

        return new Pairwise(
                minimumSeparation,
                nearestDistances,
                (double) repeatedNearest / members.size());
    }

    private static double occupiedFraction(int[] owner) {
        long occupied = 0L;
        for (int value : owner) {
            if (value >= 0) {
                occupied++;
            }
        }
        return owner.length == 0 ? 0.0 : (double) occupied / owner.length;
    }

    private static double boundsAspectRatio(
            double minimumX,
            double maximumX,
            double minimumZ,
            double maximumZ) {
        double width = maximumX - minimumX;
        double depth = maximumZ - minimumZ;
        double shorter = Math.min(width, depth);
        double longer = Math.max(width, depth);
        return shorter > 0.0 ? longer / shorter : 1.0;
    }

    private static double dominantShare(List<Integer> counts) {
        long total = 0L;
        int maximum = 0;
        for (int count : counts) {
            total += count;
            maximum = Math.max(maximum, count);
        }
        return total > 0L ? (double) maximum / total : 0.0;
    }

    private static double coefficientOfVariation(double[] values) {
        if (values.length == 0) {
            return 0.0;
        }
        double mean = 0.0;
        for (double value : values) {
            mean += value;
        }
        mean /= values.length;
        if (!(mean > 0.0) || !Double.isFinite(mean)) {
            return 0.0;
        }
        double variance = 0.0;
        for (double value : values) {
            double delta = value - mean;
            variance += delta * delta;
        }
        variance /= values.length;
        return Math.sqrt(variance) / mean;
    }

    private record Pairwise(
            double minimumSeparationRadiusSum,
            double[] nearestNeighborDistances,
            double nearestNeighborMorphologyRepeatFraction) {}
}
