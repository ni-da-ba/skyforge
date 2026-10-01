package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** Stable diagnostic captured when the one AUTH-0056 candidate planning attempt rejects. */
public record SkyIslandSupportPlannerFailure(
        String exceptionType,
        String message) {

    public SkyIslandSupportPlannerFailure {
        exceptionType = Objects.requireNonNull(exceptionType, "exceptionType");
        message = Objects.requireNonNull(message, "message");
        if (exceptionType.isBlank()) {
            throw new IllegalArgumentException("exceptionType must not be blank");
        }
    }

    public static SkyIslandSupportPlannerFailure from(RuntimeException failure) {
        Objects.requireNonNull(failure, "failure");
        return new SkyIslandSupportPlannerFailure(
                failure.getClass().getName(),
                failure.getMessage() == null ? "" : failure.getMessage());
    }
}
