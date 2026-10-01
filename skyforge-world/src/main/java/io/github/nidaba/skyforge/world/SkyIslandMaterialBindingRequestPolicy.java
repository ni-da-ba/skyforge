package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * Stable AUTH-0039 request-side projection of AUTH-0037 material-role eligibility constraints.
 *
 * <p>These values describe the semantic contract a backend resolver may rely on. Per-position
 * support and expression ceilings remain on the local AUTH-0037 candidate and are not folded into
 * binding identity.
 */
public final class SkyIslandMaterialBindingRequestPolicy {
    private SkyIslandMaterialBindingRequestPolicy() {}

    public static boolean required(SkyIslandSemanticMaterialPaletteRole role) {
        return Objects.requireNonNull(role, "role")
                == SkyIslandSemanticMaterialPaletteRole.PRIMARY_MATRIX;
    }

    public static double minimumEligibleSupport(
            SkyIslandSemanticMaterialPaletteRole role) {
        return switch (Objects.requireNonNull(role, "role")) {
            case PRIMARY_MATRIX -> 0.0;
            case SECONDARY_MATRIX ->
                    SkyIslandSemanticMaterialPaletteField.SECONDARY_MATRIX_MIN_SUPPORT;
            case ALTERATION_OVERPRINT ->
                    SkyIslandSemanticMaterialPaletteField.ALTERATION_MIN_SUPPORT;
            case HYDROLOGIC_CONDITIONING ->
                    SkyIslandSemanticMaterialPaletteField.HYDROLOGIC_MIN_SUPPORT;
            case MINERAL_BEARING_STRUCTURE ->
                    SkyIslandSemanticMaterialPaletteField.MINERAL_MIN_SUPPORT;
        };
    }

    public static double minimumSecondaryHostRatio(
            SkyIslandSemanticMaterialPaletteRole role) {
        return Objects.requireNonNull(role, "role")
                        == SkyIslandSemanticMaterialPaletteRole.SECONDARY_MATRIX
                ? SkyIslandSemanticMaterialPaletteField.SECONDARY_MATRIX_MIN_RATIO
                : 0.0;
    }

    public static double maximumExpressionCeiling(
            SkyIslandSemanticMaterialPaletteRole role) {
        return switch (Objects.requireNonNull(role, "role")) {
            case PRIMARY_MATRIX -> 1.0;
            case SECONDARY_MATRIX -> 0.48;
            case ALTERATION_OVERPRINT -> 0.56;
            case HYDROLOGIC_CONDITIONING -> 0.48;
            case MINERAL_BEARING_STRUCTURE -> 0.34;
        };
    }

    public static boolean sourceChannelAllowed(
            SkyIslandSemanticMaterialPaletteRole role,
            SkyIslandLithologicRealizationChannel channel) {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(channel, "channel");
        return switch (role) {
            case PRIMARY_MATRIX, SECONDARY_MATRIX ->
                    channel == SkyIslandLithologicRealizationChannel.MASSIVE_MATRIX
                            || channel == SkyIslandLithologicRealizationChannel.FABRIC_RICH_MATRIX;
            case ALTERATION_OVERPRINT ->
                    channel == SkyIslandLithologicRealizationChannel.ALTERATION_OVERPRINT;
            case HYDROLOGIC_CONDITIONING ->
                    channel == SkyIslandLithologicRealizationChannel.WATER_CONDITIONING;
            case MINERAL_BEARING_STRUCTURE ->
                    channel == SkyIslandLithologicRealizationChannel.MINERAL_BEARING_STRUCTURE;
        };
    }
}
