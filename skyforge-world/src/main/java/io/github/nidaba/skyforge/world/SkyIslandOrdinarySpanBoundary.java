package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.Optional;

/** One parent-reach boundary separating an ordinary span from a source/outlet or owned transition. */
public record SkyIslandOrdinarySpanBoundary(
        SkyIslandHydraulicTransitionBoundaryState state,
        SkyIslandOrdinarySpanBoundaryStatus status,
        Optional<Double> fixedHeadWorldUnits,
        Optional<String> diagnostic) {

    public SkyIslandOrdinarySpanBoundary {
        state = Objects.requireNonNull(state, "state");
        status = Objects.requireNonNull(status, "status");
        fixedHeadWorldUnits = Objects.requireNonNull(fixedHeadWorldUnits, "fixedHeadWorldUnits");
        diagnostic = Objects.requireNonNull(diagnostic, "diagnostic");
        fixedHeadWorldUnits.ifPresent(value -> {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("fixed head must be finite");
            }
        });
        switch (status) {
            case FREE -> {
                if (fixedHeadWorldUnits.isPresent() || diagnostic.isPresent()) {
                    throw new IllegalArgumentException("FREE boundary carries neither head nor deferral");
                }
            }
            case FIXED_HEAD -> {
                if (fixedHeadWorldUnits.isEmpty() || diagnostic.isPresent()) {
                    throw new IllegalArgumentException("FIXED_HEAD boundary requires only a fixed head");
                }
            }
            case DEFERRED -> {
                if (fixedHeadWorldUnits.isPresent() || diagnostic.isEmpty()) {
                    throw new IllegalArgumentException("DEFERRED boundary requires only a diagnostic");
                }
            }
        }
    }

    public static SkyIslandOrdinarySpanBoundary free(
            SkyIslandHydraulicTransitionBoundaryState state) {
        return new SkyIslandOrdinarySpanBoundary(
                state, SkyIslandOrdinarySpanBoundaryStatus.FREE, Optional.empty(), Optional.empty());
    }

    public static SkyIslandOrdinarySpanBoundary fixed(
            SkyIslandHydraulicTransitionBoundaryState state,
            double headWorldUnits) {
        return new SkyIslandOrdinarySpanBoundary(
                state,
                SkyIslandOrdinarySpanBoundaryStatus.FIXED_HEAD,
                Optional.of(headWorldUnits),
                Optional.empty());
    }

    public static SkyIslandOrdinarySpanBoundary deferred(
            SkyIslandHydraulicTransitionBoundaryState state,
            String diagnostic) {
        if (diagnostic == null || diagnostic.isBlank()) {
            throw new IllegalArgumentException("deferred boundary diagnostic must be non-blank");
        }
        return new SkyIslandOrdinarySpanBoundary(
                state,
                SkyIslandOrdinarySpanBoundaryStatus.DEFERRED,
                Optional.empty(),
                Optional.of(diagnostic));
    }
}
