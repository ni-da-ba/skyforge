package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** One hard AUTH-0040 semantic capability floor required by a material-binding request. */
public record SkyIslandMaterialCapabilityRequirement(
        SkyIslandMaterialCapability capability,
        double minimum) {

    public SkyIslandMaterialCapabilityRequirement {
        capability = Objects.requireNonNull(capability, "capability");
        if (!Double.isFinite(minimum) || minimum <= 0.0 || minimum > 1.0) {
            throw new IllegalArgumentException(
                    "material capability requirement minimum must be finite and in (0, 1]");
        }
    }
}
