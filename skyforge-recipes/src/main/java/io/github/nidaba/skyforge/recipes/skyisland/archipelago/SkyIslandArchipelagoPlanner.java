package io.github.nidaba.skyforge.recipes.skyisland.archipelago;

import io.github.nidaba.skyforge.kernel.seed.SeedDerivation;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupMemberPlan;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupPlan;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupPlanner;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupRequest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Deterministic hierarchical planner that arranges complete island groups into archipelagos. */
public final class SkyIslandArchipelagoPlanner {
    private static final double TWO_PI = 2.0 * Math.PI;
    private static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));
    private static final int MAXIMUM_HUB_ATTEMPTS = 1024;
    private static final double SPACING_TOLERANCE = 1.0e-9;

    private final SkyIslandGroupPlanner groupPlanner = new SkyIslandGroupPlanner();

    /** Produces one immutable hierarchy from a validated archipelago request. */
    public SkyIslandArchipelagoPlan plan(SkyIslandArchipelagoRequest request) {
        Objects.requireNonNull(request, "request");
        List<Placement> placements = switch (request.layout()) {
            case SkyIslandArchipelagoLayout.Arc arc -> planArc(request, arc);
            case SkyIslandArchipelagoLayout.Hub hub -> planHub(request, hub);
        };

        List<SkyIslandArchipelagoGroupPlan> groups = new ArrayList<>(request.groupCount());
        Set<Long> groupSeeds = new HashSet<>();
        for (int ordinal = 0; ordinal < request.groupCount(); ordinal++) {
            SkyIslandGroupTemplate template = request.groupTemplates().get(ordinal);
            Placement placement = placements.get(ordinal);
            long groupRootSeed = SeedDerivation.derive(
                    request.rootSeed(), "archipelago.group-" + ordinal + ".plan");
            if (!groupSeeds.add(groupRootSeed)) {
                throw new IllegalStateException("derived duplicate child-group seed at ordinal " + ordinal);
            }
            SkyIslandGroupRequest groupRequest = template.instantiate(
                    groupRootSeed,
                    placement.x(),
                    placement.z(),
                    placement.suspensionElevation(),
                    placement.orientationRadians());
            SkyIslandGroupPlan groupPlan = groupPlanner.plan(groupRequest);
            requireContained(template, groupPlan);
            groups.add(new SkyIslandArchipelagoGroupPlan(
                    ordinal,
                    template.identifier(),
                    template.role(),
                    groupRootSeed,
                    template.reservedGroupRadius(),
                    placement.orientationRadians(),
                    groupPlan));
        }

        SkyIslandArchipelagoPlan result = new SkyIslandArchipelagoPlan(
                request.rootSeed(),
                request.centerX(),
                request.centerZ(),
                request.baseSuspensionElevation(),
                request.minimumGroupGap(),
                request.layout(),
                groups);
        requireGroupSpacing(result);
        return result;
    }

    private static List<Placement> planArc(
            SkyIslandArchipelagoRequest request, SkyIslandArchipelagoLayout.Arc layout) {
        int count = request.groupCount();
        double[] rawAlong = new double[count];
        for (int edge = 0; edge < count - 1; edge++) {
            long spacingSeed = SeedDerivation.derive(
                    request.rootSeed(), "archipelago.arc.edge-" + edge + ".spacing");
            double preferred = layout.preferredCenterSpacing()
                    * (1.0 + layout.spacingJitterFraction() * signedUnit(spacingSeed));
            double required = request.groupTemplates().get(edge).reservedGroupRadius()
                    + request.groupTemplates().get(edge + 1).reservedGroupRadius()
                    + request.minimumGroupGap();
            rawAlong[edge + 1] = rawAlong[edge] + Math.max(preferred, required);
        }

        double totalLength = count > 1 ? rawAlong[count - 1] : 0.0;
        double pathMidpoint = totalLength * 0.5;
        double alongMean = mean(rawAlong);
        double[] across = new double[count];
        for (int ordinal = 0; ordinal < count; ordinal++) {
            double centered = rawAlong[ordinal] - pathMidpoint;
            double normalized = totalLength == 0.0 ? 0.0 : 2.0 * centered / totalLength;
            double curve = layout.curveAmplitude() * normalized * normalized;
            long lateralSeed = SeedDerivation.derive(
                    request.rootSeed(), "archipelago.arc.group-" + ordinal + ".lateral");
            across[ordinal] = curve + layout.lateralJitter() * signedUnit(lateralSeed);
        }
        double acrossMean = mean(across);

        double cosine = Math.cos(layout.headingRadians());
        double sine = Math.sin(layout.headingRadians());
        List<Placement> result = new ArrayList<>(count);
        for (int ordinal = 0; ordinal < count; ordinal++) {
            double localAlong = rawAlong[ordinal] - alongMean;
            double localAcross = across[ordinal] - acrossMean;
            double x = request.centerX() + localAlong * cosine - localAcross * sine;
            double z = request.centerZ() + localAlong * sine + localAcross * cosine;

            double centered = rawAlong[ordinal] - pathMidpoint;
            double normalized = totalLength == 0.0 ? 0.0 : 2.0 * centered / totalLength;
            double slope = totalLength == 0.0
                    ? 0.0
                    : 4.0 * layout.curveAmplitude() * normalized / totalLength;
            long orientationSeed = SeedDerivation.derive(
                    request.rootSeed(), "archipelago.arc.group-" + ordinal + ".orientation");
            double orientation = layout.headingRadians()
                    + Math.atan(slope)
                    + layout.orientationJitterRadians() * signedUnit(orientationSeed);
            double elevation = request.baseSuspensionElevation()
                    + layout.elevationJitter() * signedUnit(SeedDerivation.derive(
                            request.rootSeed(), "archipelago.arc.group-" + ordinal + ".elevation"));
            result.add(new Placement(x, z, elevation, orientation));
        }
        return result;
    }

    private static List<Placement> planHub(
            SkyIslandArchipelagoRequest request, SkyIslandArchipelagoLayout.Hub layout) {
        int count = request.groupCount();
        double[] localX = new double[count];
        double[] localZ = new double[count];
        double rootPhase = layout.phaseRadians()
                + TWO_PI * unit(SeedDerivation.derive(request.rootSeed(), "archipelago.hub.phase"));

        for (int ordinal = 1; ordinal < count; ordinal++) {
            SkyIslandGroupTemplate current = request.groupTemplates().get(ordinal);
            double anchorRequired = request.groupTemplates().get(0).reservedGroupRadius()
                    + current.reservedGroupRadius()
                    + request.minimumGroupGap();
            double baseRadius = Math.max(
                    layout.preferredRadialSpacing() * Math.sqrt(ordinal),
                    anchorRequired);
            boolean accepted = false;
            for (int attempt = 0; attempt < MAXIMUM_HUB_ATTEMPTS; attempt++) {
                long candidateSeed = SeedDerivation.derive(
                        request.rootSeed(),
                        "archipelago.hub.group-" + ordinal + ".candidate-" + attempt);
                double angleNoise = 0.30 * signedUnit(SeedDerivation.derive(candidateSeed, "angle"));
                double radialNoise = layout.radialJitterFraction()
                        * unit(SeedDerivation.derive(candidateSeed, "radius"));
                double expansion = 0.25 * layout.preferredRadialSpacing() * (attempt / 16.0);
                double radius = (baseRadius + expansion) * (1.0 + radialNoise);
                double angle = rootPhase
                        + GOLDEN_ANGLE * ordinal
                        + GOLDEN_ANGLE * 0.3819660112501051 * attempt
                        + angleNoise;
                double candidateX = radius * Math.cos(angle);
                double candidateZ = radius * Math.sin(angle);
                if (separatedFromEarlier(
                        request,
                        ordinal,
                        candidateX,
                        candidateZ,
                        localX,
                        localZ)) {
                    localX[ordinal] = candidateX;
                    localZ[ordinal] = candidateZ;
                    accepted = true;
                    break;
                }
            }
            if (!accepted) {
                throw new IllegalStateException(
                        "hub planner exhausted deterministic candidates at group " + ordinal);
            }
        }

        List<Placement> result = new ArrayList<>(count);
        for (int ordinal = 0; ordinal < count; ordinal++) {
            double x = request.centerX() + localX[ordinal];
            double z = request.centerZ() + localZ[ordinal];
            if (ordinal == 0) {
                result.add(new Placement(
                        x,
                        z,
                        request.baseSuspensionElevation(),
                        rootPhase));
                continue;
            }
            double radialAngle = Math.atan2(localZ[ordinal], localX[ordinal]);
            long orientationSeed = SeedDerivation.derive(
                    request.rootSeed(), "archipelago.hub.group-" + ordinal + ".orientation");
            double orientation = radialAngle
                    + Math.PI * 0.5
                    + layout.orientationJitterRadians() * signedUnit(orientationSeed);
            double elevation = request.baseSuspensionElevation()
                    + layout.elevationJitter() * signedUnit(SeedDerivation.derive(
                            request.rootSeed(), "archipelago.hub.group-" + ordinal + ".elevation"));
            result.add(new Placement(x, z, elevation, orientation));
        }
        return result;
    }

    private static boolean separatedFromEarlier(
            SkyIslandArchipelagoRequest request,
            int ordinal,
            double candidateX,
            double candidateZ,
            double[] acceptedX,
            double[] acceptedZ) {
        double currentRadius = request.groupTemplates().get(ordinal).reservedGroupRadius();
        for (int index = 0; index < ordinal; index++) {
            double required = currentRadius
                    + request.groupTemplates().get(index).reservedGroupRadius()
                    + request.minimumGroupGap();
            if (Math.hypot(candidateX - acceptedX[index], candidateZ - acceptedZ[index])
                    + SPACING_TOLERANCE < required) {
                return false;
            }
        }
        return true;
    }

    private static void requireContained(
            SkyIslandGroupTemplate template, SkyIslandGroupPlan groupPlan) {
        for (SkyIslandGroupMemberPlan member : groupPlan.members()) {
            double distance = Math.hypot(
                    member.descriptor().centerX() - groupPlan.groupCenterX(),
                    member.descriptor().centerZ() - groupPlan.groupCenterZ());
            double required = distance + member.reservedHorizontalRadius();
            if (required > template.reservedGroupRadius() + SPACING_TOLERANCE) {
                throw new IllegalStateException(
                        "child group exceeds reserved archipelago envelope: template="
                                + template.identifier()
                                + ", member=" + member.ordinal()
                                + ", requiredRadius=" + required
                                + ", reservedRadius=" + template.reservedGroupRadius());
            }
        }
    }

    private static void requireGroupSpacing(SkyIslandArchipelagoPlan plan) {
        if (plan.minimumObservedGroupGap() + SPACING_TOLERANCE < plan.minimumGroupGap()) {
            throw new IllegalStateException(
                    "archipelago violates reserved group gap: observed="
                            + plan.minimumObservedGroupGap()
                            + ", required=" + plan.minimumGroupGap());
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

    private record Placement(
            double x,
            double z,
            double suspensionElevation,
            double orientationRadians) {}
}
