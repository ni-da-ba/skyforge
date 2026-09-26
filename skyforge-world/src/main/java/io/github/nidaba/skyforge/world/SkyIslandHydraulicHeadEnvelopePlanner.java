package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.List;
import java.util.Objects;

/**
 * Single authority for the D2-derived pointwise head envelope used by F2C and transition solves.
 */
public final class SkyIslandHydraulicHeadEnvelopePlanner {
    private SkyIslandHydraulicHeadEnvelopePlanner() {}

    public static SkyIslandHydraulicHeadEnvelope evaluate(
            SkyIslandDescriptor descriptor,
            SkyIslandSemanticChannelReach semantic,
            double stationFraction,
            SkyIslandLocalPosition position,
            double bankfullHalfWidth,
            double waterDepthPotential,
            double terrainElevation,
            double normalX,
            double normalZ,
            SkyIslandSemanticField terrain,
            SkyIslandGeomorphicQualificationPolicy policy) {
        Objects.requireNonNull(semantic, "semantic");
        Objects.requireNonNull(policy, "policy");
        SkyIslandChannelProfileKind kind =
                profileKind(semantic.profiles(), stationFraction);
        return evaluateForKind(
                descriptor,
                kind,
                position,
                bankfullHalfWidth,
                waterDepthPotential,
                terrainElevation,
                normalX,
                normalZ,
                terrain,
                policy.limits(semantic));
    }

    /**
     * Evaluates an ordinary-side D2 envelope when transition ownership makes the one-sided profile
     * class explicit.
     *
     * <p>This method does not grant CASCADE pointwise geometry. It is used at an exact transition
     * boundary with the adjacent ALLUVIAL or INCISED profile class while retaining the caller's
     * accepted D2 limit set.
     */
    public static SkyIslandHydraulicHeadEnvelope evaluateForKind(
            SkyIslandDescriptor descriptor,
            SkyIslandChannelProfileKind kind,
            SkyIslandLocalPosition position,
            double bankfullHalfWidth,
            double waterDepthPotential,
            double terrainElevation,
            double normalX,
            double normalZ,
            SkyIslandSemanticField terrain,
            SkyIslandGeomorphicProfileLimits limits) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(terrain, "terrain");
        Objects.requireNonNull(limits, "limits");
        requireFinitePositive(bankfullHalfWidth, "bankfullHalfWidth");
        requireFraction(waterDepthPotential, "waterDepthPotential");
        requireFraction(terrainElevation, "terrainElevation");
        if (kind == SkyIslandChannelProfileKind.CASCADE) {
            throw new IllegalArgumentException(
                    "ordinary hydraulic head envelope cannot use CASCADE profile");
        }

        double normalLength = Math.hypot(normalX, normalZ);
        if (!Double.isFinite(normalLength) || normalLength <= 0.0) {
            throw new IllegalArgumentException("normal must be finite and non-zero");
        }
        normalX /= normalLength;
        normalZ /= normalLength;

        double relief = descriptor.reliefBudget();
        if (!Double.isFinite(relief) || relief <= 0.0) {
            throw new IllegalArgumentException(
                    "descriptor relief budget must be finite and positive");
        }

        double valleyHalfWidth = bankfullHalfWidth * valleyMultiplier(kind);
        double fullValleyWidth = 2.0 * valleyHalfWidth;

        double centerTerrain = terrainElevation * relief;
        double depth = waterDepthPotential * relief;
        double leftValleyTerrain =
                terrain.sample(offset(position, normalX, normalZ, valleyHalfWidth))
                        * relief;
        double rightValleyTerrain =
                terrain.sample(offset(position, normalX, normalZ, -valleyHalfWidth))
                        * relief;
        double leftBankTerrain =
                terrain.sample(offset(position, normalX, normalZ, bankfullHalfWidth))
                        * relief;
        double rightBankTerrain =
                terrain.sample(offset(position, normalX, normalZ, -bankfullHalfWidth))
                        * relief;

        double maximumLowering =
                limits.maximumCenterlineLoweringPotential() * relief;
        double lowerHead = depth;
        lowerHead = Math.max(
                lowerHead,
                centerTerrain - maximumLowering + depth);
        lowerHead = Math.max(
                lowerHead,
                leftValleyTerrain
                        - limits.maximumLateralRecoveryGrade() * valleyHalfWidth
                        + depth);
        lowerHead = Math.max(
                lowerHead,
                rightValleyTerrain
                        - limits.maximumLateralRecoveryGrade() * valleyHalfWidth
                        + depth);
        lowerHead = Math.max(
                lowerHead,
                leftValleyTerrain
                        - limits.maximumReliefToValleyWidthRatio() * fullValleyWidth
                        + depth);
        lowerHead = Math.max(
                lowerHead,
                rightValleyTerrain
                        - limits.maximumReliefToValleyWidthRatio() * fullValleyWidth
                        + depth);

        double upperHead = Math.min(
                relief,
                Math.min(leftBankTerrain, rightBankTerrain)
                        + limits.maximumBankContainmentDeficitWorldUnits());

        double preferredPotential = Math.max(
                waterDepthPotential,
                terrainElevation
                        - SkyIslandHydraulicGeometryCalibration.freeboardFromDepthPotential(
                                waterDepthPotential));

        return new SkyIslandHydraulicHeadEnvelope(
                kind,
                preferredPotential * relief,
                lowerHead,
                upperHead);
    }

    public static SkyIslandChannelProfileKind profileKind(
            List<SkyIslandChannelProfile> profiles,
            double stationFraction) {
        Objects.requireNonNull(profiles, "profiles");
        if (profiles.isEmpty()) {
            throw new IllegalArgumentException("profiles must not be empty");
        }
        if (!Double.isFinite(stationFraction)
                || stationFraction < 0.0
                || stationFraction > 1.0) {
            throw new IllegalArgumentException("stationFraction must be finite and in [0, 1]");
        }
        int index =
                Math.min(
                        profiles.size() - 1,
                        (int)
                                Math.floor(
                                        Math.max(0.0, Math.min(0.999999999, stationFraction))
                                                * profiles.size()));
        return profiles.get(index).kind();
    }

    private static double valleyMultiplier(SkyIslandChannelProfileKind kind) {
        return switch (kind) {
            case ALLUVIAL -> 3.5;
            case INCISED -> 2.5;
            case CASCADE -> throw new IllegalArgumentException(
                    "ordinary head envelope cannot use CASCADE valley geometry");
        };
    }

    private static SkyIslandLocalPosition offset(
            SkyIslandLocalPosition position,
            double normalX,
            double normalZ,
            double distance) {
        return new SkyIslandLocalPosition(
                position.x() + normalX * distance,
                position.z() + normalZ * distance);
    }

    private static void requireFraction(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
    }

    private static void requireFinitePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }
}
