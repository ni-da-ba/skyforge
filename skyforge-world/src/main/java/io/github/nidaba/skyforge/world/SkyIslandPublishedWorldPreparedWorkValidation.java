package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * AUTH-0062 immutable commit-handoff validation for one exact prepared-work envelope.
 *
 * <p>This binds AUTH-0061 currentness validation to the exact work whose preparation provenance is
 * being checked.
 */
public record SkyIslandPublishedWorldPreparedWorkValidation(
        SkyIslandPublishedWorldPreparedWork preparedWork,
        SkyIslandPublishedWorldBindingValidation bindingValidation) {

    public SkyIslandPublishedWorldPreparedWorkValidation {
        preparedWork = Objects.requireNonNull(preparedWork, "preparedWork");
        bindingValidation = Objects.requireNonNull(bindingValidation, "bindingValidation");
        if (!preparedWork.binding().equals(bindingValidation.binding())) {
            throw new IllegalArgumentException(
                    "prepared-work validation does not belong to the exact work binding");
        }
    }

    public SkyIslandPublishedWorldBindingStatus status() {
        return bindingValidation.status();
    }

    public boolean current() {
        return bindingValidation.current();
    }

    /**
     * Requires the exact work binding to be current in the activation state that produced this
     * validation.
     *
     * <p>This remains an identity gate, not an atomic backend commit primitive.
     */
    public void requireCurrent() {
        bindingValidation.requireCurrent();
    }
}
