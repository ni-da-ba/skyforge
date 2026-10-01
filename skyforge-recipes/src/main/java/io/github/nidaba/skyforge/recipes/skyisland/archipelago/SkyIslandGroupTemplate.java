package io.github.nidaba.skyforge.recipes.skyisland.archipelago;

import io.github.nidaba.skyforge.kernel.seed.SeedDerivation;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupLayout;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupRequest;
import io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandMorphologySpec;
import java.util.List;
import java.util.Objects;

/** Reusable child-group definition instantiated by an archipelago planner. */
public record SkyIslandGroupTemplate(
        String identifier,
        SkyIslandGroupRole role,
        SkyIslandVolumeDescriptor memberTemplate,
        double reservedHorizontalRadius,
        double minimumMemberGap,
        double memberElevationJitter,
        List<SkyIslandMorphologySpec> memberMorphologies,
        SkyIslandGroupLayout layout,
        double reservedGroupRadius) {

    /** Validates immutable group intent and the explicit higher-level reservation envelope. */
    public SkyIslandGroupTemplate {
        SeedDerivation.requireNamespace(identifier);
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(memberTemplate, "memberTemplate");
        Objects.requireNonNull(memberMorphologies, "memberMorphologies");
        Objects.requireNonNull(layout, "layout");
        if (memberTemplate.schemaVersion() != SkyIslandVolumeDescriptor.SCHEMA_VERSION_1) {
            throw new IllegalArgumentException("archipelago group template requires descriptor schema 1");
        }
        if (memberTemplate.signalAmplitude() != 0.0) {
            throw new IllegalArgumentException("archipelago group template requires zero embedded detail amplitude");
        }
        requirePositive("reservedHorizontalRadius", reservedHorizontalRadius);
        requireNonNegative("minimumMemberGap", minimumMemberGap);
        requireNonNegative("memberElevationJitter", memberElevationJitter);
        requirePositive("reservedGroupRadius", reservedGroupRadius);
        if (reservedGroupRadius < reservedHorizontalRadius) {
            throw new IllegalArgumentException(
                    "reservedGroupRadius must be at least reservedHorizontalRadius");
        }
        memberMorphologies = List.copyOf(memberMorphologies);
        if (memberMorphologies.isEmpty()) {
            throw new IllegalArgumentException("archipelago group template must contain at least one member");
        }
    }

    /** Instantiates this template at one archipelago placement with a derived group root seed. */
    public SkyIslandGroupRequest instantiate(
            long groupRootSeed,
            double centerX,
            double centerZ,
            double suspensionElevation,
            double orientationRadians) {
        SkyIslandVolumeDescriptor translated = new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                memberTemplate.seed(),
                centerX,
                centerZ,
                suspensionElevation,
                memberTemplate.nominalRadius(),
                memberTemplate.upperElevation(),
                memberTemplate.undersideDepth(),
                memberTemplate.coastalFalloff(),
                memberTemplate.ridgeAzimuth(),
                memberTemplate.ridgeStrength(),
                memberTemplate.undersideTaper(),
                memberTemplate.undersideAsymmetry(),
                0.0,
                memberTemplate.signalScale());
        return new SkyIslandGroupRequest(
                groupRootSeed,
                translated,
                reservedHorizontalRadius,
                minimumMemberGap,
                memberElevationJitter,
                memberMorphologies,
                rotate(layout, orientationRadians));
    }

    private static SkyIslandGroupLayout rotate(SkyIslandGroupLayout layout, double orientation) {
        if (layout instanceof SkyIslandGroupLayout.Chain chain) {
            return new SkyIslandGroupLayout.Chain(
                    chain.headingRadians() + orientation,
                    chain.centerSpacing(),
                    chain.spacingJitterFraction(),
                    chain.lateralJitter(),
                    chain.curveAmplitude(),
                    chain.orientationJitterRadians());
        }
        SkyIslandGroupLayout.Cluster cluster = (SkyIslandGroupLayout.Cluster) layout;
        return new SkyIslandGroupLayout.Cluster(
                cluster.minimumCenterSpacing(),
                cluster.phaseRadians() + orientation,
                cluster.radialJitterFraction(),
                cluster.orientationJitterRadians());
    }

    private static void requirePositive(String property, double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(property + " must be finite and positive");
        }
    }

    private static void requireNonNegative(String property, double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(property + " must be finite and non-negative");
        }
    }
}
