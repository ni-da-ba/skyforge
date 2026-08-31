package io.github.nidaba.skyforge.recipes.skyisland.group;

import io.github.nidaba.skyforge.kernel.seed.SeedDerivation;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Deterministic backend-neutral planner for suspended-island chains and clusters. */
public final class SkyIslandGroupPlanner {
    private static final double TWO_PI = 2.0 * Math.PI;
    private static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));
    private static final int MAXIMUM_CLUSTER_ATTEMPTS = 768;
    private static final double SPACING_TOLERANCE = 1.0e-9;

    /** Produces an immutable placement plan from one validated request. */
    public SkyIslandGroupPlan plan(SkyIslandGroupRequest request) {
        Objects.requireNonNull(request, "request");
        List<Placement> placements = switch (request.layout()) {
            case SkyIslandGroupLayout.Chain chain -> planChain(request, chain);
            case SkyIslandGroupLayout.Cluster cluster -> planCluster(request, cluster);
        };

        List<SkyIslandGroupMemberPlan> members = new ArrayList<>(placements.size());
        Set<Long> seeds = new HashSet<>();
        for (int ordinal = 0; ordinal < placements.size(); ordinal++) {
            Placement placement = placements.get(ordinal);
            long memberSeed = memberSeed(request.rootSeed(), ordinal);
            if (!seeds.add(memberSeed)) {
                throw new IllegalStateException("derived duplicate group member seed at ordinal " + ordinal);
            }
            SkyIslandVolumeDescriptor descriptor = memberDescriptor(
                    request.memberTemplate(),
                    memberSeed,
                    placement.x(),
                    placement.z(),
                    placement.suspensionElevation(),
                    placement.ridgeAzimuth());
            members.add(new SkyIslandGroupMemberPlan(
                    ordinal,
                    descriptor,
                    request.memberMorphologies().get(ordinal),
                    request.reservedHorizontalRadius()));
        }

        SkyIslandGroupPlan result = new SkyIslandGroupPlan(
                request.rootSeed(),
                request.memberTemplate().centerX(),
                request.memberTemplate().centerZ(),
                request.memberTemplate().suspensionElevation(),
                request.requiredCenterSpacing(),
                request.layout(),
                members);
        requireReservedSpacing(result);
        return result;
    }

    private static List<Placement> planChain(
            SkyIslandGroupRequest request, SkyIslandGroupLayout.Chain layout) {
        int count = request.memberCount();
        double[] rawAlong = new double[count];
        for (int edge = 0; edge < count - 1; edge++) {
            long spacingSeed = SeedDerivation.derive(
                    request.rootSeed(), "group.chain.edge-" + edge + ".spacing");
            double spacing = layout.centerSpacing()
                    * (1.0 + layout.spacingJitterFraction() * signedUnit(spacingSeed));
            rawAlong[edge + 1] = rawAlong[edge] + spacing;
        }

        double totalLength = count > 1 ? rawAlong[count - 1] : 0.0;
        double pathMidpoint = totalLength * 0.5;
        double alongMean = mean(rawAlong);
        double[] across = new double[count];
        for (int ordinal = 0; ordinal < count; ordinal++) {
            double pathCentered = rawAlong[ordinal] - pathMidpoint;
            double normalized = totalLength == 0.0 ? 0.0 : 2.0 * pathCentered / totalLength;
            double curve = layout.curveAmplitude() * normalized * normalized;
            long lateralSeed = SeedDerivation.derive(
                    request.rootSeed(), "group.chain.member-" + ordinal + ".lateral");
            across[ordinal] = curve + layout.lateralJitter() * signedUnit(lateralSeed);
        }
        double acrossMean = mean(across);

        double cosine = Math.cos(layout.headingRadians());
        double sine = Math.sin(layout.headingRadians());
        List<Placement> result = new ArrayList<>(count);
        for (int ordinal = 0; ordinal < count; ordinal++) {
            double localAlong = rawAlong[ordinal] - alongMean;
            double localAcross = across[ordinal] - acrossMean;
            double x = request.memberTemplate().centerX()
                    + localAlong * cosine - localAcross * sine;
            double z = request.memberTemplate().centerZ()
                    + localAlong * sine + localAcross * cosine;

            double pathCentered = rawAlong[ordinal] - pathMidpoint;
            double normalized = totalLength == 0.0 ? 0.0 : 2.0 * pathCentered / totalLength;
            double slope = totalLength == 0.0
                    ? 0.0
                    : 4.0 * layout.curveAmplitude() * normalized / totalLength;
            long orientationSeed = SeedDerivation.derive(
                    request.rootSeed(), "group.chain.member-" + ordinal + ".orientation");
            double ridgeAzimuth = layout.headingRadians()
                    + Math.atan(slope)
                    + request.memberTemplate().ridgeAzimuth()
                    + layout.orientationJitterRadians() * signedUnit(orientationSeed);
            double elevation = memberElevation(request, ordinal);
            result.add(new Placement(x, z, elevation, ridgeAzimuth));
        }
        return result;
    }

    private static List<Placement> planCluster(
            SkyIslandGroupRequest request, SkyIslandGroupLayout.Cluster layout) {
        int count = request.memberCount();
        double[] localX = new double[count];
        double[] localZ = new double[count];
        double rootPhase = layout.phaseRadians()
                + TWO_PI * unit(SeedDerivation.derive(request.rootSeed(), "group.cluster.phase"));

        for (int ordinal = 1; ordinal < count; ordinal++) {
            boolean accepted = false;
            double baseRadius = layout.minimumCenterSpacing() * Math.sqrt(ordinal);
            for (int attempt = 0; attempt < MAXIMUM_CLUSTER_ATTEMPTS; attempt++) {
                long candidateSeed = SeedDerivation.derive(
                        request.rootSeed(),
                        "group.cluster.member-" + ordinal + ".candidate-" + attempt);
                double angleNoise = 0.35 * signedUnit(SeedDerivation.derive(candidateSeed, "angle"));
                double radialNoise = layout.radialJitterFraction()
                        * unit(SeedDerivation.derive(candidateSeed, "radius"));
                double expansion = 0.30 * layout.minimumCenterSpacing() * (attempt / 24);
                double radius = (baseRadius + expansion) * (1.0 + radialNoise);
                double angle = rootPhase
                        + GOLDEN_ANGLE * ordinal
                        + GOLDEN_ANGLE * 0.3819660112501051 * attempt
                        + angleNoise;
                double candidateX = radius * Math.cos(angle);
                double candidateZ = radius * Math.sin(angle);
                if (separatedFromEarlier(
                        candidateX,
                        candidateZ,
                        localX,
                        localZ,
                        ordinal,
                        layout.minimumCenterSpacing())) {
                    localX[ordinal] = candidateX;
                    localZ[ordinal] = candidateZ;
                    accepted = true;
                    break;
                }
            }
            if (!accepted) {
                throw new IllegalStateException(
                        "cluster planner exhausted deterministic candidates at member " + ordinal);
            }
        }

        // Translation does not change pairwise spacing. Recenter the accepted cluster so the request
        // anchor denotes the actual member-center centroid rather than a privileged first member.
        double meanX = mean(localX);
        double meanZ = mean(localZ);
        List<Placement> result = new ArrayList<>(count);
        for (int ordinal = 0; ordinal < count; ordinal++) {
            double x = request.memberTemplate().centerX() + localX[ordinal] - meanX;
            double z = request.memberTemplate().centerZ() + localZ[ordinal] - meanZ;
            long orientationSeed = SeedDerivation.derive(
                    request.rootSeed(), "group.cluster.member-" + ordinal + ".orientation");
            double orientationBase = ordinal == 0
                    ? rootPhase
                    : Math.atan2(localZ[ordinal], localX[ordinal]) + Math.PI * 0.5;
            double ridgeAzimuth = orientationBase
                    + request.memberTemplate().ridgeAzimuth()
                    + layout.orientationJitterRadians() * signedUnit(orientationSeed);
            result.add(new Placement(x, z, memberElevation(request, ordinal), ridgeAzimuth));
        }
        return result;
    }

    private static boolean separatedFromEarlier(
            double candidateX,
            double candidateZ,
            double[] acceptedX,
            double[] acceptedZ,
            int acceptedCount,
            double minimumSpacing) {
        for (int index = 0; index < acceptedCount; index++) {
            if (Math.hypot(candidateX - acceptedX[index], candidateZ - acceptedZ[index])
                    + SPACING_TOLERANCE < minimumSpacing) {
                return false;
            }
        }
        return true;
    }

    private static double memberElevation(SkyIslandGroupRequest request, int ordinal) {
        long elevationSeed = SeedDerivation.derive(
                request.rootSeed(), "group.member-" + ordinal + ".elevation");
        return request.memberTemplate().suspensionElevation()
                + request.elevationJitter() * signedUnit(elevationSeed);
    }

    private static long memberSeed(long rootSeed, int ordinal) {
        return SeedDerivation.derive(rootSeed, "group.member-" + ordinal + ".geometry");
    }

    private static SkyIslandVolumeDescriptor memberDescriptor(
            SkyIslandVolumeDescriptor template,
            long seed,
            double centerX,
            double centerZ,
            double suspensionElevation,
            double ridgeAzimuth) {
        return new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                seed,
                centerX,
                centerZ,
                suspensionElevation,
                template.nominalRadius(),
                template.upperElevation(),
                template.undersideDepth(),
                template.coastalFalloff(),
                ridgeAzimuth,
                template.ridgeStrength(),
                template.undersideTaper(),
                template.undersideAsymmetry(),
                0.0,
                template.signalScale());
    }

    private static void requireReservedSpacing(SkyIslandGroupPlan plan) {
        double observed = plan.minimumObservedCenterSpacing();
        if (observed + SPACING_TOLERANCE < plan.requiredCenterSpacing()) {
            throw new IllegalStateException(
                    "planned group violates reserved spacing: observed=" + observed
                            + ", required=" + plan.requiredCenterSpacing());
        }
    }

    private static double mean(double[] values) {
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.length;
    }

    private static double unit(long seed) {
        return (seed >>> 11) * 0x1.0p-53;
    }

    private static double signedUnit(long seed) {
        return 2.0 * unit(seed) - 1.0;
    }

    private record Placement(double x, double z, double suspensionElevation, double ridgeAzimuth) {}
}
