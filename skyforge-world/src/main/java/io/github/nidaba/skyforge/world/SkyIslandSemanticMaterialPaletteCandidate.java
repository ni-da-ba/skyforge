package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * One AUTH-0037 backend-neutral candidate role derived from AUTH-0036 realization state.
 *
 * <p>{@code support} is authored semantic support. {@code expressionCeiling} limits how strongly a
 * downstream palette binding may replace/overlay local material for this role. It is a constraint,
 * not a final stochastic selection probability.
 */
public record SkyIslandSemanticMaterialPaletteCandidate(
        SkyIslandSemanticMaterialPaletteRole role,
        SkyIslandLithologicRealizationChannel sourceChannel,
        double support,
        double expressionCeiling,
        boolean required) {

    public SkyIslandSemanticMaterialPaletteCandidate {
        role = Objects.requireNonNull(role, "role");
        sourceChannel = Objects.requireNonNull(sourceChannel, "sourceChannel");
        requireNormalized("support", support);
        requireNormalized("expressionCeiling", expressionCeiling);
        if (support <= 0.0) {
            throw new IllegalArgumentException("palette candidate support must be positive");
        }
        if (expressionCeiling <= 0.0) {
            throw new IllegalArgumentException("palette candidate expression ceiling must be positive");
        }
        if (required && role != SkyIslandSemanticMaterialPaletteRole.PRIMARY_MATRIX) {
            throw new IllegalArgumentException("only PRIMARY_MATRIX may be required");
        }
        validateRoleChannel(role, sourceChannel);
    }

    private static void validateRoleChannel(
            SkyIslandSemanticMaterialPaletteRole role,
            SkyIslandLithologicRealizationChannel channel) {
        boolean valid = switch (role) {
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
        if (!valid) {
            throw new IllegalArgumentException(
                    "semantic palette role must retain its AUTH-0036 source channel");
        }
    }

    private static void requireNormalized(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
    }
}
