package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.Objects;

/**
 * Immutable backend-neutral environmental inputs consumed by {@link SkyIslandEcologyField}.
 *
 * <p>The fields in this record retain the normalized AUTH-0002/AUTH-0003 semantic meanings. They are
 * not physical SI climate quantities. The current factory adapts one authored descriptor through
 * {@link SkyIslandSemanticFieldSet}; later accepted atmosphere, hydrology, substrate, or other
 * graph-backed producers may supply equivalent semantic fields without giving ecology a dependency
 * on a subsystem service API.
 *
 * <p>The scalar priors are kept explicit because AUTH-0003 currently consumes descriptor-level
 * hydrological and ecological potential. They remain compatibility inputs, not a claim that those
 * two priors are the final ecological state model.
 */
public record SkyIslandEcologyInputFieldSet(
        SkyIslandDescriptor descriptor,
        SkyIslandSemanticField interiority,
        SkyIslandSemanticField temperature,
        SkyIslandSemanticField moisture,
        SkyIslandSemanticField elevationTendency,
        SkyIslandSemanticField exposure,
        double hydrologicalPotentialPrior,
        double ecologicalPotentialPrior) {

    /** Validates one immutable ecology input bundle. */
    public SkyIslandEcologyInputFieldSet {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        interiority = Objects.requireNonNull(interiority, "interiority");
        temperature = Objects.requireNonNull(temperature, "temperature");
        moisture = Objects.requireNonNull(moisture, "moisture");
        elevationTendency = Objects.requireNonNull(elevationTendency, "elevationTendency");
        exposure = Objects.requireNonNull(exposure, "exposure");
        requireNormalized("hydrologicalPotentialPrior", hydrologicalPotentialPrior);
        requireNormalized("ecologicalPotentialPrior", ecologicalPotentialPrior);
    }

    /**
     * Reconstructs the exact environmental inputs used by the accepted AUTH-0003 implementation.
     *
     * <p>This is the compatibility path used by {@link SkyIslandEcologyField#create(SkyIslandDescriptor)}.
     */
    public static SkyIslandEcologyInputFieldSet fromCurrentSemantics(SkyIslandDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        SkyIslandSemanticFieldSet fields = SkyIslandSemanticFieldSet.create(descriptor);
        return new SkyIslandEcologyInputFieldSet(
                descriptor,
                fields.interiority(),
                fields.temperature(),
                fields.moisture(),
                fields.elevationTendency(),
                fields.exposure(),
                descriptor.hydrologicalPotential(),
                descriptor.ecologicalPotential());
    }

    private static void requireNormalized(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
    }
}
