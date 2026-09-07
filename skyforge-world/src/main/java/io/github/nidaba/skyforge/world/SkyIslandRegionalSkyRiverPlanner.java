package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * AUTH-0094 deterministic planner for one cross-island floating sky-river semantic trajectory.
 *
 * <p>The first-generation planner consumes one exact AUTH-0087 binding and one phenomenon key.
 * Content decides whether such a phenomenon exists in playable geography; this planner only defines
 * the authored relationship after that decision.
 */
public final class SkyIslandRegionalSkyRiverPlanner {
    private static final long CURVE_DOMAIN = 0x4155543934524956L;
    private static final long CURVE_SIGN_DOMAIN = 0x4155543934534947L;

    public Optional<SkyIslandRegionalSkyRiverPlan> plan(
            SkyIslandPublishedAuthoredRealizationBinding binding,
            long phenomenonKey) {
        Objects.requireNonNull(binding, "binding");
        List<SkyIslandAuthoredRealizationAssociation> associations =
                binding.associationCatalog().associations();
        if (associations.size() < 2) {
            return Optional.empty();
        }

        Pair pair = farthestPair(associations);
        SkyIslandAuthoredRealizationAssociation first = pair.first();
        SkyIslandAuthoredRealizationAssociation second = pair.second();

        double firstTop = upperReferenceY(first);
        double secondTop = upperReferenceY(second);
        SkyIslandAuthoredRealizationAssociation source;
        SkyIslandAuthoredRealizationAssociation sink;
        if (Double.compare(firstTop, secondTop) > 0) {
            source = first;
            sink = second;
        } else if (Double.compare(firstTop, secondTop) < 0) {
            source = second;
            sink = first;
        } else {
            source = first;
            sink = second;
        }

        return Optional.of(new SkyIslandRegionalSkyRiverPlan(
                binding,
                phenomenonKey,
                source,
                sink,
                trajectory(source, sink, phenomenonKey)));
    }

    private static Pair farthestPair(
            List<SkyIslandAuthoredRealizationAssociation> associations) {
        SkyIslandAuthoredRealizationAssociation bestFirst = null;
        SkyIslandAuthoredRealizationAssociation bestSecond = null;
        double bestDistance = -1.0;

        for (int firstIndex = 0; firstIndex < associations.size(); firstIndex++) {
            for (int secondIndex = firstIndex + 1;
                    secondIndex < associations.size();
                    secondIndex++) {
                SkyIslandAuthoredRealizationAssociation first =
                        associations.get(firstIndex);
                SkyIslandAuthoredRealizationAssociation second =
                        associations.get(secondIndex);
                double distance = horizontalCenterDistance(first, second);
                if (Double.compare(distance, bestDistance) > 0) {
                    bestDistance = distance;
                    bestFirst = first;
                    bestSecond = second;
                }
            }
        }
        return new Pair(bestFirst, bestSecond);
    }

    private static List<SkyIslandRegionalSkyRiverWaypoint> trajectory(
            SkyIslandAuthoredRealizationAssociation source,
            SkyIslandAuthoredRealizationAssociation sink,
            long phenomenonKey) {
        var sourceDescriptor = source.realizedVolume().compiledVolume().descriptor();
        var sinkDescriptor = sink.realizedVolume().compiledVolume().descriptor();

        double sourceX = sourceDescriptor.centerX();
        double sourceY = upperReferenceY(source);
        double sourceZ = sourceDescriptor.centerZ();
        double sinkX = sinkDescriptor.centerX();
        double sinkY = upperReferenceY(sink);
        double sinkZ = sinkDescriptor.centerZ();

        double dx = sinkX - sourceX;
        double dz = sinkZ - sourceZ;
        double horizontal = Math.hypot(dx, dz);
        double scaleReference = Math.max(
                horizontal,
                sourceDescriptor.nominalRadius() + sinkDescriptor.nominalRadius());

        double lateralX;
        double lateralZ;
        if (horizontal > 0.0) {
            lateralX = -dz / horizontal;
            lateralZ = dx / horizontal;
        } else {
            double angle = unit(mix64(phenomenonKey ^ CURVE_DOMAIN)) * 2.0 * Math.PI;
            lateralX = Math.cos(angle);
            lateralZ = Math.sin(angle);
        }

        double sign = unit(mix64(phenomenonKey ^ CURVE_SIGN_DOMAIN)) < 0.5 ? -1.0 : 1.0;
        double amplitude =
                scaleReference
                        * (0.08 + 0.06 * unit(mix64(phenomenonKey ^ CURVE_DOMAIN)));

        SkyIslandRegionalSkyRiverWaypoint first =
                new SkyIslandRegionalSkyRiverWaypoint(
                        0.0, sourceX, sourceY, sourceZ);
        SkyIslandRegionalSkyRiverWaypoint second =
                control(
                        1.0 / 3.0,
                        sourceX,
                        sourceY,
                        sourceZ,
                        sinkX,
                        sinkY,
                        sinkZ,
                        lateralX,
                        lateralZ,
                        sign * amplitude);
        SkyIslandRegionalSkyRiverWaypoint third =
                control(
                        2.0 / 3.0,
                        sourceX,
                        sourceY,
                        sourceZ,
                        sinkX,
                        sinkY,
                        sinkZ,
                        lateralX,
                        lateralZ,
                        sign * amplitude * 0.82);
        SkyIslandRegionalSkyRiverWaypoint fourth =
                new SkyIslandRegionalSkyRiverWaypoint(
                        1.0, sinkX, sinkY, sinkZ);
        return List.of(first, second, third, fourth);
    }

    private static SkyIslandRegionalSkyRiverWaypoint control(
            double parameter,
            double sourceX,
            double sourceY,
            double sourceZ,
            double sinkX,
            double sinkY,
            double sinkZ,
            double lateralX,
            double lateralZ,
            double lateralOffset) {
        return new SkyIslandRegionalSkyRiverWaypoint(
                parameter,
                lerp(sourceX, sinkX, parameter) + lateralX * lateralOffset,
                lerp(sourceY, sinkY, parameter),
                lerp(sourceZ, sinkZ, parameter) + lateralZ * lateralOffset);
    }

    private static double horizontalCenterDistance(
            SkyIslandAuthoredRealizationAssociation first,
            SkyIslandAuthoredRealizationAssociation second) {
        var a = first.realizedVolume().compiledVolume().descriptor();
        var b = second.realizedVolume().compiledVolume().descriptor();
        return Math.hypot(b.centerX() - a.centerX(), b.centerZ() - a.centerZ());
    }

    private static double upperReferenceY(
            SkyIslandAuthoredRealizationAssociation association) {
        var descriptor = association.realizedVolume().compiledVolume().descriptor();
        return descriptor.suspensionElevation() + descriptor.upperElevation();
    }

    private static double lerp(double first, double second, double parameter) {
        return first + (second - first) * parameter;
    }

    private static double unit(long bits) {
        return (bits >>> 11) * 0x1.0p-53;
    }

    private static long mix64(long value) {
        long mixed = value;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        return mixed ^ (mixed >>> 31);
    }

    private record Pair(
            SkyIslandAuthoredRealizationAssociation first,
            SkyIslandAuthoredRealizationAssociation second) {}
}
