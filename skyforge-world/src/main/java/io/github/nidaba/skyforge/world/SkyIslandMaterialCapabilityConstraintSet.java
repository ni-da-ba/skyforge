package io.github.nidaba.skyforge.world;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/** Deterministic AUTH-0040 hard capability constraints for one stable AUTH-0039 request. */
public record SkyIslandMaterialCapabilityConstraintSet(
        SkyIslandMaterialBindingRequest request,
        List<SkyIslandMaterialCapabilityRequirement> requirements) {

    public SkyIslandMaterialCapabilityConstraintSet {
        request = Objects.requireNonNull(request, "request");
        requirements = List.copyOf(requirements);
        if (requirements.isEmpty()) {
            throw new IllegalArgumentException(
                    "material capability constraint set requires at least one hard requirement");
        }

        EnumSet<SkyIslandMaterialCapability> seen =
                EnumSet.noneOf(SkyIslandMaterialCapability.class);
        int previousOrdinal = -1;
        for (SkyIslandMaterialCapabilityRequirement requirement : requirements) {
            Objects.requireNonNull(requirement, "capability requirement");
            if (!seen.add(requirement.capability())) {
                throw new IllegalArgumentException(
                        "material capability requirements must be unique");
            }
            if (requirement.capability().ordinal() <= previousOrdinal) {
                throw new IllegalArgumentException(
                        "material capability requirements must be ordered by capability");
            }
            previousOrdinal = requirement.capability().ordinal();
        }
    }

    public double minimum(SkyIslandMaterialCapability capability) {
        Objects.requireNonNull(capability, "capability");
        return requirements.stream()
                .filter(requirement -> requirement.capability() == capability)
                .mapToDouble(SkyIslandMaterialCapabilityRequirement::minimum)
                .findFirst()
                .orElse(0.0);
    }

    public boolean requires(SkyIslandMaterialCapability capability) {
        return minimum(capability) > 0.0;
    }
}
