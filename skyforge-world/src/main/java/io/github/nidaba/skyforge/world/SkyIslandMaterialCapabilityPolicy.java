package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * AUTH-0040 deterministic mapping from stable material-binding requests to hard capability floors.
 *
 * <p>Compatibility depends on semantic role and AUTH-0036 source channel. Assemblage breadth and
 * contact context remain available to a backend resolver but do not silently alter capability
 * thresholds in AUTH-0040.
 */
public final class SkyIslandMaterialCapabilityPolicy {
    public static final double PRIMARY_HOST_MINIMUM = 0.75;
    public static final double SECONDARY_HOST_MINIMUM = 0.55;
    public static final double PRIMARY_FABRIC_MINIMUM = 0.65;
    public static final double SECONDARY_FABRIC_MINIMUM = 0.60;
    public static final double ALTERATION_MINIMUM = 0.65;
    public static final double HYDROLOGIC_MINIMUM = 0.65;
    public static final double STRUCTURAL_ACCENT_MINIMUM = 0.70;

    private SkyIslandMaterialCapabilityPolicy() {}

    public static SkyIslandMaterialCapabilityConstraintSet constraints(
            SkyIslandMaterialBindingRequest request) {
        Objects.requireNonNull(request, "request");
        List<SkyIslandMaterialCapabilityRequirement> requirements =
                new ArrayList<>(2);

        switch (request.role()) {
            case PRIMARY_MATRIX -> {
                add(
                        requirements,
                        SkyIslandMaterialCapability.HOST_MATRIX_SUITABILITY,
                        PRIMARY_HOST_MINIMUM);
                if (request.sourceChannel()
                        == SkyIslandLithologicRealizationChannel.FABRIC_RICH_MATRIX) {
                    add(
                            requirements,
                            SkyIslandMaterialCapability.FABRIC_EXPRESSIVENESS,
                            PRIMARY_FABRIC_MINIMUM);
                }
            }
            case SECONDARY_MATRIX -> {
                add(
                        requirements,
                        SkyIslandMaterialCapability.HOST_MATRIX_SUITABILITY,
                        SECONDARY_HOST_MINIMUM);
                if (request.sourceChannel()
                        == SkyIslandLithologicRealizationChannel.FABRIC_RICH_MATRIX) {
                    add(
                            requirements,
                            SkyIslandMaterialCapability.FABRIC_EXPRESSIVENESS,
                            SECONDARY_FABRIC_MINIMUM);
                }
            }
            case ALTERATION_OVERPRINT ->
                    add(
                            requirements,
                            SkyIslandMaterialCapability.ALTERATION_OVERPRINT_SUITABILITY,
                            ALTERATION_MINIMUM);
            case HYDROLOGIC_CONDITIONING ->
                    add(
                            requirements,
                            SkyIslandMaterialCapability.HYDROLOGIC_CONDITIONING_SUITABILITY,
                            HYDROLOGIC_MINIMUM);
            case MINERAL_BEARING_STRUCTURE ->
                    add(
                            requirements,
                            SkyIslandMaterialCapability.STRUCTURAL_ACCENT_SUITABILITY,
                            STRUCTURAL_ACCENT_MINIMUM);
        }

        requirements.sort(
                Comparator.comparingInt(requirement -> requirement.capability().ordinal()));
        return new SkyIslandMaterialCapabilityConstraintSet(request, requirements);
    }

    private static void add(
            List<SkyIslandMaterialCapabilityRequirement> requirements,
            SkyIslandMaterialCapability capability,
            double minimum) {
        requirements.add(new SkyIslandMaterialCapabilityRequirement(capability, minimum));
    }
}
